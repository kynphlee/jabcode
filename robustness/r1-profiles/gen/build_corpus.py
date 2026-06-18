#!/usr/bin/env python3
"""R1 per-medium profile corpus builder.

Ties together the existing R0 instruments to measure decode-rate as a function
of (Nc, ECC) per degradation family, from which per-medium encoding profiles are
derived. It does NOT reimplement any degradation or decoding -- it orchestrates:

  1. grid_encode (this dir)  -> clean (Nc x ECC) symbols, AUTO geometry, for a
       FIXED-LENGTH payload. AUTO (not pinned) geometry is deliberate: the
       reference encoder's fitDataIntoSymbols() recomputes the LDPC (wc,wr) to
       fill the chosen version (getOptimalECC, encoder.c:2087), so pinning a
       large version with a small payload makes ECC 3/5/8 produce BYTE-IDENTICAL
       symbols -- the ECC axis collapses (verified: identical md5). The only
       conformant lever that lets the requested ECC change the symbol is version
       selection: higher ECC forces a larger version (more parity area). AUTO
       geometry therefore exercises the real ECC effect; the area it costs is
       recorded in grid_density.csv -- the larger symbol IS how ECC buys
       robustness. See README "Why AUTO geometry".

  2. robustness/r0/synthetic/degrade.py  -> the SAME deterministic degradation
       families, restricted to the colour/luminance set this task targets:
       chroma, lighting, blur, downscale (full param ladders for a dense curve).

  3. robustness/r0/rig (r0_decode + run_rig.py)  -> decode-rate + fail-stage,
       bucketed per (Nc, ECC, family, param), with known-payload SHA-256
       verification so a decode counts only if the bytes are correct.

De-noising: a single degraded image yields a 0/1 Bernoulli outcome, so a one-
image "rate" is one coin flip. We instead encode N distinct fixed-LENGTH
payloads per (Nc, ECC) cell (deterministic, reproducible), degrade and decode
each, and report decode-rate per (Nc, ECC, family, param) as the mean over the
N payloads x the family's params -- smoothing both the Bernoulli noise and the
blur-vs-module-size resonance that makes a single image jump 0<->1.

Outputs (under robustness/r1-profiles/data/):
  grid_density.csv      per (Nc, ECC): AUTO side_size, image px, area, area-cost
  manifest.jsonl        one rig record per degraded image (nc, ecc, payload sha)
  rig.per_image.jsonl   raw rig per-image outcomes
  rig.aggregate.json    rig aggregate (bucketed by conditions)
  decode_rates.csv      tidy (nc, colors, ecc, family, param, n, decoded, rate)
  clean_baseline.csv    per (Nc, ECC, payload): clean (undegraded) decode sanity

The committed clean grid lives in data/symbols/ (N payloads x 15 cells, small
PNGs). The degraded corpus (data/corpus/) is gitignored (regenerable); only the
measurement CSV/JSON are committed.

Usage:
  build_corpus.py [--module-size 12] [--payloads 5]
                  [--families chroma,lighting,blur,downscale]
                  [--keep-corpus]   # also keep degraded PNGs (default: prune)
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
R1_DIR = os.path.dirname(HERE)                       # robustness/r1-profiles
REPO_ROOT = os.path.abspath(os.path.join(R1_DIR, "..", ".."))
SYNTH_DIR = os.path.join(REPO_ROOT, "robustness", "r0", "synthetic")
RIG_DIR = os.path.join(REPO_ROOT, "robustness", "r0", "rig")

DATA_DIR = os.path.join(R1_DIR, "data")
SYMBOLS_DIR = os.path.join(DATA_DIR, "symbols")      # committed clean grid
CORPUS_DIR = os.path.join(DATA_DIR, "corpus")        # gitignored degraded set

GRID_ENCODE = os.path.join(HERE, "grid_encode")
GRID_CAPACITY = os.path.join(HERE, "grid_capacity")
PROBE = os.path.join(RIG_DIR, "r0_decode")
RUN_RIG = os.path.join(RIG_DIR, "run_rig.py")

# Common side version for the constant-geometry density (capacity) probe. 7 =>
# side 45, the largest the AUTO grid reaches for this payload, so every cell has
# real headroom to express its capacity.
CAPACITY_VERSION = 7

# Import the R0 degradation families directly (reuse, do not duplicate).
sys.path.insert(0, SYNTH_DIR)
import degrade  # noqa: E402  (path injected above)
from PIL import Image  # noqa: E402

# The colour/luminance families this task targets, in report order.
DEFAULT_FAMILIES = ["chroma", "lighting", "blur", "downscale"]

# Fixed-length COA-style payload template. {sn} is replaced by a per-payload
# 16-hex serial so the payloads are DISTINCT but identical in length (geometry
# stays comparable across payloads within a cell). Deterministic -> reproducible.
PAYLOAD_TMPL = "COA:RHABI-2026:SN={sn};url=https://verify.example/c/{sn}"


def make_payload(idx: int) -> bytes:
    """Deterministic distinct fixed-length payload #idx."""
    # 16 hex chars from a fixed LCG seeded by idx -> stable across runs.
    x = (0x9E3779B97F4A7C15 ^ (idx * 0xD1B54A32D192ED03)) & ((1 << 64) - 1)
    sn = f"{x:016X}"
    p = PAYLOAD_TMPL.format(sn=sn).encode("ascii")
    return p


