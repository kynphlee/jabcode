# 9. Detection as robust estimation

<!-- objective: A mathematically mature reader can model finder detection as hypothesis testing over scanline profiles with tolerance bands, explain found-counters as vote aggregation and missing-finder inference as constraint propagation, and derive the homography estimation and the Table 24 side-size snap as quantization to the 4v+17 lattice. -->

**Where it lives.** Finder search: `findMasterSymbol` (`src/jabcode/detector.c:1811`), scanline profile test `checkPatternCross` (`detector.c:125`), vote aggregation `saveFinderPattern` (`detector.c:1143`) and `selectBestPatterns` (`detector.c:1312`). Missing-finder estimation: `detector.c:2078-2145`. Homography: `square2Quad`/`quad2Square`/`perspectiveTransform` (`src/jabcode/transform.c:33, 92, 164`) behind `getPerspectiveTransform` (`transform.c:202`). Side-size quantization: `calculateModuleNumber`/`getSideSize`/`chooseSideSize` (`detector.c:3057, 2997, 3034`); AP-guided resampling `sampleSymbolByAlignmentPattern` (`detector.c:3296`). Spec: ISO/IEC 23634:2022 Clause 6 (detection), Table 24 and Formulas (5)-(8). Failure-to-stage mapping: [JC-T ch. 5](../developers-manual/05-detector-and-decoder.md).
<!-- anchor: src/jabcode/detector.c:125,1143,1312,1811,2997,3057,3296 -->
<!-- anchor: src/jabcode/transform.c:33,92,164,202 -->
<!-- anchor: ISO 23634 Clause 6, Table 24 -->

## The problem

The decoder receives a photograph, not a bitstream. Before a single LDPC iteration can run, the detector must answer four estimation questions from pixels alone: *where* are the finder patterns, *which* candidates are real, *what* projective transform maps symbol coordinates to image coordinates, and *what size* is the symbol in modules. Each answer is an estimate from noisy data, and each stage is built the way robust statistics says to build estimators you cannot afford to have fail loudly: cheap hypothesis tests with generous tolerance bands, redundant measurements aggregated by voting, hard consistency constraints to reject outliers, and — at the end — quantization onto the known discrete answer lattice, which forgives all residual error smaller than half a lattice step.

This chapter walks the pipeline in that order. Geometry arguments were developed in full in chapter 8; second occurrence here, so the homography is a sketch. Probability language stays qualitative throughout — the corpus contains no measured error rates, and we invent none.

## Theory

### Scanline profiles as hypothesis tests

A JAB finder pattern crossed by a scanline produces a run-length signature: background, then three colour layers of one module each, then background — the p:1:1:1:q profile of Clause 6, where the outer runs p and q are unconstrained above a floor. Scanning a row is a sliding hypothesis test: the null hypothesis is "background clutter", the alternative is "finder core here", and the test statistic is the 5-run state vector.

The implementation's acceptance region is `checkPatternCross` (`detector.c:125-150`). It estimates the module size as the mean of the three inner runs,

$$
\hat{m} = \frac{s_1 + s_2 + s_3}{3},
$$

and accepts iff every inner run lies within a half-module of that mean, the outer runs clear a floor, and the two flanking layers match each other:

$$
|s_i - \hat{m}| < \tfrac{\hat{m}}{2} \;\; (i = 1,2,3), \qquad
s_0, s_4 > \tfrac{1}{2}\cdot\tfrac{\hat{m}}{2} = \tfrac{\hat{m}}{4}, \qquad
|s_1 - s_3| < \tfrac{\hat{m}}{2}.
$$

The 50 % band of the spec is here verbatim: `layer_tolerance = layer_size / 2.0f` (`detector.c:138`). The source comment states the profile shape being tested: "layer size proportion must be n-1-1-1-m where n>1, m>1" (`detector.c:141`). Companion tests reuse the pattern at different tolerances: cross-channel module-size agreement uses mean/2.5 — a 40 % band (`checkModuleSize2`/`checkModuleSize3`, `detector.c:162, 180`), and the colour cross-check along a finder's axis tolerates run-length mismatch up to max(module_size/7, 3) pixels (`crossCheckColor`, `detector.c:789`, widened from a fixed 3 px by the WS-5 scale-tolerance fix documented at `detector.c:768-788`).

Why so loose? A tolerance band is a classic size-vs-power dial. A 50 % band accepts profiles distorted by perspective foreshortening, ink spread, and sampling phase — high power against the "miss the symbol entirely" failure, at the price of a high false-alarm rate on textured backgrounds. The design answer to those false alarms is not a tighter band; it is the next stage.

### Found-counters as vote aggregation

