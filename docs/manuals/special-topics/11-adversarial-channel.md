# 11. The adversarial channel

<!-- objective: The reader can construct an attacker-defender model of symbol damage, analyze where LDPC, interleaving and masking each move the equilibrium, and evaluate ECC-level choice as a resource-allocation decision under both random and adversarial damage. -->

**Where it lives.** Recovery capability: ISO/IEC 23634:2022 Table 20 qualifier (clause 5.4.1) and the `(wc, wr)` ladder it fixes, `ecclevel2wcwr` (`src/jabcode/encoder.h:234`), rates `ecclevel2coderate` (`encoder.h:226`). Fixed-pattern damage grading: Clause 8.2.4 (and the UEC formula 8.2.2, Table 29 Ecap). Interleaving: `interleave.c` (seeded Fisher-Yates). Finder protection in masking: `W1 = 100` (`src/jabcode/mask.c:22`). Cascade layout: chapter 10 ([10-cascade-combinatorics.md](10-cascade-combinatorics.md)). Operator-level restatements: [JC-U ch. 2/5](../operators-manual/index.md). The crypto layer is deferred to AF-S (forthcoming).
<!-- anchor: src/jabcode/encoder.h:226,234 -->
<!-- anchor: src/jabcode/mask.c:22 -->
<!-- anchor: ISO 23634 Table 20, 8.2.2, 8.2.4, 5.4.1 -->

## Scope note — read this first

> The game-theoretic models in this chapter are **this book's analytical framing**, not part of ISO/IEC 23634 and not present in the codebase. Everything I anchor to a spec clause or a source line (the recovery qualifier, the ECC ladder, the interleave, `W1 = 100`, the fixed-pattern grading) is a verified fact of the standard or the implementation. Everything cast as a *game* — players, strategies, payoffs, equilibria — is a lens I am placing over those facts to reason about robustness under a hostile channel. The numeric game I solve below uses **illustrative toy parameters, stated explicitly at the point of use**; its equilibrium is a worked demonstration of method, not a measured property of JAB Code. Where I have no verified number, I say so rather than invent one. And this chapter models only the **physical carrier channel** — ink and pixels and error-correction. The economic games that motivate JAB Code as an authentication carrier — cloning cost, key extraction, oracle attacks — belong to the framework's Special Topics book (AF-S, forthcoming) and are named here only to mark the boundary.

## The problem

Chapters 3 through 9 built the defence and never named an attacker. LDPC corrects errors; interleaving scatters bursts; masking suppresses finder-lookalikes; detection tolerates blur. Each mechanism was designed against *the channel* — and the channel was tacitly assumed random: independent noise, arbitrary but not malicious damage. Table 20's headline is exactly such a statement: "recovery capability of the bit errors in more than 95 % of cases" (clause 5.4.1). That is a claim about *random* damage — a distribution of error patterns, most of which are recoverable. It is silent about the worst 5 %, and an adversary lives in the worst case, not the average.

The question this chapter frames: what changes when the damage is chosen by someone who has read this book? A random channel draws error patterns from nature; an adversarial channel lets an opponent *place* a fixed damage budget wherever it hurts most. The defender's mechanisms were all designed with the adversarial reading latent in them — this chapter makes it explicit, ties each instrument to the mechanism that supplies it, and then, under stated toy parameters, solves the smallest honest game that captures the core tension: where should a defender put redundancy, and where should an attacker put damage?

## Theory

### Two channels, one code

Fix a symbol of Pg gross payload modules carrying an LDPC code of rate R_ℓ at ECC level ℓ (rates `ecclevel2coderate`, `encoder.h:226`: level 1 → 0.63, default level 3 → 0.55, down to level 10 → 0.14). The code corrects up to some fraction of module errors; call the correctable fraction c_ℓ, increasing as R_ℓ falls — more parity, more correction, less payload. That is the defender's rate–robustness dial, and it is the same dial in both channels.

The channels differ in *who chooses the error pattern* for a given error count e:

- **Random channel.** Errors fall i.i.d. across modules. The relevant quantity is the probability that e exceeds the code's radius; Table 20's ≥ 95 % is a statement in this regime. LDPC's design assumption — sparse, near-independent parity checks — is tuned for it.
- **Adversarial channel.** The attacker spends a damage budget to *maximize* the chance of an uncorrectable pattern. Against a pure data-module code the attacker would simply concentrate damage until e > c_ℓ·Pg. But JAB is not a pure data code — it has structure the attacker can target more cheaply than "flip enough data bits", and structure the defender can use to make concentration expensive. Those are the instruments.

The verification-side accounting makes the "worst case is different" point precisely. Clause 8.2.2's unrecoverable-error-count metric,

$$
\mathrm{UEC} = 1 - \frac{e + 2t}{P_g \times E_{\mathrm{cap}}},
$$

