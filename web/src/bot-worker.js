import { chooseAction } from './bot.js';

// The owner terminates this dedicated worker when a game/revision is replaced.
globalThis.addEventListener('message', event => {
  const { id, revision, snapshot, difficulty } = event.data ?? {};
  try {
    globalThis.postMessage({ id, revision, action: chooseAction(snapshot, difficulty) });
  } catch (error) {
    globalThis.postMessage({ id, revision, error: error instanceof Error ? error.message : 'Robot search failed' });
  }
});
