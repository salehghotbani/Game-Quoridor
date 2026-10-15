import { decodeSave } from './save.js';

const REQUEST_LIMIT = 2048;
const RESPONSE_LIMIT = 192 * 1024;
const FORM_TYPE = 'application/x-www-form-urlencoded; charset=UTF-8';
const encoder = new TextEncoder();
const javaWhitespace = '[\\u0009-\\u000D\\u001C-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]';
const edgeWhitespace = new RegExp(`^${javaWhitespace}+|${javaWhitespace}+$`, 'gu');

/** Stable, bounded codes only: response bodies, URLs and credentials never enter error messages. */
export class OnlineError extends Error {
  constructor(code, httpStatus = 0) {
    super(`Online request failed: ${code}`);
    this.name = 'OnlineError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

/** Browser transport. Membership lives only in private memory; callers own polling and UI epochs. */
export class OnlineClient {
  #endpoint;
  #fetch;
  #timeout;
  #entryTimeout;
  #token = null;
  #pin = null;
  #active = null;
  #tail = Promise.resolve();
  #closing = false;
  #closed = false;
  #closePromise = null;

  constructor(endpoint, options = {}) {
    this.#endpoint = checkedEndpoint(endpoint);
    this.#fetch = options.fetch ?? globalThis.fetch.bind(globalThis);
    this.#timeout = checkedTimeout(options.timeout ?? 12000, 12000);
    this.#entryTimeout = checkedTimeout(options.entryTimeout ?? 90000, 90000);
  }

  createPrivate(count, name) {
    return this.#enter('create', { count: checkedCount(count), name: checkedName(name) });
  }

  joinPrivate(code, name) {
    const normalized = typeof code === 'string' ? code.trim().toUpperCase() : '';
    if (!/^[A-Z2-9]{8}$/.test(normalized)) throw new OnlineError('INVALID_REQUEST');
    return this.#enter('join', { code: normalized, name: checkedName(name) });
  }

  findMatch(count, name) {
    return this.#enter('match', { count: checkedCount(count), name: checkedName(name) });
  }

  state() {
    return this.#enqueue(async () => {
      this.#requireMembership();
      return this.#accept(await this.#request('state', null, true), false);
    });
  }

  move(row, col, revision) {
    return this.#act('move', { row: checkedInteger(row, 0, 8), col: checkedInteger(col, 0, 8), revision: checkedInteger(revision, 0, Number.MAX_SAFE_INTEGER) });
  }

  wall(row, col, orientation, revision) {
    if (!['HORIZONTAL', 'VERTICAL'].includes(orientation)) throw new OnlineError('INVALID_REQUEST');
    return this.#act('wall', { row: checkedInteger(row, 0, 7), col: checkedInteger(col, 0, 7), orientation, revision: checkedInteger(revision, 0, Number.MAX_SAFE_INTEGER) });
  }