def run_grid_encode(out_dir: str, payload_path: str, module_size: int,
                    fixed_version: int, tag: str):
    """Run grid_encode into out_dir for one payload; return per-cell dicts."""
    os.makedirs(out_dir, exist_ok=True)
    proc = subprocess.run(
        [GRID_ENCODE, out_dir, payload_path, str(module_size), str(fixed_version), tag],
        capture_output=True, text=True,
    )
    rows = []
    for line in proc.stdout.splitlines():
        line = line.strip()
        if line.startswith("{"):
            rows.append(json.loads(line))
    if not rows:
        sys.exit(f"grid_encode produced no records (rc={proc.returncode}):\n{proc.stderr}")
    return rows


def probe_decode(img_path: str):
    pr = subprocess.run([PROBE, img_path], capture_output=True, text=True)
    return json.loads(pr.stdout.strip().splitlines()[-1])


def main():
    ap = argparse.ArgumentParser(description="Build the R1 per-medium profile corpus + measurements")
    ap.add_argument("--module-size", type=int, default=12)
    ap.add_argument("--payloads", type=int, default=5,
                    help="distinct fixed-length payloads per cell (de-noises the rate)")
    ap.add_argument("--families", default=",".join(DEFAULT_FAMILIES),
                    help="comma-separated degrade families (colour/luminance set)")
    ap.add_argument("--keep-corpus", action="store_true",
                    help="keep the degraded PNGs in data/corpus (default: prune after rig)")
    args = ap.parse_args()

    families = [f.strip() for f in args.families.split(",") if f.strip()]
    for f in families:
        if f not in degrade.FAMILY_BY_NAME:
            sys.exit(f"unknown family {f!r}; choices: {list(degrade.FAMILY_BY_NAME)}")
    n_pay = max(1, args.payloads)

    # fresh symbols/ + corpus/ so reruns are clean
    if os.path.isdir(SYMBOLS_DIR):
        shutil.rmtree(SYMBOLS_DIR)
    if os.path.isdir(CORPUS_DIR):
        shutil.rmtree(CORPUS_DIR)
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(SYMBOLS_DIR, exist_ok=True)
    os.makedirs(CORPUS_DIR, exist_ok=True)

    # --- 1. AUTO-geometry clean grid for each of N payloads -------------------
    # tag pN keeps filenames unique per payload in the shared symbols/ dir.
    # grid[(nc,ecc,tag)] = the grid_encode record (carries side, sha, file).
    grid = {}
    payload_meta = {}     # tag -> (length, sha256)
    pay_dir = os.path.join(DATA_DIR, "_payloads")
    os.makedirs(pay_dir, exist_ok=True)
    for pi in range(n_pay):
        tag = f"p{pi}"
        pbytes = make_payload(pi)
        ppath = os.path.join(pay_dir, f"{tag}.txt")
        with open(ppath, "wb") as f:
            f.write(pbytes)
        rows = run_grid_encode(SYMBOLS_DIR, ppath, args.module_size, 0, tag)
        bad = [r for r in rows if not r["fit"]]
        if bad:
            sys.exit(f"payload {tag} did not fit cells: {[(r['nc'], r['ecc']) for r in bad]}")
        payload_meta[tag] = (rows[0]["payload_len"], rows[0]["payload_sha256"])
        for r in rows:
            grid[(r["nc"], r["ecc"], tag)] = r
    shutil.rmtree(pay_dir)   # plaintext payloads are throwaway; never committed

    cells = sorted({(nc, ecc) for (nc, ecc, _t) in grid})
    colors_by_nc = {nc: (1 << (nc + 1)) for nc, _ in cells}

    # --- density CSV (geometry is payload-independent at fixed length: use p0) -
    density_csv = os.path.join(DATA_DIR, "grid_density.csv")
    p0_rows = [grid[(nc, ecc, "p0")] for (nc, ecc) in cells]
    min_area = min(r["side"] * r["side"] for r in p0_rows)
    with open(density_csv, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["nc", "colors", "ecc", "side", "image_px", "area_modules", "area_vs_min"])
        for r in sorted(p0_rows, key=lambda d: (d["nc"], d["ecc"])):
            area = r["side"] * r["side"]
            w.writerow([r["nc"], r["colors"], r["ecc"], r["side"], r["image_px"],
                        area, round(area / min_area, 3)])

    # --- density at CONSTANT geometry: max payload per cell at one version ----
    # The AUTO side above answers "area a fixed payload costs"; this answers the
    # dual "payload a fixed area buys" -- the cleaner density metric, since at a
    # small fixed payload many AUTO cells pile on the version floor.
    cap_proc = subprocess.run([GRID_CAPACITY, str(CAPACITY_VERSION)],
                              capture_output=True, text=True)
    cap = {}
    for line in cap_proc.stdout.splitlines():
        line = line.strip()
        if line.startswith("{"):
            c = json.loads(line)
            cap[(c["nc"], c["ecc"])] = c
    capacity_csv = os.path.join(DATA_DIR, "density_capacity.csv")
    if cap:
        cap_side = next(iter(cap.values()))["side"]
        min_cap = min(c["max_payload_bytes"] for c in cap.values())
        with open(capacity_csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["nc", "colors", "ecc", "version", "side",
                        "max_payload_bytes", "bytes_vs_min", "bytes_vs_nc1_ecc3"])
            base = cap.get((1, 3), {}).get("max_payload_bytes")
            for (nc, ecc) in cells:
                c = cap.get((nc, ecc))
                if not c:
                    continue
                b = c["max_payload_bytes"]
                w.writerow([nc, colors_by_nc[nc], ecc, c["version"], c["side"], b,
                            round(b / min_cap, 3),
                            round(b / base, 3) if base else ""])

    # --- clean-baseline sanity: every clean symbol decodes to its payload -----
    clean_csv = os.path.join(DATA_DIR, "clean_baseline.csv")
    clean_fail = 0
    with open(clean_csv, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["nc", "colors", "ecc", "payload", "side", "clean_decode_ok", "decoded_nc"])
        for (nc, ecc) in cells:
            for pi in range(n_pay):
                tag = f"p{pi}"
                r = grid[(nc, ecc, tag)]
                _len, sha = payload_meta[tag]
                res = probe_decode(os.path.join(SYMBOLS_DIR, r["file"]))
                ok = int(bool(res["decode_ok"]) and res["payload_sha256"] == sha)
                clean_fail += (1 - ok)
                w.writerow([nc, colors_by_nc[nc], ecc, tag, r["side"], ok, res["nc"]])
    if clean_fail:
        sys.exit(f"clean baseline FAILED for {clean_fail} symbols -- aborting (grid is unsound)")

    # --- 2. degrade every (cell x payload) across the families ----------------
    manifest_path = os.path.join(DATA_DIR, "manifest.jsonl")
    n_imgs = 0
    with open(manifest_path, "w") as mf:
        for (nc, ecc) in cells:
            for pi in range(n_pay):
                tag = f"p{pi}"
                r = grid[(nc, ecc, tag)]
                _len, sha = payload_meta[tag]
                clean = Image.open(os.path.join(SYMBOLS_DIR, r["file"])).convert("RGB")
                base = os.path.splitext(r["file"])[0]
                for fam_name in families:
                    fam = degrade.FAMILY_BY_NAME[fam_name]
                    for param in fam.params(full=True):     # dense ladder
                        out_img = degrade.apply(fam, clean, param, quiet_zone=12)
                        pstr = fam.fmt.format(param)
                        out_name = f"{base}__{fam_name}_{pstr}.png"
                        out_img.save(os.path.join(CORPUS_DIR, out_name))
                        mf.write(json.dumps({
                            "id": f"{base}__{fam_name}_{pstr}",
                            "file": os.path.join("corpus", out_name),
                            "nc": nc,
                            "ecc": ecc,
                            "payload_known": True,
                            "payload_sha256": sha,
                            "medium": "synthetic",
                            "degradation_type": fam_name,
                            "param": param,
                            # one bucket per (nc, ecc, family, param); payloads
                            # share a bucket so the rate averages over them.
                            "conditions": f"nc{nc} ecc{ecc} {fam_name}={pstr}",
                        }) + "\n")
                        n_imgs += 1

    # --- 3. run the R0 rig over the manifest (bucket by conditions) -----------
    per_image = os.path.join(DATA_DIR, "rig.per_image.jsonl")
    aggregate = os.path.join(DATA_DIR, "rig.aggregate.json")
    rc = subprocess.call([
        sys.executable, RUN_RIG,
        "--manifest", manifest_path,
        "--probe", PROBE,
        "--out", per_image,
        "--agg", aggregate,
        "--bucket", "conditions",
    ])
    if rc != 0:
        sys.exit(f"run_rig.py failed (rc={rc})")

    # --- 4. tidy decode_rates.csv : (nc, colors, ecc, family, param, rate) ----
    tally = {}
    with open(per_image) as f:
        for line in f:
            rec = json.loads(line)
            cond = rec.get("conditions") or ""        # "nc<N> ecc<E> <fam>=<pstr>"
            try:
                ncp, eccp, fp = cond.split(" ", 2)
                nc = int(ncp[2:]); ecc = int(eccp[3:])
                fam_name, pstr = fp.split("=", 1)
            except ValueError:
                continue
            t = tally.setdefault((nc, ecc, fam_name, pstr), {"n": 0, "ok": 0})
            t["n"] += 1
            t["ok"] += rec["decode_ok"]

    rates_csv = os.path.join(DATA_DIR, "decode_rates.csv")
    with open(rates_csv, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["nc", "colors", "ecc", "family", "param", "n", "decoded", "decode_rate"])
        for (nc, ecc, fam_name, pstr), t in sorted(tally.items()):
            w.writerow([nc, colors_by_nc.get(nc, 1 << (nc + 1)), ecc, fam_name, pstr,
                        t["n"], t["ok"], round(t["ok"] / t["n"], 4) if t["n"] else 0.0])

    # --- 4b. derive profiles_table.csv from profile_select's picks + the data --
    # The profile picks live in profile_select.py (single source of truth); here
    # we attach the measured rig numbers that justify each, so the table is
    # always consistent with both the picks and the latest measurements.
    sys.path.insert(0, R1_DIR)
    import profile_select as ps  # noqa: E402

    def rate(nc, ecc, fams):
        v = [t["ok"] / t["n"] for (n_, e_, fam_, _p), t in tally.items()
             if n_ == nc and e_ == ecc and fam_ in fams and t["n"]]
        return round(sum(v) / len(v), 4) if v else None

    ALL_FAMS = list(degrade.FAMILY_BY_NAME)
    THREAT = {  # which families define each medium's threat, for the cited rate
        "hostile": ["chroma", "lighting"],
        "print": ["blur", "downscale"],
        "clean": ALL_FAMS,
    }
    base_cap = cap.get((ps.PROFILES["hostile"].nc, ps.PROFILES["hostile"].ecc), {})
    base_cap = base_cap.get("max_payload_bytes") if base_cap else None
    profiles_csv = os.path.join(DATA_DIR, "profiles_table.csv")
    with open(profiles_csv, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["medium", "nc", "colors", "ecc", "threat_families",
                    "threat_decode_rate", "allfam_decode_rate",
                    "capacity_v7_bytes", "density_vs_hostile"])
        for key in ("hostile", "print", "clean"):
            p = ps.PROFILES[key]
            fams = THREAT[key]
            c = cap.get((p.nc, p.ecc), {}).get("max_payload_bytes")
            w.writerow([
                {"hostile": "hostile / screen", "print": "print / luxury-COA",
                 "clean": "clean / archival"}[key],
                p.nc, p.colors, p.ecc, "+".join(fams) if len(fams) <= 2 else "all",
                rate(p.nc, p.ecc, fams), rate(p.nc, p.ecc, ALL_FAMS), c,
                round(c / base_cap, 2) if (c and base_cap) else "",
            ])

    # --- 5. prune the (large, regenerable) degraded corpus unless asked --------
    if not args.keep_corpus:
        shutil.rmtree(CORPUS_DIR)
        os.makedirs(CORPUS_DIR, exist_ok=True)

    plen = payload_meta["p0"][0]
    print(f"R1 corpus: {n_imgs} degraded images = "
          f"{len(cells)} (Nc,ECC) cells x {n_pay} payloads x {len(families)} families")
    print(f"  payload: {plen} B fixed length, {n_pay} distinct  |  AUTO geometry "
          f"(side varies per cell -> grid_density.csv)")
    print(f"  density   -> {os.path.relpath(density_csv, REPO_ROOT)}  (area a fixed payload costs)")
    print(f"  capacity  -> {os.path.relpath(capacity_csv, REPO_ROOT)}  (payload a fixed area buys)")
    print(f"  clean     -> {os.path.relpath(clean_csv, REPO_ROOT)}  (all {len(cells)*n_pay} OK)")
    print(f"  rig agg   -> {os.path.relpath(aggregate, REPO_ROOT)}")
    print(f"  rates     -> {os.path.relpath(rates_csv, REPO_ROOT)}")
    print(f"  profiles  -> {os.path.relpath(profiles_csv, REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
