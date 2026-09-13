/**
 * GamutlockDetector — a BarcodeDetector-polyfill-shaped wrapper over the
 * gamutlock-wasm module (bridge #3 of the jabcode fork).
 *
 * API shape follows https://github.com/Sec-ant/barcode-detector (the
 * BarcodeDetector polyfill zxing-wasm ships). Known deviations a polyfill
 * consumer may hit: detect(Blob) and detect(VideoFrame) are not supported
 * (they return [] — rasterize via createImageBitmap/canvas first, as
 * scan.html does); boundingBox is a frozen rect-shaped plain object, not a
 * DOMRectReadOnly; at most one detection per call (JAB cascades decode as
 * one payload through the master symbol). Core shape:
 *
 *   const det = new GamutlockDetector();
 *   const results = await det.detect(imageDataOrCanvasSource);
 *   // → [{ format: 'jab_code', rawValue, rawBytes, cornerPoints,
 *   //      boundingBox, colorCount }]
 *
 * Extensions beyond the polyfill contract (additive, never breaking):
 *   rawBytes    Uint8Array — the exact decoded payload. JAB Code carries
 *               binary credential blobs; rawValue alone (a lossy UTF-8
 *               projection) is NOT what you POST to the verify endpoint.
 *   colorCount  decoded colour mode (2..256).
 *
 * Instantiation is deferred (zxing-wasm style): the WASM module loads and
 * compiles on the first detect() — or explicitly via prepare() — so pages
 * that import the detector but never scan pay nothing.
 */

import createGamutlockCodec from './dist/gamutlock-codec.mjs';

const FORMAT = 'jab_code';

function makeRect(x, y, width, height) {
  // DOMRectReadOnly shape, constructed portably (Node has no DOMRect).
  return Object.freeze({
    x, y, width, height,
    top: y, left: x, right: x + width, bottom: y + height,
  });
}

export class GamutlockDetector {
  #modulePromise = null;
  #module = null;
  #options;
  #scratchCanvas = null;

  /**
   * @param {object} [options]
   * @param {string[]} [options.formats]  Accepted for BarcodeDetector parity;
   *   only 'jab_code' is supported and anything else throws.
   * @param {boolean} [options.quiet=true]  Silence the codec's stdout/stderr
   *   (the decoder reports every failed frame; a live scan loop fails on most
   *   frames by design, which would otherwise flood the console).
   * @param {object} [options.moduleOptions]  Extra Emscripten Module options
   *   (e.g. locateFile to serve the .wasm from a CDN path).
   * @param {function} [options.moduleFactory]  Alternative Emscripten module
   *   factory (e.g. the dist-simd build). Defaults to the baseline dist build.
   *   The A/B bench uses this so both builds run through the identical wrapper.
   */
  constructor(options = {}) {
    const { formats } = options;
    if (formats && formats.some((f) => f !== FORMAT)) {
      throw new TypeError(
        `GamutlockDetector supports only '${FORMAT}' (got: ${formats.join(', ')})`);
    }
    this.#options = options;
  }

  static async getSupportedFormats() {
    return [FORMAT];
  }

  /** Load + instantiate the WASM module now instead of on first detect(). */
  async prepare() {
    if (!this.#modulePromise) {
      const { quiet = true, moduleOptions = {}, moduleFactory } = this.#options;
      const factory = moduleFactory ?? createGamutlockCodec;
      const silenced = quiet ? { print: () => {}, printErr: () => {} } : {};
      this.#modulePromise = factory({ ...silenced, ...moduleOptions })
        .then((m) => (this.#module = m));
    }
    await this.#modulePromise;
    return this;
  }

  /** Pin the decoder to one colour count (0 = auto ladder). */
  async setPreferredColorCount(count) {
    await this.prepare();
    this.#module._gw_set_preferred_color_count(count | 0);
  }

  async version() {
    await this.prepare();
    const m = this.#module;
    return m.UTF8ToString(m._gw_version());
  }

  /**
   * @param {ImageData|{data:Uint8ClampedArray|Uint8Array,width:number,height:number}|CanvasImageSource|ImageBitmap} source
   * @returns {Promise<Array>} zero or one DetectedBarcode-shaped results
   *   (JAB cascades decode as one payload through the master symbol).
   */
  async detect(source) {
    await this.prepare();
    const imageData = this.#toImageData(source);
    if (!imageData) return [];

    const m = this.#module;
    const { data, width, height } = imageData;
    const ptr = m._gw_frame_buffer(width, height);
    if (!ptr) {
      throw new Error(`gamutlock-wasm: ${m.UTF8ToString(m._gw_last_error())}`);
    }
    m.HEAPU8.set(data, ptr);

    const ok = m._gw_decode_frame();
    if (!ok) return [];

    const len = m._gw_result_length();
    const dataPtr = m._gw_result_data();
    // Copy out before anything else touches the heap (growth invalidates views).
    const rawBytes = new Uint8Array(m.HEAPU8.subarray(dataPtr, dataPtr + len));

    const cornerPoints = [];
    for (let i = 0; i < 4; i++) {
      cornerPoints.push({
        x: m._gw_result_corner_x(i),
        y: m._gw_result_corner_y(i),
      });
    }
    const xs = cornerPoints.map((p) => p.x);
    const ys = cornerPoints.map((p) => p.y);
    const minX = Math.min(...xs), minY = Math.min(...ys);

    return [{
      format: FORMAT,
      rawValue: new TextDecoder('utf-8', { fatal: false }).decode(rawBytes),
      rawBytes,
      colorCount: m._gw_result_color_count(),
      cornerPoints,
      boundingBox: makeRect(minX, minY,
        Math.max(...xs) - minX, Math.max(...ys) - minY),
    }];
  }

  #toImageData(source) {
    if (!source) return null;
    // ImageData or a structurally-equivalent plain object (Node tests).
    if (typeof source.width === 'number' && typeof source.height === 'number'
        && source.data && typeof source.data.length === 'number') {
      return source;
    }
    // CanvasImageSource / ImageBitmap: rasterize at intrinsic size.
    if (typeof document !== 'undefined') {
      const w = source.videoWidth ?? source.naturalWidth ?? source.width;
      const h = source.videoHeight ?? source.naturalHeight ?? source.height;
      if (!w || !h) return null;
      if (!this.#scratchCanvas) this.#scratchCanvas = document.createElement('canvas');
      const canvas = this.#scratchCanvas;
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(source, 0, 0, w, h);
      return ctx.getImageData(0, 0, w, h);
    }
    throw new TypeError('gamutlock-wasm: unsupported detect() source in this environment');
  }
}

export default GamutlockDetector;
