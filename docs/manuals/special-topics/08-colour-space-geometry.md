# 8. Colour-space geometry

<!-- objective: A mathematically mature reader can analyze palette design as vertex/lattice selection in the RGB cube maximizing minimum pairwise distance, derive nearest-neighbour classification regions (L2 vs the fork's CIELAB dE), and evaluate the extended-mode interpolation scheme (Annex G.3 R-channel tiers; embedded palettes capped at 64) for its tolerance-window consequences. -->

**Where it lives.** The reference palette: `jab_default_palette` at `src/jabcode/encoder.h:26-34`, order `[K,B,G,C,R,M,Y,W]` per ISO/IEC 23634 Table 21. The decoder's classification region: `getNearestPalette` (`src/jabcode/decoder.c:670`) and `decodeModuleHD` (`src/jabcode/decoder.c:710`). Perceptual machinery: `src/jabcode/lab_color.c` (CIELAB conversion, ΔE), `src/jabcode/kdtree_color.c` (nearest-neighbour tree). Spec: ISO/IEC 23634:2022 Annex G (palette construction, G.1, G.3), Clause 8.3.1-8.3.2 (CPA/CVDM normalization). Extended-mode implementation status: [JC-T ch. 16](../developers-manual/16-extended-colour-modes.md); gate status of the LAB path: [JC-T ch. 10](../developers-manual/10-fork-extensions.md).
<!-- anchor: src/jabcode/encoder.h:26-34 -->
<!-- anchor: src/jabcode/decoder.c:670,710 -->
<!-- anchor: ISO 23634 Annex G, 8.3.1, 8.3.2 -->

## The problem

A JAB Code module carries its payload as a colour, and the channel between encoder and decoder — printing, illumination, sensor, binarization — perturbs that colour as a vector in RGB space. The decoder classifies each perturbed sample to the nearest palette entry (`decodeModuleHD`, `decoder.c:710`). Classification is correct exactly when the perturbation does not carry the sample across the midplane between the true colour and any other palette colour. Palette design is therefore a packing problem: place the palette's points in the cube \[0, 255\]³ so that the smallest pairwise distance — equivalently, the narrowest classification margin — is as large as possible, for a given point count.

The point count is the density dial. A module in an Nc-mode symbol carries

$$
b = \log_2 N_c \ \text{bits per module}, \qquad N_c \in \{2, 4, 8, 16, 32, 64, 128, 256\}.
$$

Every doubling of the palette buys exactly one bit per module and pays for it in geometry. This chapter derives what it pays.

## Theory

### The 8-colour palette as cube vertices

The default palette (`encoder.h:26-34`) is, verbatim, the set

$$
P_8 = \{0, 255\}^3 = \{(0,0,0), (0,0,255), (0,255,0), (0,255,255), (255,0,0), (255,0,255), (255,255,0), (255,255,255)\},
$$

the eight vertices of the RGB cube, indexed \[K, B, G, C, R, M, Y, W\]. Pairwise distances take exactly three values, by the number of channels in which two vertices differ:

$$
d_1 = 255, \qquad d_2 = 255\sqrt{2} \approx 360.6, \qquad d_3 = 255\sqrt{3} \approx 441.7 .
$$

The minimum pairwise distance is therefore

$$
d_{\min}(P_8) = 255,
$$

attained by every edge of the cube (28 pairs total: 12 at distance 255, 12 at 255√2, 4 at 255√3). Reading the vertex set as a code, this is the extended Hamming picture: each vertex is a binary triple scaled by 255, and Euclidean distance is 255·√(Hamming distance). Under nearest-neighbour classification the decision regions are the eight closed octants around the cube centre (127.5, 127.5, 127.5): a sample classifies by thresholding each channel independently at the midpoint. Per channel, the tolerance to the nearest rival is half the gap — 127.5 counts.

Is 255 the best possible minimum distance for eight points in the cube? Choosing the vertices is the natural max-min-distance configuration, and the spec's Annex G.1 construction is consistent with that objective — but the standard states the palette, not an optimality proof. We label the maximization claim as design rationale, not theorem: eight points in \[0, 255\]³ with pairwise distances at least 255 exist (the vertices), and the vertex configuration is the canonical solution family for this packing size, but ISO/IEC 23634 offers no argument and we prove none here. What the spec does fix normatively is the four-colour subset: Annex G.1 a) — 4-colour mode uses black, cyan, magenta, yellow, and "Black, cyan, and yellow are used for encoding Nc and cyan and yellow are used for alignment patterns."
<!-- anchor: ISO 23634 Annex G.1 -->

### Extended modes as lattice refinements

Annex G.3 constructs the reserved modes by refining channels one at a time, from a 2-tier ladder \{0, 255\} to a 4-tier ladder to an 8-tier ladder:

| Mode | R channel | G channel | B channel | Structure |
|---|---|---|---|---|
| 16-colour | \{0, 85, 170, 255\} | \{0, 255\} | \{0, 255\} | 4 × 2 × 2 |
| 32-colour | \{0, 85, 170, 255\} | \{0, 85, 170, 255\} | \{0, 255\} | 4 × 4 × 2 |
| 64-colour | \{0, 85, 170, 255\} | \{0, 85, 170, 255\} | \{0, 85, 170, 255\} | 4 × 4 × 4 |
| 128-colour | \{0, 36, 73, 109, 146, 182, 219, 255\} | \{0, 85, 170, 255\} | \{0, 85, 170, 255\} | 8 × 4 × 4 |
| 256-colour | 8 values (as 128-mode R) | 8 values (as 128-mode R) | \{0, 85, 170, 255\} | 8 × 8 × 4 |

<!-- anchor: ISO 23634 Annex G.3 -->

Every palette is a product lattice — a grid, not a general point cloud. For a product set the minimum pairwise distance is the minimum *single-channel* neighbour gap, since two grid points differing in one channel by the smallest tier step realize it:

$$
d_{\min} = \min_{c \in \{R,G,B\}} \; \min_i \; \left( t^{(c)}_{i+1} - t^{(c)}_i \right).
$$

The 4-tier ladder \{0, 85, 170, 255\} has uniform gaps of 85. The 8-tier ladder \{0, 36, 73, 109, 146, 182, 219, 255\} has gaps 36, 37, 36, 37, 36, 37, 36 — 255 is not divisible by 7, so the spec alternates 36 and 37; the minimum is 36. Hence:

| Mode | bits/module | min channel gap | d_min |
|---|---|---|---|
| 8 | 3 | 255 | 255 |
| 16 | 4 | 85 | 85 |
| 32 | 5 | 85 | 85 |
| 64 | 6 | 85 | 85 |
| 128 | 7 | 36 | 36 |
| 256 | 8 | 36 | 36 |

Two structural observations follow immediately, and both are analysis (this book's), not spec text. First, the frontier is a staircase, not a slope: 16-, 32- and 64-colour modes all share d_min = 85 because refinement proceeds channel by channel. Once one channel has paid for the 4-tier ladder, extending the same ladder to the remaining channels adds bits at *no further cost in minimum distance* — within a tier family, the densest member (64 of the 85-family, 256 of the 36-family) dominates the others on the bits-per-margin frontier. Second, the product structure means nearest-neighbour cells are axis-aligned boxes (the Voronoi cells of a grid), so optimal classification remains per-channel thresholding — exactly the geometry Clause 8's normalization assumes, next.

### Tolerance windows: the CPA/CVDM half-gap normalization

Clause 8.3.1 grades Colour Palette Accuracy by a Euclidean distance normalized per channel, where — quoting the normalization — "dR, dG, dB is half the distance to the next colour in this colour channel (see Annex G.1)". Clause 8.3.2 (CVDM) applies the same normalization with the variance taken over data modules. Half the channel gap is precisely the distance from a palette colour to the classification midplane along that channel: the normalization measures error *in units of the margin*. In normalized coordinates every mode's decision boundary sits at 1.0 by construction, which is what makes one grading scale serve all modes.
<!-- anchor: ISO 23634 8.3.1, 8.3.2 -->

The consequence for the density frontier: as tiers double, the absolute per-channel tolerance halves-or-worse. With half-gap tolerance per channel,

$$
\delta_c = \frac{\text{gap}_c}{2}: \qquad
\delta = 127.5 \;(8\text{-colour}) \;\longrightarrow\; 42.5 \;(85\text{-gap tiers}) \;\longrightarrow\; 18 \;(36\text{-gap tiers}).
$$

The grading thresholds put numbers on how much of that window a symbol may consume: CPA < 0.2 grades 4.0; the band 0.2 ≤ CPA < 0.75 grades by the printed formula "5,6 – round(80 * CAP)" (quoted as printed in the standard, decimal comma and "CAP" included; the extract's rounding note reads "down to the next 0,1"); CPA ≥ 0.75 grades 0.0. Taking the top grade's 0.2 at face value: a top-grade print must keep its mean palette error within 20 % of the half-gap — about 25.5 RGB counts in 8-colour mode, 8.5 counts in the 85-gap family, and only about 3.6 counts on the refined channel of 128/256-mode. That last number is the whole extended-mode story in one figure: at 36-count gaps, top-grade colour fidelity demands channel accuracy near the quantization floor of an 8-bit pipeline.

The frontier, stated once:

$$
\text{bits/module} = \log_2 N_c
\quad \text{vs.} \quad
\delta_{\min} = \tfrac{1}{2}\min \text{gap}:
\qquad (3, 127.5) \to (4\text{-}6, 42.5) \to (7\text{-}8, 18).
$$

Each ~2.5 bits of density costs a factor of ~2.7 in margin (8-colour to 256-colour: 5 bits gained, minimum channel gap 255 → 36, a ×7.1 margin loss). Whether that trade closes depends on the channel's noise scale — which is why only the 4- and 8-colour modes are standard-defined and the rest are reserved (see the interchange discussion in [JC-T ch. 16](../developers-manual/16-extended-colour-modes.md)).

### Embedded palettes as lossy palette compression

The symbol carries its palette in-band, and here the spec imposes a hard cap: "there are 128 modules reserved for two colour palettes. Therefore, each colour palette can contain up to 64 colours." (Annex G.3). Modes up to 64 colours embed their full palette; 128- and 256-colour modes cannot. The spec's construction embeds a *subsampled* palette — 128-mode embeds the colours with R ∈ \{0, 73, 182, 255\}; 256-mode embeds those with R, G ∈ \{0, 73, 182, 255\} — and reconstructs the rest at decode time: "the colours whose original R channel value is 36 should be restored by interpolating the colours whose original R channel value is 0 and 73."
<!-- anchor: ISO 23634 Annex G.3 -->

This is lossy compression of the palette, and it is worth being precise about what is lost. The embedded subset \{0, 73, 182, 255\} is tiers \{0, 2, 5, 7\} of the 8-tier ladder — the max-min-spread 4-subset, with gaps 73, 109, 73. The decoder's information deficit for 128/256-mode is then:

- **Four of the eight R-tiers are never observed as printed ink.** The decoder sees how the channel rendered tiers 0, 73, 182, 255 and must *infer* how it rendered 36, 109, 146, 219. Interpolation is exact only if the channel's distortion is affine in R between embedded anchors.
- **Even the nominal reconstruction is inexact.** Taking the spec's example literally as midpoint interpolation, tier 36 reconstructs to (0 + 73)/2 = 36.5 against a nominal 36 — a built-in half-count bias before any channel error. (The spec extract fixes only this example; the interpolation weights for the other missing tiers are not in our verified extract — NOT FOUND at this book's evidence standard.)
- **The deficit lands exactly where the margin is thinnest.** The unobserved tiers live on the 36-gap channel, where the half-gap tolerance is 18 counts and top-grade slack ≈ 3.6 counts. Any curvature in the channel response between anchors 73 and 182 (gap 109) eats directly into the smallest margins in the system.

So the extended modes stack two compromises multiplicatively: a halved geometric margin *and* a partially blind palette estimate on the same channel. This composition — not either factor alone — is the analytical reason to expect 128/256-mode robustness to degrade faster than the frontier table alone suggests.

### Euclidean or perceptual? CIELAB and ΔE, sketched

Everything above measured distance in raw RGB. Human-calibrated colour science says the channel's *effective* noise is not isotropic in RGB: perceptually (and, roughly, for consumer imaging pipelines built to serve perception) equal distances in RGB are not equally distinguishable. CIELAB is the standard remedy, and the fork implements it in `lab_color.c`: sRGB gamma linearization, the D65 linear map to XYZ (`rgb_to_xyz`, `lab_color.c:90-114`), then the cube-root compression

$$
L^* = 116\, f(Y/Y_n) - 16, \qquad
a^* = 500\,[f(X/X_n) - f(Y/Y_n)], \qquad
b^* = 200\,[f(Y/Y_n) - f(Z/Z_n)],
$$

with f(t) = t^{1/3} above the ε = (6/29)³ knee (`LAB_EPSILON`, `lab_color.c:27`, `lab_f` at `lab_color.c:63`). We sketch rather than derive — the design intent is what matters here: the nonlinearity re-scales the space so that Euclidean distance in (L\*, a\*, b\*), the CIE76 difference

$$
\Delta E_{76} = \sqrt{(\Delta L^*)^2 + (\Delta a^*)^2 + (\Delta b^*)^2},
$$

approximates perceptual difference (`delta_e_76`, `lab_color.c:224`). CIEDE2000 (`delta_e_2000`, `lab_color.c:239`) further corrects known non-uniformities with chroma/hue-dependent scale factors and a rotation term. In this metric the RGB-cube palette is *not* equidistant: the geometry chapter's clean vertex symmetry breaks, some colour pairs (notoriously involving high-luminance mixtures) sit closer than raw RGB suggests, and a nearest-neighbour classifier inherits differently-shaped cells. Whether that reshaping helps depends entirely on whether the dominant channel distortions are perception-shaped — exercise 3.

### Sublinear lookup: the k-d tree

`kdtree_color.c` supplies the classical answer to "nearest neighbour got expensive": a median-split k-d tree over the palette in LAB space, cycling split axes L, a, b (`build_recursive`, `kdtree_color.c:38-66`), searched with best-first descent and the standard hyperplane-pruning test — recurse into the far child only if the splitting-plane distance beats the current best (`kdtree_color.c:139`). Two analytical notes. First, the pruning test compares an axis distance against a ΔE76 best: that is sound precisely because ΔE76 *is* Euclidean in LAB coordinates (`kdtree_nearest` uses `delta_e_76`, `kdtree_color.c:117`); the identical tree searched under ΔE2000 would prune unsoundly, since ΔE2000 is not a coordinate metric in (L, a, b). The implementation is consistent on this point. Second, expected query cost is O(log n) for n palette colours against the linear scan's O(n) — material only at n = 128/256, exactly the modes where classification runs hottest.

Status, per the findings register: this machinery is **gate-dormant**. The `USE_LAB_DISTANCE` compile gate that would route `decodeModuleHD` through LAB is defined by no build file, and the k-d tree's consumer (`adaptive_palette.c`) has no in-tree caller ([JC-T ch. 10](../developers-manual/10-fork-extensions.md); corpus model §2.1). We analyze it as designed; nothing in the shipping decode path executes it.

## Back to the code

The live classifier is `decodeModuleHD` (`decoder.c:710`), and it implements the theory with three pragmatic twists:

- **Palette locality first.** `getNearestPalette` (`decoder.c:670`) picks which of the four embedded palette copies (`COLOR_PALETTE_NUMBER 4`, `jabcode.h:41`) to classify against, by spatial proximity of the module to the palette blocks — a hedge against illumination gradients across the symbol: each quadrant is judged by the palette copy that suffered the same lighting.
- **Two metrics, switched on Nc.** For ≤ 8 colours the sample and palette are normalized by the max channel (`rgb_max` division, `decoder.c:795-798`) — a chromaticity-style normalization that discards overall intensity — before the squared-Euclidean scan at `decoder.c:818`. For > 8 colours (`use_direct_rgb`, `decoder.c:750`) it compares raw RGB directly: with same-hue, different-lightness palette entries (the 85- and 36-gap tiers), normalizing away intensity would collapse exactly the channel that separates them.
- **Guard rails at the achromatic corners.** A dedicated black test against per-palette thresholds short-circuits first (`decoder.c:741`); a black/white disambiguation re-decides index 0 vs the last index by total intensity against the palette's own endpoints (`decoder.c:837-853`); and an 8-colour-only "magenta rescue" re-files high-chroma samples that landed on white to the nearest chromatic vertex (`decoder.c:855` ff.) — a patch for a measured camera-pipeline failure, not a spec behaviour. Each guard is a hand-shaped correction to the Voronoi geometry where the ideal-channel assumption breaks worst.

The dormant LAB path (`decoder.c:752-792`, under `USE_LAB_DISTANCE`) slots ΔE2000 into the same min1/min2 bookkeeping, only for color_number > 8 — the designers aimed the perceptual metric precisely at the thin-margin modes. And on the encoder side, note that classification tolerances are also *grading* quantities: the fork ships no Clause 8 verifier at all (CPA/CVDM are **NOT FOUND** in code — corpus model §4/§5; roadmap discussion in [JC-T ch. 15](../developers-manual/15-conformance-testing.md)), so every Clause 8 number in this chapter is spec-side only.

For the capacity side of the density trade, see chapter 2 ([02-information-density.md](02-information-density.md)); for what happens to these margins under deliberate attack rather than noise, chapter 11 ([11-adversarial-channel.md](11-adversarial-channel.md)).

## Exercises

**1 (guided).** Annex G.3's 16-colour palette (Table G.1) is the product set R ∈ \{0, 85, 170, 255\}, G, B ∈ \{0, 255\}. Compute its minimum pairwise Euclidean distance and exhibit all pairs attaining it. How many such pairs are there?

<details><summary>Answer</summary>

For a product set, the minimum distance is realized by a pair differing in a single channel by the smallest gap. The smallest gap is 85 (adjacent R-tiers); any pair differing only by one R-step attains d = 85. Pairs differing in G or B alone are at 255; multi-channel differences are larger still (e.g. √(85² + 255²) ≈ 268.8). Count: fix G and B (2 × 2 = 4 ways), choose an adjacent R pair (3 ways): **12 pairs at distance 85**.

</details>

**2 (sketch).** Model the print/scan channel on the R axis as an unknown monotone distortion φ with bounded curvature, observed only at the embedded anchors \{0, 73, 182, 255\}. The decoder reconstructs φ(36) by linear interpolation between φ(0) and φ(73). Sketch a bound on the reconstruction error in terms of max |φ″|, and compare it to the 128-mode half-gap tolerance of 18 counts. At what curvature does interpolation alone consume the top-grade slack (≈ 3.6 counts)?

<details><summary>Hint</summary>

Standard linear-interpolation error on \[0, 73\]: |error| ≤ (max|φ″|/8)·73² ≈ 666·max|φ″|, plus the fixed 0.5-count nominal offset (36.5 vs 36). Slack is consumed when 666·max|φ″| + 0.5 ≥ 3.6, i.e. max|φ″| ≈ 4.7 × 10⁻³ counts per count² — a gentle S-curve in an 8-bit tone response is already of this order. The wider 73-to-182 span (bound ∝ 109²/8 ≈ 1485) is worse. State your assumptions: the analysis presumes midpoint weights, which the spec fixes only for the tier-36 example.

</details>

**3 (open).** When does perceptual classification beat Euclidean? Construct (or argue for) a distortion model under which ΔE76-nearest-neighbour strictly reduces misclassification of the 64-colour palette relative to raw-RGB nearest-neighbour — and a second model under which it strictly increases it. Consider: chroma compression by ink spread; luminance-only shifts from exposure; the fact that JAB's channel ends at a silicon sensor, not an eye. Where does `decodeModuleHD`'s max-channel normalization for Nc ≤ 2 sit between the two metrics?

## Further reading

- CIE, *Colorimetry*, CIE Publication 15 — the normative definition of XYZ, CIELAB and the ΔE76 difference.
- M. D. Fairchild, *Color Appearance Models*, 3rd ed., Wiley, 2013 — the perceptual non-uniformities that motivate ΔE2000, from the source.
- G. Sharma, W. Wu, E. N. Dalal, "The CIEDE2000 color-difference formula: implementation notes, supplementary test data, and mathematical observations", *Color Research & Application* 30(1), 2005 — the exact formula `delta_e_2000` implements, with its discontinuity pitfalls.
- J. H. Conway, N. J. A. Sloane, *Sphere Packings, Lattices and Groups*, 3rd ed., Springer, 1999 — max-min-distance point configurations and lattice packings, the general theory behind "vertices are the right eight points".
- J. L. Bentley, "Multidimensional binary search trees used for associative searching", *Communications of the ACM* 18(9), 1975 — the k-d tree, first-hand.
