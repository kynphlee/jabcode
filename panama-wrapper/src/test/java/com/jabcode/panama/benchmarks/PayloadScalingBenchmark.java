package com.jabcode.panama.benchmarks;

import com.jabcode.panama.JABCodeDecoder;
import com.jabcode.panama.JABCodeEncoder;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Payload-scaling + multithreaded benchmark.
 *
 * <p>Closes two gaps in the existing suite:
 * <ol>
 *   <li><b>Higher payloads per colour mode</b> — sweeps {@code sizeBytes} from 1&nbsp;KB to
 *       16&nbsp;KB. Cells that exceed a mode's single-symbol capacity fail in {@code @Setup}
 *       and JMH marks them errored — cleanly <i>mapping each mode's ceiling</i> (mode- and
 *       content-dependent: dense payloads reach further).</li>
 *   <li><b>Framework-realistic content</b> — unlike the rest of the suite (one repeating
 *       alphanumeric filler), this varies {@code payloadType} across the things the jab-auth /
 *       COA framework actually encodes:
 *       <ul>
 *         <li>{@code NUMERIC} — GTIN / serial numbers (numeric mode, densest)</li>
 *         <li>{@code LOREM} — lorem ipsum prose (mixed-mode text)</li>
 *         <li>{@code JSON} — COA auth/product metadata, batch-manifest shaped</li>
 *         <li>{@code JWT} — auth tokens (base64url header.claims.signature)</li>
 *         <li>{@code SIGNATURE} — raw crypto signature / ABE ciphertext (binary → base64)</li>
 *       </ul></li>
 * </ol>
 *
 * <p><b>Multithreaded-ready:</b> {@code @State(Scope.Thread)} gives every thread its own
 * encoder, decoder, and temp files — no shared native state, no file collisions — so it is
 * correct to run {@code -t 1,2,4,8} for the throughput-vs-threads curve that determines server
 * capacity. {@code Mode.Throughput} (ops/sec) is the metric that matters under concurrency.
 *
 * <p>Run against the <b>swift-lineage</b> {@code libjabcode.so} (the production decoder), e.g.:
 * <pre>
 *   bash run-benchmark.sh PayloadScalingBenchmark "-t 4 -p colorMode=8,64,256 -p payloadType=JWT"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class PayloadScalingBenchmark {

    public enum PayloadType { NUMERIC, LOREM, JSON, JWT, SIGNATURE }

    @Param({"4", "8", "16", "32", "64", "128", "256"})
    public int colorMode;

    @Param({"NUMERIC", "LOREM", "JSON", "JWT", "SIGNATURE"})
    public PayloadType payloadType;

    /** Higher payloads. Over-capacity cells error in setup → they map the per-mode ceiling. */
    @Param({"1000", "4000", "8000", "16000"})
    public int sizeBytes;

    private static final int ECC_LEVEL = 5;

    private JABCodeEncoder encoder;
    private JABCodeDecoder decoder;
    private JABCodeEncoder.Config config;
    private String payload;
    private Path encodedFixture;   // pre-encoded, decoded by the decode() benchmark
    private Path scratchFile;      // overwritten by the encode() benchmark

    @Setup(Level.Trial)
    public void setup() throws Exception {
        encoder = new JABCodeEncoder();
        decoder = new JABCodeDecoder();
        payload = generatePayload(payloadType, sizeBytes);
        config = JABCodeEncoder.Config.builder()
            .colorNumber(colorMode)
            .eccLevel(ECC_LEVEL)
            .symbolNumber(1)
            .moduleSize(12)
            .build();

        long tid = Thread.currentThread().threadId();
        encodedFixture = Files.createTempFile("payscale-enc-" + tid + "-", ".png");
        scratchFile = Files.createTempFile("payscale-scr-" + tid + "-", ".png");

        // Pre-encode the decode fixture. Failure here means the payload exceeds this mode's
        // single-symbol capacity — throw so JMH errors only this parameter combination,
        // which is exactly how we want the per-mode ceiling to surface.
        if (!encoder.encodeToPNG(payload, encodedFixture.toString(), config)) {
            throw new IllegalStateException(
                "exceeds single-symbol capacity: colour=" + colorMode
                + " type=" + payloadType + " size=" + sizeBytes + "B");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        Files.deleteIfExists(encodedFixture);
        Files.deleteIfExists(scratchFile);
    }

    /** Encode throughput: payload -> JABCode PNG, per (colour, type, size). */
    @Benchmark
    public boolean encode() {
        return encoder.encodeToPNG(payload, scratchFile.toString(), config);
    }

    /** Decode throughput: pre-encoded JABCode PNG -> payload, per (colour, type, size). */
    @Benchmark
    public String decode() {
        return decoder.decodeFromFile(encodedFixture);
    }

    // --- framework-realistic, deterministic payload generators ---

    private static final String LOREM =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
        + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud "
        + "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. ";

    /** COA auth/product metadata — repeats as a batch manifest. */
    private static final String JSON_RECORD =
        "{\"certId\":\"C-7f3a9b2e\",\"tokenId\":\"T-0042\",\"product\":\"LX-Handbag-2026\","
        + "\"batch\":\"B-2026-0613\",\"serial\":\"SN-000123456\",\"issuer\":\"RHABI-COA\","
        + "\"ts\":1781592000},";

    /** Auth token: base64url header.claims.signature (ES256-shaped). */
    private static final String JWT =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9."
        + "eyJzdWIiOiJjb2EtYXV0aCIsImNlcnRJZCI6IkMtN2YzYTliMmUiLCJpYXQiOjE3ODE1OTIwMDAsImV4cCI6MTc4MTY3ODQwMH0."
        + "MEUCIQDx7Z3kq2bHsigStubBase64urlSignatureSegmentAbCdEf0123456789-_ \n";

    static String generatePayload(PayloadType type, int size) {
        switch (type) {
            case NUMERIC:   return fill("0123456789", size);   // GTIN / serial (numeric mode, densest)
            case LOREM:     return fill(LOREM, size);          // prose text (mixed mode)
            case JSON:      return fill(JSON_RECORD, size);    // COA metadata / batch manifest
            case JWT:       return fill(JWT, size);            // auth tokens (base64url)
            case SIGNATURE: {                                  // crypto sig / ABE ciphertext (binary)
                byte[] raw = new byte[size];
                new Random(0xBA5EL).nextBytes(raw);
                String b64 = Base64.getEncoder().encodeToString(raw);
                return b64.substring(0, Math.min(size, b64.length()));
            }
            default:        return fill(LOREM, size);
        }
    }

    private static String fill(String pattern, int size) {
        StringBuilder sb = new StringBuilder(size);
        while (sb.length() < size) sb.append(pattern);
        return sb.substring(0, Math.min(size, sb.length()));
    }
}
