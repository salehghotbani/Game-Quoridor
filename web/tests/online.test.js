import test from 'node:test';
import assert from 'node:assert/strict';
import { OnlineClient, OnlineError } from '../src/online.js';
import { GameState } from '../src/game.js';
import { encodeSave } from '../src/save.js';

const TOKEN = 'A'.repeat(43);
const ROOM = 'ABCDEFGH';
const TYPE = 'application/x-www-form-urlencoded; charset=UTF-8';
const form = fields => new URLSearchParams(fields).toString();
const response = (fields, status = 200) => new Response(form(fields), { status, headers: { 'Content-Type': TYPE } });
const errorCode = code => error => error instanceof OnlineError && error.code === code;

function stateFields({ count = 2, names = ['Blue'], player = 0, revision = 1, status = 'WAITING', reason = '', moves = [], game = null, token = true, room = ROOM } = {}) {
  const fields = { protocol: '1', room, status, player, count, revision, names: names.length, reason, moves: moves.length };
  names.forEach((name, index) => { fields[`name${index}`] = name; });
  moves.forEach((move, index) => { fields[`move${index}`] = move; });
  if (game) fields.session = Buffer.from(encodeSave({ state: game, config: { playerNames: names, mode: 'LOCAL', difficulty: 'EASY' } }), 'utf8').toString('base64');
  if (token) fields.token = TOKEN;
  return fields;
}

function fakeClient(replies, options = {}) {
  const requests = [];
  const client = new OnlineClient('https://server.example', {
    ...options,
    fetch: async (url, init) => {
      requests.push({ url, init });
      const reply = replies.shift();
      assert.notEqual(reply, undefined, 'Unexpected network request');
      return typeof reply === 'function' ? reply(url, init) : reply;
    },
  });
  return { client, requests };
}

async function until(predicate) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 1));
  }
  assert.fail('Test request did not begin');
}

test('only base HTTPS endpoints or explicit loopback HTTP are accepted', async () => {
  for (const endpoint of ['https://server.example', 'https://server.example:8443/', 'http://localhost:5173', 'http://127.0.0.1:8080', 'http://[::1]:8080']) {
    const client = new OnlineClient(endpoint);
    await client.close();
  }
  for (const endpoint of ['http://public.example', 'http://192.168.1.1:8080', 'http://10.0.2.2:8080', 'https://user:secret@server.example', 'https://server.example/api', 'https://server.example?token=secret', 'https://server.example#x', 'https://server.example:443', ' https://server.example', 'file:///tmp/server', 'javascript:alert(1)']) {
    assert.throws(() => new OnlineClient(endpoint), errorCode('INVALID_ENDPOINT'));
  }
});

test('membership remains private and bearer authentication uses headers only', async () => {
  const { client, requests } = fakeClient([
    response(stateFields({ names: ['آبی 🎮'] })),
    response(stateFields({ names: ['آبی 🎮'], token: false })),
    response(stateFields({ names: ['آبی 🎮'], token: false, revision: 2, status: 'CLOSED', reason: 'LEFT' })),
  ]);
  const entered = await client.createPrivate(2, '  آبی 🎮  ');
  assert.equal(entered.playerNames[0], 'آبی 🎮');
  assert.equal(client.hasMembership(), true);
  assert.equal(JSON.stringify(entered).includes(TOKEN), false);
  assert.equal(JSON.stringify(client), '{}');
  assert.throws(() => entered.playerNames.push('Injected'), TypeError);
  await client.state();
  await client.leave();
  assert.equal(client.hasMembership(), false);
  for (const { url, init } of requests) {
    assert.equal(url.includes(TOKEN), false);
    assert.equal(init.body?.includes(TOKEN) ?? false, false);
    assert.equal(init.credentials, 'omit');
    assert.equal(init.redirect, 'error');
    assert.equal(init.cache, 'no-store');
    assert.equal(init.referrerPolicy, 'no-referrer');
  }
  assert.equal(requests[0].init.headers.Authorization, undefined);
  assert.equal(requests[1].init.headers.Authorization, `Bearer ${TOKEN}`);
  assert.equal(new URLSearchParams(requests[0].init.body).get('name'), 'آبی 🎮');
  await client.close();
});

