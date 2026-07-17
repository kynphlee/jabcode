package com.jabauth.client.v2

import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * COA Payload Format **v2** — the variable-size, self-contained TLV container that carries a real sealed
 * credential (SD-JWT VC + ABE-sealed layer + trust chain) so a COA is offline-verifiable. Byte-for-byte
 * Kotlin port of the server `org.nexus.jabauth.jabcode.secure.v2.PayloadFormatV2`.
 *
 * Wire layout (all integers big-endian):
 * ```
 *   Header (8B): magic 'J''A''C''2' | version 0x02 | flags(1) | sectionCount(1) | reserved 0x00
 *   Body:        flags.LZ4 -> rawLen(4) ++ lz4(region)   else -> region
 *                region = repeated TLV: type(1) ++ length(4) ++ value(length)
 *   Trailer(4B): CRC-32 over [header..body]  (framing integrity only; authenticity rests on the sections)
 * ```
 * **Cross-impl parity:** LZ4 *decompression* is universal, so a server-encoded blob always decodes here
 * (proven by a server golden fixture in the tests). LZ4 *compressed bytes* can differ across LZ4 impls, so
 * byte-identical encode is guaranteed only on the uncompressed path (also golden-tested). Transport (e.g.
 * A1 base64url) is applied outside this container; unknown section types are preserved (forward-compat).
 */
object PayloadFormatV2 {

    private val MAGIC = byteArrayOf('J'.code.toByte(), 'A'.code.toByte(), 'C'.code.toByte(), '2'.code.toByte())
    private const val VERSION: Byte = 0x02
    private const val FLAG_LZ4 = 0x01
    private const val HEADER_LEN = 8
    private const val CRC_LEN = 4
    private const val MAX_SECTIONS = 255

    private val lz4Compressor by lazy { LZ4Factory.fastestInstance().fastCompressor() }
    private val lz4Decompressor by lazy { LZ4Factory.fastestInstance().fastDecompressor() }

    /** Canonical v2 section types; [code] is the on-wire type byte. */
    enum class SectionType(val code: Int) {
        TRUST_CHAIN(0x01), SDJWT_VC(0x02), ABE_SEALED(0x03), META(0x04), EXT(0x05),

        /**
         * Compact trust-list anchor (the D-trust-anchor-direction ADR): the issuer signing
         * key's RFC 7638 JWK SHA-256 thumbprint, raw 32 bytes. Carried INSTEAD of
         * [TRUST_CHAIN] by trust-list tenants — the verifier resolves the issuer key from a
         * cached signed trust list and confirms the resolved key re-derives this thumbprint.
         * Exactly one anchor section (this or TRUST_CHAIN) per mark.
         */
        ISSUER_KEY_ID(0x06);

        companion object {
            /** Known type for a code, or null for an unknown (forward-compat) type. */
            fun fromCode(code: Int): SectionType? = entries.firstOrNull { it.code == code }
        }
    }

    /** One TLV section; [typeCode] is kept raw so unknown types survive a decode. */
    class Section private constructor(val typeCode: Int, private val bytes: ByteArray) {
        /** A defensive copy of the section value. */
        val value: ByteArray get() = bytes.copyOf()
        val type: SectionType? get() = SectionType.fromCode(typeCode)

        companion object {
            fun of(type: SectionType, value: ByteArray?): Section = raw(type.code, value)
            fun raw(typeCode: Int, value: ByteArray?): Section {
                require(typeCode in 0..0xFF) { "section type out of byte range: $typeCode" }
                return Section(typeCode, value?.copyOf() ?: ByteArray(0))
            }
        }
    }

    /** Result of [decode]. */
    data class Parsed(val sections: List<Section>, val compressed: Boolean) {
        /** First section of the given type, if present. */
        fun first(type: SectionType): ByteArray? = sections.firstOrNull { it.typeCode == type.code }?.value
    }