A real finder pattern is hit by many scanlines — one per pixel row through its core at scan stride 1 (`min_module_size`, `detector.c:1814-1815`, forced to 1 in `INTENSIVE_DETECT`, the pinned mode per the JC-T findings register). Clutter that fooled one scanline rarely fools ten in the same place with the same module size. `saveFinderPattern` (`detector.c:1143-1167`) exploits this by merging each accepted candidate into any existing candidate within one module's distance and compatible size, updating the stored centre and module size as a running mean:

$$
c_{k+1} = \frac{k\,c_k + x}{k+1},
$$

and incrementing `found_count` — the vote total. This is simultaneously a clustering step and a variance-reduction step: the centre estimate for an n-vote pattern is an n-sample average of scanline centres.

`selectBestPatterns` (`detector.c:1312-1417`) then applies two suppression rules, both quoted from source:

- Candidates with `found_count < 3` are dropped — "abandon the finder patterns which are founds less than 3 times, which means a module shall not be smaller than 3 pixels" (`detector.c:1323-1325`). This is Clause 6's found-counter cross-check (≥ 3) in the flesh, and it doubles as a resolution floor.
- After the best candidate per finder type is chosen (max vote count, `getBestPattern`), any finalist with fewer than half the votes of the strongest finalist is discarded (`detector.c:1381-1402`).

Read game-theoretically for a moment (chapter 11 develops this properly): the vote threshold prices a false positive at "fool ≥ 3 co-located scanlines consistently", and the relative 50 %-of-max rule prices it higher still when a genuine symbol is present — a decoy must not merely exist but keep pace with the real pattern's vote accumulation.

### Three finders recover the fourth: constraint solving

Four finder types (FP0-FP3, corners UL, UR, LR, LL) anchor the symbol. If exactly one is missing (`missing_fp_count == 1`), detection does not fail; the fourth corner is *solved for* from the constraint that the four corners form the projected image of a square. The pure form of that constraint, in affine approximation, is the parallelogram identity — for a missing FP0:

$$
\mathbf{p}_0 = \mathbf{p}_1 + (\mathbf{p}_3 - \mathbf{p}_2).
$$

What the source actually does (`detector.c:2082-2093`, and symmetrically for the other three cases) is this identity with a scale correction: the displacement p₃ − p₂ is first divided by the mean module size of the measuring pair and re-multiplied by the mean module size of the pair adjacent to the target —

$$
\mathbf{p}_0 = \mathbf{p}_1 + (\mathbf{p}_3 - \mathbf{p}_2)\cdot\frac{\bar{m}_{13}}{\bar{m}_{23}},
\qquad \bar{m}_{ij} = \tfrac{1}{2}(m_i + m_j).
$$

Module size is a proxy for local projective scale (a finder farther from the camera images smaller), so the correction bends the affine parallelogram toward the projective truth without solving a full homography from three points — which is underdetermined anyway. The estimate then gets two sanity gates: the solved corner must land inside the image (`detector.c:2131-2138`), and a local search re-detects an actual pattern near the predicted position (`seekMissingFinderPattern`, `detector.c:2144`) rather than trusting the extrapolation blind. The estimated corner's module size is set to the mean of the three found ones, and its scan direction to the constraint-consistent sign (`detector.c:2091-2092`). Missing two finders is unrecoverable by design — with only two corners the projected-square constraint has a continuum of solutions — and detection reports failure (`detector.c:2070-2075`).

### Homography from four correspondences — sketch

With four corners in hand, sampling needs the projective map between module coordinates and image pixels. This is the second geometry derivation family in the book, so per the fade schedule: the idea, not the algebra.

A plane-to-plane projective transform in homogeneous coordinates is a 3 × 3 matrix H, defined up to scale — 8 degrees of freedom. Each point correspondence (x, y) ↦ (x′, y′) contributes two linear constraints on H's entries (write x′ = (h₁₁x + h₂₁y + h₃₁)/(h₁₃x + h₂₃y + h₃₃), clear the denominator, likewise y′). Four correspondences in general position give 8 equations: exactly determined. The textbook route is the Direct Linear Transformation — stack the 8 equations, solve the homogeneous system (Hartley & Zisserman, ch. 4).

