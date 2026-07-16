# 11. How the service reaches this library

<!-- objective: A SaaS operator can trace a /api/jabcode/generate request from the REST surface through the Panama FFM binding to the native libjabcode.so encode call, and state what the build-time provenance validation guarantees -->

**In this chapter you will** follow one HTTP request through every layer of the jab-auth service until it lands in the native library you built in Part II — and learn what the framework's build-time provenance validation guarantees about that library before a single request is served.

**You should already** know the codec from the SDK side: the build artifacts ([chapter 6](06-building-the-library.md)), the encode parameters ([chapter 7](07-encoding-with-jabcodewriter.md)), and the five-call C API flow ([chapter 9](09-embedding-the-c-api.md)). No Java fluency is required — we read the chain, we do not write it.

## The chain at a glance

The jab-auth-framework service (a separate repository, `jab-auth-framework`) wraps this codec behind REST endpoints. A generate request crosses five layers:

| Hop | Layer | What happens |
|---|---|---|
| 1 | Security filter chain | `RateLimitingFilter` → `ApiKeyAuthenticationFilter` → `JwtAuthenticationFilter` <!-- anchor: JABCodeCOA-crypto corpus §3.8 (SecurityConfig.java:191-197) --> |
| 2 | `JabCodeController` | `POST /api/jabcode/generate` handler receives a `JabCodeRequest` <!-- anchor: JABCodeCOA-crypto corpus §3.9 (JabCodeController.java:37-38) --> |
| 3 | `JabCodeService` / `PanamaJabCodeService` | config resolved, payload handed to the production codec service <!-- anchor: JabCodeService.java:50-55; PanamaJabCodeService.java:94 --> |
| 4 | Panama FFM wrapper | reflection into `com.jabcode.panama.JABCodeEncoder`, vendored as `libs/jabcode-panama-1.0.0-SNAPSHOT.jar` <!-- anchor: PanamaJabCodeService.java:37-38; jab-auth-jabcode/build.gradle:116 --> |
| 5 | Native `libjabcode.so` | the same encode flow you met in [chapter 9](09-embedding-the-c-api.md) <!-- anchor: src/jabcode/include/jabcode.h:217-219 --> |

The rest of the chapter walks each hop with the facts an operator needs: who may call what, which defaults apply, and what can silently go wrong.

## Hop 1 — the REST surface and who may call it

The five `/api/jabcode/*` endpoints carry different authentication modes, quoted from the framework corpus model's REST table (which resolves to `SecurityConfig.java`):

| Method + path | Handler | Auth mode |
|---|---|---|
| POST `/api/jabcode/generate` | `JabCodeController.generateJabCode` | API key (dev-open) <!-- anchor: JABCodeCOA-crypto corpus §3.9; SecurityConfig.java:172-176, 153-168 --> |
| POST `/api/jabcode/generate/simple` | `.generateSimple` | JWT (dev-open) <!-- anchor: JABCodeCOA-crypto corpus §3.9 --> |
| POST `/api/jabcode/decode` | `.decodeJabCode` | JWT (dev-open) <!-- anchor: JABCodeCOA-crypto corpus §3.9 --> |
| POST `/api/jabcode/validate` | `.validateJabCode` | JWT (dev-open) <!-- anchor: JABCodeCOA-crypto corpus §3.9 --> |
| GET `/api/jabcode/health` | `.health` | public <!-- anchor: JABCodeCOA-crypto corpus §3.8 (SecurityConfig.java:127-133) --> |

Two operator-relevant footnotes. "Dev-open" means these endpoints are `permitAll` **only under the dev profile**, and that block is deliberately time-boxed — the source carries an expiry check commented "Auto-expires: 2026-12-01". And the API key is the property `jabauth.security.api-key`, documented "REQUIRED in production". Everything else under `/api/**` requires authentication, and anything unmatched is denied outright. <!-- anchor: JABCodeCOA-crypto corpus §3.8 (SecurityConfig.java:39, 179-182), §3.11 (application.properties:74) -->

