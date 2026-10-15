import { GameState, ORIENTATIONS } from './game.js';

export const MAX_SAVE_BYTES = 64 * 1024;
const encoder = new TextEncoder();
const whiteSpace = /[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/u;
const malformedUtf16 = text => {
  for (const character of text) {
    const point = character.codePointAt(0);
    if (point >= 0xd800 && point <= 0xdfff) return true;
  }
  return false;
};

function checkedName(value) {
  if (typeof value !== 'string') throw new TypeError('Player name is required');
  const points = [...value];
  let first = 0, last = points.length;
  while (first < last && whiteSpace.test(points[first])) first++;
  while (last > first && whiteSpace.test(points[last - 1])) last--;
  const name = points.slice(first, last).join('');
  if (first === last || last - first > 24 || /[\u0000-\u001f\u007f-\u009f]/u.test(name) || malformedUtf16(name)) {
    throw new TypeError('Player names must contain 1 to 24 characters without control characters');
  }
  return name;
}

/** Matches GameConfig, including Java String.strip rather than JavaScript's broader trim. */
export function validateConfig(config, playerCount = config?.playerNames?.length) {
  if (!config || !Array.isArray(config.playerNames) || ![2, 4].includes(playerCount)
    || config.playerNames.length !== playerCount || !['LOCAL', 'BOT'].includes(config.mode)
    || !['EASY', 'MEDIUM', 'HARD'].includes(config.difficulty) || (config.mode === 'BOT' && playerCount !== 2)) {
    throw new TypeError('Invalid match configuration');
  }
  return { playerNames: config.playerNames.map(checkedName), mode: config.mode, difficulty: config.difficulty };
}

function boundedText(text) {
  if (typeof text !== 'string' || text.length > MAX_SAVE_BYTES || malformedUtf16(text)
    || encoder.encode(text).length > MAX_SAVE_BYTES) throw new TypeError('Save must be valid UTF-8 text of at most 64 KiB');
  return text;
}

function unescapeProperty(text) {
  let result = '';
  for (let index = 0; index < text.length; index++) {
    let character = text[index];
    if (character !== '\\') { result += character; continue; }
    if (++index === text.length) break;
    character = text[index];
    if (character === 'u') {
      const digits = text.slice(index + 1, index + 5);
      if (!/^[0-9a-fA-F]{4}$/.test(digits)) throw new TypeError('Malformed Properties Unicode escape');
      result += String.fromCharCode(Number.parseInt(digits, 16));
      index += 4;
    } else {
      result += ({ t: '\t', n: '\n', r: '\r', f: '\f' })[character] ?? character;
    }
  }
  return result;
}

/** Java Properties.load(Reader) grammar, with the same duplicate-key rejection as SaveStore. */
function parseProperties(text) {
  const data = new Map();
  const lines = text.split(/\r\n|\r|\n/);
  for (let index = 0; index < lines.length; index++) {
    let line = lines[index].replace(/^[ \t\f]+/, '');
    if (!line || line[0] === '#' || line[0] === '!') continue;
    while (true) {
      let slashes = 0;
      for (let end = line.length - 1; end >= 0 && line[end] === '\\'; end--) slashes++;
      if (slashes % 2 === 0) break;
      line = line.slice(0, -1);
      if (++index === lines.length) break;
      line += lines[index].replace(/^[ \t\f]+/, '');
    }
    let end = 0;
    while (end < line.length) {
      if (line[end] === '\\') { end += 2; continue; }
      if (/[ \t\f=:]/.test(line[end])) break;
      end++;
    }
    let valueStart = end;
    while (/[ \t\f]/.test(line[valueStart] ?? '\0')) valueStart++;
    if (line[valueStart] === '=' || line[valueStart] === ':') valueStart++;
    while (/[ \t\f]/.test(line[valueStart] ?? '\0')) valueStart++;
    const key = unescapeProperty(line.slice(0, end));
    if (data.has(key)) throw new TypeError(`Duplicate saved field: ${key}`);
    data.set(key, unescapeProperty(line.slice(valueStart)));
  }
  return data;
}

function escapeProperty(text) {
  return String(text).replace(/[\\\t\n\r\f=:#!]|^ /g, character => ({
    '\\': '\\\\', '\t': '\\t', '\n': '\\n', '\r': '\\r', '\f': '\\f', ' ': '\\ ',
  })[character] ?? `\\${character}`);
}

