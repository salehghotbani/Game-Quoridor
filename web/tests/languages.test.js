import test from 'node:test';
import assert from 'node:assert/strict';
import { extraLanguages } from '../src/languages.js';
import {
  setLanguage,
  currentLanguage,
  t,
  supportedLanguages,
} from '../src/i18n.js';

test('five explicit languages translate every visible template with stable placeholders', () => {
  assert.deepEqual(supportedLanguages, ['en', 'fa', 'es', 'fr', 'ar']);
  for (const language of ['es', 'fr', 'ar']) {
    for (const [key, value] of Object.entries(extraLanguages[language])) {
      setLanguage('en');
      const english = t(
        key,
        Object.fromEntries(
          [...value.matchAll(/\{(\w+)\}/g)].map((match) => [
            match[1],
            match[0],
          ]),
        ),
      );
      assert.notEqual(english, key, key);
      assert.deepEqual(
        value.match(/\{\w+\}/g) ?? [],
        english.match(/\{\w+\}/g) ?? [],
        key,
      );
      setLanguage(language);
      assert.equal(t(key), value.replace(/\{\w+\}/g, ''));
    }
  }
  setLanguage('he');
  assert.equal(currentLanguage(), 'en');
});
