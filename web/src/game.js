/** Pure Quoridor rules shared by the browser UI, worker and portable-save decoder. */
export const SIZE = 9;
export const HORIZONTAL = 'HORIZONTAL';
export const VERTICAL = 'VERTICAL';
export const ORIENTATIONS = Object.freeze([HORIZONTAL, VERTICAL]);
const ANCHORS = SIZE - 1;
const CELL_COUNT = SIZE * SIZE;
const DIRECTIONS = Object.freeze([[-1, 0], [1, 0], [0, -1], [0, 1]]);
const NEXT_FOUR = Object.freeze([2, 3, 1, 0]);
const inside = (row, col, size = SIZE) => Number.isInteger(row) && Number.isInteger(col)
  && row >= 0 && col >= 0 && row < size && col < size;
const isOrientation = value => value === HORIZONTAL || value === VERTICAL;
const invalid = message => new TypeError(message || 'Invalid saved game state');
const cellCopy = cell => ({ row: cell.row + 0, col: cell.col + 0 });

export class GameState {
  static SIZE = SIZE;
  static STARTING_WALLS = 10;
  #players;
  #stocks;
  #horizontal = new Uint8Array(ANCHORS * ANCHORS);
  #vertical = new Uint8Array(ANCHORS * ANCHORS);
  #currentPlayer = 0;
  #winner = -1;

  constructor(playerCount = 2) {
    if (playerCount !== 2 && playerCount !== 4) throw invalid('Player count must be 2 or 4');
    this.#players = new Array(playerCount);
    this.#stocks = new Array(playerCount);
    this.reset();
  }

