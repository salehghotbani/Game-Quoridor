import test from 'node:test';
import assert from 'node:assert/strict';
import { GameState, applyAction } from '../src/game.js';
import { chooseAction, DIFFICULTIES } from '../src/bot.js';

const cell = (row, col) => ({ row, col });
function position(blue, green, turn = 0, walls = [], stocks = [10, 10]) {
  return GameState.fromSnapshot({ players: [blue, green], currentPlayer: turn, winner: -1, walls, wallsRemaining: stocks });
}
function seeded(seed) { return () => ((seed = Math.imul(seed, 1664525) + 1013904223 >>> 0) / 2 ** 32); }
function choice(game, difficulty, random = seeded(17)) {
  const before = game.snapshot(), started = performance.now();
  const action = chooseAction(before, difficulty, { random });
  assert(performance.now() - started < 1500, `${difficulty} decision exceeded1.5sec`);
  assert.deepEqual(game.snapshot(), before);
  assert.deepEqual(before, game.snapshot(), 'input snapshot not mutated');
  assert(action && applyAction(game.copy(), action));
  return action;
}

test('all bot levels take immediate wins for either side and reject four-player games', () => {
  for (const difficulty of DIFFICULTIES) for (const player of [0, 1]) {
    const game = position(player === 0 ? cell(1, 4) : cell(8, 0), player === 1 ? cell(7, 4) : cell(0, 0), player);
    assert(applyAction(game, choice(game, difficulty)));
    assert.equal(game.winner, player);
    assert.equal(chooseAction(game.snapshot(), difficulty), null);
  }
  assert.throws(() => chooseAction(new GameState(4).snapshot(), 'EASY'));
  assert.throws(() => chooseAction(new GameState().snapshot(), 'UNKNOWN'));
});

test('medium and hard prevent immediate opponent wins and place useful delaying walls', () => {
  for (const difficulty of ['MEDIUM', 'HARD']) {
    for (const player of [0, 1]) {
      const game = position(player === 0 ? cell(5, 4) : cell(1, 4), player === 1 ? cell(3, 4) : cell(7, 4), player);
      assert(applyAction(game, choice(game, difficulty)));
      assert(!game.legalMoves().some(move => game.isGoal(game.currentPlayer, move)));
    }
    const behind = position(cell(4, 4), cell(0, 0), 1, [{ row: 3, col: 4, orientation: 'VERTICAL' }], [9, 10]);
    const blueBefore = behind.goalDistance(0), greenBefore = behind.goalDistance(1);
    const action = choice(behind, difficulty);
    assert.equal(action.kind, 'wall');
    assert(applyAction(behind, action));
    assert((behind.goalDistance(0) - blueBefore) - (behind.goalDistance(1) - greenBefore) > 1);
  }
});

test('hard lookahead avoids an accelerating jump that greedy medium permits', () => {
  const game = position(cell(5, 1), cell(5, 0), 1,
    [{ row: 5, col: 0, orientation: 'HORIZONTAL' }, { row: 5, col: 1, orientation: 'VERTICAL' }], [9, 9]);
  const medium = choice(game, 'MEDIUM'), hard = choice(game, 'HARD');
  assert.notDeepEqual(medium, hard);
  const mediumLine = game.copy(), hardLine = game.copy();
  applyAction(mediumLine, medium); applyAction(hardLine, hard);
  assert(mediumLine.legalMoves().some(move => move.row === 3 && move.col === 1));
  assert(!hardLine.legalMoves().some(move => move.row === 3 && move.col === 1));
});

test('easy varies progress-biased choices; all levels remain legal in varied positions', () => {
  const random = seeded(19), opening = new GameState(), variants = new Set();
  for (let index = 0; index < 50; index++) variants.add(JSON.stringify(choice(opening, 'EASY', random)));
  assert(variants.size > 1);
  assert([...variants].some(value => JSON.parse(value).row === 7));
  const game = new GameState();
  for (let turn = 0; turn < 18; turn++) {
    if (game.winner !== -1) game.reset();
    if (turn % 3 === 0) for (const difficulty of DIFFICULTIES) choice(game, difficulty, random);
    const action = choice(game, turn % 2 ? 'EASY' : 'MEDIUM', random);
    assert(applyAction(game, action));
  }
});

test('full games versus a deterministic shortest-path mover terminate at all difficulties', () => {
  for (const difficulty of DIFFICULTIES) {
    const game = new GameState(), random = seeded(81);
    for (let turn = 0; turn < 220 && game.winner === -1; turn++) {
      if (game.currentPlayer === 1) assert(applyAction(game, choice(game, difficulty, random)));
      else {
        const distances = game.goalDistances(0);
        const moves = game.legalMoves().sort((a, b) => distances[a.row * 9 + a.col] - distances[b.row * 9 + b.col] || a.row - b.row || a.col - b.col);
        assert(game.move(moves[0].row, moves[0].col));
      }
      assert(game.goalDistance(0) < 100 && game.goalDistance(1) < 100);
      assert.deepEqual(GameState.fromSnapshot(game.snapshot()).snapshot(), game.snapshot());
    }
    assert.notEqual(game.winner, -1, `${difficulty} game did not finish`);
  }
});
