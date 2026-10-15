import { GameState } from "./game.js";
import {
  decodeSave,
  encodeSave,
  MAX_SAVE_BYTES,
  validateConfig,
} from "./save.js";
import { LocalSession } from "./local-session.js";
import { BoardView, renderPlayers } from "./board-view.js";
import {
  currentLanguage,
  number,
  playerColors,
  setLanguage,
  t,
  translatePage,
} from "./i18n.js";
import { OnlineClient } from "./online.js";

const $ = (id) => document.getElementById(id);
const RECOVERY_KEY = "quoridor.web.recovery.v1";
const PREFERENCES_KEY = "quoridor.web.preferences.v1";
let local = null;
let online = null;
let room = null;
let screen = "menu";
let tool = "move";
let preview = null;
let hint = null;
let worker = null;
let botFailed = false;
let generation = 0;
let busy = false;
let connectionHealthy = true;
let pollTimer = null;
let storageNoticeShown = false;
let version = "";

function readStorage(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function notice(key, error = false) {
  $("notice").textContent = t(key);
  $("notice").classList.toggle("error", error);
  $("notice").hidden = false;
  $("notice").dataset.message = key;
}

function clearNotice() {
  $("notice").hidden = true;
}

function savePreferences() {
  try {
    localStorage.setItem(
      PREFERENCES_KEY,
      JSON.stringify({
        language: currentLanguage(),
        theme: $("theme").value,
      }),
    );
  } catch {
    /* Preferences are optional; match export remains available. */
  }
}

function persistLocal() {
  if (!local) return;
  try {
    localStorage.setItem(RECOVERY_KEY, encodeSave(local));
  } catch {
    if (!storageNoticeShown) {
      notice("storageError", true);
      storageNoticeShown = true;
    }
  }
}

function showScreen(value) {
  screen = value;
  document.body.dataset.screen = value;
  $("menu-view").hidden = value !== "menu";
  $("game-view").hidden = value !== "game";
  $("continue-game").disabled = !local && !readStorage(RECOVERY_KEY);
}

function cancelBot() {
  worker?.terminate();
  worker = null;
  botFailed = false;
}

function resetInteraction() {
  tool = "move";
  preview = null;
  hint = null;
}

function applyLocal(action) {
  if (!local?.play(action)) return;
  resetInteraction();
  persistLocal();
  render();
  startBot();
}

function startBot() {
  if (
    screen !== "game" ||
    online ||
    !local?.isBotTurn ||
    worker ||
    document.hidden
  )
    return;
  const owner = local;
  const expectedGeneration = generation;
  const revision = owner.revision;
  try {
    const current = new Worker(new URL("./bot-worker.js", import.meta.url), {
      type: "module",
    });
    worker = current;
    current.onmessage = (event) => {
      if (
        worker !== current ||
        generation !== expectedGeneration ||
        local !== owner ||
        owner.revision !== revision
      )
        return;
      current.terminate();
      worker = null;
      const result = event.data;
      if (
        result?.id !== expectedGeneration ||
        result.revision !== revision ||
        result.error ||
        !result.action
      ) {
        botFailed = true;
        notice("botError", true);
        render();
        return;
      }
      if (!owner.play(result.action)) {
        botFailed = true;
        notice("botError", true);
        render();
        return;
      }
      persistLocal();
      render();
    };
    current.onerror = () => {
      if (worker !== current) return;
      current.terminate();
      worker = null;
      botFailed = true;
      notice("botError", true);
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
    notice("botError", true);
    render();
  }
}

function currentMatch() {
  if (!online)
    return local ? { state: local.state, config: local.config } : null;
  const state = room?.session
    ? GameState.fromSnapshot(room.session.game)
    : new GameState(room?.playerCount ?? Number($("online-count").value));
  const names = room?.playerNames ?? [];
  return {
    state,
    config: {
      playerNames: Array.from(
        { length: state.playerCount },
        (_, index) => names[index] || t("waitingName"),
      ),
      mode: "LOCAL",
      difficulty: "MEDIUM",
    },
  };
}

function canInteract() {
  if (screen !== "game" || busy) return false;
  if (online)
    return (
      connectionHealthy &&
      room?.status === "PLAYING" &&
      room.session &&
      room.playerIndex === room.session.game.currentPlayer &&
      room.session.game.winner < 0
    );
  return local && !local.isBotTurn && local.state.winner < 0;
}

const board = new BoardView(
  $("board"),
  (row, col) => {
    if (canInteract() && tool === "move")
      commitAction({ kind: "move", row, col });
  },
  (wall) => {
    if (!canInteract()) return;
    preview = wall;
    render();
  },
);

function renderHistory(config) {
  const list = $("history-list");
  list.replaceChildren();
  const moves = online
    ? (room?.moves ?? [])
    : (local?.actions.slice(0, local.cursor) ?? []);
  if (!moves.length) {
    const item = document.createElement("li");
    item.textContent = t("emptyHistory");
    list.append(item);
    return;
  }
  for (const entry of moves) {
    const fields = typeof entry === "string" ? entry.split(" ") : null;
    const action = fields
      ? {
          player: Number(fields[0]),
          kind: fields[1] === "M" ? "move" : "wall",
          row: Number(fields[2]),
          col: Number(fields[3]),
          orientation: fields[4],
        }
      : entry;
    const item = document.createElement("li");
    // The transport validates protocol history; names remain isolated plain text.
    item.textContent = t(action.kind === "move" ? "moved" : "placed", {
      name: `\u2068${config.playerNames[action.player]}\u2069`,
      row: number(action.row + 1),
      col: number(action.col + 1),
      orientation: t(
        action.orientation === "HORIZONTAL" ? "horizontal" : "vertical",
      ),
    });
    list.append(item);
  }
}

function render() {
  $("continue-game").disabled = !local && !readStorage(RECOVERY_KEY);
  const match = currentMatch();
  if (screen !== "game" || !match) return;
  const { state, config } = match;
  const interactive = Boolean(canInteract());
  const turn = $("turn-status");
  const name = `\u2068${config.playerNames[state.winner >= 0 ? state.winner : state.currentPlayer]}\u2069`;
  turn.textContent =
    online && room?.status === "WAITING"
      ? t("waitingRoom")
      : state.winner >= 0
        ? t("won", { name })
        : online && room?.status === "CLOSED"
          ? t("closedRoom")
          : local?.isBotTurn && !online
            ? t("thinking")
            : t("turn", { name });
  turn.dataset.currentPlayer = state.currentPlayer;
  turn.dataset.winner = state.winner;
  $("turn-dot").style.background =
    `var(--${playerColors[state.currentPlayer]})`;
  $("match-kind").textContent = online
    ? t("onlineMatch", { count: number(state.playerCount) })
    : config.mode === "BOT"
      ? t("botMatch", { level: t(config.difficulty.toLowerCase()) })
      : t("localMatch", { count: number(state.playerCount) });
  $("online-room-status").hidden = !online;
  $("online-room-status").textContent = room
    ? t("roomInfo", {
        code: room.roomCode,
        joined: number(room.playerNames.length),
        total: number(room.playerCount),
        seat: number(room.playerIndex + 1),
      })
    : t("connecting");
  $("online-retry").hidden =
    !(online && !connectionHealthy && room?.status !== "CLOSED") && !botFailed;
  $("online-retry").textContent = t(botFailed ? "botRetry" : "retry");
  $("online-leave").hidden = !online;
  $("save-game").disabled = Boolean(online && !room?.session);
  $("new-game").disabled = busy;
  renderPlayers($("wall-counts"), state, config.playerNames);
  board.render({
    state,
    names: config.playerNames,
    interactive,
    tool,
    preview,
    hint,
  });
  for (const [id, value] of [
    ["tool-move", "move"],
    ["tool-horizontal", "HORIZONTAL"],
    ["tool-vertical", "VERTICAL"],
  ]) {
    $(id).disabled = !interactive;
    $(id).setAttribute("aria-pressed", String(tool === value));
  }
  $("wall-confirmation").hidden = !preview || !interactive;
  const validWall =
    preview &&
    state.canPlaceWall(preview.row, preview.col, preview.orientation);
  $("confirm-wall").disabled = !validWall || !interactive;
  $("wall-preview-status").textContent = t(
    validWall ? "validWall" : "invalidWall",
  );
  $("undo").disabled = Boolean(online) || !local?.canUndo;
  $("redo").disabled = Boolean(online) || !local?.canRedo;
  $("hint").disabled = Boolean(online) || !interactive;
  $("board-instruction").textContent =
    online && room?.status === "CLOSED"
      ? t("closedRoom")
      : online && !connectionHealthy
        ? t("reconnecting")
        : !interactive && online
          ? t("waitingTurn")
          : !interactive && local?.isBotTurn
            ? t("thinking")
            : t(tool === "move" ? "tapMove" : "tapWall");
  renderHistory(config);
}

async function commitAction(action) {
  if (!canInteract()) return;
  if (!online) {
    clearNotice();
    applyLocal(action);
    return;
  }
  const owner = online;
  const expectedGeneration = generation;
  const revision = room.revision;
  busy = true;
  clearTimeout(pollTimer);
  render();
  try {
    const result =
      action.kind === "move"
        ? await owner.move(action.row, action.col, revision)
        : await owner.wall(
            action.row,
            action.col,
            action.orientation,
            revision,
          );
    if (online === owner && generation === expectedGeneration) {
      room = result;
      connectionHealthy = true;
      resetInteraction();
      clearNotice();
    }
  } catch {
    // A failed POST may have reached the server; refresh state, never replay it.
    if (online === owner && generation === expectedGeneration) {
      connectionHealthy = false;
      notice("reconnecting", true);
    }
  } finally {
    if (online === owner && generation === expectedGeneration) {
      busy = false;
      render();
      schedulePoll(0);
    }
  }
}

function schedulePoll(delay = 1000) {
  clearTimeout(pollTimer);
  if (
    !online ||
    document.hidden ||
    screen !== "game" ||
    room?.status === "CLOSED"
  )
    return;
  pollTimer = setTimeout(pollOnline, delay);
}

async function pollOnline() {
  if (!online || busy || document.hidden) return;
  const owner = online;
  const expectedGeneration = generation;
  busy = true;
  try {
    const result = await owner.state();
    if (online === owner && generation === expectedGeneration) {
      room = result;
      if (!connectionHealthy) clearNotice();
      connectionHealthy = true;
    }
  } catch (error) {
    if (online === owner && generation === expectedGeneration) {
      connectionHealthy = false;
      notice("reconnecting", true);
      if ([401, 404, 410].includes(error.httpStatus) && room) {
        room = { ...room, status: "CLOSED" };
        notice("closedRoom", true);
      }
    }
  } finally {
    if (online === owner && generation === expectedGeneration) {
      busy = false;
      render();
      schedulePoll();
    }
  }
}

function setEntryBusy(value) {
  for (const id of [
    "online-create",
    "online-join",
    "online-find",
    "start-game",
    "continue-game",
    "load-game",
  ])
    $(id).disabled = value;
  if (!value)
    $("continue-game").disabled = !local && !readStorage(RECOVERY_KEY);
  $("online-cancel").hidden = !value;
}

async function enterOnline(kind) {
  if (busy || !$("online-form").reportValidity()) return;
  const name = $("online-name").value;
  try {
    validateConfig(
      { playerNames: [name, name], mode: "LOCAL", difficulty: "MEDIUM" },
      2,
    );
  } catch {
    notice("nameError", true);
    return;
  }
  const code = $("room-code").value.trim().toUpperCase();
  if (kind === "join" && !/^[A-Z2-9]{8}$/.test(code)) {
    notice("codeError", true);
    return;
  }
  let client;
  try {
    client = new OnlineClient($("server-url").value.trim());
  } catch {
    notice("networkError", true);
    return;
  }
  generation++;
  cancelBot();
  const expectedGeneration = generation;
  online = client;
  busy = true;
  setEntryBusy(true);
  notice("connecting");
  try {
    const count = Number($("online-count").value);
    const result =
      kind === "create"
        ? await client.createPrivate(count, name)
        : kind === "join"
          ? await client.joinPrivate(code, name)
          : await client.findMatch(count, name);
    if (generation !== expectedGeneration || online !== client) {
      await client.close();
      return;
    }
    room = result;
    connectionHealthy = true;
    resetInteraction();
    clearNotice();
    showScreen("game");
  } catch (error) {
    if (generation === expectedGeneration && online === client) {
      online = null;
      room = null;
      notice(
        error.httpStatus >= 400 && error.httpStatus < 500
          ? "roomError"
          : "networkError",
        true,
      );
    }
    await client.close().catch(() => {});
  } finally {
    if (generation === expectedGeneration) {
      busy = false;
      setEntryBusy(false);
      render();
      schedulePoll();
    }
  }
}

async function leaveOnline(confirm = true) {
  if (!online) return true;
  if (confirm && !window.confirm(t("confirmLeave"))) return false;
  generation++;
  const previous = online;
  online = null;
  room = null;
  busy = false;
  clearTimeout(pollTimer);
  previous.close().catch(() => {});
  resetInteraction();
  return true;
}

function updateSetup() {
  const bot = $("mode").value === "BOT";
  $("difficulty-field").hidden = !bot;
  document.querySelectorAll(".extra-player").forEach((element) => {
    element.hidden = $("mode").value !== "LOCAL4";
  });
}

$("setup-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (busy || online) return;
  const count = $("mode").value === "LOCAL4" ? 4 : 2;
  let next;
  try {
    next = new LocalSession(new GameState(count), {
      playerNames: Array.from(
        { length: count },
        (_, index) => $(`name-${index}`).value || t(playerColors[index]),
      ),
      mode: $("mode").value === "BOT" ? "BOT" : "LOCAL",
      difficulty: $("difficulty").value,
    });
  } catch {
    notice("nameError", true);
    return;
  }
  generation++;
  cancelBot();
  local = next;
  clearNotice();
  resetInteraction();
  persistLocal();
  showScreen("game");
  render();
  startBot();
});

$("mode").addEventListener("change", updateSetup);
$("continue-game").addEventListener("click", () => {
  if (busy || online) return;
  if (!local) {
    try {
      const saved = decodeSave(readStorage(RECOVERY_KEY));
      local = new LocalSession(saved.state, saved.config);
    } catch {
      notice(readStorage(RECOVERY_KEY) ? "saveError" : "noSave", true);
      return;
    }
  }
  generation++;
  cancelBot();
  clearNotice();
  resetInteraction();
  showScreen("game");
  render();
  startBot();
});

$("new-game").addEventListener("click", async () => {
  if (!(await leaveOnline())) return;
  generation++;
  cancelBot();
  persistLocal();
  clearNotice();
  showScreen("menu");
  updateSetup();
});
$("online-leave").addEventListener("click", async () => {
  if (!(await leaveOnline())) return;
  showScreen("menu");
  notice("onlineLeft");
});
for (const [id, kind] of [
  ["online-create", "create"],
  ["online-join", "join"],
  ["online-find", "find"],
])
  $(id).addEventListener("click", () => enterOnline(kind));
$("online-form").addEventListener("submit", (event) => {
  event.preventDefault();
  enterOnline("join");
});
$("online-cancel").addEventListener("click", async () => {
  await leaveOnline(false);
  setEntryBusy(false);
  clearNotice();
});
$("online-retry").addEventListener("click", () => {
  if (online) {
    if (!busy) schedulePoll(0);
  } else {
    botFailed = false;
    clearNotice();
    startBot();
  }
});

for (const [id, value] of [
  ["tool-move", "move"],
  ["tool-horizontal", "HORIZONTAL"],
  ["tool-vertical", "VERTICAL"],
]) {
  $(id).addEventListener("click", () => {
    if (canInteract()) {
      tool = value;
      preview = null;
      hint = null;
      render();
    }
  });
}
$("confirm-wall").addEventListener("click", () => {
  if (preview && canInteract()) commitAction({ kind: "wall", ...preview });
});
$("cancel-wall").addEventListener("click", () => {
  preview = null;
  render();
});
for (const id of ["undo", "redo"])
  $(id).addEventListener("click", () => {
    if (online || !local) return;
    generation++;
    cancelBot();
    if (local[id]()) {
      resetInteraction();
      clearNotice();
      persistLocal();
      render();
      startBot();
    }
  });
$("hint").addEventListener("click", () => {
  if (!canInteract() || online) return;
  const state = local.state;
  const distances = state.goalDistances(state.currentPlayer);
  hint = state
    .legalMoves()
    .sort(
      (a, b) => distances[a.row * 9 + a.col] - distances[b.row * 9 + b.col],
    )[0];
  tool = "move";
  preview = null;
  render();
  notice("hintNotice");
});
$("show-history").addEventListener("click", () => {
  $("history-list").hidden = !$("history-list").hidden;
  $("show-history").setAttribute(
    "aria-expanded",
    String(!$("history-list").hidden),
  );
});

$("save-game").addEventListener("click", () => {
  const match = currentMatch();
  if (!match || (online && !room?.session)) return;
  try {
    const blob = new Blob([encodeSave(match)], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `Quoridor-${version || "web"}.qsave`;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notice("saved");
  } catch {
    notice("saveError", true);
  }
});
$("load-game").addEventListener("click", () => {
  if (!busy && !online) $("save-file-input").click();
});
$("save-file-input").addEventListener("change", async (event) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file || busy || online) return;
  if (file.size > MAX_SAVE_BYTES) {
    notice("saveError", true);
    return;
  }
  const expectedGeneration = ++generation;
  cancelBot();
  try {
    const text = new TextDecoder("utf-8", { fatal: true }).decode(
      await file.arrayBuffer(),
    );
    const saved = decodeSave(text);
    const replacement = new LocalSession(saved.state, saved.config);
    if (generation !== expectedGeneration) return;
    local = replacement;
    resetInteraction();
    persistLocal();
    showScreen("game");
    render();
    notice("imported");
    startBot();
  } catch {
    if (generation === expectedGeneration) {
      notice("saveError", true);
      render();
      startBot();
    }
  }
});

