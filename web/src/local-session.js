import { GameState, applyAction } from "./game.js";
import { validateConfig } from "./save.js";

/** Bounded history owns defensive snapshots; async robot results use revision fencing. */
export class LocalSession {
  constructor(state, config) {
    this.state = GameState.fromSnapshot(state.snapshot());
    this.config = validateConfig(config, state.playerCount);
    this.positions = [this.state.snapshot()];
    this.actions = [];
    this.cursor = 0;
    this.revision = 0;
  }

  get isBotTurn() {
    return (
      this.config.mode === "BOT" &&
      this.state.currentPlayer === 1 &&
      this.state.winner < 0
    );
  }
  get canUndo() {
    return this.cursor > 0;
  }
  get canRedo() {
    return this.cursor < this.actions.length;
  }

  play(action) {
    const player = this.state.currentPlayer;
    if (!applyAction(this.state, action)) return false;
    this.positions.splice(this.cursor + 1);
    this.actions.splice(this.cursor);
    this.positions.push(this.state.snapshot());
    this.actions.push({ ...action, player });
    this.cursor++;
    if (this.cursor > 1000) {
      this.positions.shift();
      this.actions.shift();
      this.cursor--;
    }
    this.revision++;
    return true;
  }

  undo() {
    if (!this.canUndo) return false;
    do {
      this.cursor--;
      this.state = GameState.fromSnapshot(this.positions[this.cursor]);
    } while (
      this.config.mode === "BOT" &&
      this.state.currentPlayer === 1 &&
      this.canUndo
    );
    this.revision++;
    return true;
  }

  redo() {
    if (!this.canRedo) return false;
    do {
      this.cursor++;
      this.state = GameState.fromSnapshot(this.positions[this.cursor]);
    } while (
      this.config.mode === "BOT" &&
      this.state.currentPlayer === 1 &&
      this.canRedo &&
      this.state.winner < 0
    );
    this.revision++;
    return true;
  }
}