export function encodeSave({ state, config }) {
  const game = GameState.fromSnapshot(state instanceof GameState ? state.snapshot() : state);
  const settings = validateConfig(config, game.playerCount);
  const snapshot = game.snapshot();
  const data = new Map([['version', game.playerCount === 2 ? '1' : '2']]);
  if (game.playerCount === 4) data.set('player.count', '4');
  data.set('mode', settings.mode);
  data.set('difficulty', settings.difficulty);
  for (let player = 0; player < game.playerCount; player++) {
    const prefix = game.playerCount === 2 ? `${player === 0 ? 'blue' : 'green'}.` : `player.${player}.`;
    data.set(`${prefix}name`, settings.playerNames[player]);
    data.set(`${prefix}row`, snapshot.players[player].row);
    data.set(`${prefix}col`, snapshot.players[player].col);
    data.set(`${prefix}walls`, snapshot.wallsRemaining[player]);
  }
  data.set('turn', game.currentPlayer);
  data.set('winner', game.winner);
  data.set('wall.count', snapshot.walls.length);
  snapshot.walls.forEach((wall, index) => data.set(`wall.${index}`, `${wall.row},${wall.col},${wall.orientation}`));
  return boundedText('# Quoridor save - UTF-8\n' + [...data].map(([key, value]) => `${key}=${escapeProperty(value)}`).join('\n') + '\n');
}

export function decodeSave(text) {
  boundedText(text);
  const data = parseProperties(text.startsWith('\ufeff') ? text.slice(1) : text);
  const required = key => {
    if (!data.has(key)) throw new TypeError(`Missing saved field: ${key}`);
    return data.get(key);
  };
  const integer = key => parseInteger(required(key), key);
  const version = required('version');
  if (!['1', '2'].includes(version)) throw new TypeError('Unsupported saved game version');
  const count = version === '1' ? 2 : integer('player.count');
  if (![2, 4].includes(count)) throw new TypeError('Saved player count must be 2 or 4');
  const wallCount = integer('wall.count');
  if (wallCount < 0 || wallCount > 20) throw new TypeError('Invalid saved wall count');
  const expected = new Set(['version', 'mode', 'difficulty', 'turn', 'winner', 'wall.count']);
  if (version === '2') expected.add('player.count');
  const names = [], players = [], stocks = [], walls = [];
  for (let player = 0; player < count; player++) {
    const prefix = version === '1' ? `${player === 0 ? 'blue' : 'green'}.` : `player.${player}.`;
    for (const suffix of ['name', 'row', 'col', 'walls']) expected.add(prefix + suffix);
    names.push(required(prefix + 'name'));
    players.push({ row: integer(prefix + 'row'), col: integer(prefix + 'col') });
    stocks.push(integer(prefix + 'walls'));
  }
  for (let index = 0; index < wallCount; index++) {
    const key = `wall.${index}`;
    expected.add(key);
    const fields = required(key).split(',');
    if (fields.length !== 3 || !ORIENTATIONS.includes(fields[2])) throw new TypeError('Invalid saved wall');
    walls.push({ row: parseInteger(fields[0], key), col: parseInteger(fields[1], key), orientation: fields[2] });
  }
  if (data.size !== expected.size || [...data.keys()].some(key => !expected.has(key))) throw new TypeError('Missing or unexpected saved fields');
  const config = validateConfig({ playerNames: names, mode: required('mode'), difficulty: required('difficulty') }, count);
  const state = GameState.fromSnapshot({ players, wallsRemaining: stocks, currentPlayer: integer('turn'), winner: integer('winner'), walls });
  return { state, config };
}

function parseInteger(value, field) {
  if (!/^-?(0|[1-9][0-9]*)$/.test(value)) throw new TypeError(`Invalid saved number: ${field}`);
  const number = Number(value);
  if (!Number.isInteger(number) || number < -2147483648 || number > 2147483647) throw new TypeError(`Saved number out of range: ${field}`);
  return number + 0;
}
