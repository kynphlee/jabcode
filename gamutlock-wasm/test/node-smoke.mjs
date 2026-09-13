/**
 * Node smoke guard for bridge #3: a payload encoded by the HOST build of the
 * fork's codec must decode byte-identically through the WASM build.
 *
 * Run via `make smoke` (which builds dist/ and the fixture first).
 *
 * Guarded properties:
 *   1. round-trip:  native encode → WASM decode → exact payload bytes
 *   2. metadata:    decoded colour count matches the fixture's encode mode
 *   3. honesty:     a corrupted frame must NOT decode to the fixture payload
 *                   (proves the harness can detect a negative — a checker that
 *                   cannot fail is not a check)
 */

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import GamutlockDetector from '../gamutlock-detector.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const meta = JSON.parse(await readFile(join(here, 'fixtures/fixture.json'), 'utf8'));
const rgba = new Uint8Array(await readFile(join(here, 'fixtures/fixture.rgba')));

const expected = meta.width * meta.height * 4;
if (rgba.length !== expected) {
  console.error(`FAIL: fixture.rgba is ${rgba.length} bytes, expected ${expected}`);
  process.exit(1);
}

const detector = new GamutlockDetector();
console.log(await detector.version());

let failures = 0;
const check = (name, cond, detail = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'}: ${name}${detail ? ` — ${detail}` : ''}`);
  if (!cond) failures++;
};

// 1+2 — clean round-trip
{
  const results = await detector.detect({ data: rgba, width: meta.width, height: meta.height });
  check('clean fixture decodes', results.length === 1);
  if (results.length === 1) {
    const got = new TextDecoder().decode(results[0].rawBytes);
    check('payload bytes exact', got === meta.payload, `got "${got}"`);
    check('colour count matches encode', results[0].colorCount === meta.colorNumber,
      `got ${results[0].colorCount}`);
    check('corner points present',
      results[0].cornerPoints.length === 4 &&
      results[0].cornerPoints.every((p) => p.x >= 0 && p.y >= 0));
  }
}

// 3 — negative control: zero out the symbol's core; decode must not return
// the fixture payload (either no result, or at minimum different bytes).
{
  const corrupted = rgba.slice();
  const rowBytes = meta.width * 4;
  const y0 = Math.floor(meta.height * 0.25), y1 = Math.floor(meta.height * 0.75);
  for (let y = y0; y < y1; y++) {
    corrupted.fill(0, y * rowBytes + Math.floor(rowBytes * 0.25),
                      y * rowBytes + Math.floor(rowBytes * 0.75));
  }
  const results = await detector.detect({ data: corrupted, width: meta.width, height: meta.height });
  const reproduced = results.length === 1 &&
    new TextDecoder().decode(results[0].rawBytes) === meta.payload;
  check('corrupted frame does not reproduce payload', !reproduced,
    results.length === 0 ? 'no decode (strict gate held)' : 'decoded different bytes');
}

// Repeat-decode stability: same module instance, second clean decode.
{
  const results = await detector.detect({ data: rgba, width: meta.width, height: meta.height });
  check('second decode on same instance', results.length === 1 &&
    new TextDecoder().decode(results[0].rawBytes) === meta.payload);
}

process.exit(failures === 0 ? 0 : 1);