## Hops 2 and 3 — controller to codec service, and the defaults that apply

`JabCodeController.generateJabCode` takes the JSON body as a `JabCodeRequest` — three fields: `certificateId`, `tokenId`, and `data` (a `byte[]`, so JSON carries it Base64-encoded) — and delegates to `JabCodeService.generateJabCode(request)`. Any exception becomes a plain 400 response. <!-- anchor: JabCodeController.java:37-45; JabCodeRequest.java:12-17 -->

Here is the fact that surprises SDK users most: that delegate is a `default` method on the `JabCodeService` interface, and its body pins the configuration:

```java
// Use default config - can be enhanced later to extract from request
JabCodeConfig config = JabCodeConfig.defaultConfig();
```

<!-- anchor: JabCodeService.java:50-52 -->

So a `/api/jabcode/generate` caller gets `defaultConfig()` — no request field changes it. And `defaultConfig()` is **not** the library default you know from Part II:

| Parameter | Library default (this repo) | Service default (`JabCodeConfig.defaultConfig()`) |
|---|---|---|
| Colour count | `DEFAULT_COLOR_NUMBER 8` <!-- anchor: src/jabcode/include/jabcode.h:33 --> | 4 (QUATERNARY) <!-- anchor: JABCodeCOA-crypto corpus §3.5 (JabCodeConfig.java:241-250) --> |
| Module size | `DEFAULT_MODULE_SIZE 12` <!-- anchor: src/jabcode/include/jabcode.h:32 --> | 12 <!-- anchor: JABCodeCOA-crypto corpus §3.5 --> |
| ECC level | `DEFAULT_ECC_LEVEL 3` <!-- anchor: src/jabcode/include/jabcode.h:35 --> | 3 <!-- anchor: JABCodeCOA-crypto corpus §3.5 --> |
| Symbol number | `DEFAULT_SYMBOL_NUMBER 1` <!-- anchor: src/jabcode/include/jabcode.h:31 --> | 1 <!-- anchor: JABCodeCOA-crypto corpus §3.5 (JabCodeConfig.java:140-151) -->

The divergence is deliberate: the framework record's own Javadoc says "Default uses QUATERNARY (4-color) mode for maximum reliability in physical product applications" — the service optimizes for surviving print and capture, where the bare library optimizes for density. If you compare a CLI symbol against a service symbol of the same payload and wonder why they look different, this table is the answer. <!-- anchor: JabCodeConfig.java:7; docs/manuals/corpus-model.md §3.1 (jabcode.h:31-36) -->

The service bean that actually encodes is `PanamaJabCodeService` — marked `@Primary`, so it is the implementation Spring injects wherever a `JabCodeService` is asked for. Its encode path stays in memory: the payload bytes go in, PNG bytes come back, no temp file ever touches disk (the source comment calls out the "plaintext-at-rest exposure" that removed). <!-- anchor: PanamaJabCodeService.java:33-35, 105-115 -->

## Hops 4 and 5 — the Panama wrapper and the native library

`PanamaJabCodeService` loads two classes by reflection — `com.jabcode.panama.JABCodeEncoder` and `com.jabcode.panama.JABCodeDecoder` — from a jar vendored at `libs/jabcode-panama-1.0.0-SNAPSHOT.jar`. That wrapper uses the JDK's Foreign Function and Memory API (Panama FFM) to call straight into native code: the framework's build files record that `libjabcode.so` "is loaded BY NAME via the Panama FFM binding (SymbolLookup.libraryLookup), so it must sit on java.library.path / LD_LIBRARY_PATH", and every consumer JVM runs with `--enable-native-access=ALL-UNNAMED`. <!-- anchor: PanamaJabCodeService.java:37-38; JABCodeCOA-crypto corpus §3.12 (jab-auth-emulator/build.gradle:70-75; build.gradle:75-81) -->

