import { GameState } from './game.js';
import { BoardView } from './board-view.js';
import { number, t } from './i18n.js';

export function tutorialLesson(index) {
  const actions = [
    { kind: 'move', row: 7, col: 4 },
    { kind: 'wall', row: 3, col: 3, orientation: 'HORIZONTAL' },
    { kind: 'move', row: 2, col: 4 },
    { kind: 'move', row: 3, col: 3 },
    { kind: 'move', row: 0, col: 4 },
  ];
  if (!Number.isInteger(index) || index < 0 || index >= actions.length)
    throw new RangeError('Invalid lesson');
  const state = new GameState();
  const snapshot = state.snapshot();
  if (index === 2 || index === 3)
    snapshot.players = [
      { row: 4, col: 4 },
      { row: 3, col: 4 },
    ];
  if (index === 3) {
    snapshot.walls = [{ row: 2, col: 4, orientation: 'HORIZONTAL' }];
    snapshot.wallsRemaining = [9, 10];
  }
  if (index === 4)
    snapshot.players = [
      { row: 1, col: 4 },
      { row: 7, col: 4 },
    ];
  return { state: GameState.fromSnapshot(snapshot), action: actions[index] };
}

export class VisualTutorial {
  constructor(dialog, callbacks) {
    this.dialog = dialog;
    this.callbacks = callbacks;
    this.board = new BoardView(
      dialog.querySelector('#lesson-board'),
      (row, col) => this.play({ kind: 'move', row, col }),
      (wall) => this.play({ kind: 'wall', ...wall }),
    );
    dialog
      .querySelector('#tutorial-close')
      .addEventListener('click', () => dialog.close());
    dialog
      .querySelector('#lesson-back')
      .addEventListener('click', () => this.load(this.index - 1));
    dialog.querySelector('#lesson-next').addEventListener('click', () => {
      if (!this.complete) return;
      if (this.index === 4) dialog.close();
      else this.load(this.index + 1);
    });
    dialog.addEventListener('close', () => callbacks.onClose());
  }
  get isOpen() {
    return this.dialog.open;
  }
  open() {
    if (this.isOpen) return;
    this.dialog.showModal();
    this.callbacks.onOpen();
    this.load(0);
  }
  load(index) {
    this.index = index;
    const lesson = tutorialLesson(index);
    this.state = lesson.state;
    this.action = lesson.action;
    this.complete = false;
    this.board.focus = { row: lesson.action.row, col: lesson.action.col };
    this.refresh();
  }
  play(action) {
    if (this.complete || !this.isOpen || !this.matches(action)) return;
    this.complete =
      action.kind === 'move'
        ? this.state.move(action.row, action.col)
        : this.state.placeWall(action.row, action.col, action.orientation);
    this.refresh();
    if (this.complete)
      this.dialog.querySelector('#lesson-next').focus({ preventScroll: true });
  }
  matches(action) {
    return (
      action.kind === this.action.kind &&
      action.row === this.action.row &&
      action.col === this.action.col &&
      action.orientation === this.action.orientation
    );
  }
  refresh() {
    if (!this.isOpen || !this.state) return;
    const $ = (id) => this.dialog.querySelector('#' + id);
    $('lesson-progress').textContent =
      number(this.index + 1) + ' / ' + number(5);
    $('lesson-title').textContent = t('lessonTitle' + this.index);
    $('lesson-prompt').textContent = t('lessonPrompt' + this.index);
    $('lesson-feedback').textContent = this.complete ? t('wellDone') : '';
    $('lesson-back').disabled = this.index === 0;
    $('lesson-next').disabled = !this.complete;
    $('lesson-next').textContent = t(this.index === 4 ? 'done' : 'next');
    this.board.render({
      state: this.state,
      names: [t('blue'), t('green')],
      interactive: !this.complete,
      allowedAction: (action) => this.matches(action),
      target: this.complete ? null : this.action,
    });
  }
}
