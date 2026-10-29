import { GameState } from './game.js';
import { decodeSave, encodeSave, MAX_SAVE_BYTES } from './save.js';
import { LocalSession } from './local-session.js';
import { BoardView, renderPlayers } from './board-view.js';
import {
  currentLanguage,
  number,
  playerColors,
  setLanguage,
  t,
  translatePage,
} from './i18n.js';
import { VisualTutorial } from './tutorial.js';

const $ = (id) => document.getElementById(id);
const RECOVERY_KEY = 'quoridor.web.recovery.v1';
const PREFERENCES_KEY = 'quoridor.web.preferences.v1';
let local = null;
let screen = 'menu';
let hint = null;
let worker = null;
let botFailed = false;
let generation = 0;
let storageNoticeShown = false;
let version = '';

function readStorage(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function notice(key, error = false) {
  $('notice').textContent = t(key);
  $('notice').classList.toggle('error', error);
  $('notice').hidden = false;
  $('notice').dataset.message = key;
}
function clearNotice() {
  $('notice').hidden = true;
}
function savePreferences() {
  try {
    localStorage.setItem(
      PREFERENCES_KEY,
      JSON.stringify({
        language: currentLanguage(),
        theme: $('theme').value,
      }),
    );
  } catch {
    /* Preferences are optional. */
  }
}
function persistLocal() {
  if (!local) return;
  try {
    localStorage.setItem(RECOVERY_KEY, encodeSave(local));
  } catch {
    if (!storageNoticeShown) {
      notice('storageError', true);
      storageNoticeShown = true;
    }
  }
}
function showScreen(value) {
  screen = value;
  document.body.dataset.screen = value;
  $('menu-view').hidden = value !== 'menu';
  $('game-view').hidden = value !== 'game';
  $('continue-game').disabled = !local && !readStorage(RECOVERY_KEY);
}
function cancelBot() {
  worker?.terminate();
  worker = null;
  botFailed = false;
}
function canInteract() {
  return (
    screen === 'game' &&
    !tutorial.isOpen &&
    local &&
    !local.isBotTurn &&
    local.state.winner < 0
  );
}
const board = new BoardView(
  $('board'),
  (row, col) => play({ kind: 'move', row, col }),
  (wall) => play({ kind: 'wall', ...wall }),
);
const tutorial = new VisualTutorial($('tutorial-dialog'), {
  onOpen() {
    generation++;
    cancelBot();
    render();
  },
  onClose() {
    render();
    startBot();
  },
});
function play(action) {
  if (!canInteract() || !local.play(action)) return;
  hint = null;
  clearNotice();
  persistLocal();
  render();
  startBot();
}
function replaceLocal(replacement) {
  generation++;
  cancelBot();
  local = replacement;
  hint = null;
  clearNotice();
  persistLocal();
  showScreen('game');
  render();
  startBot();
}
function startBot() {
  if (
    screen !== 'game' ||
    tutorial.isOpen ||
    !local?.isBotTurn ||
    worker ||
    document.hidden
  )
    return;
  const owner = local;
  const expectedGeneration = generation;
  const revision = owner.revision;
  try {
    const current = new Worker(new URL('./bot-worker.js', import.meta.url), {
      type: 'module',
    });
    worker = current;
    current.onmessage = ({ data }) => {
      if (
        worker !== current ||
        generation !== expectedGeneration ||
        local !== owner ||
        owner.revision !== revision
      )
        return;
      current.terminate();
      worker = null;
      if (
        data?.id !== expectedGeneration ||
        data.revision !== revision ||
        data.error ||
        !data.action ||
        !owner.play(data.action)
      ) {
        botFailed = true;
        notice('botError', true);
      } else persistLocal();
      render();
    };
    current.onerror = () => {
      if (worker !== current) return;
      current.terminate();
      worker = null;
      botFailed = true;
      notice('botError', true);
      render();
    };
    current.postMessage({
      id: expectedGeneration,
      revision,
      snapshot: owner.state.snapshot(),
      difficulty: owner.config.difficulty,
    });
    render();
  } catch {
    worker = null;
    botFailed = true;
    notice('botError', true);
    render();
  }
}
function renderHistory() {
  $('history-list').replaceChildren();
  const moves = local.actions.slice(0, local.cursor);
  if (!moves.length) {
    const item = document.createElement('li');
    item.textContent = t('emptyHistory');
    $('history-list').append(item);
  }
  for (const action of moves) {
    const item = document.createElement('li');
    item.textContent = t(action.kind === 'move' ? 'moved' : 'placed', {
      name: '\u2068' + local.config.playerNames[action.player] + '\u2069',
      row: number(action.row + 1),
      col: number(action.col + 1),
      orientation: t(
        action.orientation === 'HORIZONTAL' ? 'horizontal' : 'vertical',
      ),
    });
    $('history-list').append(item);
  }
}
function render() {
  $('continue-game').disabled = !local && !readStorage(RECOVERY_KEY);
  if (screen !== 'game' || !local) return;
  const { state, config } = local;
  const finished = state.winner >= 0;
  const name =
    '\u2068' +
    config.playerNames[finished ? state.winner : state.currentPlayer] +
    '\u2069';
  $('turn-status').textContent = t(
    finished ? 'won' : local.isBotTurn ? 'thinking' : 'turn',
    { name },
  );
  $('turn-status').dataset.currentPlayer = state.currentPlayer;
  $('turn-status').dataset.winner = state.winner;
  $('turn-dot').style.background =
    'var(--' + playerColors[state.currentPlayer] + ')';
  $('turn-dot').style.color = $('turn-dot').style.background;
  $('match-kind').textContent =
    config.mode === 'BOT'
      ? t('botMatch', { level: t(config.difficulty.toLowerCase()) })
      : t('localMatch', { count: number(state.playerCount) });
  $('round-result').hidden = !finished;
  $('bot-retry').hidden = !botFailed;
  $('undo').disabled = !local.canUndo;
  $('redo').disabled = !local.canRedo;
  $('hint').disabled = !canInteract();
  $('board-instruction').textContent = finished
    ? ''
    : t(local.isBotTurn ? 'thinking' : 'directPlay');
  renderPlayers($('wall-counts'), state, config.playerNames);
  board.render({
    state,
    names: config.playerNames,
    interactive: Boolean(canInteract()),
    hint,
  });
  renderHistory();
}
function updateSetup() {
  $('difficulty-field').hidden = $('mode').value !== 'BOT';
  document.querySelectorAll('.extra-player').forEach((element) => {
    element.hidden = $('mode').value !== 'LOCAL4';
  });
}
$('setup-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const count = $('mode').value === 'LOCAL4' ? 4 : 2;
  try {
    replaceLocal(
      new LocalSession(new GameState(count), {
        playerNames: Array.from(
          { length: count },
          (_, index) => $('name-' + index).value || t(playerColors[index]),
        ),
        mode: $('mode').value === 'BOT' ? 'BOT' : 'LOCAL',
        difficulty: $('difficulty').value,
      }),
    );
  } catch {
    notice('nameError', true);
  }
});
$('mode').addEventListener('change', updateSetup);
$('continue-game').addEventListener('click', () => {
  let replacement = local;
  if (!replacement) {
    try {
      const saved = decodeSave(readStorage(RECOVERY_KEY));
      replacement = new LocalSession(saved.state, saved.config);
    } catch {
      notice(readStorage(RECOVERY_KEY) ? 'saveError' : 'noSave', true);
      return;
    }
  }
  replaceLocal(replacement);
});
$('new-game').addEventListener('click', () => {
  generation++;
  cancelBot();
  persistLocal();
  clearNotice();
  showScreen('menu');
  updateSetup();
});
$('replay-game').addEventListener('click', () => {
  if (local?.state.winner >= 0)
    replaceLocal(
      new LocalSession(new GameState(local.state.playerCount), local.config),
    );
});
$('bot-retry').addEventListener('click', () => {
  botFailed = false;
  clearNotice();
  startBot();
});
for (const id of ['undo', 'redo'])
  $(id).addEventListener('click', () => {
    if (!local) return;
    generation++;
    cancelBot();
    if (local[id]()) {
      hint = null;
      clearNotice();
      persistLocal();
      render();
      startBot();
    }
  });