The `.so` at the end of the chain lives at `jab-auth-jabcode/src/main/resources/libjabcode.so` in the framework repo — a vendored copy of the very library `make -C src/jabcode` produces here ([chapter 6](06-building-the-library.md)). This repo's Makefile even carries the supply hooks for wrapper builds: `VENDORED_DIR := ../../lib` with `refresh-lib` ("the ONLY sanctioned way to update lib/libjabcode.{so,a}") and the `check-lib` ABI-freshness guard. <!-- anchor: src/jabcode/Makefile:18, 49-50, 61; JABCodeCOA-crypto corpus §3.12 -->

## What the build-time provenance validation guarantees

Because the native library is vendored, the framework refuses to trust it blindly. A Gradle task, `validateNativeLib`, runs before resources are packaged (`processResources.dependsOn validateNativeLib`) and fails the **build** — not a request, the build — on any of these checks: <!-- anchor: JABCodeCOA-crypto corpus §3.12 (jab-auth-jabcode/build.gradle:4-102) -->

| Check | Constant / expectation |
|---|---|
| File exists, is a real file | not a symlink ("Symlinks break Docker COPY") <!-- anchor: jab-auth-jabcode/build.gradle:12-22 --> |
| Plausible size | at least `100_000` bytes <!-- anchor: jab-auth-jabcode/build.gradle:23-26 --> |
| ELF magic | bytes `0x7F 'E' 'L' 'F'` <!-- anchor: jab-auth-jabcode/build.gradle:35-38 --> |
| 64-bit | `EI_CLASS = 2` <!-- anchor: jab-auth-jabcode/build.gradle:39-41 --> |
| Little-endian | `EI_DATA = 1` <!-- anchor: jab-auth-jabcode/build.gradle:42-44 --> |
| Shared object | `e_type = 3` (`ET_DYN`) <!-- anchor: jab-auth-jabcode/build.gradle:45-48 --> |
| x86-64 | `e_machine = 0x3E` <!-- anchor: jab-auth-jabcode/build.gradle:49-52 --> |
| Identity | SHA-256 of the `.so` must equal `libjabcode.so.sha256` in the `libjabcode.so.provenance` record; the paired jar must match `jabcode-panama.jar.sha256` <!-- anchor: jab-auth-jabcode/build.gradle:53-95 --> |

The provenance record also pins `jabcode.decoder.commit` and `libjabcode.so.buildid` — that is, it names the exact commit of **this repository** the binary was certified from. The task's own comment states the guarantee precisely: the ELF checks "prove this is *a* shared object; the SHA-256 checks below prove it is *the* artifact we certified. A drifted .so (an old build copied in without refreshing provenance) fails here instead of silently shipping a month-old decoder." <!-- anchor: jab-auth-jabcode/build.gradle:53-57, 73-83 -->

So the two repos guard the two ends of one supply chain: here, `check-lib` catches a stale vendored library against a fresh source build; there, `validateNativeLib` catches a stale or tampered vendored library against its certified hash.

## The caveat: runtime degradation is graceful, and that cuts both ways

Provenance runs at build time. At **runtime**, if the Panama wrapper class is missing or the native library cannot be loaded, `PanamaJabCodeService` does not crash — its constructor logs a warning ("JABCode Panama wrapper not found in classpath" or "Native library libjabcode.so not found", each ending "Falling back to stub implementation") and marks itself unavailable. From then on, generate returns a blank 100 by 100 stub image and decode returns the literal bytes `stub-decoded-data`. <!-- anchor: PanamaJabCodeService.java:57-91, 95-96, 131-134, 402-404 -->

For an operator this is the one behavior to internalize: **a misdeployed codec does not produce request errors — it produces stubs.** If every generated symbol is a small blank square, check the service startup logs for the fallback warnings, and the `isPanamaAvailable()` health facts, before debugging anything else. <!-- anchor: PanamaJabCodeService.java:409-411 -->

## Worked example: one request, five hops

