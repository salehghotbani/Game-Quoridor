import test from 'node:test';
import assert from 'node:assert/strict';
import { GameState, SIZE, HORIZONTAL as H, VERTICAL as V, applyAction } from '../src/game.js';

const cell = (row, col) => ({ row, col });
const has = (moves, row, col) => moves.some(move => move.row === row && move.col === col);
const sorted = moves => moves.map(move => move.row * SIZE + move.col).sort((a, b) => a - b);
export function position(players, currentPlayer = 0, walls = []) {
  const stock = new Array(players.length).fill(20 / players.length);
  for (let index = 0; index < walls.length; index++) stock[index % players.length]--;
  return GameState.fromSnapshot({ players, wallsRemaining: stock, currentPlayer, winner: -1, walls });
}

test('initial state, clockwise turns, guards and defensive copies', () => {
  for (const count of [2, 4]) {
    const game = new GameState(count), original = game.snapshot();
    assert.equal(game.playerCount, count);
    assert.equal(game.startingWalls, 20 / count);
    assert.deepEqual(game.playerPosition(0), cell(8, 4));
    assert.equal(game.goalDistance(0), 8);
    const turns = count === 2 ? [0, 1, 0] : [0, 2, 1, 3, 0];
    for (const player of turns.slice(0, -1)) {
      assert.equal(game.currentPlayer, player);
      const next = game.legalMoves()[0];
      assert.equal(game.move(next.row, next.col), true);
    }
    assert.equal(game.currentPlayer, 0);
    const copied = game.copy();
    copied.reset();
    assert.deepEqual(copied.snapshot(), original);
    assert.notDeepEqual(game.snapshot(), original);
    const exposed = game.snapshot();
    exposed.players[0].row = 100;
    exposed.wallsRemaining[0] = 100;
    game.playerPosition(0).row = 100;
    game.legalMoves().push(cell(100, 100));
    assert.notEqual(game.playerPosition(0).row, 100);
    assert.notEqual(game.wallsRemaining(0), 100);
    assert.throws(() => { game.currentPlayer = 3; });
    for (const index of [-1, count, NaN, 0.1, '0']) assert.throws(() => game.playerPosition(index));
    const before = game.snapshot();
    for (const [row, col] of [[-1, 0], [9, 0], [0.5, 2], ['7', 4], [NaN, 0]]) {
      assert.equal(game.move(row, col), false);
      assert.equal(game.placeWall(row, col, H), false);
    }
    assert.equal(game.placeWall(0, 0, 'horizontal'), false);
    assert.equal(applyAction(game, { kind: 'unknown' }), false);
    assert.deepEqual(game.snapshot(), before);
  }
  for (const count of [0, 1, 3, 5, '2']) assert.throws(() => new GameState(count));
});

function emptyBoardOracle(players, active) {
  const result = new Set(), start = players[active];
  const within = (r, c) => r >= 0 && c >= 0 && r < 9 && c < 9;
  const occupied = (r, c) => players.some(p => p.row === r && p.col === c);
  for (const [dr, dc] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
    const r = start.row + dr, c = start.col + dc;
    if (!within(r, c)) continue;
    if (!occupied(r, c)) { result.add(r * 9 + c); continue; }
    const jumpR = r + dr, jumpC = c + dc;
    if (within(jumpR, jumpC) && !occupied(jumpR, jumpC)) result.add(jumpR * 9 + jumpC);
    else for (const [sideR, sideC] of [[r + dc, c + dr], [r - dc, c - dr]]) {
      if (within(sideR, sideC) && !occupied(sideR, sideC)) result.add(sideR * 9 + sideC);
    }
  }
  return [...result].sort((a, b) => a - b);
}

test('every valid empty-board pawn pair and both turns match an independent geometric oracle', t => {
  let comparisons = 0;
  for (let blue = 9; blue < 81; blue++) for (let green = 0; green < 72; green++) {
    if (blue === green) continue;
    const players = [cell(Math.floor(blue / 9), blue % 9), cell(Math.floor(green / 9), green % 9)];
    const game = position(players);
    for (const active of [0, 1]) {
      assert.deepEqual(sorted(game.legalMoves(active)), emptyBoardOracle(players, active), `${blue}/${green} active${active}`);
      comparisons++;
    }
  }
  t.diagnostic(`${comparisons} pawn-pair/turn comparisons`);
});

test('straight jumps, wall/edge diagonals and crowded four-player landings', () => {
  let game = position([cell(4, 4), cell(3, 4)]);
  assert(has(game.legalMoves(), 2, 4));
  assert(!has(game.legalMoves(), 3, 3));
  game = position([cell(4, 4), cell(3, 4)], 0, [{ row: 2, col: 4, orientation: H }, { row: 3, col: 3, orientation: V }]);
  assert(!has(game.legalMoves(), 2, 4));
  assert(!has(game.legalMoves(), 3, 3));
  assert(has(game.legalMoves(), 3, 5));
  game = position([cell(1, 0), cell(0, 0)]);
  assert.deepEqual(sorted(game.legalMoves()), [1, 10, 18]);
  game = position([cell(4, 4), cell(3, 4), cell(2, 4), cell(3, 3)]);
  assert(has(game.legalMoves(), 3, 5));
  assert(!has(game.legalMoves(), 1, 4));
  assert(!has(game.legalMoves(), 2, 4));
  assert(!has(game.legalMoves(), 3, 3));
  assert(game.move(3, 5));
  assert.equal(game.currentPlayer, 2);
});

