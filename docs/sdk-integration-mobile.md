# SDK Integration Map — Mobile (`jabcode` @ `swift-java-poc`)

**Workstream B Part 3** of the StockX × Entrupy × JABAuth handoff (2026-07-11).
**Anchor:** branch `claude/sdk-integration-survey-mobile` @ `2895177` (swift-java-poc,
2026-07-11). All class/line references verified against this tree (survey agent +
coordinator spot-checks).

**Thesis:** the mobile framework is the verification (reader) half of the rail, and it
validates **fully offline** — decode, certificate chain, SD-JWT VC signature/claims, and
CP-ABE policy, all on-device with zero backend. That single property is what the
StockX pointer-tag model and the Entrupy cloud-rescan model structurally cannot do, and it
is the wedge (customs, live resale, cross-border, outages, EU DPP).

## 1. Module map (7 Gradle modules, `jabauth-android/`)

| Module | Role | Load-bearing classes |
|--------|------|----------------------|
| `framework:core` | Base validation + crypto primitives | `CertificateValidator(Impl)`, `JWTValidator(Impl)`, `SecureStorage(Impl)` (EncryptedSharedPreferences: AES256-GCM values, AES256-SIV keys, Keystore master key) |
| `framework:jabcode-sdk` | Codec + camera | `JABCodeMobile` (JNI), `JABCodeDecoder`, `JABCodeCameraAnalyzer`, `ImageQualityAnalyzer`, `CameraUtils` |
| `framework:jabauth-client` | On-device PKI / SD-JWT / ABE / v2 format | `CertificateChainValidator(Impl)`, `TrustStoreManager(Impl)`, `JWTParser(Impl)`, `SdJwtVcService`, `ABEPolicyEngine` → `NativeABEPolicyEngine` → `NativeAbeEngine` → `RabeCpAbeKem`, `PayloadFormatV2` |
| `framework:diagnostic-engine` | Verification orchestration | `VerificationOrchestrator`, `VerdictRollup`, `TrustVerdict`, `StageResult` |
| `framework:ui-components` | Scanner + verdict UI | `ScannerHeader`, `Camera2Preview`, `TrustVerdictBadge` |
| `diagnostic-app` | E2E reference app | `ScanVerifier`, stage runners, drill-down screens |
| `benchmark-macro` | On-device perf | `VerifyLatencyBenchmark` |

Native: `libjabcode-mobile.so` (codec) + `librabe_kem.so` (CP-ABE KEM), shipped for
`arm64-v8a` / `armeabi-v7a` / `x86_64` (verified in `framework/jabauth-client/src/main/jniLibs/`).

## 2. The reader-side flow (scan → decode → offline-validate → verdict)

```
CameraX frame (throttled, quality-gated: focus 0.6 / contrast 0.2 / exposure 0.2 weights)
  → CameraUtils.imageProxyToBitmap (YUV_420_888 → RGBA)
  → JABCodeMobile.nativeDecodeFromBitmapWithMeta (libjabcode-mobile.so; binary-clean v2 bytes)
  → PayloadFormatV2.isV2 (JAC2 magic) → decode → sections {TRUST_CHAIN, SDJWT_VC, ABE_SEALED, META}
  → ScanVerifier.verify(payload, decodeLatencyMs)          [diagnostic-app/.../verify/ScanVerifier.kt:45]
      Stage 1 DECODE  — v2 structural gate (magic, CRC-32, TLV framing)
      Stage 2 PKI     — PkiStageRunner: expiry → signature linkage → PKIX chain to on-device
                        trust store; revocation ALWAYS UNKNOWN_OFFLINE (see §4)
      Stage 3 JWT     — JwtStageRunner: SdJwtVcService.verify() against the leaf's public key;
                        algorithm allowlist (RS*/ES* only, HS* blocked); expiry; selective
                        disclosure split (revealed vs withheld _sd digests)
      Stage 4 ABE     — AbeStageRunner: cleartext policy → monotone evaluation against
                        verifier attributes; deny names the failingClause
  → VerdictRollup [diagnostic-app/.../verify/VerdictRollup.kt:32-57, verified]
      DECODE FAIL → FAILED · non-COA → NOT_A_COA · any crypto FAIL → FAILED
      A′ opt-in: policy=TRUST_ANCHOR ∧ PKI WARN ∧ reached-anchor ∧ revocation UNKNOWN_OFFLINE
                 ∧ JWT PASS ∧ ABE PASS → TRUSTED_OFFLINE (teal)
      any WARN → UNTRUSTED · else → VERIFIED
```

