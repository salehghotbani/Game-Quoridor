import { GameState, SIZE, ORIENTATIONS, applyAction } from './game.js';

export const DIFFICULTIES = Object.freeze(['EASY', 'MEDIUM', 'HARD']);
const WIN = 1_000_000;
const SEARCH_MS = 650;
const ROOT_LIMIT = 8;
const REPLY_LIMIT = 6;
const now = () => globalThis.performance?.now() ?? Date.now();
const moveAction = cell => ({ kind: 'move', row: cell.row, col: cell.col });
const immediateWin = game => game.legalMoves().find(cell => game.isGoal(game.currentPlayer, cell));

function evaluate(game, player) {
  if (game.winner !== -1) return game.winner === player ? WIN : -WIN;
  if (immediateWin(game)) return game.currentPlayer === player ? WIN - 100 : -WIN + 100;
  const opponent = 1 - player;
  return (game.goalDistance(opponent) - game.goalDistance(player)) * 100
    + (game.wallsRemaining(player) - game.wallsRemaining(opponent)) * 4
    + (game.currentPlayer === player ? 35 : -35);
}

function rankedCandidates(game, player, deadline) {
  const candidates = [];
  if (game.winner !== -1) return candidates;
  for (const cell of game.legalMoves()) {
    const action = moveAction(cell), next = game.copy();
    applyAction(next, action);
    candidates.push({ action, next, score: evaluate(next, player) });
  }
  if (game.wallsRemaining(game.currentPlayer) > 0) {
    wallScan: for (let row = 0; row < SIZE - 1; row++) {
      for (let col = 0; col < SIZE - 1; col++) for (const orientation of ORIENTATIONS) {
        if (now() >= deadline) break wallScan;
        const next = game.copy(), action = { kind: 'wall', row, col, orientation };
        if (applyAction(next, action)) candidates.push({ action, next, score: evaluate(next, player) });
      }
    }
  }
  candidates.sort((a, b) => b.score - a.score || Number(a.action.kind === 'wall') - Number(b.action.kind === 'wall')
    || a.action.row - b.action.row || a.action.col - b.action.col);
  return candidates;
}

function easyMove(game, random) {
  const moves = game.legalMoves(), distances = game.goalDistances(game.currentPlayer);
  const distance = cell => distances[cell.row * SIZE + cell.col];
  const best = Math.min(...moves.map(distance));
  const allowance = random() < 0.2 ? 1 : 0;
  const options = moves.filter(cell => distance(cell) <= best + allowance);
  return moveAction(options[Math.min(options.length - 1, Math.floor(random() * options.length))]);
}

function bestPawnReply(game, player) {
  if (game.winner !== -1) return evaluate(game, player);
  let best = -Infinity;
  for (const cell of game.legalMoves()) {
    const next = game.copy();
    next.move(cell.row, cell.col);
    best = Math.max(best, evaluate(next, player));
  }
  return best;
}

/** Input is reconstructed and validated, so searches cannot mutate a live UI position. */
export function chooseAction(snapshot, difficulty = 'MEDIUM', options = {}) {
  if (!DIFFICULTIES.includes(difficulty)) throw new TypeError('Unknown robot difficulty');
  const game = snapshot instanceof GameState ? snapshot.copy() : GameState.fromSnapshot(snapshot);
  if (game.playerCount !== 2) throw new TypeError('Robot games support exactly two players');
  if (game.winner !== -1) return null;
  const winning = immediateWin(game);
  if (winning) return moveAction(winning);
  if (difficulty === 'EASY') return easyMove(game, options.random ?? Math.random);
  const deadline = now() + SEARCH_MS, player = game.currentPlayer;
  const candidates = rankedCandidates(game, player, deadline);
  let best = candidates[0];
  if (difficulty === 'MEDIUM' || best.score >= WIN) return best.action;
  let bestScore = -Infinity;
  for (const candidate of candidates.slice(0, ROOT_LIMIT)) {
    if (now() >= deadline) break;
    const replies = rankedCandidates(candidate.next, 1 - player, deadline).slice(0, REPLY_LIMIT);
    let worst = Infinity, completed = true;
    for (const reply of replies) {
      if (now() >= deadline) { completed = false; break; }
      worst = Math.min(worst, bestPawnReply(reply.next, player));
      if (worst < bestScore) break;
    }
    if (completed && worst > bestScore) { bestScore = worst; best = candidate; }
  }
  return best.action;
}
