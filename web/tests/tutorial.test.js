import test from 'node:test';
import assert from 'node:assert/strict';
import { applyAction } from '../src/game.js';
import { tutorialLesson } from '../src/tutorial.js';

test('each visual lesson has an independent legal practice target, including blocked-jump diagonals and a win', () => {
  for (let index = 0; index < 5; index++) {
    const { state, action } = tutorialLesson(index);
    const original = state.snapshot();
    assert(applyAction(state, action), 'lesson ' + index);
    if (index === 4) assert.equal(state.winner, 0);
    assert.deepEqual(tutorialLesson(index).state.snapshot(), original);
  }
  for (const invalid of [-1, 5, 1.5, NaN])
    assert.throws(() => tutorialLesson(invalid), RangeError);
});
