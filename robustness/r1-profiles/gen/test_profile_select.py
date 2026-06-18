#!/usr/bin/env python3
"""End-to-end conformance check for the R1 profile selector.

For every canonical medium, ask profile_select.select() for an (Nc, ECC), then
actually ENCODE a representative payload at those parameters with the reference
encoder (a tiny one-off via the jabcodeWriter CLI) and DECODE it through the R0
rig probe with strict-PartII enabled. Asserts:

  * the selected (Nc, ECC) are spec-legal (Nc in 0..7, ECC in 1..10);
  * the encode succeeds and the rendered colour count == 2^(Nc+1);
  * the decode succeeds (status 3) AND the decoded payload SHA-256 matches the
    input -- i.e. the recommendation yields a real, conformant, round-trip-clean
    symbol, not just a parameter pair.

This is the helper's own guard; the three core conformance guards remain
`make -C src/jabcode test-eci test-table15 test-roundtrip`.

Run:  /tmp/bench-venv/bin/python test_profile_select.py   (or system python3)
"""
import hashlib
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
R1_DIR = os.path.dirname(HERE)
REPO_ROOT = os.path.abspath(os.path.join(R1_DIR, "..", ".."))
WRITER = os.path.join(REPO_ROOT, "src", "jabcodeWriter", "bin", "jabcodeWriter")
PROBE = os.path.join(REPO_ROOT, "robustness", "r0", "rig", "r0_decode")

sys.path.insert(0, R1_DIR)
import profile_select as ps  # noqa: E402

PAYLOAD = b"COA:RHABI-2026:SN=DEADBEEF0BADF00D;url=https://verify.example/c/DEADBEEF0BADF00D"
MEDIA = ["hostile", "screen", "print", "luxury-coa", "clean", "archival"]


def main() -> int:
    if not os.path.isfile(WRITER):
        print(f"SKIP: jabcodeWriter not built ({WRITER}); "
              f"run `make -C src/jabcodeWriter`", file=sys.stderr)
        return 0
    if not os.path.isfile(PROBE):
        print(f"SKIP: r0_decode not built ({PROBE}); "
              f"run `make -C robustness/r0/rig`", file=sys.stderr)
        return 0

    want_sha = hashlib.sha256(PAYLOAD).hexdigest()
    fails = 0
    with tempfile.TemporaryDirectory() as td:
        for medium in MEDIA:
            rec = ps.select(medium, payload_len=len(PAYLOAD))
            nc, ecc, colors = rec["nc"], rec["ecc"], rec["colors"]

            # legality
            assert nc in ps.NC_TO_COLORS, f"{medium}: illegal Nc {nc}"
            assert ps.ECC_MIN <= ecc <= ps.ECC_MAX, f"{medium}: illegal ECC {ecc}"
            assert colors == 1 << (nc + 1)

            png = os.path.join(td, f"{medium}.png")
            enc = subprocess.run(
                [WRITER, "--input", PAYLOAD.decode(), "--output", png,
                 "--color-number", str(colors), "--ecc-level", str(ecc)],
                capture_output=True, text=True)
            if enc.returncode != 0 or not os.path.isfile(png):
                print(f"FAIL {medium:11} Nc{nc}/ECC{ecc}: encode rc={enc.returncode} "
                      f"{enc.stdout.strip()} {enc.stderr.strip()}")
                fails += 1
                continue

            dec = subprocess.run([PROBE, png], capture_output=True, text=True)
            import json
            res = json.loads(dec.stdout.strip().splitlines()[-1])
            ok = (res["decode_ok"] == 1 and res["status"] == 3
                  and res["payload_sha256"] == want_sha and res["nc"] == nc)
            tag = "ok  " if ok else "FAIL"
            if not ok:
                fails += 1
            print(f"{tag} {medium:11} Nc{nc}/ECC{ecc} ({colors:3}c) "
                  f"-> decode_ok={res['decode_ok']} status={res['status']} "
                  f"decoded_nc={res['nc']} sha_match={res['payload_sha256']==want_sha} "
                  f"| cap_v7={rec['capacity_v7_bytes']}B fits={rec['fits_at_v7']}")

    print(f"\nprofile-select e2e: {len(MEDIA)-fails}/{len(MEDIA)} media "
          f"encode+decode+verify clean")
    print("RESULT:", "PASS" if fails == 0 else "FAIL")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