Constructed from source, not executed — a trace of `POST /api/jabcode/generate` with this body:

```json
{
  "certificateId": "cert-2026-0001",
  "tokenId": "tok-2026-0001",
  "data": "Q09BLTIwMjYtMDAwMTIz"
}
```

1. The request passes `RateLimitingFilter`, then `ApiKeyAuthenticationFilter` checks the API key (outside the dev profile). <!-- anchor: SecurityConfig.java:172-176, 191-197 -->
2. `JabCodeController.generateJabCode` binds the body to `JabCodeRequest`; the `data` field decodes from Base64 to the payload bytes `COA-2026-000123`. <!-- anchor: JabCodeController.java:37-41; JabCodeRequest.java:26-33 -->
3. The `JabCodeService` default method pins `JabCodeConfig.defaultConfig()` — 4 colours, module size 12, ECC 3 — and calls `generateJabCode(request.data(), config)`. <!-- anchor: JabCodeService.java:50-55 -->
4. `PanamaJabCodeService.generateJabCode` translates the config for the wrapper (`createPanamaConfig`), then invokes `encoder.encodeBytes(byte[], Config)`, receiving PNG bytes. <!-- anchor: PanamaJabCodeService.java:94-121, 268 -->
5. Inside the wrapper, Panama FFM downcalls into `libjabcode.so` — the `createEncode` → `generateJABCode` flow of [chapter 9](09-embedding-the-c-api.md). <!-- anchor: src/jabcode/include/jabcode.h:217-219 -->

The response is a `JabCodeResult` whose metadata reports colour depth 4, the rendered pixel size, and ECC level 3 — the service defaults, exactly as the table above predicts. <!-- anchor: JabCodeService.java:57-70 -->

## Try it

1. Which `/api/jabcode/*` endpoint can be called with no credentials in a production profile?
2. Every symbol your service generates is a blank 100 by 100 image. What happened, and where do you look first?
3. A teammate rebuilds `libjabcode.so` from this repo and copies it into the framework without touching anything else. What happens at the next framework build, and why is that the designed outcome?
4. A client posts to `/api/jabcode/generate` and receives a 4-colour symbol, yet chapter 7 told you the codec's default is 8 colours. Reconcile the two facts.

<details><summary>Answers</summary>

1. Only GET `/api/jabcode/health` — it is in the `SecurityConfig` public list. The generate/decode/validate endpoints need an API key or JWT outside the (time-boxed) dev profile. <!-- anchor: SecurityConfig.java:127-133 -->
2. `PanamaJabCodeService` fell back to its stub: the wrapper jar or `libjabcode.so` failed to load at startup. Check the startup log for the "Falling back to stub implementation" warnings before anything else. <!-- anchor: PanamaJabCodeService.java:74-91, 402-404 -->
3. `validateNativeLib` fails the build with "libjabcode.so is STALE or MISMATCHED — provenance assertion failed", because the new binary's SHA-256 no longer matches `libjabcode.so.provenance`. Designed: a deliberate re-vendor must also regenerate the provenance record, so an accidental or tampered swap can never ship silently. <!-- anchor: jab-auth-jabcode/build.gradle:73-83 -->
4. Both are true. The library's own default is `DEFAULT_COLOR_NUMBER 8`, but the service never uses it: the REST path pins `JabCodeConfig.defaultConfig()`, which is 4-colour by explicit framework choice ("maximum reliability in physical product applications"). <!-- anchor: src/jabcode/include/jabcode.h:33; JabCodeService.java:50-52; JabCodeConfig.java:7 -->

</details>

## Where to go next

- Next: [chapter 12](12-service-vs-sdk-configuration.md) maps every service configuration field to the C API and writer flags you already know — and marks which knobs the REST surface can actually reach.
- Deeper: the framework's module graph, the fail-closed design register, and the full provenance policy (`LIBJABCODE.md`) are covered in the framework Developer's Manual (AF-T), forthcoming.
