/*
 * make_fixture — encode a known payload with the HOST build of the fork's
 * codec and dump the raw RGBA bitmap + metadata, so the Node smoke test can
 * decode it through the WASM build and byte-compare.
 *
 * Native encode → WASM decode is the point: the two sides share source but
 * not toolchain, so a byte-identical round-trip is evidence the Emscripten
 * build actually reproduces the codec (not just that it links).
 *
 * Encodes through the mobile bridge (jabMobileEncode) — the same entry the
 * Android SDK uses — so the fixture's provenance matches the decode side's
 * (which mirrors the bridge's strict camera-decode call sequence).
 *
 * Output: <prefix>.rgba  raw R,G,B,A bytes, width*height*4
 *         <prefix>.json  { width, height, colorNumber, payload }
 *
 * Deliberately file-format-free (no PNG): the fixture feeds gw_frame_buffer
 * directly, mirroring how ImageData bytes arrive in the browser.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mobile_bridge.h"

static const char *PAYLOAD = "GAMUTLOCK-WASM-BRIDGE3-FIXTURE-2026-09-13";
static const int COLOR_NUMBER = 8;

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <output-prefix>\n", argv[0]);
        return 2;
    }

    jab_mobile_encode_params params = {
        .color_number = COLOR_NUMBER,
        .symbol_number = 1,
        .ecc_level = 3,
        .module_size = 12,
    };

    jab_mobile_encode_result *res = jabMobileEncode(
        (jab_char *)PAYLOAD, (jab_int32)strlen(PAYLOAD), &params);
    if (!res) {
        const char *err = jabMobileGetLastError();
        fprintf(stderr, "jabMobileEncode failed: %s\n", err ? err : "unknown");
        return 1;
    }

    char path[1024];

    snprintf(path, sizeof(path), "%s.rgba", argv[1]);
    FILE *f = fopen(path, "wb");
    if (!f) { fprintf(stderr, "cannot write %s\n", path); return 1; }
    size_t n = (size_t)res->width * res->height * 4;
    if (fwrite(res->rgba_buffer, 1, n, f) != n) {
        fprintf(stderr, "short write on %s\n", path);
        return 1;
    }
    fclose(f);

    snprintf(path, sizeof(path), "%s.json", argv[1]);
    f = fopen(path, "w");
    if (!f) { fprintf(stderr, "cannot write %s\n", path); return 1; }
    fprintf(f,
            "{\"width\":%d,\"height\":%d,\"colorNumber\":%d,\"payload\":\"%s\"}\n",
            res->width, res->height, COLOR_NUMBER, PAYLOAD);
    fclose(f);

    printf("fixture: %dx%d, %d colours, %zu-byte payload\n",
           res->width, res->height, COLOR_NUMBER, strlen(PAYLOAD));

    jabMobileEncodeResultFree(res);
    return 0;
}