$('hint').addEventListener('click', () => {
  if (!canInteract()) return;
  const distances = local.state.goalDistances(local.state.currentPlayer);
  hint = local.state
    .legalMoves()
    .sort(
      (a, b) => distances[a.row * 9 + a.col] - distances[b.row * 9 + b.col],
    )[0];
  render();
});
$('show-history').addEventListener('click', () => {
  $('history-list').hidden = !$('history-list').hidden;
  $('show-history').setAttribute(
    'aria-expanded',
    String(!$('history-list').hidden),
  );
});
$('save-game').addEventListener('click', () => {
  if (!local) return;
  try {
    const url = URL.createObjectURL(
      new Blob([encodeSave(local)], { type: 'text/plain;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = 'Quoridor-' + (version || 'web') + '.qsave';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notice('saved');
  } catch {
    notice('saveError', true);
  }
});
$('load-game').addEventListener('click', () => $('save-file-input').click());
$('save-file-input').addEventListener('change', async (event) => {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) return;
  if (file.size > MAX_SAVE_BYTES) {
    notice('saveError', true);
    return;
  }
  const expectedGeneration = ++generation;
  cancelBot();
  try {
    const text = new TextDecoder('utf-8', { fatal: true }).decode(
      await file.arrayBuffer(),
    );
    const saved = decodeSave(text);
    const replacement = new LocalSession(saved.state, saved.config);
    if (generation !== expectedGeneration) return;
    replaceLocal(replacement);
    notice('imported');
  } catch {
    if (generation === expectedGeneration) {
      notice('saveError', true);
      render();
      startBot();
    }
  }
});
$('language').addEventListener('change', () => {
  setLanguage($('language').value);
  translatePage();
  if (!$('notice').hidden)
    notice(
      $('notice').dataset.message,
      $('notice').classList.contains('error'),
    );
  savePreferences();
  render();
  tutorial.refresh();
});
$('theme').addEventListener('change', () => {
  document.documentElement.dataset.theme = $('theme').value;
  document.querySelector('meta[name="theme-color"]').content =
    $('theme').value === 'dark' ? '#101827' : '#f2f5f9';
  savePreferences();
});
for (const id of ['tutorial-menu', 'tutorial-game'])
  $(id).addEventListener('click', () => tutorial.open());
$('about-toggle').addEventListener('click', () =>
  $('about-dialog').showModal(),
);
$('about-close').addEventListener('click', () => $('about-dialog').close());
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    cancelBot();
    persistLocal();
  } else startBot();
});
window.addEventListener('pagehide', () => {
  persistLocal();
  cancelBot();
});
window.addEventListener('pageshow', (event) => {
  if (event.persisted) {
    render();
    startBot();
  }
});
let preferences = {};
try {
  preferences = JSON.parse(readStorage(PREFERENCES_KEY) || '{}') ?? {};
} catch {
  /* Ignore invalid optional settings. */
}
setLanguage(preferences.language || navigator.language?.split('-')[0] || 'en');
$('language').value = currentLanguage();
$('theme').value =
  preferences.theme === 'light' ||
  (!preferences.theme && matchMedia('(prefers-color-scheme: light)').matches)
    ? 'light'
    : 'dark';
document.documentElement.dataset.theme = $('theme').value;
document.querySelector('meta[name="theme-color"]').content =
  $('theme').value === 'dark' ? '#101827' : '#f2f5f9';
translatePage();
updateSetup();
showScreen('menu');
fetch('./VERSION')
  .then((response) => (response.ok ? response.text() : ''))
  .then((text) => {
    if (/^\d+\.\d+\.\d+$/.test(text.trim())) {
      version = text.trim();
      $('app-version').textContent = 'v' + version;
    }
  })
  .catch(() => {});
fetch('./LICENSE')
  .then((response) => (response.ok ? response.text() : ''))
  .then((text) => {
    $('license-text').textContent = text;
  })
  .catch(() => {});