    /** True if [blob] begins with the v2 magic + version (dispatch helper). */
    fun isV2(blob: ByteArray?): Boolean {
        if (blob == null || blob.size < HEADER_LEN + CRC_LEN) return false
        for (i in MAGIC.indices) if (blob[i] != MAGIC[i]) return false
        return blob[4] == VERSION
    }

    /** Serialize [sections] into a v2 blob (whole-body LZ4 only when it strictly saves space). */
    fun encode(sections: List<Section>): ByteArray {
        require(sections.isNotEmpty()) { "at least one section is required" }
        require(sections.size <= MAX_SECTIONS) { "too many sections (max $MAX_SECTIONS): ${sections.size}" }

        var regionLen = 0
        for (s in sections) regionLen += 1 + 4 + s.value.size
        val region = ByteBuffer.allocate(regionLen)
        for (s in sections) {
            region.put(s.typeCode.toByte())
            region.putInt(s.value.size)
            region.put(s.value)
        }
        val rawRegion = region.array()

        // Whole-body LZ4 only when it strictly saves space (compressed + 4-byte rawLen < raw).
        val compressed = lz4Compressor.compress(rawRegion)
        val useLz4 = compressed.size + 4 < rawRegion.size
        val flags = if (useLz4) FLAG_LZ4 else 0
        val bodyLen = if (useLz4) 4 + compressed.size else rawRegion.size

        val out = ByteBuffer.allocate(HEADER_LEN + bodyLen + CRC_LEN)
        out.put(MAGIC)
        out.put(VERSION)
        out.put(flags.toByte())
        out.put(sections.size.toByte())
        out.put(0x00.toByte()) // reserved
        if (useLz4) {
            out.putInt(rawRegion.size)
            out.put(compressed)
        } else {
            out.put(rawRegion)
        }

        val crc = CRC32()
        crc.update(out.array(), 0, out.position())
        out.putInt(crc.value.toInt())
        return out.array()
    }

    /** Parse a v2 blob: verify magic/version + CRC, decompress if flagged, walk the TLV sections. */
    fun decode(blob: ByteArray?): Parsed {
        require(blob != null && blob.size >= HEADER_LEN + CRC_LEN) { "v2 payload too small" }
        require(isV2(blob)) { "not a v2 payload (bad magic/version)" }

        val crcOffset = blob.size - CRC_LEN
        val crc = CRC32()
        crc.update(blob, 0, crcOffset)
        val expected = crc.value.toInt()
        val actual = ByteBuffer.wrap(blob, crcOffset, CRC_LEN).int
        require(expected == actual) { "v2 CRC mismatch — payload corrupt or tampered" }

        val flags = blob[5].toInt() and 0xFF
        val sectionCount = blob[6].toInt() and 0xFF
        val compressed = (flags and FLAG_LZ4) != 0

        val bodyOffset = HEADER_LEN
        val bodyLen = crcOffset - bodyOffset
        val region: ByteArray
        if (compressed) {
            require(bodyLen >= 4) { "v2 compressed body too small" }
            val rawLen = ByteBuffer.wrap(blob, bodyOffset, 4).int
            require(rawLen in 0..(1 shl 26)) { "v2 rawLen out of range: $rawLen" }
            region = ByteArray(rawLen)
            try {
                lz4Decompressor.decompress(blob, bodyOffset + 4, region, 0, rawLen)
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("v2 LZ4 decompress failed", e)
            }
        } else {
            region = ByteArray(bodyLen)
            System.arraycopy(blob, bodyOffset, region, 0, bodyLen)
        }

        val sections = ArrayList<Section>(sectionCount)
        val buf = ByteBuffer.wrap(region)
        for (i in 0 until sectionCount) {
            require(buf.remaining() >= 1 + 4) { "v2 truncated section header at index $i" }
            val type = buf.get().toInt() and 0xFF
            val len = buf.int
            require(len in 0..buf.remaining()) { "v2 section length out of range at index $i: $len" }
            val value = ByteArray(len)
            buf.get(value)
            sections.add(Section.raw(type, value))
        }
        require(buf.remaining() == 0) { "v2 trailing bytes after $sectionCount sections" }
        return Parsed(sections, compressed)
    }
}
