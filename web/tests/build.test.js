import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { buildSite, productionFiles } from '../scripts/build.mjs';
import { assertGeneratedPath, repositoryRoot, siteRoot } from '../scripts/paths.mjs';
import { startServer } from '../scripts/serve.mjs';

async function inventory(directory, prefix = '') {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const relative = prefix + entry.name;
    if (entry.isDirectory()) result.push(...await inventory(path.join(directory, entry.name), `${relative}/`));
    else result.push(relative);
  }
  return result.sort();
}

test('production build publishes only explicit game files, notices, and matching hashes', async () => {
  const result = await buildSite();
  const expected = [...productionFiles, 'LICENSE', 'AUTHORS.md', 'VERSION', 'version.json', '.nojekyll', 'build-manifest.json'].sort();
  assert.deepEqual(await inventory(siteRoot), expected);
  const manifest = JSON.parse(await readFile(path.join(siteRoot, 'build-manifest.json'), 'utf8'));
  assert.equal(manifest.version, result.version);
  for (const [relative, expectedHash] of Object.entries(manifest.files)) {
    assert.equal(createHash('sha256').update(await readFile(path.join(siteRoot, relative))).digest('hex'), expectedHash);
  }
  for (const notice of ['LICENSE', 'AUTHORS.md', 'VERSION']) {
    assert.deepEqual(await readFile(path.join(siteRoot, notice)), await readFile(path.join(repositoryRoot, notice)));
  }
  await writeFile(path.join(siteRoot, 'stale-private-file.txt'), 'must not survive a rebuild');
  await buildSite();
  assert.deepEqual(await inventory(siteRoot), expected);
  await assert.rejects(assertGeneratedPath(repositoryRoot), /child/);
  await assert.rejects(assertGeneratedPath(path.resolve(repositoryRoot, '..', 'other')), /child/);
});

test('preview serves the Pages subpath with correct modules and no path escape', async () => {
  await buildSite();
  const preview = await startServer({ port: 0 });
  try {
    const root = await fetch(preview.url);
    assert.equal(root.status, 200);
    assert.match(root.headers.get('content-type'), /^text\/html/);
    assert.equal(root.headers.get('cache-control'), 'no-store');
    const script = await fetch(`${preview.url}src/app.js`);
    assert.equal(script.status, 200);
    assert.match(script.headers.get('content-type'), /^text\/javascript/);
    for (const relative of ['../VERSION', '%2e%2e/VERSION', '%2e%2e%2fVERSION', '..%5cVERSION', '.git/config', 'src/', 'missing.js']) {
      const response = await fetch(`${preview.url}${relative}`);
      assert.equal(response.status, 404, relative);
    }
    const method = await fetch(preview.url, { method: 'POST' });
    assert.equal(method.status, 405);
    const head = await fetch(`${preview.url}styles.css`, { method: 'HEAD' });
    assert.equal(head.status, 200);
    assert.equal(await head.text(), '');
    const withoutSlash = await fetch(preview.url.slice(0, -1), { redirect: 'manual' });
    assert.equal(withoutSlash.status, 308);
    assert.equal(withoutSlash.headers.get('location'), '/Game-Quoridor/');
  } finally {
    await preview.close();
  }
});