  leave() {
    return this.#enqueue(async () => {
      this.#requireMembership();
      try {
        return this.#accept(await this.#request('leave', {}, true), false);
      } finally {
        this.#token = null;
        this.#pin = null;
      }
    });
  }

  hasMembership() {
    return this.#token !== null;
  }

  /** Cancels an in-flight read/action without discarding the reconnect credential. */
  abort() {
    this.#active?.controller.abort();
  }

  close() {
    if (this.#closePromise) return this.#closePromise;
    this.#closing = true;
    // Let entry finish so its newly reserved seat can be released with the received token.
    // Authenticated operations can be interrupted safely; no POST is automatically replayed.
    if (this.#active?.authenticated) this.abort();
    this.#closePromise = this.#tail.then(async () => {
      try {
        if (this.#token) await this.#request('leave', {}, true);
      } catch {
        // Best effort only; the server's bounded idle timeout handles a disconnected browser.
      } finally {
        this.#token = null;
        this.#pin = null;
        this.#closed = true;
      }
    });
    return this.#closePromise;
  }

  #enter(action, fields) {
    return this.#enqueue(async () => {
      if (this.#token) throw new OnlineError('ALREADY_IN_ROOM', 409);
      const response = await this.#request(action, fields, false);
      if (!/^[A-Za-z0-9_-]{43}$/.test(response.token ?? '')) throw invalidResponse();
      const state = this.#accept(response, true);
      if ((Object.hasOwn(fields, 'count') && state.playerCount !== Number(fields.count))
        || state.playerNames[state.playerIndex] !== fields.name) throw invalidResponse();
      this.#token = response.token;
      return state;
    });
  }

  #act(action, fields) {
    return this.#enqueue(async () => {
      this.#requireMembership();
      return this.#accept(await this.#request(action, fields, true), false);
    });
  }

  #enqueue(operation) {
    const task = this.#tail.then(() => {
      if (this.#closing || this.#closed) throw new OnlineError('CANCELLED');
      return operation();
    });
    this.#tail = task.catch(() => {});
    return task;
  }

  #requireMembership() {
    if (!this.#token) throw new OnlineError('NO_MEMBERSHIP', 401);
  }

  #accept(fields, credential) {
    const state = decodeState(fields, credential);
    const pin = this.#pin;
    if (!credential && (!pin || state.roomCode !== pin.roomCode || state.playerCount !== pin.playerCount
      || state.revision < pin.revision || (pin.playingSeat !== null && (state.playerIndex !== pin.playingSeat || state.status === 'WAITING'))
      || (pin.closed && state.status !== 'CLOSED'))) throw invalidResponse();
    this.#pin = {
      roomCode: state.roomCode, playerCount: state.playerCount, revision: state.revision,
      playingSeat: state.session ? state.playerIndex : (credential ? null : pin.playingSeat),
      closed: state.status === 'CLOSED',
    };
    return state;
  }

  async #request(action, fields, authenticated) {
    const body = fields === null ? null : encodeForm(fields);
    if (body !== null && encoder.encode(body).byteLength > REQUEST_LIMIT) throw new OnlineError('INVALID_REQUEST');
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, authenticated ? this.#timeout : this.#entryTimeout);
    this.#active = { controller, authenticated };
    try {
      const headers = { Accept: FORM_TYPE };
      if (body !== null) headers['Content-Type'] = FORM_TYPE;
      if (authenticated) headers.Authorization = `Bearer ${this.#token}`;
      const response = await this.#fetch(`${this.#endpoint}api/${action}`, {
        method: body === null ? 'GET' : 'POST', headers, body,
        signal: controller.signal, mode: 'cors', redirect: 'error', credentials: 'omit',
        cache: 'no-store', referrerPolicy: 'no-referrer',
      });
      if (response.redirected || response.type === 'opaqueredirect' || response.status < 200
        || (response.status >= 300 && response.status < 400) || response.status >= 600) throw invalidResponse();
      if (!/^application\/x-www-form-urlencoded(?:\s*;|$)/i.test(response.headers.get('Content-Type') ?? '')) throw invalidResponse();
      const data = decodeForm(await readBounded(response));
      if (response.status !== 200) {
        if (!exactKeys(data, ['error', 'message']) || !/^[A-Z_]{1,40}$/.test(data.error)) throw invalidResponse();
        throw new OnlineError(data.error, response.status);
      }
      return data;
    } catch (error) {
      if (controller.signal.aborted) throw new OnlineError(timedOut ? 'TIMEOUT' : 'CANCELLED');
      if (error instanceof OnlineError) throw error;
      throw new OnlineError('NETWORK_ERROR');
    } finally {
      clearTimeout(timer);
      this.#active = null;
    }
  }
}

function checkedEndpoint(source) {
  try {
    if (typeof source !== 'string' || source.length > 2048) throw new Error();
    const url = new URL(source);
    const local = url.hostname === 'localhost' || url.hostname === '[::1]' || /^127(?:\.(?:0|[1-9][0-9]{0,2})){3}$/.test(url.hostname);
    if (url.username || url.password || url.search || url.hash || url.pathname !== '/'
      || !['https:', 'http:'].includes(url.protocol) || (url.protocol === 'http:' && !local)
      || (source !== url.origin && source !== `${url.origin}/`)) throw new Error();
    return `${url.origin}/`;
  } catch {
    throw new OnlineError('INVALID_ENDPOINT');
  }
}

function checkedTimeout(value, maximum) {
  if (!Number.isInteger(value) || value < 1 || value > maximum) throw new OnlineError('INVALID_REQUEST');
  return value;
}

function checkedInteger(value, minimum, maximum) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) throw new OnlineError('INVALID_REQUEST');
  return String(value);
}

function checkedCount(value) {
  if (value !== 2 && value !== 4) throw new OnlineError('INVALID_REQUEST');
  return String(value);
}

function checkedName(value) {
  if (typeof value !== 'string') throw new OnlineError('INVALID_REQUEST');
  const name = value.replace(edgeWhitespace, '');
  if (!name || [...name].length > 24 || /[\u0000-\u001F\u007F-\u009F]/u.test(name)
    || [...name].some(point => point.length === 1 && /[\uD800-\uDFFF]/u.test(point))) throw new OnlineError('INVALID_REQUEST');
  return name;
}

function encodeForm(fields) {
  return Object.entries(fields).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&');
}