Every stage is local: `TrustStoreManagerImpl` is an in-memory on-device anchor set;
`JWTParserImpl` verifies against asymmetric public keys only; `NativeAbeEngine` does the rabe
KEM + AES-GCM on-device (AAD = policy UTF-8 ‖ SHA-256(kemCiphertext)). **No network call
exists anywhere in the validation path.**

## 3. What a verification-event record renders as, today

The diagnostic app's drill-downs already render exactly the fields a verification-event
record carries:

- **Summary HUD** (`VerificationSummaryContent`) — per-stage rows: PKI revocation label,
  JWT algorithm, ABE GRANTED/DENIED, decode latency, `FORMAT v2 · PROFILE · ON-DEVICE`.
- **Credential drill-down** (`CredentialContent`) — "X of Y claims disclosed", revealed
  claim rows (this is where `event_type` / `facility` / `inspected_at` / `result` would
  appear), withheld `_sd` digest rows, `token_class`, issuer, validity.
- **Trust Anchor drill-down** (`TrustAnchorScreen`) — full chain nodes (subject, issuer,
  serial, validity, key usage, SHA-256 fingerprint, in-trust-store flag) + revocation status.
- **ABE drill-down** (`AbePolicyContent`) — policy expression, per-attribute satisfied
  chips, failing clause on deny.

So a StockX-style integrator gets record *display* nearly free; what's missing is history
(§4.3).

## 4. Gaps (the honest list)

1. **Revocation / status-list when connectivity IS available — absent by design, ready by
   design.** `CertificateChainValidatorImpl` deliberately refuses to claim "not revoked"
   (`CertificateChainValidatorImpl.kt:20,72`, verified): PKIX runs with
   `isRevocationEnabled = false` and the PKI stage renders `UNKNOWN_OFFLINE`; the verdict
   ceiling is `TRUSTED_OFFLINE`, never `VERIFIED`, without server confirmation. The
   `RevocationStatus` enum already has `VALID`/`REVOKED` arms — an *opportunistic online
   check* (fetch status list / OCSP when a network exists, cache signed lists for offline
   windows) is the single highest-value mobile build item, and it upgrades the verdict tier
   the UI already knows how to show.
2. **Holder binding — intentionally out of scope, both sides.** No KB-JWT, no `cnf` claim,
   no device binding (`SdJwtVcService.kt:29`, verified — mirrors the server). Fine for
   public item-provenance records; required before any owner-bound / transfer-of-custody
   feature (the Stage-6 loyalty flywheel). UX shape when it comes: holder key in Keystore,
   KB-JWT minted at presentation.
3. **No verification-event *history*.** One scan → one `VerificationResult`; nothing
   persists, nothing aggregates multiple events for one item. Provenance compounding needs:
   parse multiple `SDJWT_VC` sections (format is forward-compatible; `sectionCount` allows
   it), an `item_ref`-keyed local store (`SecureStorage` is the natural seat), and a
   timeline UI in the drill-down family.
4. **Replay store is process-lifetime.** `ReplayPolicy.consumeIfSession`
   (`TokenClass.kt:80`, verified) holds consumed JTIs in memory only. Correct for SESSION
   demos; carried records must be issued `ARTIFACT` (re-verifiable), so this is a
   server-issuance discipline more than a mobile gap.
5. **v2 section parsing sits in diagnostic-app seams.** `extractToken` / `extractChain` /
   `extractPolicy` live in `ScanVerifier` (diagnostic-app), not yet promoted into the
   framework SDK proper — an integrator today would copy the seams. Promotion into
   `jabauth-client` is mechanical but not done.

## 5. Why this beats the incumbents at the edge (one paragraph)

Entrupy's re-identification is a re-scan against its cloud; StockX's tag is a pointer whose
QR, per Workstream A video A3, resolves to a bare internal SKU string — both require the
operator to be online, solvent, and honest at verification time. This stack verifies a
carried credential with the operator absent: the mark itself carries the leaf cert, the
signed event claims, and the sealed policy layer; the phone carries the trust anchors and
the ABE user key. Customs at 2 a.m., a convention floor with no signal, a buyer in 2031
verifying a 2026 CT-scan event after the issuing program shut down — all identical to the
happy path. The honest ceiling: without connectivity the verdict is `TRUSTED_OFFLINE`
(revocation unknowable), which is exactly the right claim to make — and one neither
incumbent model can make at all.