test('invalid outgoing arguments never issue a network request', async () => {
  const { client, requests } = fakeClient([]);
  for (const name of ['', ' '.repeat(4), '\uD800', 'line\nbreak', 'x'.repeat(25)]) {
    assert.throws(() => client.createPrivate(2, name), errorCode('INVALID_REQUEST'));
  }
  assert.throws(() => client.findMatch(3, 'Blue'), errorCode('INVALID_REQUEST'));
  assert.throws(() => client.joinPrivate('bad', 'Blue'), errorCode('INVALID_REQUEST'));
  assert.throws(() => client.move(-1, 0, 1), errorCode('INVALID_REQUEST'));
  assert.throws(() => client.wall(8, 0, 'VERTICAL', 1), errorCode('INVALID_REQUEST'));
  assert.throws(() => client.wall(0, 0, 'DIAGONAL', 1), errorCode('INVALID_REQUEST'));
  await assert.rejects(client.state(), errorCode('NO_MEMBERSHIP'));
  assert.equal(requests.length, 0);
  await client.close();
});

test('strict forms, credentials and public room fields reject corrupt replies', async () => {
  const valid = stateFields();
  const mutations = [
    fields => { delete fields.room; }, fields => { fields.extra = 'unexpected'; },
    fields => { fields.protocol = '2'; }, fields => { fields.token = 'bad'; },
    fields => { fields.count = '3'; }, fields => { fields.player = '1'; },
    fields => { fields.revision = '-1'; }, fields => { fields.revision = '9007199254740993'; },
    fields => { fields.revision = '01'; }, fields => { fields.name0 = 'Untrimmed '; },
    fields => { fields.name0 = 'x'.repeat(25); },
    fields => { fields.status = 'PLAYING'; }, fields => { fields.reason = 'LEFT'; },
    fields => { fields.moves = '1001'; }, fields => { fields.moves = '1'; fields.move0 = '0 M 7 4'; },
  ];
  for (const mutate of mutations) {
    const fields = { ...valid }; mutate(fields);
    const { client } = fakeClient([response(fields)]);
    await assert.rejects(client.createPrivate(2, 'Blue'), errorCode('INVALID_RESPONSE'));
    assert.equal(client.hasMembership(), false);
    await client.close();
  }
  for (const body of [form(valid) + '&room=ABCDEFGH', form(valid) + '&broken', form(valid).replace('name0=Blue', 'name0=%ED%A0%80'), 'name=%C3%28', 'name=%XX', 'name=%', new Uint8Array([255])]) {
    const { client } = fakeClient([new Response(body, { headers: { 'Content-Type': TYPE } })]);
    await assert.rejects(client.createPrivate(2, 'Blue'), errorCode('INVALID_RESPONSE'));
    await client.close();
  }
});

test('playing replies validate save configuration, history and immutable snapshots', async () => {
  const game = new GameState(2);
  const playing = stateFields({ names: ['Blue', 'Green'], status: 'PLAYING', revision: 2, game });
  const corruptions = [
    fields => { fields.session = '%%%'; }, fields => { fields.name1 = 'Mismatch'; },
    fields => { fields.moves = 1; fields.move0 = '3 M 7 4'; },
    fields => { fields.moves = 1; fields.move0 = '0 W 8 0 VERTICAL'; },
    fields => { fields.moves = 1; fields.move0 = '0 W 3 3 DIAGONAL'; },
    fields => { fields.session = Buffer.from(encodeSave({ state: game, config: { playerNames: ['Blue', 'Green'], mode: 'BOT', difficulty: 'HARD' } })).toString('base64'); },
  ];
  for (const mutate of corruptions) {
    const fields = { ...playing }; mutate(fields);
    const { client } = fakeClient([response(fields)]);
    await assert.rejects(client.createPrivate(2, 'Blue'), errorCode('INVALID_RESPONSE'));
    await client.close();
  }
  const { client } = fakeClient([response(playing), response({ error: 'UNAUTHORIZED', message: 'not exposed' }, 401)]);
  const state = await client.createPrivate(2, 'Blue');
  assert.deepEqual(state.session.game, game.snapshot());
  assert.throws(() => { state.session.game.players[0].row = 0; }, TypeError);
  assert.throws(() => { state.session.config.playerNames[0] = 'Changed'; }, TypeError);
  await client.close();
});