async function readBounded(response) {
  const length = response.headers.get('Content-Length');
  if (length !== null && (!/^[0-9]+$/.test(length) || Number(length) > RESPONSE_LIMIT)) throw invalidResponse();
  if (!response.body || typeof response.body.getReader !== 'function') throw invalidResponse();
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > RESPONSE_LIMIT) {
        await reader.cancel().catch(() => {});
        throw invalidResponse();
      }
      chunks.push(value);
    }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    return bytes;
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally {
    reader.releaseLock();
  }
}

function decodeForm(bytes) {
  if (bytes.some(byte => byte > 127)) throw invalidResponse();
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  const result = Object.create(null);
  if (!text) return result;
  const pairs = text.split('&');
  if (pairs.length > 1100) throw invalidResponse();
  try {
    for (const pair of pairs) {
      const separator = pair.indexOf('=');
      if (separator <= 0) throw invalidResponse();
      const key = decodeURIComponent(pair.slice(0, separator).replaceAll('+', ' '));
      const value = decodeURIComponent(pair.slice(separator + 1).replaceAll('+', ' '));
      if (!/^[A-Za-z][A-Za-z0-9]*$/.test(key) || Object.hasOwn(result, key)) throw invalidResponse();
      result[key] = value;
    }
  } catch { throw invalidResponse(); }
  return result;
}

function number(fields, key, minimum, maximum) {
  const text = fields[key];
  if (typeof text !== 'string' || !/^(0|[1-9][0-9]{0,18})$/.test(text)) throw invalidResponse();
  const value = Number(text);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) throw invalidResponse();
  return value;
}

function exactKeys(fields, expected) {
  return Object.keys(fields).length === expected.length && expected.every(key => Object.hasOwn(fields, key));
}

function decodeState(fields, credential) {
  const count = number(fields, 'count', 2, 4);
  if (count !== 2 && count !== 4) throw invalidResponse();
  const nameCount = number(fields, 'names', 1, count);
  const moveCount = number(fields, 'moves', 0, 1000);
  const expected = ['protocol', 'room', 'status', 'player', 'count', 'revision', 'names', 'moves', 'reason'];
  if (credential) expected.push('token');
  const names = [];
  const moves = [];
  for (let index = 0; index < nameCount; index++) {
    const key = `name${index}`;
    expected.push(key);
    try { if (checkedName(fields[key]) !== fields[key]) throw invalidResponse(); } catch { throw invalidResponse(); }
    names.push(fields[key]);
  }
  for (let index = 0; index < moveCount; index++) {
    const key = `move${index}`;
    expected.push(key);
    const move = fields[key];
    if (!/^[0-3] (M [0-8] [0-8]|W [0-7] [0-7] (HORIZONTAL|VERTICAL))$/.test(move ?? '') || Number(move[0]) >= count) throw invalidResponse();
    moves.push(move);
  }
  let session = null;
  if (Object.hasOwn(fields, 'session')) {
    expected.push('session');
    try {
      const encoded = fields.session;
      if (!encoded || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded) || encoded.length > 87384) throw invalidResponse();
      const binary = atob(encoded);
      if (binary.length > 65536 || btoa(binary) !== encoded) throw invalidResponse();
      const text = new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(binary, char => char.charCodeAt(0)));
      const decoded = decodeSave(text);
      const game = decoded.state.snapshot();
      if (decoded.config.mode !== 'LOCAL' || game.players.length !== count || decoded.config.playerNames.length !== names.length
        || names.some((name, index) => name !== decoded.config.playerNames[index])) throw invalidResponse();
      session = { game, config: decoded.config };
    } catch { throw invalidResponse(); }
  }
  const status = fields.status;
  const reason = fields.reason;
  const playerIndex = number(fields, 'player', 0, count - 1);
  if (!exactKeys(fields, expected) || fields.protocol !== '1' || !/^[A-Z2-9]{8}$/.test(fields.room ?? '')
    || !/^[A-Z_]{0,32}$/.test(reason ?? '') || !['WAITING', 'PLAYING', 'CLOSED'].includes(status)
    || playerIndex >= names.length || (status === 'PLAYING' && (!session || session.game.winner >= 0 || nameCount !== count))
    || (status === 'WAITING' && (session !== null || nameCount >= count || moveCount !== 0))
    || (status !== 'CLOSED' && reason !== '') || (status === 'CLOSED' && !reason)
    || (reason === 'FINISHED' && (!session || session.game.winner < 0))) throw invalidResponse();
  return deepFreeze({ roomCode: fields.room, status, playerIndex, playerCount: count, playerNames: names,
    revision: number(fields, 'revision', 0, Number.MAX_SAFE_INTEGER), session, reason, moves });
}

function deepFreeze(value) {
  if (value && typeof value === 'object') {
    for (const nested of Object.values(value)) deepFreeze(nested);
    Object.freeze(value);
  }
  return value;
}

function invalidResponse() {
  return new OnlineError('INVALID_RESPONSE');
}