`transform.c` takes the classical shortcut instead of a linear solver: for the *unit square* as source, the DLT system collapses to closed form. `square2Quad` (`transform.c:33-78`) maps square-to-quadrilateral by solving just a 2 × 2 system for the perspective terms a₁₃, a₂₃ (`transform.c:60-66`; the affine branch at `transform.c:46-57` handles the degenerate case where the fourth point is parallelogram-consistent and the perspective terms vanish). `quad2Square` (`transform.c:92-116`) inverts by taking the adjugate matrix — for homogeneous transforms, the adjugate *is* the inverse up to the irrelevant scale, so no division or determinant test is needed. A general quad-to-quad map is then composed through the square: `perspectiveTransform` = quad→square, then square→quad (`transform.c:173-183`). The entry point `getPerspectiveTransform` (`transform.c:202-217`) feeds it the four finder centres against their known module coordinates — (3.5, 3.5), (side − 3.5, 3.5), (side − 3.5, side − 3.5), (3.5, side − 3.5) — the finder cores sit 3.5 modules in from each corner. Sampling is the forward map applied to every module centre (`warpPoints`, `transform.c:225`), refined per grid block by alignment patterns for large symbols (`sampleSymbolByAlignmentPattern`, `detector.c:3296`, which drafts FPs as the corner APs and re-estimates a local homography per AP cell).

Note what is *absent*: no least squares, no RANSAC, no fifth point. The four correspondences are trusted because the voting stage already spent the redundancy budget; the estimation philosophy is "clean your inputs, then solve exactly" rather than "solve robustly from dirty inputs".

### Side size: quantization onto the 4v + 17 lattice

Symbol sides take only the values 4v + 17, v = 1..32 — 21, 25, …, 145 modules (`VERSION2SIZE`, `jabcode.h:53`). Any raw side-size estimate can therefore be *snapped* to this lattice, and every estimation error under half a lattice step (2 modules) is silently forgiven. This is the detector's last and cheapest robustness mechanism.

The raw estimate first: Formula (5) of Clause 6.3, as printed —

$$
\text{side\_size\_x\_top} = \left\lfloor \frac{\text{dist\_ul\_ur\_x}}{\tfrac{\text{ul\_module\_size} + \text{ur\_module\_size}}{2} \times \cos\theta_1} + 7.5 \right\rfloor
$$

The source computes exactly this, split across two functions: `calculateModuleNumber` (`detector.c:3057-3065`) returns ⌊dist/mean + 0.5⌋ with mean = (m₁ + m₂)·cos θ / 2, where cos θ is computed as max(|Δx|, |Δy|)/dist (`detector.c:3061`) — the cosine of the angle between the finder-to-finder line and the nearer image axis, correcting module sizes that were measured along scanlines; `calculateSideSize` then adds 7 (`detector.c:3082-3092`). Since ⌊x + 0.5⌋ + 7 = ⌊x + 7.5⌋, the match with Formula (5) is exact — a JC-T ch. 5 finding, reconfirmed here. The +7 is geometry, the +0.5 is round-to-nearest-via-floor: exercise 1.

Then the snap, `getSideSize` (`detector.c:2997-3024`), against Table 24. Valid sides are ≡ 1 (mod 4), so the residue class of the estimate encodes both the correction and a confidence flag:

| size mod 4 | correction | flag | reading |
|---|---|---|---|
| 1 | none | 1 | on-lattice: error < 0.5 module measured |
| 0 | +1 | 1 | off by one, unambiguous nearest neighbour |
| 2 | −1 | 1 | off by one, unambiguous nearest neighbour |
| 3 | +2 | 0 | *tie*: equidistant between two versions — "error is bigger than 1, guess the next version and try anyway" (`detector.c:3009`) |

Residue 3 is the midpoint of the lattice cell — nearest-neighbour quantization is ambiguous there, so the code guesses upward and *records that it guessed* in the flag. The source adds one thing Table 24 does not have: a range clamp — snapped sizes outside \[21, 145\] return −1 with flag −1 (`detector.c:3013-3022`), rejecting estimates no version can explain. Each axis is measured twice (top and bottom edges, left and right edges), and `chooseSideSize` (`detector.c:3034-3048`) arbitrates: the bigger flag wins — a confident measurement beats a tie-guess beats an out-of-range — and equal flags take the max of the two sizes. Two independent quantized measurements plus a confidence-ordered merge: the same aggregate-then-suppress pattern as the finder votes, in miniature.

### Where each stage fails

The corpus gives no error probabilities and we assign none; what it does give is a deterministic mapping from stage failures to observable outcomes, tabulated in [JC-T ch. 5](../developers-manual/05-detector-and-decoder.md)'s failure-to-stage table. Qualitatively, along this chapter's pipeline: profile tests that never fire (blur beyond the 50 % band, module size below the 3-scanline floor) surface as "too few finder pattern found" after vote suppression; a wrongly *promoted* decoy that survives voting poisons the homography and fails downstream at metadata decode, not at detection — the misleading case, since the reported stage is later than the causal stage; a missed fourth corner degrades to the three-finder solve, whose extrapolation error grows with perspective skew until the local re-search misses; and side-size ties (flag 0) either resolve correctly against the other edge's measurement or produce a symbol sampled at the wrong version — again failing downstream, at metadata or LDPC. The general lesson of the table is worth stating as theory: in a pipeline of estimators, *robustness mechanisms convert loud early failures into quiet late ones*, so the observed failure stage is an upper bound, not an identification, of the causal stage.

