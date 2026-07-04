package com.jabauth.client.abe

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Reader for the CP-ABE **envelope** carried in a v2 COA's `ABE_SEALED` section.
 *
 * Byte-exact mirror of the server encoder (`org.nexus.jabauth.abe.encryption.AbeEnvelope`), big-endian:
 * ```
 *   magic[4]        = 'A','B','E','1'
 *   version[1]      = 0x01
 *   suite[1]        = 0x01 (AES-256-GCM)
 *   policy_len[2]
 *   ct_len[4]
 *   policyData_len[2]
 *   policy[policy_len]          <- the access policy, CLEARTEXT UTF-8
 *   ciphertext[ct_len]
 *   policyData[policyData_len]
 * ```
 *
 * The access policy sits in the header in cleartext (it is AAD-bound, not encrypted), so a verifier can
 * recover **which attributes are authorized to open the sealed credential** offline, with no master
 * secret and no native call — exactly what the ABE pre-check adjudicates (Principle F: the device
 * pre-checks the policy; the native decapsulation is the server/device-only confirmation). We read only
 * the policy; the ciphertext/policyData are not needed to adjudicate.
 */
object AbeEnvelope {
    private const val MAGIC0 = 'A'.code.toByte()
    private const val MAGIC1 = 'B'.code.toByte()
    private const val MAGIC2 = 'E'.code.toByte()
    private const val MAGIC3 = '1'.code.toByte()
    private const val VERSION: Byte = 0x01
    private const val HEADER_BYTES = 4 + 1 + 1 + 2 + 4 + 2

    /**
     * The cleartext access policy string from [envelope] (e.g. `role:inspector AND region:EU`), or null if
     * the bytes are not a well-formed ABE1 envelope. Never throws — a malformed sealed section is simply
     * "no adjudicable policy".
     */
    fun policyOf(envelope: ByteArray?): String? = runCatching {
        if (envelope == null || envelope.size < HEADER_BYTES) return null
        val buf = ByteBuffer.wrap(envelope)
        if (buf.get() != MAGIC0 || buf.get() != MAGIC1 || buf.get() != MAGIC2 || buf.get() != MAGIC3) return null
        if (buf.get() != VERSION) return null
        buf.get() // suite — read past; only AES-256-GCM is defined, but the policy is suite-independent
        val policyLen = java.lang.Short.toUnsignedInt(buf.short)
        buf.int // ct_len — the policy precedes the ciphertext on the wire; advance past both length fields
        buf.short // policyData_len
        if (policyLen == 0 || buf.remaining() < policyLen) return null
        val policyBytes = ByteArray(policyLen)
        buf.get(policyBytes)
        String(policyBytes, StandardCharsets.UTF_8)
    }.getOrNull()
}
