package com.jabauth.client.abe

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Reader for the CP-ABE **envelope** carried in a v2 COA's `ABE_SEALED` section.
 *
 * Byte-exact mirror of the server encoder (`org.nexus.jabauth.abe.encryption.AbeEnvelope`), big-endian:
 * ```
 *   magic[4]        = 'A','B','E','1'
 *   version[1]      = 0x01 | 0x02
 *   suite[1]        = 0x01 (AES-256-GCM)
 *   policy_len[2]
 *   ct_len[4]
 *   policyData_len[2]
 *   keysetId_len[1]             <- v2 only
 *   policy[policy_len]          <- the access policy, CLEARTEXT UTF-8
 *   ciphertext[ct_len]
 *   policyData[policyData_len]
 *   keysetId[keysetId_len]      <- v2 only
 * ```
 *
 * The access policy sits in the header in cleartext (it is AAD-bound, not encrypted), so a verifier can
 * recover **which attributes are authorized to open the sealed credential** offline, with no master
 * secret and no native call — exactly what the ABE pre-check adjudicates (Principle F: the device
 * pre-checks the policy; the native decapsulation is the confirmation).
 *
 * **v2 adds [keysetIdOf]**: the id of the authority that sealed the layer. A reader holding provisioned
 * keys from several issuers cannot otherwise tell which one applies — a wrong-key attempt is just a
 * decryption failure, indistinguishable from "not authorised". Matching the stamp first keeps an honest
 * denial distinguishable from a key-selection mistake.
 *
 * Every accessor is total: malformed bytes yield null rather than throwing, so a corrupt sealed section
 * degrades to "no adjudicable policy" instead of breaking the scan.
 */
object AbeEnvelope {
    private const val MAGIC0 = 'A'.code.toByte()
    private const val MAGIC1 = 'B'.code.toByte()
    private const val MAGIC2 = 'E'.code.toByte()
    private const val MAGIC3 = '1'.code.toByte()
    private const val VERSION_1: Byte = 0x01
    private const val VERSION_2: Byte = 0x02
    private const val HEADER_BYTES = 4 + 1 + 1 + 2 + 4 + 2

    /** Parsed envelope header: field lengths plus the version-dependent keyset stamp. */
    private class Header(
        val policyLen: Int,
        val ctLen: Int,
        val policyDataLen: Int,
        val keysetLen: Int,
        val buf: ByteBuffer
    )

    /** Walk the header, validating every length against what actually remains. Null = malformed. */
    private fun header(envelope: ByteArray?): Header? {
        if (envelope == null || envelope.size < HEADER_BYTES) return null
        val buf = ByteBuffer.wrap(envelope)
        if (buf.get() != MAGIC0 || buf.get() != MAGIC1 || buf.get() != MAGIC2 || buf.get() != MAGIC3) return null
        val version = buf.get()
        if (version != VERSION_1 && version != VERSION_2) return null
        buf.get() // suite — only AES-256-GCM is defined; the policy is suite-independent
        val policyLen = java.lang.Short.toUnsignedInt(buf.short)
        val ctLen = buf.int
        val policyDataLen = java.lang.Short.toUnsignedInt(buf.short)
        val keysetLen = if (version == VERSION_2) {
            if (buf.remaining() < 1) return null else java.lang.Byte.toUnsignedInt(buf.get())
        } else 0
        if (ctLen < 0) return null
        if (buf.remaining().toLong() < policyLen.toLong() + ctLen + policyDataLen + keysetLen) return null
        return Header(policyLen, ctLen, policyDataLen, keysetLen, buf)
    }

    /**
     * The cleartext access policy string from [envelope] (e.g. `role:inspector AND region:EU`), or null if
     * the bytes are not a well-formed ABE1 envelope.
     */
    fun policyOf(envelope: ByteArray?): String? = runCatching {
        val h = header(envelope) ?: return null
        if (h.policyLen == 0) return null
        val policyBytes = ByteArray(h.policyLen)
        h.buf.get(policyBytes)
        String(policyBytes, StandardCharsets.UTF_8)
    }.getOrNull()

    /**
     * The id of the authority that sealed [envelope], or null for v1 (unstamped) or malformed bytes.
     * Use it to select the matching provisioned key before attempting decryption.
     */
    fun keysetIdOf(envelope: ByteArray?): String? = runCatching {
        val h = header(envelope) ?: return null
        if (h.keysetLen == 0) return null
        h.buf.position(h.buf.position() + h.policyLen + h.ctLen + h.policyDataLen)
        val keyset = ByteArray(h.keysetLen)
        h.buf.get(keyset)
        String(keyset, StandardCharsets.UTF_8)
    }.getOrNull()

    /**
     * Full decode into the [EncryptedData] the engine decrypts, or null if malformed.
     *
     * [policyOf] is what a *verifier* needs to adjudicate; this is what a *reader* needs to actually open
     * the layer with a provisioned key.
     */
    fun decode(envelope: ByteArray?): EncryptedData? = runCatching {
        val h = header(envelope) ?: return null
        val policyBytes = ByteArray(h.policyLen)
        h.buf.get(policyBytes)
        val ct = ByteArray(h.ctLen)
        h.buf.get(ct)
        val policyData = ByteArray(h.policyDataLen)
        h.buf.get(policyData)
        EncryptedData(ct, String(policyBytes, StandardCharsets.UTF_8), policyData)
    }.getOrNull()
}