test('server errors are bounded codes and never repeat raw error text', async () => {
  const { client } = fakeClient([response({ error: 'ROOM_FULL', message: `sensitive ${TOKEN}` }, 409)]);
  await assert.rejects(client.joinPrivate(ROOM, 'Blue'), error => {
    assert.equal(error.code, 'ROOM_FULL');
    assert.equal(error.httpStatus, 409);
    assert.equal(error.message.includes(TOKEN), false);
    return true;
  });
  await client.close();
  for (const fields of [{ error: 'bad error', message: 'x' }, { error: 'ROOM_FULL', message: 'x', token: TOKEN }]) {
    const entry = fakeClient([response(fields, 409)]).client;
    await assert.rejects(entry.createPrivate(2, 'Blue'), errorCode('INVALID_RESPONSE'));
    await entry.close();
  }
});

test('unsupported, redirected and oversized responses fail without replay', async () => {
  const replies = [
    new Response('html', { headers: { 'Content-Type': 'text/html' } }),
    new Response(form(stateFields()), { status: 302, headers: { 'Content-Type': TYPE, Location: 'https://evil.example' } }),
    new Response(form(stateFields()), { headers: { 'Content-Type': TYPE, 'Content-Length': '196609' } }),
    new Response(new Uint8Array(196609), { headers: { 'Content-Type': TYPE } }),
  ];
  for (const reply of replies) {
    const { client, requests } = fakeClient([reply]);
    await assert.rejects(client.createPrivate(2, 'Blue'), errorCode('INVALID_RESPONSE'));
    assert.equal(requests.length, 1);
    await client.close();
  }
  const { client, requests } = fakeClient([() => { throw new TypeError('Redirect or network error with secret'); }]);
  await assert.rejects(client.createPrivate(2, 'Blue'), errorCode('NETWORK_ERROR'));
  assert.equal(requests.length, 1);
  await client.close();
});

test('room identity, seat, revision and lifecycle cannot roll back after playing', async () => {
  const playing = stateFields({ names: ['Blue', 'Green'], status: 'PLAYING', revision: 3, game: new GameState(2) });
  for (const bad of [
    { ...playing, token: undefined, revision: 2 }, { ...playing, token: undefined, room: 'BCDEFGHJ' },
    { ...playing, token: undefined, player: 1 }, stateFields({ token: false, revision: 4 }),
  ]) {
    delete bad.token;
    const { client } = fakeClient([response(playing), response(bad), response({ error: 'UNAUTHORIZED', message: 'x' }, 401)]);
    await client.createPrivate(2, 'Blue');
    await assert.rejects(client.state(), errorCode('INVALID_RESPONSE'));
    assert.equal(client.hasMembership(), true, 'Malformed state preserves reconnect credential');
    await client.close();
  }
  const closed = { ...playing, token: undefined, status: 'CLOSED', reason: 'PLAYER_LEFT', revision: 4 }; delete closed.token;
  const resumed = { ...playing, token: undefined, revision: 5 }; delete resumed.token;
  const { client } = fakeClient([response(playing), response(closed), response(resumed), response({ error: 'UNAUTHORIZED', message: 'x' }, 401)]);
  await client.createPrivate(2, 'Blue');
  assert.equal((await client.state()).status, 'CLOSED');
  await assert.rejects(client.state(), errorCode('INVALID_RESPONSE'));
  await client.close();
});

