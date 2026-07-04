package com.jabauth.jabcode

/**
 * Evidence-backed v2 COA encoding profiles, chosen by the deployment's capture environment and latency
 * tier. Each pins an **(Nc colour, ECC)** pair from the jabcode R1 per-medium robustness study.
 *
 * Kotlin mirror of the server `org.nexus.jabauth.jabcode.secure.v2.CoaProfile`: the `(colorMode, eccLevel)`
 * pairs match byte-for-byte, so a profile the server selects means the same thing on the phone — the wire
 * parity the v2 format depends on. (The server's `toConfig()` codec builder is intentionally omitted; the
 * device needs the profile *identity* — colour + ECC + name — to render the format line and group latency.)
 */
enum class CoaProfile(val colorMode: ColorMode, val eccLevel: Int, val rationale: String) {

    /** Default — uncontrolled/mobile field, latency-tolerant. R1 print profile (Nc3/ECC5): 100% under
     *  blur+downscale, ~2× density vs 4-colour so the full credential symbol stays manageable. */
    FIELD(ColorMode.COLOR_16, 5, "Uncontrolled/mobile field; 100% under blur+downscale, manageable symbol."),

    /** Hostile capture — harsh light / on-screen. R1 hostile profile (Nc1/ECC5): the only colour mode 100%
     *  under chroma+lighting; lowest density → large symbol / cascade for a full COA. */
    FIELD_HOSTILE(ColorMode.COLOR_4, 5, "Harsh light / on-screen; max colour+lighting robustness, large symbol."),

    /** Controlled capture — kiosk/scanner, latency-critical. Nc5/ECC3: controlled capture permits high
     *  colour → small symbol → fast decode (T1 ~87 ms). Needs decent light. */
    CONTROLLED(ColorMode.COLOR_64, 3, "Kiosk/scanner, latency-critical; small symbol, fast decode, needs light."),

    /** Server / archival — image already captured, backend re-verify, not latency-bound. R1 archival profile
     *  (Nc7/ECC3): maximum density. */
    SERVER(ColorMode.COLOR_256, 3, "Backend re-verify / archival; maximum density, capture already done.");

    companion object {
        /** The safe default for an unknown ecosystem: [FIELD]. A COA that may be scanned anywhere must
         *  decode everywhere; deployments opt *up* to [CONTROLLED] only when they guarantee capture quality. */
        fun defaultProfile(): CoaProfile = FIELD
    }
}
