import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { GameState, ORIENTATIONS } from '../src/game.js';
import { encodeSave, decodeSave } from '../src/save.js';

const project = fileURLToPath(new URL('../..', import.meta.url));
const output = path.join(project, 'out', 'web-tests', 'java-oracle');
const hasJdk = spawnSync('javac', ['-version'], { encoding: 'utf8' }).status === 0;
const settings = count => ({ playerNames: ['سید صالح 🎮', "O'Brien 50%=!\\", 'قرمز', 'طلایی'].slice(0, count), mode: 'LOCAL', difficulty: 'MEDIUM' });

test('Java oracle: rules, every candidate wall, goal distances, actions and bidirectional portable saves', { skip: !hasJdk, timeout: 120000 }, t => {
  mkdirSync(output, { recursive: true });
  const core = ['GameState', 'GoalDistances', 'BotPlayer', 'GameConfig', 'GameSession', 'CurrentSaveFormat', 'SaveFields', 'SaveStore', 'LegacySaveReader'];
  const compilation = spawnSync('javac', ['--release', '17', '-encoding', 'UTF-8', '-d', output,
    ...core.map(name => path.join(project, 'src', 'Board', name + '.java')),
    path.join(project, 'web', 'tests', 'java', 'Board', 'WebRulesOracle.java')], { encoding: 'utf8', timeout: 30000 });
  assert.equal(compilation.status, 0, compilation.stderr);
  const commands = [], expectations = [];
  const record = (command, game, config, accepted = true) => {
    commands.push(command);
    expectations.push({ game: game.copy(), config, accepted });
  };
  let seed = 321;
  const random = max => ((seed = Math.imul(seed, 1664525) + 1013904223 >>> 0) % max);
  for (const count of [2, 4]) {
    const game = new GameState(count), config = settings(count);
    const reload = () => record('SAVE ' + Buffer.from(encodeSave({ state: game, config })).toString('base64'), game, config);
    reload();
    for (let turn = 0; turn < 90; turn++) {
      if (game.winner !== -1) { game.reset(); reload(); }
      if (turn % 11 === 0) record('MOVE -1 0', game, config, game.move(-1, 0));
      if (turn % 13 === 0) reload();
      if (random(2) === 0) {
        const row = random(8), col = random(8), orientation = ORIENTATIONS[random(2)];
        const accepted = game.placeWall(row, col, orientation);
        record(`WALL ${row} ${col} ${orientation}`, game, config, accepted);
      } else {
        const moves = game.legalMoves(), move = moves[random(moves.length)];
        record(`MOVE ${move.row} ${move.col}`, game, config, game.move(move.row, move.col));
      }
    }
  }
  // Nontrivial crowded jumps, edge diagonals and a route through the ninth goal cell.
  for (const snapshot of [
    { players: [{ row: 4, col: 4 }, { row: 3, col: 4 }, { row: 2, col: 4 }, { row: 3, col: 3 }], wallsRemaining: [5, 5, 5, 5], currentPlayer: 0, winner: -1, walls: [] },
    { players: [{ row: 1, col: 0 }, { row: 0, col: 0 }], wallsRemaining: [10, 10], currentPlayer: 0, winner: -1, walls: [] },
    { players: [{ row: 4, col: 4 }, { row: 3, col: 4 }], wallsRemaining: [9, 9], currentPlayer: 0, winner: -1,
      walls: [{ row: 2, col: 4, orientation: 'HORIZONTAL' }, { row: 3, col: 3, orientation: 'VERTICAL' }] },
  ]) {
    const game = GameState.fromSnapshot(snapshot), config = settings(game.playerCount);
    record('SAVE ' + Buffer.from(encodeSave({ state: game, config })).toString('base64'), game, config);
  }
  const oracle = spawnSync('java', ['-cp', output, 'Board.WebRulesOracle'], {
    input: commands.join('\n') + '\n', encoding: 'utf8', timeout: 90000, maxBuffer: 32 * 1024 * 1024,
  });
  assert.equal(oracle.status, 0, oracle.stderr);
  const answers = oracle.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(answers.length, expectations.length);
  let comparisons = 0;
  answers.forEach((answer, index) => {
    const { game, config, accepted } = expectations[index];
    assert(!answer.error, `Oracle rejected command${index}: ${commands[index]}`);
    assert.equal(answer.accepted, accepted, `Action${index}`);
    assert.deepEqual(answer.snapshot, game.snapshot(), `Snapshot${index}`);
    for (let player = 0; player < game.playerCount; player++) {
      assert.deepEqual(answer.moves[player], game.legalMoves(player), `Moves${index}/${player}`);
      assert.deepEqual(answer.distances[player], game.goalDistances(player), `Goal distances${index}/${player}`);
      comparisons += answer.distances[player].length + 1;
    }
    let wall = 0;
    for (let row = 0; row < 8; row++) for (let col = 0; col < 8; col++) for (const orientation of ORIENTATIONS) {
      assert.equal(answer.wallLegality[wall++], game.canPlaceWall(row, col, orientation), `Wall${index}/${row}/${col}/${orientation}`);
      comparisons++;
    }
    const javaSave = decodeSave(Buffer.from(answer.save, 'base64').toString('utf8'));
    assert.deepEqual(javaSave.state.snapshot(), game.snapshot(), `Java-written save${index}`);
    assert.deepEqual(javaSave.config, config, `Java-written config${index}`);
    comparisons += 4;
  });
  t.diagnostic(`${answers.length} Java positions/actions; ${comparisons} movement, distance, wall and save parity comparisons`);

  const valid = encodeSave({ state: new GameState(), config: settings(2) });
  const variants = [
    valid, '\ufeff' + valid, valid.replace('blue.name=', 'blue\\.name : '),
    valid.replace('blue.name=سید صالح 🎮', 'blue.name=Ali\\\n  Reza'),
    valid.replace('blue.name=سید صالح 🎮', 'blue.name=Ali\\\n \nReza'),
    valid.replace('mode=LOCAL', 'mode : LOCAL'), valid.replace('turn=0', 'turn=-0'),
    valid + '\\u0074urn=0\n', valid.replace('green.name=', 'green.name=\\uD800'),
    valid.replace('wall.count=0', 'wall.count=01'), valid.replace('turn=0', 'turn=+0'),
    valid.replace('green.name=', 'green.name=\\u12XZ'), valid + 'unexpected=x\n',
    valid.replace('blue.name=سید صالح 🎮', 'blue.name=\u00a0'),
  ];
  const decoded = spawnSync('java', ['-cp', output, 'Board.WebRulesOracle'], {
    input: variants.map(text => 'SAVE ' + Buffer.from(text).toString('base64')).join('\n') + '\n', encoding: 'utf8', timeout: 30000,
  });
  assert.equal(decoded.status, 0, decoded.stderr);
  const results = decoded.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  variants.forEach((text, index) => {
    let result;
    try { result = decodeSave(text); } catch { result = null; }
    assert.equal(Boolean(result), !results[index].error, `Properties acceptance variant${index}`);
    if (result) {
      assert.deepEqual(result.state.snapshot(), results[index].snapshot);
      assert.deepEqual(result.config, decodeSave(Buffer.from(results[index].save, 'base64').toString('utf8')).config);
    }
  });
});
