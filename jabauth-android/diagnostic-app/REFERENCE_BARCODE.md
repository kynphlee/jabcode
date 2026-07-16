# Reference Barcode: HELLO (4-color, 21×21)

## Files
- `reference_HELLO_4c.png` — 630×630px (30px/module) — good for printing
- `reference_HELLO_4c_large.png` — 1260×1260px (60px/module) — best for screen display

## Display Instructions
1. Open `reference_HELLO_4c_large.png` on the display screen
2. Ensure NO image scaling/interpolation — display at native pixels or use "nearest neighbor" zoom
3. Set screen brightness to maximum
4. Use a DARK background behind the image viewer
5. Hold the phone 30-50cm from the screen

## Expected Decode Result
- Content: "HELLO"
- Color mode: Nc=1 (4 colors: K=Black, M=Magenta, Y=Yellow, C=Cyan)
- Symbol version: 1×1 (21×21 modules)
- ECC level: 3 (default)

## Ground Truth Grid
r00: MCCYYYCMMMMYKYMMKCMYK
r01: YKKKCYCMMKCKMKKKKKCYM
r02: CKCCYYMKKKCKYKMKYYMCK
r03: YKCKCKYCCKKKMCCKYKYKM
r04: KYMCCKMYMKMKYCYKMYYKY
r05: YYMKKKKMMMCYCCKMMKKKY
r06: YYCYYKMKCYYCKMYKYYMMK
r07: CYCCKYKCKKKMYKCYYKCKC
r08: MYMMKCMKMKYYYKCYMYYCM
r09: KMYKYMMKYKYKYMKYMCMYM
r10: MKKMYCMYYYKMKCMKCKYKM
r11: YCCCKMYMKMKKCKMMCKYMM
r12: KKMYKKMYMKCKYKYCKCKCC
r13: MCMCYYCMYYMYKKMCKMYYK
r14: KCMMKMKMKYKKYKKMCKCYC
r15: YMCCCCKMKYMCMYMKYYYYM
r16: CCYKKCYKKCYCMKCMMKKYM
r17: CCKCKCYKCKCYYCCYKYKYM
r18: MCKKMYMCMYCYKMMYKKYMM
r19: KCCCMCCKKKCMCYKYYYCCY
r20: MYCMYYMMMCYMCYMKCYMMY

## FP Verification
- FP0 (3,3) = K (Black)
- FP1 (3,17) = K (Black)
- FP2 (17,17) = Y (Yellow)
- FP3 (17,3) = C (Cyan)

## Critical Notes
- The previous barcode being scanned had corrupted FP diagonal modules
- If camera grid doesn't match ground truth above, the DISPLAY is corrupting the image
- Use PNG viewer with NO anti-aliasing and NO color management
- Avoid Chrome/web browsers (they may apply sub-pixel rendering)