## Back to the code

The full flow of `findMasterSymbol` (`detector.c:1811-2158`), stitched from the pieces above: scan rows at stride `min_module_size` in the green channel (`seekPatternHorizontal`), cross-check candidates against blue then red channels to classify the corner type by core colour (`detector.c:1862-1923`, using the FP core colour tables of `encoder.h:50-70`; chapter 8 explains why the cores are colour-coded), verify module-size consistency across channels (`checkModuleSize2`), run the full 2-D cross-check (`crossCheckPattern`, vertical + horizontal + diagonal), and only then vote (`saveFinderPattern`). A vertical rescan triggers when only the top pair or only the bottom pair was found (`detector.c:2042-2047`) — the symbol may be rotated 90°. Then suppression, the three-of-four solve if needed, and hand-off to side-size estimation and the homography. Mode 0 (the fork's 2-colour extension) bypasses the colour classification entirely and assigns corner types by image quadrant (`detector.c:1999-2009`) — with all cores black, colour cannot disambiguate; the workaround trades rotation invariance for monochrome operation (fork extension, no ISO counterpart).

## Exercises

**1 (guided).** Derive the +7.5 in Formula (5). The finder cores sit 3.5 modules in from each corner (`transform.c:208-211`), so the centre-to-centre distance between adjacent finders spans side − 7 modules. Show that ⌊dist/m̂ + 7.5⌋ implements round-to-nearest of (dist/m̂) + 7, and that the source's ⌊dist/m̂ + 0.5⌋ + 7 is the same integer for every real dist/m̂.

<details><summary>Answer</summary>

dist/m̂ estimates side − 7, so side ≈ dist/m̂ + 7. For any real x, ⌊x + 0.5⌋ is x rounded to nearest (half-up): the classic floor-shift trick. Adding the integer 7 commutes with the floor: ⌊x + 0.5⌋ + 7 = ⌊x + 0.5 + 7⌋ = ⌊x + 7.5⌋. So Formula (5) is "round the module count to the nearest integer, then add the 7-module finder offset", and the two-step source computation is exactly equal, not merely close.

</details>

**2 (open).** Construct a background texture that defeats the 50 % tolerance test — a run-length profile that is *not* a finder pattern yet passes `checkPatternCross`, `checkModuleSize2`, and accumulates ≥ 3 votes across adjacent scanlines. What image structures in the wild have this property (consider text, halftone dots, fabric weave)? Which later stage catches your construction, and what would it cost an adversary to make it survive that stage too? (Chapter 11 prices this attack.)

**3 (guided).** Build the full truth table of the snap-plus-merge rules: for each pair of residues (r₁, r₂) ∈ \{0, 1, 2, 3\}² of the two opposite-edge estimates of one axis, give the flags, the merged outcome of `chooseSideSize`, and whether the result can be wrong by a whole version when the true side is s ≡ 1 (mod 4) and both raw estimates are within ±2 of s.

<details><summary>Hint</summary>

Flags: r ∈ \{0, 1, 2\} → flag 1; r = 3 → flag 0 (and size +2). Within ±2 of a valid s, raw estimates hit residues 1 (error 0), 0 or 2 (error ∓1), 3 (error ±2 — the ambiguous cell edge). Note the asymmetry: residue 3 always snaps *upward*, so a −2 error snaps to s, but a +2 error snaps to s + 4. The dangerous rows are those where a flag-0 tie-guess of s + 4 meets a flag-0 tie-guess of s + 4 (wrong by one version, undetected) — check which raw-error combinations produce them, and confirm that any flag-1 measurement on the other edge overrides the guess.

</details>

## Further reading

- R. Hartley, A. Zisserman, *Multiple View Geometry in Computer Vision*, 2nd ed., Cambridge University Press, 2004 — ch. 2 for the projective plane and homographies, ch. 4 for DLT estimation; the closed-form unit-square parametrization appears as the "four-point" special case.
- M. A. Fischler, R. C. Bolles, "Random Sample Consensus: a paradigm for model fitting with applications to image analysis and automated cartography", *Communications of the ACM* 24(6), 1981 — the canonical vote-then-fit robust estimator; JAB's found-counters are a structured cousin.
- P. J. Huber, *Robust Statistics*, Wiley, 1981 — tolerance bands, breakdown points, and why aggregation beats tightening the test.
- R. M. Gray, D. L. Neuhoff, "Quantization", *IEEE Transactions on Information Theory* 44(6), 1998 — quantization as estimation, for the side-size snap as a lattice quantizer with an erasure symbol.
