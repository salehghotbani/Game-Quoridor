import test from 'node:test';
import assert from 'node:assert/strict';
import { GameState } from '../src/game.js';
import { encodeSave, decodeSave, validateConfig, MAX_SAVE_BYTES } from '../src/save.js';

const config = count => ({ playerNames: ['سید صالح 🎮', "50% O'Brien=:#!\\", 'قرمز', 'طلایی'].slice(0, count), mode: 'LOCAL', difficulty: 'HARD' });

test('current two/four-player saves preserve Unicode, names, walls, turns and terminal positions', () => {
  for (const count of [2, 4]) {
    const game = new GameState(count);
    assert(game.move(7, 4));
    assert(game.placeWall(3, 3, 'HORIZONTAL'));
    assert(game.placeWall(5, 5, 'VERTICAL'));
    const settings = config(count), text = encodeSave({ state: game, config: settings });
    assert.match(text, new RegExp(`version=${count === 2 ? 1 : 2}`));
    const decoded = decodeSave(text);
    assert.deepEqual(decoded.state.snapshot(), game.snapshot());
    assert.deepEqual(decoded.config, settings);
    assert.deepEqual(decodeSave('\ufeff' + text).state.snapshot(), game.snapshot());
    decoded.state.reset();
    assert.notDeepEqual(decoded.state.snapshot(), game.snapshot());
  }
  const won = GameState.fromSnapshot({ players: [{ row: 0, col: 3 }, { row: 1, col: 4 }], wallsRemaining: [10, 10], currentPlayer: 0, winner: 0, walls: [] });
  assert.equal(decodeSave(encodeSave({ state: won, config: config(2) })).state.winner, 0);
});

test('Java Properties comments, separators, escapes, continuations and exact duplicate detection', () => {
  const text = encodeSave({ state: new GameState(), config: config(2) });
  const changed = text.replace('blue.name=سید صالح 🎮', '  blue\\.name : Ali\\u0020\\\r\n  Reza')
    .replace('mode=LOCAL', 'mode\tLOCAL').replace('difficulty=HARD', '! ignored comment\\\n# still a comment\ndifficulty:HARD');
  assert.equal(decodeSave(changed).config.playerNames[0], 'Ali Reza');
  assert.throws(() => decodeSave(text + '\\u0062lue.name=duplicate\n'));
  assert.throws(() => decodeSave(text.replace('blue.name=', 'blue.name=\\uZZZZ')));
});

test('strict field grammar rejects unsupported, extra, missing and malformed values atomically', () => {
  const game = new GameState(), before = game.snapshot(), text = encodeSave({ state: game, config: config(2) });
  const corrupt = [
    text.replace('version=1', 'version=3'), text + 'unknown=value\n', text + 'turn=1\n',
    text.replace('turn=0', 'turn=01'), text.replace('turn=0', 'turn=+0'), text.replace('turn=0', 'turn=2147483648'),
    text.replace('winner=-1', 'winner=0'), text.replace('blue.row=8', 'blue.row=0'),
    text.replace('blue.walls=10', 'blue.walls=9'), text.replace('green.row=0', 'green.row=8'),
    text.replace('mode=LOCAL', 'mode=ONLINE'), text.replace('difficulty=HARD', 'difficulty=IMPOSSIBLE'),
    text.replace(/^green.col=.*\n/m, ''), text.replace('wall.count=0', 'wall.count=21'),
    text.replace('blue.name=', 'blue.name=\\uD800'), text + '\ud800',
  ];
  for (const invalid of corrupt) assert.throws(() => decodeSave(invalid));
  assert.deepEqual(game.snapshot(), before);
  assert.throws(() => decodeSave(' '.repeat(MAX_SAVE_BYTES + 1)));
  assert.throws(() => decodeSave('#' + 'آ'.repeat(MAX_SAVE_BYTES / 2)));
  assert.throws(() => encodeSave({ state: game, config: config(4) }));
  const four = encodeSave({ state: new GameState(4), config: config(4) });
  assert.throws(() => decodeSave(four.replace('mode=LOCAL', 'mode=BOT')));
  assert.throws(() => decodeSave(four.replace('player.count=4', 'player.count=3')));
});

test('configuration name checks match Java stripping, codepoints and ISO controls', () => {
  const settings = { playerNames: ['  Ali\u3000', '🎮'.repeat(24)], mode: 'BOT', difficulty: 'MEDIUM' };
  assert.deepEqual(validateConfig(settings).playerNames, ['Ali', '🎮'.repeat(24)]);
  assert.equal(validateConfig({ ...settings, playerNames: ['\u00a0', 'B'] }).playerNames[0], '\u00a0');
  assert.equal(validateConfig({ ...settings, playerNames: ['الف\u200cب', 'B'] }).playerNames[0], 'الف\u200cب');
  for (const name of ['', '  ', 'x'.repeat(25), 'A\0B', 'A\u007fB', '\udc00']) {
    assert.throws(() => validateConfig({ ...settings, playerNames: [name, 'B'] }));
  }
  assert.throws(() => validateConfig({ ...settings, playerNames: ['A', 'B', 'C', 'D'] }));
});