with Ecap ranging 8-29 % across levels (Table 29), separates erasures e from errors t at a 1-to-2 weight: an error (wrong value, location unknown) costs twice an erasure (known-missing location). An adversary who can *erase* rather than *corrupt* — obliterate a module rather than flip it — spends half. This asymmetry is the first place adversarial and random damage diverge, and it recurs below.

### The defender's four instruments

Each instrument is a design decision already in the corpus; the game framing is what is new.

**1. Rate, via ECC level (`encoder.h:226, 234`).** The direct dial. Dropping from level 3 (R = 0.55) to level 9 (R = 0.17) raises the parity share of the gross stream from 0.45 to 0.83 (≈ ×1.8 at fixed Pg) and raises c_ℓ, at the cost of net payload — chapter 3 derives the LDPC correction radius from `(wc, wr)`. In game terms: the defender buys a larger correctable fraction c_ℓ by shrinking the payload the attacker must overwhelm. Trading capacity for margin is only worthwhile if the threat is real; against a benign channel the spare parity is dead weight (this is the Yinsen question the operator's manual asks — *should* you, [JC-U ch. 5](../operators-manual/index.md)).

**2. Interleaving: forcing bursts to scatter (`interleave.c`).** LDPC's independence assumption is its soft spot: a *burst* — many adjacent modules lost to a scratch, a fold, a smudge — concentrates errors into few parity checks and defeats correction below the nominal radius. The seeded Fisher-Yates permutation (chapter 5) breaks spatial adjacency: physically contiguous damage lands on bit positions scattered across the codeword, converting a burst into the near-i.i.d. pattern LDPC was tuned for. Against an adversary this *raises the cost of the cheapest physical attack*: a single localized mark, which is what a real-world attacker can most easily apply, is exactly what interleaving neutralizes. The attacker is pushed from "one scratch" toward "damage distributed across the whole symbol" — more work, more conspicuous.

**3. `W1 = 100`: denying the cheapest detection-kill (`mask.c:22`).** Masking's penalty rule 1 weights finder-pattern-lookalikes in the data field a hundredfold against the other penalties (W2 = W3 = 3, `mask.c:22-24`; chapter 6). The design reading: a data region that accidentally resembles a finder pattern is a decoding hazard. The adversarial reading is sharper — finder patterns are the *keys to detection* (chapter 9: no three-of-four finders, no homography, no decode at all), and a symbol full of finder-lookalikes is a symbol where an attacker can cheaply confuse the detector into locking onto a decoy. W1 = 100 makes the encoder aggressively avoid handing the attacker that ammunition. It protects detection, which sits *upstream of ECC entirely*.

**4. Cascade layout: spreading risk (chapter 10).** An assembly's docking graph is a dependency tree rooted at the primary. That is a liability (correlated failure — a lost host orphans its subtree) but also an instrument: the defender chooses which payload sits at the root and how deep the tree runs, spreading a fixed message across symbols so that no single localized attack destroys all of it. The primary's finders are the shared single point of failure, which is exactly why instrument 3 guards them so heavily.

### The soft underbelly: fixed-pattern damage

Everything above defends the *data* path. But detection runs first, and detection depends on fixed patterns — finder patterns, alignment patterns, the metadata that reports Nc and version. Damage these and the decoder never reaches the ECC-protected payload. That an attacker might target them is not this book's speculation: **the designers graded fixed-pattern damage separately** — Clause 8.2.4 grades Fixed Pattern Damage as its own quality parameter. A grading category exists because the failure mode it measures is real and distinct. Read adversarially, 8.2.4 is the standard acknowledging its own soft underbelly: a finder kill defeats detection *before* ECC matters, so a small, well-placed budget spent on a fixed pattern can be worth far more than the same budget spread over data modules. This is the crux the toy game must capture — the attacker chooses not just *how much* damage but *what kind of target*.

### A two-player zero-sum resource game (toy parameters)

I now solve the smallest game that captures the data-vs-fixed-pattern tension. **Every parameter here is illustrative and stated; the equilibrium demonstrates method, not a measured JAB property.**

**Players and moves.**

- *Defender* picks ECC level ℓ, fixing rate R_ℓ and a correctable data-error fraction c_ℓ. For the worked instance take ℓ = 3: R = 0,55 (`encoder.h:226`), and assume a toy correctable fraction c = 0,10 of data modules (illustrative — the true c from level 3's `(wc, wr) = (4, 9)` is derived in chapter 3; 0,10 is a round stand-in).
- *Attacker* holds a damage budget p, a fraction of the symbol's modules it can destroy, and splits it: a fraction α on **fixed patterns** (finders/metadata), 1 − α on **data modules**. Toy budget p = 0,06 (six percent of modules).

**Toy payoff.** Model decode success as the product of surviving detection and surviving the data code — a deliberately simple decomposition, stated as an assumption:

- *Detection survives* unless the fixed-pattern damage exceeds a kill threshold. Fixed patterns are a small fraction of the symbol; take a toy fixed-pattern fraction f = 0,05 and a kill rule "detection fails if fixed-pattern damage covers at least half the fixed patterns." Attacker fixed-damage as a fraction of fixed patterns is (α·p)/f. Detection dies when (α·p)/f ≥ 0,5, i.e. α ≥ 0,5·f/p = 0,5·0,05/0,06 ≈ 0,417.
- *Data survives* unless data damage exceeds the code radius: data-damage fraction of data modules ≈ (1 − α)·p/(1 − f) must stay below c = 0,10.

The attacker wants decode to fail; the defender wants it to succeed — zero-sum in "probability of successful decode", which under these deterministic toy thresholds is 1 or 0.

**Solving the attacker's best response (defender fixed at ℓ = 3).** Two ways to force failure:

$$
\text{(A) detection kill:} \quad \alpha \ge \alpha^\ast = \frac{0{,}5 \cdot f}{p} = \frac{0{,}025}{0{,}06} \approx 0{,}417,
$$

$$
\text{(B) data swamp:} \quad \frac{(1-\alpha)\,p}{1-f} \ge c \;\Rightarrow\; (1-\alpha)\ge \frac{c(1-f)}{p} = \frac{0{,}10\cdot 0{,}95}{0{,}06} \approx 1{,}58.
$$

Route (B) requires (1 − α) ≥ 1,58, impossible (1 − α ≤ 1): **at this budget the attacker cannot swamp the data code by damage alone** — the rate-3 code is strong enough that 6 % spread over data is under the 10 % radius even at α = 0. Route (A), however, needs only α ≈ 0,417 of the budget aimed at fixed patterns. So the attacker's best response is the **detection kill**: spend ~42 % of a 6 % budget — about 2,5 % of all modules, concentrated on finders/metadata — and decode fails, while the same budget spread over data would have failed. The fixed patterns are the cheap target, precisely as Clause 8.2.4's separate grading warns.

**The defender's response, and where the equilibrium sits.** The defender's rate dial (instrument 1) moves c but does **nothing** against route (A) — more parity cannot recover a symbol that was never detected. The instruments that move α\* are the *upstream* ones: interleaving raises the cost of concentrating damage (instrument 2, though it acts on data bursts, not fixed patterns); `W1` and the detector's three-of-four finder recovery (chapter 9) harden detection itself, effectively lifting the kill threshold from "half the fixed patterns" toward "most of them" — with one finder recoverable, the attacker must kill **two** finders, doubling the fixed-pattern budget the kill demands and pushing α\* up. Under the toy numbers, if the kill rule tightens from 0,5 to (say) 0,75 of fixed patterns (modelling three-of-four recovery), α\* rises to 0,75·0,05/0,06 ≈ 0,625, and at a small enough total budget route (A) too becomes infeasible — at which point the attacker's best response flips back to data, and the *rate* dial re-enters as the relevant defence. That flip is the equilibrium's structure: **the binding constraint alternates between detection and data as the upstream defences and the budget move**, and a defender who spends only on ECC level while leaving finders soft has optimized the wrong margin.

The honest caveat, restated: change c, f, p, or the kill rule and the crossover moves. The model's *value* is not its numbers but its shape — it shows why fixed-pattern hardening and error-correction strength are complements, not substitutes, and why the standard grades them separately.

### Where equilibria shift

Summarizing the comparative statics as instruments change (all within the toy frame):

- **Stronger interleaving** raises the marginal cost of the attacker's data route super-linearly for a *burst-constrained* attacker — one who can only apply contiguous damage (exercise 2) — pushing such an attacker toward the fixed-pattern route or toward abandoning bursts.
- **Higher `W1` / better finder recovery** raises α\*, making the detection kill dearer, until data becomes the binding route again.
- **Deeper cascades** convert a single symbol's all-or-nothing game into a portfolio: the attacker must now kill the primary's finders (shared root) *or* pay per-subtree — but correlated failure through the dependency tree caps the diversification benefit (chapter 10's reliability caveat, now with an adversary choosing the correlation).
- **Lower rate (higher ECC level)** only helps once detection is assured; against an undetected symbol it is inert.

## Back to the code

None of this game logic is *in* the code — the corpus contains no attacker model, no cost function, and (per the corpus model and [JC-T ch. 15](../developers-manual/15-conformance-testing.md)) no Clause 8 verifier at all: CPA, CVDM and the fixed-pattern grade are spec-side quality parameters with no implementation to measure them here. What *is* in the code are the instruments the game reasons about, each already met in an earlier chapter: the rate ladder (`encoder.h:226, 234`), the interleave permutation (`interleave.c`), the `W1 = 100` finder guard (`mask.c:22`), the three-of-four finder recovery (`detector.c:2078-2145`), and the cascade dependency tree (`encoder.c:1598`). The chapter's contribution is to read them as one system under a hostile channel rather than four independent robustness features — and to locate, honestly, the seam the designers themselves flagged by grading it: fixed-pattern damage, upstream of every bit of ECC.

The layer this chapter does **not** model: whether a decoded symbol is *authentic* — clonable, forgeable, key-bound. That is a different game with different players (a copier, a verifier, an oracle) and a different payoff (economic, not bit-error). It is the subject of the framework's Special Topics book, AF-S (forthcoming); this chapter's carrier-channel game is its physical-layer prerequisite, no more.

## Exercises

**1 (guided).** Recompute the toy equilibrium at ECC level 10 (R = 0,14, `encoder.h:226`; take a toy correctable fraction c = 0,25). Keep p = 0,06, f = 0,05, kill rule 0,5. Does the data-swamp route (B) become feasible? Does the attacker's best response change? What does this say about the *marginal* value of the last few ECC levels against a detection-targeting adversary?

<details><summary>Answer</summary>

Route (B) needs (1 − α) ≥ c(1 − f)/p = 0,25·0,95/0,06 ≈ 3,96 — even more infeasible than at level 3; a higher c makes the data code *harder* to swamp, not easier. Route (A) is unchanged (α\* ≈ 0,417) because raising ECC does nothing to detection. So the attacker still kills detection, and best response is identical. Lesson: against an adversary who targets fixed patterns, ECC levels 3 → 10 buy *zero* additional protection — the entire capacity sacrifice (R 0,55 → 0,14) is spent on a margin the attacker routes around. The last ECC levels earn their keep only in the random channel or once detection is independently hardened.

</details>

**2 (guided).** Argue that interleaving raises the attacker's cost super-linearly for a burst-constrained attacker. Model the attacker as able to place a single contiguous run of length L modules (a scratch). Before interleaving, those L errors hit L adjacent codeword positions — concentrated in few parity checks. After the Fisher-Yates permutation, where do the L errors land, and how does the number of *distinct affected parity checks* grow with L? Why does that make forcing an uncorrectable pattern cost the attacker a burst length that grows faster than the code's nominal radius?

<details><summary>Hint</summary>

A uniform-random permutation (chapter 5's Fisher-Yates) sends the contiguous run to L positions that are, in expectation, spread uniformly over the codeword — a "balls into bins" scatter across the parity checks. The number of distinct checks touched grows roughly linearly in L until saturation, but the attacker needs enough errors *in a single check's neighbourhood* to defeat local correction; post-scatter, achieving k co-located errors requires L ≫ k (coupon-collector-style), so the burst length needed to break the code grows faster than the k the code tolerates. Contrast the pre-interleave case, where L = k suffices. State the independence assumption you are borrowing from the LDPC design.

</details>

**3 (open).** Design the cheapest attack against a symbol whose **mask reference is known** to the attacker (`mask_type`, `jabcode.h:184`; default reference 7, `jabcode.h:36`). Knowing the mask, the attacker can predict the post-mask value of every module. What is the minimum-budget damage pattern that (a) forces an uncorrectable data error while evading the erasure/error weighting of UEC (clause 8.2.2, favour flips over erasures — or the reverse?), or (b) corrupts metadata Part II (version/ECC/mask, `decoder.h:24`) so the decoder mis-sizes the symbol? Compare the budgets. Which of the defender's four instruments, if any, responds to a *known-mask* adversary — and does knowing the mask help attack the fixed patterns, which are not masked at all? (The economics of *how* an attacker comes to know the mask belong to AF-S.)

## Further reading

- J. von Neumann, O. Morgenstern, *Theory of Games and Economic Behavior*, Princeton University Press, 1944 — zero-sum games and the minimax theorem, the backbone of the resource game above.
- M. J. Osborne, A. Rubinstein, *A Course in Game Theory*, MIT Press, 1994 — best-response and equilibrium formalism, and the comparative-statics reasoning used for the instrument shifts.
- R. G. Gallager, *Low-Density Parity-Check Codes*, MIT Press, 1963 — the independence assumption interleaving exists to protect, from the source; pairs with chapter 3.
- ISO/IEC 23634:2022, Clause 8 — the print-quality and damage-grading parameters (CPA, CVDM, fixed-pattern damage, UEC) that this chapter reads adversarially; note that none are implemented in the corpus ([JC-T ch. 15](../developers-manual/15-conformance-testing.md)).
- AF-S (forthcoming) — the authentication-layer games (cloning economics, key extraction, verification oracles) deliberately excluded here.
