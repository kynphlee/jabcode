#!/usr/bin/env python3
"""
transcode_survival.py -- measured digital-transcode survival.

Encodes a JABCode to PNG (transcode_tool enc), applies a real PIL transform
(JPEG recompress, downscale, 4:2:0 chroma subsampling), decodes the result
(transcode_tool dec), and records whether the payload survived. The digital
channel (compression/resampling) -- distinct from the optical/camera channel.

Output: data/transcode.jsonl  (transform, colors, survival, control)
"""
import os, subprocess, json, io
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "src", "jabcode", "build", "transcode_tool")
TMP = os.path.join(HERE, "data", "_transcode_tmp")
os.makedirs(TMP, exist_ok=True)
OUT = os.path.join(HERE, "data", "transcode.jsonl")

COLORS = [4, 8, 16, 32, 64, 128, 256]   # skip 2-colour (Nc0 host-decode is flaky)
ECC = 3

def enc(colours, path):
    return subprocess.run([TOOL, "enc", str(colours), str(ECC), path]).returncode == 0

def dec(path):
    r = subprocess.run([TOOL, "dec", path], capture_output=True, text=True)
    return "SURVIVE" in r.stdout

def jpeg(q):
    def f(im):
        b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=q); b.seek(0)
        return Image.open(b).convert("RGB")
    return f

def downscale(frac):
    def f(im):
        w, h = im.size
        return im.resize((max(1, int(w*frac)), max(1, int(h*frac))), Image.BILINEAR).resize((w, h), Image.BILINEAR)
    return f

def chroma420(im):
    b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=95, subsampling=2); b.seek(0)
    return Image.open(b).convert("RGB")

TRANSFORMS = [
    ("JPEG q75", jpeg(75)),
    ("JPEG q50", jpeg(50)),
    ("JPEG q30", jpeg(30)),
    ("downscale 0.5", downscale(0.5)),
    ("downscale 0.3", downscale(0.3)),
    ("chroma 4:2:0", chroma420),
    ("JPEG30 + ds0.5", lambda im: jpeg(30)(downscale(0.5)(im))),
]

recs = []
for c in COLORS:
    base = os.path.join(TMP, f"base_{c}.png")
    if not enc(c, base):
        print(f"  Nc={c:3d}  encode FAILED, skipping"); continue
    ctrl = dec(base)
    for tname, tf in TRANSFORMS:
        try:
            out = tf(Image.open(base).convert("RGB"))
            tp = os.path.join(TMP, f"{c}_{tname.replace(' ','_').replace(':','')}.png")
            out.save(tp)
            ok = dec(tp)
        except Exception:
            ok = False
        recs.append({"transform": tname, "colors": c, "survival": 1.0 if ok else 0.0,
                     "control": 1 if ctrl else 0})
        print(f"  Nc={c:3d}  {tname:14s} -> {'SURVIVE' if ok else 'FAIL':7s} (ctrl={'ok' if ctrl else 'X'})")

with open(OUT, "w") as f:
    for r in recs:
        f.write(json.dumps(r) + "\n")
print(f"\nwrote {len(recs)} records -> {OUT}")
