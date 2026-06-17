/*
 * bench_profile.c -- per-stage JABCode decode profiling harness.
 *
 * Encodes a fixed ~256-byte payload, then decodes it many times with the opt-in
 * stage profiler ON (jabSetProfileStages), across the colour modes Nc=4..256 at
 * ECC level 3. For each mode it reports the accumulated microseconds attributed
 * to each decode pipeline stage:
 *
 *   DETECT          finder-pattern search + RGB balance/binarize
 *   PALETTE         colour-palette read
 *   COLOR_CLASSIFY  per-module colour classification (decodeModuleHD loop)
 *   DEINTERLEAVE    bit de-interleave
 *   LDPC            LDPC hard-decision decode
 *   DATA_DECODE     final byte/encode-mode decode
 *
 * The point is attribution, not wall-clock: which stage dominates at LOW Nc vs
 * HIGH Nc. The accumulator is reset per mode (jabResetDecodeProfile) and the
 * reported per-stage value is the total over `iters` decodes divided by the
 * decode count -> mean microseconds per decode per stage. Warmup decodes (not
 * reset into the measured window) populate the LDPC matrix cache so the timed
 * window is cached steady-state, matching bench_codec.c.
 *
 * This links the static libjabcode and does no PNG I/O -- it profiles the pure
 * C codec on a pristine in-memory bitmap, the cleanest stage attribution.
 *
 * Output:
 *   stdout -> JSON array of per-(Nc,stage) records (feeds the chart script)
 *   stderr -> human-readable table
 *
 * Usage: bench_profile [warmup] [iters]      (defaults: 5 30)
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "decode_profile.h"

#if defined(__aarch64__)
#  define PLATFORM "arm64"
#elif defined(__x86_64__)
#  define PLATFORM "x86_64"
#elif defined(__arm__)
#  define PLATFORM "armv7"
#else
#  define PLATFORM "unknown"
#endif

/* Stage labels, indexed by jab_decode_stage. Kept in lock-step with the enum in
 * decode_profile.h (compile-time checked below). */
static const char *STAGE_NAME[JAB_STAGE_COUNT] = {
	"DETECT", "PALETTE", "COLOR_CLASSIFY", "DEINTERLEAVE", "LDPC", "DATA_DECODE"
};

static jab_data *mkdata(const jab_byte *s, int n) {
	jab_data *d = (jab_data *)malloc(sizeof(jab_data) + n);
	d->length = n;
	memcpy(d->data, s, n);
	return d;
}

int main(int argc, char **argv) {
	int warmup = argc > 1 ? atoi(argv[1]) : 5;
	int iters  = argc > 2 ? atoi(argv[2]) : 30;
	if (warmup < 0) warmup = 0;
	if (iters < 1) iters = 1;

	/* Nc=4..256 (the colour modes requested); Nc=2 is excluded by design. */
	const int modes[] = {4, 8, 16, 32, 64, 128, 256};
	const int n_modes = (int)(sizeof(modes) / sizeof(modes[0]));
	const int ecc = 3;

	/* Fixed ~256-byte payload (printable, deterministic). */
	const int payload_len = 256;
	jab_byte payload[256];
	for (int i = 0; i < payload_len; i++)
		payload[i] = (jab_byte)(0x20 + (i % 95));   /* cycle printable ASCII */

	fprintf(stderr, "platform=%s warmup=%d iters=%d payload_bytes=%d ecc=%d\n",
	        PLATFORM, warmup, iters, payload_len, ecc);
	fprintf(stderr, "%-5s %10s %10s %10s %10s %10s %10s %10s %6s\n",
	        "Nc", "DETECT", "PALETTE", "COLORCLS", "DEINTLV", "LDPC",
	        "DATADEC", "TOTAL", "ok");

	printf("[\n");
	int first = 1;
	for (int m = 0; m < n_modes; m++) {
		int color = modes[m];

		/* Encode once -> pristine in-memory bitmap reused for every decode. */
		jab_encode *e0 = createEncode(color, 1);
		if (e0 && e0->symbol_ecc_levels) e0->symbol_ecc_levels[0] = ecc;
		jab_data *d0 = mkdata(payload, payload_len);
		int genrc = generateJABCode(e0, d0);
		if (genrc != 0 || !e0->bitmap) {
			fprintf(stderr, "%-5d  encode failed (genrc=%d) -- skipped\n", color, genrc);
			free(d0);
			destroyEncode(e0);
			continue;
		}

		/* Warmup (profiling OFF): prime the LDPC matrix cache so the measured
		 * window is cached steady-state and not polluted by first-touch cost. */
		jabSetProfileStages(0);
		for (int i = 0; i < warmup; i++) {
			jab_int32 st = 0;
			jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
			if (r) free(r);
		}

		/* Measured window (profiling ON): accumulate per-stage microseconds. */
		jabResetDecodeProfile();
		jabSetProfileStages(1);
		int ok = 0;
		for (int i = 0; i < iters; i++) {
			jab_int32 st = 0;
			jab_data *r = decodeJABCode(e0->bitmap, NORMAL_DECODE, &st);
			if (r) { ok++; free(r); }
		}
		jabSetProfileStages(0);

		const jab_decode_profile *p = jabGetDecodeProfile();
		jab_int64 denom = p->decode_count > 0 ? p->decode_count : 1;
		double per[JAB_STAGE_COUNT];
		double total = 0.0;
		for (int s = 0; s < JAB_STAGE_COUNT; s++) {
			per[s] = (double)p->stage_us[s] / (double)denom;   /* mean us/decode */
			total += per[s];
		}

		/* JSON record per (Nc, stage): mean microseconds per decode. */
		for (int s = 0; s < JAB_STAGE_COUNT; s++) {
			if (!first) printf(",\n");
			first = 0;
			printf("  {\"platform\":\"%s\",\"colours\":%d,\"stage\":\"%s\","
			       "\"us_per_decode\":%.3f,\"decodes\":%lld,\"ok\":%d}",
			       PLATFORM, color, STAGE_NAME[s], per[s],
			       (long long)p->decode_count, ok);
		}

		fprintf(stderr, "%-5d %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %3d/%d\n",
		        color, per[JAB_STAGE_DETECT], per[JAB_STAGE_PALETTE],
		        per[JAB_STAGE_COLOR_CLASSIFY], per[JAB_STAGE_DEINTERLEAVE],
		        per[JAB_STAGE_LDPC], per[JAB_STAGE_DATA_DECODE], total, ok, iters);

		free(d0);
		destroyEncode(e0);
	}
	printf("\n]\n");
	return 0;
}