  get playerCount() { return this.#players.length; }
  get currentPlayer() { return this.#currentPlayer; }
  get winner() { return this.#winner; }
  get startingWalls() { return 20 / this.playerCount; }

  reset() {
    this.#players[0] = { row: SIZE - 1, col: SIZE >> 1 };
    this.#players[1] = { row: 0, col: SIZE >> 1 };
    if (this.playerCount === 4) {
      this.#players[2] = { row: SIZE >> 1, col: 0 };
      this.#players[3] = { row: SIZE >> 1, col: SIZE - 1 };
    }
    this.#stocks.fill(this.startingWalls);
    this.#horizontal.fill(0);
    this.#vertical.fill(0);
    this.#currentPlayer = 0;
    this.#winner = -1;
  }

  #checkPlayer(player) {
    if (!Number.isInteger(player) || player < 0 || player >= this.playerCount) {
      throw invalid('Player index is outside this game');
    }
  }

  playerPosition(player) { this.#checkPlayer(player); return cellCopy(this.#players[player]); }
  wallsRemaining(player) { this.#checkPlayer(player); return this.#stocks[player]; }

  isGoal(player, cell) {
    this.#checkPlayer(player);
    if (!cell || !inside(cell.row, cell.col)) return false;
    return player === 0 ? cell.row === 0 : player === 1 ? cell.row === SIZE - 1
      : player === 2 ? cell.col === SIZE - 1 : cell.col === 0;
  }

  snapshot() {
    const walls = [];
    for (let row = 0; row < ANCHORS; row++) {
      for (let col = 0; col < ANCHORS; col++) {
        for (const orientation of ORIENTATIONS) {
          if (this.hasWall(row, col, orientation)) walls.push({ row, col, orientation });
        }
      }
    }
    return { players: this.#players.map(cellCopy), wallsRemaining: [...this.#stocks],
      currentPlayer: this.#currentPlayer, winner: this.#winner, walls };
  }

  copy() {
    const copy = new GameState(this.playerCount);
    copy.#players = this.#players.map(cellCopy);
    copy.#stocks = [...this.#stocks];
    copy.#horizontal.set(this.#horizontal);
    copy.#vertical.set(this.#vertical);
    copy.#currentPlayer = this.#currentPlayer;
    copy.#winner = this.#winner;
    return copy;
  }

  static fromSnapshot(snapshot) {
    if (!snapshot || typeof snapshot !== 'object' || !Array.isArray(snapshot.players)
      || ![2, 4].includes(snapshot.players.length) || !Array.isArray(snapshot.wallsRemaining)
      || snapshot.wallsRemaining.length !== snapshot.players.length
      || !Array.isArray(snapshot.walls) || snapshot.walls.length > 20) throw invalid();
    const game = new GameState(snapshot.players.length);
    const occupied = new Set();
    for (let player = 0; player < game.playerCount; player++) {
      const cell = snapshot.players[player];
      if (!cell || !inside(cell.row, cell.col) || occupied.has(cell.row * SIZE + cell.col)) throw invalid();
      occupied.add(cell.row * SIZE + cell.col);
      game.#players[player] = cellCopy(cell);
      const stock = snapshot.wallsRemaining[player];
      if (!Number.isInteger(stock) || stock < 0 || stock > game.startingWalls) throw invalid();
      game.#stocks[player] = stock + 0;
    }
    if (game.#stocks.reduce((sum, stock) => sum + stock, snapshot.walls.length) !== 20) throw invalid();
    if (!Number.isInteger(snapshot.currentPlayer) || snapshot.currentPlayer < 0
      || snapshot.currentPlayer >= game.playerCount || !Number.isInteger(snapshot.winner)
      || snapshot.winner < -1 || snapshot.winner >= game.playerCount) throw invalid();
    game.#currentPlayer = snapshot.currentPlayer + 0;
    game.#winner = snapshot.winner + 0;
    for (let player = 0; player < game.playerCount; player++) {
      if (game.isGoal(player, game.#players[player]) !== (game.#winner === player)) {
        throw invalid('Saved winner and goal positions disagree');
      }
    }
    if (game.#winner !== -1 && game.#currentPlayer !== game.#winner) throw invalid('Saved winner must retain the turn');
    for (const wall of snapshot.walls) {
      if (!wall || !game.#wallFits(wall.row, wall.col, wall.orientation)) {
        throw invalid('Saved walls overlap, cross, or lie outside the board');
      }
      game.#wallGrid(wall.orientation)[wall.row * ANCHORS + wall.col] = 1;
    }
    for (let player = 0; player < game.playerCount; player++) {
      if (!game.#hasPath(player)) throw invalid('Saved walls must leave a goal route for every player');
    }
    return game;
  }

  #occupied(row, col) { return this.#players.some(cell => cell.row === row && cell.col === col); }

  legalMoves(player = this.#currentPlayer) {
    this.#checkPlayer(player);
    if (this.#winner !== -1) return [];
    const start = this.#players[player];
    const moves = new Map();
    const add = (row, col) => {
      if (inside(row, col) && !this.#occupied(row, col)) moves.set(row * SIZE + col, { row, col });
    };
    for (const [dr, dc] of DIRECTIONS) {
      const row = start.row + dr, col = start.col + dc;
      if (!inside(row, col) || this.#blocked(start.row, start.col, row, col)) continue;
      if (!this.#occupied(row, col)) { add(row, col); continue; }
      const jumpRow = row + dr, jumpCol = col + dc;
      if (inside(jumpRow, jumpCol) && !this.#blocked(row, col, jumpRow, jumpCol)
        && !this.#occupied(jumpRow, jumpCol)) {
        add(jumpRow, jumpCol);
      } else {
        for (const sign of [1, -1]) {
          const sideRow = row + sign * dc, sideCol = col + sign * dr;
          if (inside(sideRow, sideCol) && !this.#blocked(row, col, sideRow, sideCol)) add(sideRow, sideCol);
        }
      }
    }
    return [...moves.values()];
  }

  move(row, col) {
    if (!inside(row, col) || !this.legalMoves().some(cell => cell.row === row && cell.col === col)) return false;
    this.#players[this.#currentPlayer] = cellCopy({ row, col });
    if (this.isGoal(this.#currentPlayer, { row, col })) this.#winner = this.#currentPlayer;
    else this.#advanceTurn();
    return true;
  }

  moveTo(row, col) { return this.move(row, col); }
  #advanceTurn() { this.#currentPlayer = this.playerCount === 2 ? 1 - this.#currentPlayer : NEXT_FOUR[this.#currentPlayer]; }
  #wallGrid(orientation) { return orientation === HORIZONTAL ? this.#horizontal : this.#vertical; }

  hasWall(row, col, orientation) {
    return inside(row, col, ANCHORS) && isOrientation(orientation)
      && this.#wallGrid(orientation)[row * ANCHORS + col] === 1;
  }

  #wallFits(row, col, orientation) {
    if (!inside(row, col, ANCHORS) || !isOrientation(orientation)) return false;
    if (this.hasWall(row, col, HORIZONTAL) || this.hasWall(row, col, VERTICAL)) return false;
    return orientation === HORIZONTAL
      ? !this.hasWall(row, col - 1, orientation) && !this.hasWall(row, col + 1, orientation)
      : !this.hasWall(row - 1, col, orientation) && !this.hasWall(row + 1, col, orientation);
  }

  canPlaceWall(row, col, orientation) {
    if (this.#winner !== -1 || this.#stocks[this.#currentPlayer] === 0 || !this.#wallFits(row, col, orientation)) return false;
    const candidate = { row, col, orientation };
    for (let player = 0; player < this.playerCount; player++) if (!this.#hasPath(player, candidate)) return false;
    return true;
  }

  placeWall(row, col, orientation) {
    if (!this.canPlaceWall(row, col, orientation)) return false;
    this.#wallGrid(orientation)[row * ANCHORS + col] = 1;
    this.#stocks[this.#currentPlayer]--;
    this.#advanceTurn();
    return true;
  }

  #blocked(fromRow, fromCol, toRow, toCol, candidate = null) {
    if (fromRow !== toRow) {
      const boundary = Math.min(fromRow, toRow);
      return this.hasWall(boundary, fromCol, HORIZONTAL) || this.hasWall(boundary, fromCol - 1, HORIZONTAL)
        || Boolean(candidate && candidate.orientation === HORIZONTAL && candidate.row === boundary
          && (candidate.col === fromCol || candidate.col === fromCol - 1));
    }
    const boundary = Math.min(fromCol, toCol);
    return this.hasWall(fromRow, boundary, VERTICAL) || this.hasWall(fromRow - 1, boundary, VERTICAL)
      || Boolean(candidate && candidate.orientation === VERTICAL && candidate.col === boundary
        && (candidate.row === fromRow || candidate.row === fromRow - 1));
  }

  #hasPath(player, candidate = null) {
    const visited = new Uint8Array(CELL_COUNT);
    const pending = new Int16Array(CELL_COUNT);
    const start = this.#players[player];
    pending[0] = start.row * SIZE + start.col;
    visited[pending[0]] = 1;
    let head = 0, tail = 1;
    while (head < tail) {
      const index = pending[head++], row = Math.floor(index / SIZE), col = index % SIZE;
      if (this.isGoal(player, { row, col })) return true;
      for (const [dr, dc] of DIRECTIONS) {
        const nextRow = row + dr, nextCol = col + dc, next = nextRow * SIZE + nextCol;
        if (inside(nextRow, nextCol) && !visited[next] && !this.#blocked(row, col, nextRow, nextCol, candidate)) {
          visited[next] = 1;
          pending[tail++] = next;
        }
      }
    }
    return false;
  }

  /** Reverse BFS ignores pawns and targets every square on the requested goal edge. */
  goalDistances(player) {
    this.#checkPlayer(player);
    const distances = new Array(CELL_COUNT).fill(100);
    const pending = new Int16Array(CELL_COUNT);
    let head = 0, tail = 0;
    for (let row = 0; row < SIZE; row++) for (let col = 0; col < SIZE; col++) {
      if (this.isGoal(player, { row, col })) { const index = row * SIZE + col; distances[index] = 0; pending[tail++] = index; }
    }
    while (head < tail) {
      const index = pending[head++], row = Math.floor(index / SIZE), col = index % SIZE;
      for (const [dr, dc] of DIRECTIONS) {
        const nextRow = row + dr, nextCol = col + dc, next = nextRow * SIZE + nextCol;
        if (inside(nextRow, nextCol) && distances[next] === 100 && !this.#blocked(row, col, nextRow, nextCol)) {
          distances[next] = distances[index] + 1;
          pending[tail++] = next;
        }
      }
    }
    return distances;
  }

  goalDistance(player) {
    this.#checkPlayer(player);
    const cell = this.#players[player];
    return this.goalDistances(player)[cell.row * SIZE + cell.col];
  }
}

export function applyAction(game, action) {
  if (!(game instanceof GameState) || !action || typeof action !== 'object') return false;
  if (action.kind === 'move') return game.move(action.row, action.col);
  if (action.kind === 'wall') return game.placeWall(action.row, action.col, action.orientation);
  return false;
}