test('wall geometry, both segments, corner anchors and pure invalid actions', () => {
  for (const orientation of [H, V]) {
    const game = new GameState();
    assert(game.placeWall(3, 3, orientation));
    const before = game.snapshot();
    assert(!game.placeWall(3, 3, orientation));
    assert(!game.placeWall(3, 3, orientation === H ? V : H));
    for (const offset of [-1, 1]) assert(!game.canPlaceWall(3 + (orientation === V ? offset : 0), 3 + (orientation === H ? offset : 0), orientation));
    assert(game.canPlaceWall(orientation === V ? 5 : 3, orientation === H ? 5 : 3, orientation));
    assert.deepEqual(game.snapshot(), before);
    for (const offset of [0, 1]) {
      const pawn = orientation === H ? cell(4, 3 + offset) : cell(3 + offset, 4);
      const across = orientation === H ? cell(3, 3 + offset) : cell(3 + offset, 3);
      const blocked = position([pawn, cell(0, 0)], 0, before.walls);
      assert(!has(blocked.legalMoves(), across.row, across.col));
    }
    for (const row of [0, 7]) for (const col of [0, 7]) assert(new GameState().placeWall(row, col, orientation));
  }
});

test('all four players retain routes, trap attempts roll back, ninth goal square is sufficient', () => {
  for (const player of [0, 1]) {
    const row = player === 0 ? 7 : 0, game = new GameState();
    assert(game.placeWall(row, 4, H));
    assert(game.placeWall(row, 3, V));
    const before = game.snapshot();
    assert(!game.canPlaceWall(row, 5, V));
    assert(!game.placeWall(row, 5, V));
    assert.deepEqual(game.snapshot(), before);
    const bad = structuredClone(before);
    bad.walls.push({ row, col: 5, orientation: V }); bad.wallsRemaining[0]--;
    assert.throws(() => GameState.fromSnapshot(bad));
    const ninth = new GameState();
    assert(ninth.move(7, 4)); assert(ninth.move(1, 4));
    const boundary = player === 0 ? 0 : 7;
    assert(ninth.placeWall(boundary, 7, V));
    for (const col of [0, 2, 4, 6]) assert(ninth.placeWall(boundary, col, H));
    assert(ninth.goalDistance(player) < 100);
    assert.deepEqual(GameState.fromSnapshot(ninth.snapshot()).snapshot(), ninth.snapshot());
  }
  for (const [player, pawn, wallCol, closingCol] of [[2, cell(0, 0), 0, 1], [3, cell(0, 8), 7, 6]]) {
    const players = new GameState(4).snapshot().players;
    players[player] = pawn;
    const game = position(players, 0, [{ row: 0, col: wallCol, orientation: H }]);
    assert(!game.canPlaceWall(0, closingCol, V));
    const bad = game.snapshot();
    bad.walls.push({ row: 0, col: closingCol, orientation: V }); bad.wallsRemaining[1]--;
    assert.throws(() => GameState.fromSnapshot(bad));
  }
});

test('stock accounting, exhausted inventories, wins for all edges and reset', () => {
  for (const count of [2, 4]) {
    const game = new GameState(count);
    for (let row = 0; row < 5; row++) for (const col of [0, 2, 4, 6]) assert(game.placeWall(row, col, H));
    for (let player = 0; player < count; player++) assert.equal(game.wallsRemaining(player), 0);
    assert(!game.canPlaceWall(6, 0, V));
    const next = game.legalMoves()[0]; assert(game.move(next.row, next.col));
    game.reset(); assert.deepEqual(game.snapshot(), new GameState(count).snapshot());
  }
  for (let winner = 0; winner < 4; winner++) {
    const players = new GameState(4).snapshot().players;
    const starts = [cell(1, 3), cell(7, 3), cell(3, 7), cell(5, 1)];
    const goals = [cell(0, 3), cell(8, 3), cell(3, 8), cell(5, 0)];
    players[winner] = starts[winner];
    const game = position(players, winner);
    assert(game.move(goals[winner].row, goals[winner].col));
    assert.equal(game.winner, winner); assert.equal(game.currentPlayer, winner);
    assert.deepEqual(game.legalMoves(), []);
    const finished = game.snapshot();
    assert(!game.move(4, 4)); assert(!game.placeWall(0, 0, H));
    assert.deepEqual(game.snapshot(), finished);
    assert.deepEqual(GameState.fromSnapshot(finished).snapshot(), finished);
  }
});

test('snapshot validation rejects bounds, duplicates, accounting, overlap and inconsistent winners', () => {
  const mutations = [
    s => { s.players[0] = null; }, s => { s.players[0].row = 9; },
    s => { s.players[0].row = 1.5; }, s => { s.players[1] = s.players[0]; },
    s => { s.wallsRemaining[0] = 11; }, s => { s.wallsRemaining[0] = -1; },
    s => { s.wallsRemaining[0] = '10'; }, s => { s.wallsRemaining.pop(); },
    s => { s.wallsRemaining[0]--; }, s => { s.currentPlayer = 2; },
    s => { s.winner = -2; }, s => { s.winner = 0; },
    s => { s.players[0].row = 0; }, s => { s.players[1].row = 8; },
    s => { s.walls = null; }, s => { s.players = [s.players[0]]; },
  ];
  for (const change of mutations) { const snapshot = new GameState().snapshot(); change(snapshot); assert.throws(() => GameState.fromSnapshot(snapshot)); }
  for (const walls of [
    [{ row: 8, col: 0, orientation: H }], [{ row: 0, col: 0, orientation: null }],
    [{ row: 0, col: 0, orientation: H }, { row: 0, col: 0, orientation: H }],
    [{ row: 0, col: 0, orientation: H }, { row: 0, col: 0, orientation: V }],
    [{ row: 0, col: 0, orientation: H }, { row: 0, col: 1, orientation: H }],
  ]) assert.throws(() => position([cell(8, 4), cell(0, 4)], 0, walls));
});
