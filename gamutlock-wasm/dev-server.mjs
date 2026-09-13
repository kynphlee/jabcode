/**
 * Spike dev server for the gamutlock-wasm reader pages.
 *
 * Serves this directory (scan.html, bench.html, dist/, the wrapper) plus the
 * R0 corpus, and reverse-proxies /api/* to the studio backend so the scan
 * page's verify POST is same-origin — which is exactly the production shape:
 * the scan page is ultimately hosted at resolver.gamutlock.com behind the
 * same front door as the verify endpoint, so the spike encodes "no CORS
 * story needed" as an architectural assumption, not an accident.
 *
 * Zero dependencies; Node ≥18.
 *
 *   node dev-server.mjs [--port 8321] [--studio http://localhost:8080]
 */

import { createServer, request as httpRequest } from 'node:http';
import { readFile, stat, writeFile, mkdir } from 'node:fs/promises';
import { join, normalize, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));

const args = process.argv.slice(2);
const flag = (name, dflt) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : dflt;
};
const PORT = Number(flag('port', '8321'));
const STUDIO = new URL(flag('studio', 'http://localhost:8080'));

/* Static mounts. /corpus/* exposes the R0 rig + synthetic corpus read-only so
 * bench.html can fetch the exact images the native rig decodes. */
const MOUNTS = [
  { prefix: '/corpus/rig/', root: join(here, '../robustness/r0/rig/') },
  { prefix: '/corpus/synthetic/', root: join(here, '../robustness/r0/synthetic/out/') },
  { prefix: '/', root: here },
];

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.json': 'application/json',
  '.jsonl': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.css': 'text/css; charset=utf-8',
};

function proxy(req, res) {
  const upstream = httpRequest(
    new URL(req.url, STUDIO),
    { method: req.method, headers: { ...req.headers, host: STUDIO.host } },
    (up) => {
      res.writeHead(up.statusCode ?? 502, up.headers);
      up.pipe(res);
    });
  upstream.on('error', (err) => {
    res.writeHead(502, { 'content-type': 'application/json' });
    res.end(JSON.stringify({
      error: 'studio backend unreachable',
      detail: String(err), studio: STUDIO.href,
    }));
  });
  req.pipe(upstream);
}

async function serveStatic(req, res) {
  const url = new URL(req.url, 'http://x');
  let path = decodeURIComponent(url.pathname);
  if (path.endsWith('/')) path += 'scan.html';

  for (const { prefix, root } of MOUNTS) {
    if (!path.startsWith(prefix)) continue;
    const rel = path.slice(prefix.length);
    const full = normalize(join(root, rel));
    if (!full.startsWith(normalize(root))) break; // traversal guard
    try {
      const s = await stat(full);
      if (!s.isFile()) continue;
      const body = await readFile(full);
      res.writeHead(200, {
        'content-type': MIME[extname(full)] ?? 'application/octet-stream',
        'cache-control': 'no-store',
      });
      res.end(body);
      return;
    } catch {
      continue; // not in this mount; try the next
    }
  }
  res.writeHead(404, { 'content-type': 'text/plain' });
  res.end(`not found: ${path}`);
}

/* Bench-result sink: POST /save?name=<file> writes the body into
 * bench/results/ (spike convenience so browser runs land beside the native
 * runs; name is whitelisted, no traversal). */
async function saveResults(req, res) {
  const name = new URL(req.url, 'http://x').searchParams.get('name') ?? '';
  if (!/^[a-z0-9_.-]+\.jsonl?$/i.test(name)) {
    res.writeHead(400); res.end('bad name'); return;
  }
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const dir = join(here, 'bench/results');
  await mkdir(dir, { recursive: true });
  await writeFile(join(dir, name), Buffer.concat(chunks));
  res.writeHead(200, { 'content-type': 'application/json' });
  res.end(JSON.stringify({ saved: name, bytes: Buffer.concat(chunks).length }));
}

createServer((req, res) => {
  if (req.url.startsWith('/api/')) return proxy(req, res);
  if (req.method === 'POST' && req.url.startsWith('/save')) return saveResults(req, res);
  serveStatic(req, res);
}).listen(PORT, () => {
  console.log(`gamutlock-wasm dev server: http://localhost:${PORT}/scan.html`);
  console.log(`bench:                    http://localhost:${PORT}/bench.html`);
  console.log(`proxying /api/* -> ${STUDIO.href}`);
});