test('waiting-seat compaction remains valid before a match starts', async () => {
  const { client } = fakeClient([
    response(stateFields({ count: 4, names: ['Other', 'Blue'], player: 1 })),
    response(stateFields({ count: 4, names: ['Blue'], player: 0, revision: 2, token: false })),
    response({ error: 'UNAUTHORIZED', message: 'x' }, 401),
  ]);
  await client.findMatch(4, 'Blue');
  assert.equal((await client.state()).playerIndex, 0);
  await client.close();
});

test('deadlines and explicit abort terminate requests; a cancelled poll preserves membership', async () => {
  const hang = (_url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true });
  });
  const timed = fakeClient([hang], { entryTimeout: 20 }).client;
  await assert.rejects(timed.createPrivate(2, 'Blue'), errorCode('TIMEOUT'));
  await timed.close();
  const { client, requests } = fakeClient([
    response(stateFields()), hang, response(stateFields({ token: false, revision: 2 })),
    response(stateFields({ token: false, revision: 3, status: 'CLOSED', reason: 'LEFT' })),
  ]);
  await client.createPrivate(2, 'Blue');
  const pending = client.state();
  await until(() => requests.length === 2);
  client.abort();
  await assert.rejects(pending, errorCode('CANCELLED'));
  assert.equal(client.hasMembership(), true);
  assert.equal((await client.state()).revision, 2);
  await client.close();
});

test('deadline includes a streaming response body rather than only its headers', async () => {
  const slowBody = (_url, init) => {
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('protocol=1&'));
        init.signal.addEventListener('abort', () => controller.error(new DOMException('Aborted', 'AbortError')), { once: true });
      },
    });
    return new Response(body, { headers: { 'Content-Type': TYPE } });
  };
  const { client } = fakeClient([
    response(stateFields()), slowBody, response(stateFields({ token: false, revision: 2 })),
    response(stateFields({ token: false, revision: 3, status: 'CLOSED', reason: 'LEFT' })),
  ], { timeout: 20 });
  await client.createPrivate(2, 'Blue');
  await assert.rejects(client.state(), errorCode('TIMEOUT'));
  assert.equal(client.hasMembership(), true);
  assert.equal((await client.state()).revision, 2);
  await client.close();
});

test('concurrent callers serialize requests so polls cannot race room revisions', async () => {
  let complete;
  const { client, requests } = fakeClient([
    response(stateFields()), () => new Promise(resolve => { complete = resolve; }),
    response(stateFields({ token: false, revision: 3 })),
    response(stateFields({ token: false, revision: 4, status: 'CLOSED', reason: 'LEFT' })),
  ]);
  await client.createPrivate(2, 'Blue');
  const first = client.state();
  const second = client.state();
  await until(() => complete !== undefined);
  assert.equal(requests.length, 2);
  complete(response(stateFields({ token: false, revision: 2 })));
  assert.deepEqual((await Promise.all([first, second])).map(state => state.revision), [2, 3]);
  await client.close();
});

test('closing during entry obtains the reserved credential and releases exactly that seat', async () => {
  let complete;
  const { client, requests } = fakeClient([
    () => new Promise(resolve => { complete = resolve; }),
    response(stateFields({ token: false, revision: 2, status: 'CLOSED', reason: 'LEFT' })),
  ]);
  const entering = client.createPrivate(2, 'Blue');
  await until(() => complete !== undefined);
  const closing = client.close();
  complete(response(stateFields()));
  await entering;
  await closing;
  assert.equal(client.hasMembership(), false);
  assert.equal(requests.length, 2);
  assert.equal(requests[1].url.endsWith('/api/leave'), true);
  assert.equal(requests[1].init.headers.Authorization, `Bearer ${TOKEN}`);
  await assert.rejects(client.state(), errorCode('CANCELLED'));
  assert.equal(client.close(), closing);
});

