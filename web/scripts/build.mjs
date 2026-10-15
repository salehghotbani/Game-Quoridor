import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { copyFile, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import {
  assertGeneratedPath, assertSourceFile, outputRoot, repositoryRoot, siteRoot, webRoot,
} from './paths.mjs';

export const productionFiles = Object.freeze([
  'index.html', 'styles.css', 'icon.svg',
  'src/app.js', 'src/i18n.js', 'src/board-view.js', 'src/local-session.js',
  'src/game.js', 'src/bot.js', 'src/save.js', 'src/online.js', 'src/bot-worker.js',
]);
const notices = Object.freeze(['LICENSE', 'AUTHORS.md', 'VERSION']);

function sourceCommit() {
  const candidate = process.env.GITHUB_SHA ?? (() => {
    try {
      return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repositoryRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
      return undefined;
    }
  })();
  return /^[a-f0-9]{40}$/i.test(candidate ?? '') ? candidate : undefined;
}

async function removeGenerated(target) {
  await assertGeneratedPath(target, { recursive: true });
  await rm(target, { recursive: true, force: true });
}

export async function buildSite() {
  const version = (await readFile(await assertSourceFile(path.join(repositoryRoot, 'VERSION')), 'utf8')).trim();
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version)) {
    throw new Error('VERSION must contain a numeric MAJOR.MINOR.PATCH.');
  }
  await assertGeneratedPath(outputRoot);
  await mkdir(outputRoot, { recursive: true });
  const staging = await assertGeneratedPath(path.join(outputRoot, `.site-stage-${randomUUID()}`));
  const backup = await assertGeneratedPath(path.join(outputRoot, `.site-previous-${randomUUID()}`));
  await mkdir(staging);
  let previous = false;
  try {
    for (const relative of productionFiles) {
      const source = await assertSourceFile(path.join(webRoot, relative));
      const destination = path.join(staging, relative);
      await mkdir(path.dirname(destination), { recursive: true });
      await copyFile(source, destination);
    }
    for (const name of notices) {
      await copyFile(await assertSourceFile(path.join(repositoryRoot, name)), path.join(staging, name));
    }
    const commit = sourceCommit();
    const metadata = { version, ...(commit ? { commit } : {}) };
    await writeFile(path.join(staging, 'version.json'), `${JSON.stringify(metadata, null, 2)}\n`);
    await writeFile(path.join(staging, '.nojekyll'), '');
    const hashes = {};
    for (const relative of [...productionFiles, ...notices, 'version.json', '.nojekyll']) {
      hashes[relative] = createHash('sha256').update(await readFile(path.join(staging, relative))).digest('hex');
    }
    await writeFile(path.join(staging, 'build-manifest.json'), `${JSON.stringify({ ...metadata, files: hashes }, null, 2)}\n`);
    await assertGeneratedPath(siteRoot, { recursive: true });
    try {
      await rename(siteRoot, backup);
      previous = true;
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    try {
      await rename(staging, siteRoot);
    } catch (error) {
      if (previous) await rename(backup, siteRoot);
      throw error;
    }
    if (previous) await removeGenerated(backup);
    return { version, directory: siteRoot, files: Object.keys(hashes).length + 1 };
  } finally {
    await removeGenerated(staging);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const result = await buildSite();
    console.log(`Built Quoridor ${result.version}: ${result.files} files in ${result.directory}`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
