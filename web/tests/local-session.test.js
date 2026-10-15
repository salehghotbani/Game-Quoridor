import test from 'node:test';
import assert from 'node:assert/strict';
import { GameState } from '../src/game.js';
import { LocalSession } from '../src/local-session.js';

const config = (count = 2, mode = 'LOCAL') => ({ playerNames: ['Blue', 'Green', 'Red', 'Gold'].slice(0, count), mode, difficulty: 'HARD' });
const move = (row, col) => ({ kind: 'move', row, col });

test('local history owns its initial position, configuration and accepted action values', () => {
  const game = new GameState(), settings = config(), session = new LocalSession(game, settings);
  const initial = session.state.snapshot();
  game.move(7, 4); settings.playerNames[0] = '<img src=x onerror=alert(1)>';
  assert.deepEqual(session.state.snapshot(), initial);
  assert.equal(session.config.playerNames[0], 'Blue');
  assert.equal(session.canUndo, false); assert.equal(session.canRedo, false);
  const action = move(7, 4);
  assert(session.play(action));
  action.row = -1;
  assert.equal(session.actions[0].row, 7);
  assert.equal(session.actions[0].player, 0);
  assert.equal(session.revision, 1);
  assert(session.undo());
  assert.deepEqual(session.state.snapshot(), initial);
  assert(session.redo());
  assert.deepEqual(session.state.playerPosition(0), { row: 7, col: 4 });
});

test('robot undo/redo covers a complete human/robot pair and fences an old pending revision', () => {
  const session = new LocalSession(new GameState(), config(2, 'BOT'));
  const initial = session.state.snapshot();
  assert(session.play(move(7, 4)));
  assert(session.isBotTurn);
  const pendingRevision = session.revision;
  assert(session.undo());
  assert(!session.isBotTurn);
  assert.notEqual(session.revision, pendingRevision);
  assert.deepEqual(session.state.snapshot(), initial);
  assert(session.redo());
  assert(session.isBotTurn);
  assert.notEqual(session.revision, pendingRevision);
  assert(session.play(move(1, 4)));
  const completed = session.state.snapshot();
  assert(!session.isBotTurn);
  assert(session.undo());
  assert.deepEqual(session.state.snapshot(), initial);
  assert.equal(session.cursor, 0);
  assert.equal(session.actions.length, 2);
  assert(session.redo());
  assert.deepEqual(session.state.snapshot(), completed);
  assert.equal(session.cursor, 2);
});

test('branching after undo discards only the abandoned future, while invalid actions are atomic', () => {
  const session = new LocalSession(new GameState(), config());
  assert(session.play(move(7, 4))); assert(session.play(move(1, 4)));
  assert(session.undo());
  const before = session.state.snapshot(), revision = session.revision;
  assert(!session.play(move(7, 4)));
  assert.equal(session.revision, revision);
  assert.equal(session.actions.length, 2);
  assert(session.canRedo);
  assert.deepEqual(session.state.snapshot(), before);
  assert(session.play(move(0, 3)));
  assert(!session.canRedo);
  assert.equal(session.actions.length, 2);
  assert.deepEqual(session.actions[1], { kind: 'move', row: 0, col: 3, player: 1 });
  const after = session.state.snapshot(), afterRevision = session.revision;
  assert(!session.redo()); assert.equal(session.revision, afterRevision);
  assert.deepEqual(session.state.snapshot(), after);
});

test('four-player history follows clockwise seats and restores each single ply independently', () => {
  const session = new LocalSession(new GameState(4), config(4));
  const states = [session.state.snapshot()];
  for (const player of [0, 2, 1, 3]) {
    assert.equal(session.state.currentPlayer, player);
    const next = session.state.legalMoves()[0];
    assert(session.play(move(next.row, next.col)));
    states.push(session.state.snapshot());
  }
  assert.deepEqual(session.actions.map(action => action.player), [0, 2, 1, 3]);
  for (let index = 3; index >= 0; index--) { assert(session.undo()); assert.deepEqual(session.state.snapshot(), states[index]); }
  assert(!session.undo());
  for (let index = 1; index <= 4; index++) { assert(session.redo()); assert.deepEqual(session.state.snapshot(), states[index]); }
  assert(!session.redo());
});

test('terminal human and robot wins freeze actions and round-trip through paired history', () => {
  for (const winner of [0, 1]) {
    const game = GameState.fromSnapshot({ players: winner === 0 ? [{ row: 1, col: 3 }, { row: 0, col: 4 }]
      : [{ row: 8, col: 4 }, { row: 7, col: 3 }], wallsRemaining: [10, 10], currentPlayer: 0, winner: -1, walls: [] });
    const session = new LocalSession(game, config(2, 'BOT')), initial = session.state.snapshot();
    if (winner === 1) assert(session.play(move(7, 4)));
    assert(session.play(winner === 0 ? move(0, 3) : move(8, 3)));
    const finished = session.state.snapshot();
    assert.equal(session.state.winner, winner);
    assert(!session.isBotTurn);
    const revision = session.revision;
    assert(!session.play(move(4, 4)));
    assert.equal(session.revision, revision);
    assert(session.undo()); assert.deepEqual(session.state.snapshot(), initial);
    assert(session.redo()); assert.deepEqual(session.state.snapshot(), finished);
  }
});

test('history retains at most1000 plies and1001 independent positions across long games', () => {
  const session = new LocalSession(new GameState(), config());
  for (let index = 0; index < 1204; index++) {
    const player = session.state.currentPlayer, pawn = session.state.playerPosition(player);
    assert(session.play(move(pawn.row, pawn.col === 4 ? 3 : 4)));
    assert.equal(session.state.winner, -1);
  }
  assert.equal(session.actions.length, 1000);
  assert.equal(session.positions.length, 1001);
  assert.equal(session.cursor, 1000);
  const latest = session.state.snapshot();
  let undos = 0;
  while (session.undo()) undos++;
  assert.equal(undos, 1000);
  const oldest = session.state.snapshot();
  assert.equal(session.revision, 2204);
  let redos = 0;
  while (session.redo()) redos++;
  assert.equal(redos, 1000);
  assert.deepEqual(session.state.snapshot(), latest);
  assert(session.undo());
  const pawn = session.state.playerPosition(session.state.currentPlayer);
  assert(session.play(move(pawn.row, pawn.col === 4 ? 5 : 2)));
  assert(!session.canRedo);
  assert.deepEqual(session.positions[0], oldest);
  assert.equal(session.positions.length, 1001);
});
