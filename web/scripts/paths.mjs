import { lstat, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repositoryRoot = fileURLToPath(new URL('../../', import.meta.url));
export const webRoot = path.join(repositoryRoot, 'web');
export const outputRoot = path.join(repositoryRoot, 'out', 'web');
export const siteRoot = path.join(outputRoot, 'site');

export function assertWithin(parent, target) {
  const relative = path.relative(path.resolve(parent), path.resolve(target));
  if (!relative || relative.startsWith(`..${path.sep}`) || relative === '..' || path.isAbsolute(relative)) {
    throw new Error(`Expected a child of ${parent}: ${target}`);
  }
  return path.resolve(target);
}

export async function assertNoLinks(target, { recursive = false } = {}) {
  let info;
  try {
    info = await lstat(target);
  } catch (error) {
    if (error.code === 'ENOENT') return;
    throw error;
  }
  if (info.isSymbolicLink()) throw new Error(`Linked paths are not accepted: ${target}`);
  if (recursive && info.isDirectory()) {
    for (const name of await readdir(target)) {
      await assertNoLinks(path.join(target, name), { recursive: true });
    }
  }
}

export async function assertGeneratedPath(target, { recursive = false } = {}) {
  const resolved = assertWithin(path.join(repositoryRoot, 'out'), target);
  const segments = path.relative(repositoryRoot, resolved).split(path.sep);
  let current = repositoryRoot;
  for (const segment of segments) {
    current = path.join(current, segment);
    await assertNoLinks(current);
  }
  if (recursive) await assertNoLinks(resolved, { recursive: true });
  return resolved;
}

export async function assertSourceFile(target) {
  const resolved = assertWithin(repositoryRoot, target);
  let current = repositoryRoot;
  for (const segment of path.relative(repositoryRoot, resolved).split(path.sep)) {
    current = path.join(current, segment);
    await assertNoLinks(current);
  }
  if (!(await lstat(resolved)).isFile()) throw new Error(`Missing production file: ${resolved}`);
  return resolved;
}
