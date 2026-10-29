import { createServer } from 'node:http';
import { lstat, readFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { assertGeneratedPath, assertNoLinks, assertWithin, siteRoot } from './paths.mjs';

const mimeTypes = {
  '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8',
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.md': 'text/plain; charset=utf-8',
};

export async function startServer({ port = 4173, host = '127.0.0.1', base = '/Game-Quoridor/', root = siteRoot } = {}) {
  if (!['127.0.0.1', 'localhost', '::1'].includes(host)) throw new Error('The preview server only listens on loopback.');
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid preview port.');
  if (!/^\/(?:[A-Za-z0-9_-]+\/)*$/.test(base)) throw new Error('Use a simple absolute base ending in a slash.');
  await assertGeneratedPath(root, { recursive: true });
  if (!(await lstat(root)).isDirectory()) throw new Error('Build the web site before serving it.');
  const server = createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.writeHead(405, { Allow: 'GET, HEAD' });
      response.end();
      return;
    }
    try {
      if ((request.url?.length ?? 0) > 2048) { response.writeHead(414); response.end(); return; }
      const url = new URL(request.url, 'http://localhost');
      if (url.pathname === base.slice(0, -1) || (base !== '/' && url.pathname === '/')) {
        response.writeHead(308, { Location: base }); response.end(); return;
      }
      const pathname = decodeURIComponent(url.pathname);
      if (!pathname.startsWith(base) || pathname.includes('\\') || pathname.includes('\0')) {
        response.writeHead(404); response.end(); return;
      }
      const relative = pathname.slice(base.length) || 'index.html';
      if (relative.split('/').some(part => part === '..' || part.startsWith('.'))) {
        response.writeHead(404); response.end(); return;
      }
      const target = assertWithin(root, path.join(root, relative));
      let current = root;
      for (const part of path.relative(root, target).split(path.sep)) {
        current = path.join(current, part);
        await assertNoLinks(current);
      }
      if (!(await lstat(target)).isFile()) { response.writeHead(404); response.end(); return; }
      const bytes = await readFile(target);
      response.writeHead(200, { 'Content-Type': mimeTypes[path.extname(target)] ?? 'text/plain; charset=utf-8', 'Content-Length': bytes.length });
      response.end(request.method === 'HEAD' ? undefined : bytes);
    } catch (error) {
      response.writeHead(error instanceof URIError ? 400 : 404);
      response.end();
    }
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, resolve);
  });
  const address = server.address();
  const url = `http://${host === '::1' ? '[::1]' : host}:${address.port}${base}`;
  return { server, url, close: () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())) };
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const options = {};
    for (let index = 2; index < process.argv.length; index += 2) {
      const flag = process.argv[index];
      const value = process.argv[index + 1];
      if (!['--port', '--host', '--base'].includes(flag) || value === undefined) throw new Error('Usage: node scripts/serve.mjs [--port 4173] [--host 127.0.0.1] [--base /Game-Quoridor/]');
      options[flag.slice(2)] = flag === '--port' ? Number(value) : value;
    }
    const preview = await startServer(options);
    console.log(`Quoridor preview: ${preview.url}`);
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => preview.close().finally(() => process.exit()));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