$("language").addEventListener("change", () => {
  setLanguage($("language").value);
  translatePage();
  if (!$("notice").hidden)
    notice(
      $("notice").dataset.message,
      $("notice").classList.contains("error"),
    );
  savePreferences();
  render();
});
$("theme").addEventListener("change", () => {
  document.documentElement.dataset.theme = $("theme").value;
  document.querySelector('meta[name="theme-color"]').content =
    $("theme").value === "dark" ? "#101827" : "#f2f5f9";
  savePreferences();
});
$("about-toggle").addEventListener("click", () =>
  $("about-dialog").showModal(),
);
$("about-close").addEventListener("click", () => $("about-dialog").close());
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    clearTimeout(pollTimer);
    cancelBot();
    persistLocal();
  } else {
    schedulePoll(0);
    startBot();
  }
});
window.addEventListener("pagehide", () => {
  persistLocal();
  cancelBot();
  clearTimeout(pollTimer);
  online?.close().catch(() => {});
});
window.addEventListener("pageshow", (event) => {
  if (!event.persisted) return;
  // pagehide released online membership; a restored document cannot reuse that closed client.
  if (online) {
    generation++;
    online = null;
    room = null;
    busy = false;
    setEntryBusy(false);
    resetInteraction();
    showScreen("menu");
    notice("closedRoom");
  } else {
    render();
    startBot();
  }
});

let preferences = {};
try {
  preferences = JSON.parse(readStorage(PREFERENCES_KEY) || "{}") ?? {};
} catch {
  /* Ignore invalid optional settings. */
}
setLanguage(
  preferences.language === "fa" ||
    (!preferences.language && navigator.language?.startsWith("fa"))
    ? "fa"
    : "en",
);
$("language").value = currentLanguage();
$("theme").value =
  preferences.theme === "light" ||
  (!preferences.theme && matchMedia("(prefers-color-scheme: light)").matches)
    ? "light"
    : "dark";
document.documentElement.dataset.theme = $("theme").value;
document.querySelector('meta[name="theme-color"]').content =
  $("theme").value === "dark" ? "#101827" : "#f2f5f9";
translatePage();
updateSetup();
showScreen("menu");
fetch("./VERSION")
  .then((response) => (response.ok ? response.text() : ""))
  .then((text) => {
    if (/^\d+\.\d+\.\d+$/.test(text.trim())) {
      version = text.trim();
      $("app-version").textContent = `v${version}`;
    }
  })
  .catch(() => {});
fetch("./LICENSE")
  .then((response) => (response.ok ? response.text() : ""))
  .then((text) => {
    $("license-text").textContent = text;
  })
  .catch(() => {});