test('leave clears expired credentials and permits a fresh membership', async () => {
  const { client } = fakeClient([
    response(stateFields()), response({ error: 'UNAUTHORIZED', message: 'expired' }, 401),
    response(stateFields({ room: 'BCDEFGHJ' })), response({ error: 'UNAUTHORIZED', message: 'x' }, 401),
  ]);
  await client.createPrivate(2, 'Blue');
  await assert.rejects(client.leave(), errorCode('UNAUTHORIZED'));
  assert.equal(client.hasMembership(), false);
  assert.equal((await client.createPrivate(2, 'Blue')).roomCode, 'BCDEFGHJ');
  await client.close();
});

const server = process.env.QUORIDOR_TEST_SERVER;
test('browser transport interoperates with real Java private two/four-player rooms', { skip: !server, timeout: 30000 }, async () => {
  for (const count of [2, 4]) {
    const clients = Array.from({ length: count }, () => new OnlineClient(server));
    try {
      const waiting = await clients[0].createPrivate(count, 'آبی 🎮');
      for (let player = 1; player < count; player++) await clients[player].joinPrivate(waiting.roomCode, `Player ${player}`);
      const playing = await clients[0].state();
      assert.equal(playing.status, 'PLAYING');
      assert.equal(playing.session.game.players.length, count);
      const moved = await clients[0].move(7, 4, playing.revision);
      assert.deepEqual(moved.moves, ['0 M 7 4']);
      const next = count === 2 ? 1 : 2;
      const wall = await clients[next].wall(3, 3, 'HORIZONTAL', moved.revision);
      assert.equal(wall.session.game.wallsRemaining[next], 20 / count - 1);
      assert.deepEqual(wall.moves, ['0 M 7 4', `${next} W 3 3 HORIZONTAL`]);
      await assert.rejects(clients[0].move(6, 4, moved.revision), errorCode('STALE_REVISION'));
      if (count === 4) {
        assert.equal(wall.session.game.currentPlayer, 1);
        const green = await clients[1].move(1, 4, wall.revision);
        assert.equal(green.session.game.currentPlayer, 3);
        const gold = await clients[3].move(4, 7, green.revision);
        assert.equal(gold.session.game.currentPlayer, 0);
      }
      await clients[next].leave();
      assert.equal((await clients[0].state()).status, 'CLOSED');
    } finally { await Promise.all(clients.map(client => client.close())); }
  }
});

test('real Java matchmaking separates counts/private rooms and removes cancelled seats', { skip: !server, timeout: 30000 }, async () => {
  const clients = Array.from({ length: 8 }, () => new OnlineClient(server));
  try {
    const privateRoom = await clients[0].createPrivate(4, 'Private');
    const cancelled = await clients[1].findMatch(2, 'Cancelled');
    await clients[1].leave();
    const publicTwo = await clients[2].findMatch(2, 'Two');
    assert.notEqual(publicTwo.roomCode, cancelled.roomCode);
    const publicFour = await clients[3].findMatch(4, 'Four');
    assert.notEqual(publicFour.roomCode, privateRoom.roomCode);
    assert.notEqual(publicFour.roomCode, publicTwo.roomCode);
    const paired = await clients[4].findMatch(2, 'Peer');
    assert.equal(paired.roomCode, publicTwo.roomCode);
    assert.equal(paired.status, 'PLAYING');
    await clients[5].findMatch(4, 'Second');
    const third = await clients[6].findMatch(4, 'Third');
    assert.equal(third.roomCode, publicFour.roomCode);
    assert.equal(third.status, 'WAITING');
    const fourth = await clients[7].findMatch(4, 'Fourth');
    assert.equal(fourth.roomCode, publicFour.roomCode);
    assert.equal(fourth.status, 'PLAYING');
    assert.equal(fourth.session.game.players.length, 4);
  } finally { await Promise.all(clients.map(client => client.close())); }
});
