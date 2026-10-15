import { number, playerColors, t } from "./i18n.js";

const color = (player) => `var(--${playerColors[player]})`;

function wallGeometry(element, wall) {
  // Cell pitch = 11.2333%; the anchor lies halfway through the 1.1% grid gap.
  const pitch = 101.1 / 9;
  const x = pitch * (wall.col + 1) - 0.55;
  const y = pitch * (wall.row + 1) - 0.55;
  element.style.left = `${x}%`;
  element.style.top = `${y}%`;
  element.style.width =
    wall.orientation === "HORIZONTAL" ? `${pitch * 2 - 1.1}%` : "1.6%";
  element.style.height =
    wall.orientation === "VERTICAL" ? `${pitch * 2 - 1.1}%` : "1.6%";
}

export class BoardView {
  constructor(element, onMove, onWall) {
    this.element = element;
    this.onMove = onMove;
    this.onWall = onWall;
    this.focus = { row: 8, col: 4 };
  }

  render({ state, names, interactive, tool, preview, hint }) {
    const board = this.element;
    const focused = board.contains(document.activeElement)
      ? document.activeElement
      : null;
    const focusTarget = focused?.classList.contains("wall-anchor")
      ? {
          row: focused.dataset.row,
          col: focused.dataset.col,
          orientation: focused.dataset.orientation,
        }
      : focused?.classList.contains("cell")
        ? { row: focused.dataset.row, col: focused.dataset.col }
        : null;
    board.replaceChildren();
    board.setAttribute("aria-label", t("boardLabel"));
    const snapshot = state.snapshot();
    const moves = new Set(
      interactive && tool === "move"
        ? state.legalMoves().map((cell) => `${cell.row},${cell.col}`)
        : [],
    );
    for (let row = 0; row < 9; row++) {
      const rowElement = document.createElement("div");
      rowElement.className = "board-row";
      rowElement.setAttribute("role", "row");
      for (let col = 0; col < 9; col++) {
        const cell = document.createElement("button");
        const legal = moves.has(`${row},${col}`);
        cell.className = `cell${(row + col) % 2 ? " alternate" : ""}${legal ? " legal" : ""}${hint?.row === row && hint?.col === col ? " hint" : ""}`;
        cell.type = "button";
        cell.dataset.row = row;
        cell.dataset.col = col;
        cell.setAttribute("role", "gridcell");
        cell.tabIndex =
          row === this.focus.row && col === this.focus.col ? 0 : -1;
        const occupant = snapshot.players.findIndex(
          (position) => position.row === row && position.col === col,
        );
        cell.setAttribute(
          "aria-label",
          t(occupant >= 0 ? "pawnCell" : legal ? "legalCell" : "cell", {
            name: names[occupant],
            row: number(row + 1),
            col: number(col + 1),
          }),
        );
        cell.setAttribute("aria-disabled", String(!legal));
        if (occupant >= 0) {
          const pawn = document.createElement("span");
          pawn.className = `pawn${state.currentPlayer === occupant ? " current" : ""}`;
          pawn.dataset.player = occupant;
          pawn.style.background = color(occupant);
          pawn.textContent = number(occupant + 1);
          cell.append(pawn);
        }
        cell.addEventListener("click", () => {
          if (legal) this.onMove(row, col);
        });
        cell.addEventListener("keydown", (event) =>
          this.navigate(event, row, col, 9, ".cell"),
        );
        cell.addEventListener("focus", () => {
          this.focus = { row, col };
        });
        rowElement.append(cell);
      }
      board.append(rowElement);
    }
    for (const wall of snapshot.walls) {
      const element = document.createElement("span");
      element.className = "placed-wall";
      wallGeometry(element, wall);
      board.append(element);
    }
    if (interactive && tool !== "move") {
      for (let row = 0; row < 8; row++)
        for (let col = 0; col < 8; col++) {
          const wall = { row, col, orientation: tool };
          const anchor = document.createElement("button");
          const selected =
            preview?.row === row &&
            preview?.col === col &&
            preview?.orientation === tool;
          anchor.className = `wall-anchor${selected ? ` preview${state.canPlaceWall(row, col, tool) ? "" : " invalid"}` : ""}`;
          anchor.dataset.row = row;
          anchor.dataset.col = col;
          anchor.dataset.orientation = tool;
          anchor.type = "button";
          anchor.tabIndex =
            row === Math.min(this.focus.row, 7) &&
            col === Math.min(this.focus.col, 7)
              ? 0
              : -1;
          anchor.setAttribute(
            "aria-label",
            t("wallAnchor", {
              orientation: t(tool === "HORIZONTAL" ? "horizontal" : "vertical"),
              row: number(row + 1),
              col: number(col + 1),
            }),
          );
          anchor.setAttribute("aria-pressed", String(selected));
          wallGeometry(anchor, wall);
          anchor.addEventListener("click", () => this.onWall(wall));
          anchor.addEventListener("focus", () => {
            this.focus = { row, col };
          });
          anchor.addEventListener("keydown", (event) =>
            this.navigate(event, row, col, 8, ".wall-anchor"),
          );
          board.append(anchor);
        }
    }
    for (const side of [
      "top",
      "bottom",
      ...(state.playerCount === 4 ? ["left", "right"] : []),
    ]) {
      const edge = document.createElement("span");
      edge.className = `goal-edge goal-${side}`;
      board.append(edge);
    }
    if (focusTarget) {
      const selector = focusTarget.orientation
        ? `.wall-anchor[data-orientation="${focusTarget.orientation}"]`
        : ".cell";
      board
        .querySelector(
          `${selector}[data-row="${focusTarget.row}"][data-col="${focusTarget.col}"]`,
        )
        ?.focus({ preventScroll: true });
    }
  }

  navigate(event, row, col, size, selector) {
    const direction = {
      ArrowUp: [-1, 0],
      ArrowDown: [1, 0],
      ArrowLeft: [0, -1],
      ArrowRight: [0, 1],
    }[event.key];
    if (!direction) return;
    event.preventDefault();
    const targetRow = Math.max(0, Math.min(size - 1, row + direction[0]));
    const targetCol = Math.max(0, Math.min(size - 1, col + direction[1]));
    this.element.querySelectorAll(selector).forEach((cell) => {
      cell.tabIndex = -1;
    });
    const target = this.element.querySelector(
      `${selector}[data-row="${targetRow}"][data-col="${targetCol}"]`,
    );
    if (target) {
      target.tabIndex = 0;
      target.focus();
    }
  }
}

export function renderPlayers(element, state, names) {
  element.replaceChildren();
  for (let player = 0; player < state.playerCount; player++) {
    const row = document.createElement("div");
    row.className = `player-stat${state.currentPlayer === player ? " active" : ""}`;
    const swatch = document.createElement("span");
    swatch.className = "swatch";
    swatch.style.background = color(player);
    const name = document.createElement("bdi");
    name.textContent = names[player];
    const stock = document.createElement("small");
    stock.textContent = t("wallStock", {
      count: number(state.wallsRemaining(player)),
    });
    row.append(swatch, name, stock);
    element.append(row);
  }
}
