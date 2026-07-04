package com.jabauth.client.v2

import com.google.common.truth.Truth.assertThat
import com.jabauth.client.v2.PayloadFormatV2.Section
import com.jabauth.client.v2.PayloadFormatV2.SectionType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Random

/**
 * v2 TLV wire-format contract + cross-language parity. Ports the server `PayloadFormatV2Test` behaviours
 * and adds the parity proof: a blob produced by the *server* encoder decodes here, and re-encoding the
 * same sections reproduces the server bytes exactly (deterministic uncompressed path).
 */
class PayloadFormatV2Test {

    private fun b(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun hex(s: String) = ByteArray(s.length / 2) {
        ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
    }

    /** Produced by the real server encoder (org.nexus...v2.PayloadFormatV2.encode) over the 3 sections below. */
    private val serverGolden =
        "4A4143320200030001000000086C6561662D646572020000000A65794A2E702E737E647E040000000D7B22766374223A22636F61227DF71835E6"
    private fun goldenSections() = listOf(
        Section.of(SectionType.TRUST_CHAIN, b("leaf-der")),
        Section.of(SectionType.SDJWT_VC, b("eyJ.p.s~d~")),
        Section.of(SectionType.META, b("{\"vct\":\"coa\"}")),
    )

    // ── cross-language parity (the whole point of v2) ───────────────────────────
    @Test fun `a server-encoded golden blob decodes here`() {
        val parsed = PayloadFormatV2.decode(hex(serverGolden))
        assertThat(parsed.compressed).isFalse()
        assertThat(parsed.sections).hasSize(3)
        assertThat(parsed.first(SectionType.TRUST_CHAIN)!!).isEqualTo(b("leaf-der"))
        assertThat(parsed.first(SectionType.SDJWT_VC)!!).isEqualTo(b("eyJ.p.s~d~"))
        assertThat(parsed.first(SectionType.META)!!).isEqualTo(b("{\"vct\":\"coa\"}"))
    }

    @Test fun `re-encoding the same sections reproduces the server bytes exactly (uncompressed)`() {
        assertThat(PayloadFormatV2.encode(goldenSections())).isEqualTo(hex(serverGolden))
    }

    // ── ported server contract ──────────────────────────────────────────────────
    @Test fun `round-trips the core sections byte-for-byte`() {
        val out = PayloadFormatV2.decode(PayloadFormatV2.encode(goldenSections()))
        assertThat(out.sections).hasSize(3)
        assertThat(out.first(SectionType.SDJWT_VC)!!).isEqualTo(b("eyJ.p.s~d~"))
        assertThat(out.first(SectionType.ABE_SEALED)).isNull()
    }

    @Test fun `LZ4 flag is set only when it saves space, both round-trip`() {
        val compressible = ByteArray(2048) // zeros -> LZ4 wins
        val c = PayloadFormatV2.decode(PayloadFormatV2.encode(listOf(Section.of(SectionType.ABE_SEALED, compressible))))
        assertThat(c.compressed).isTrue()
        assertThat(c.first(SectionType.ABE_SEALED)!!).isEqualTo(compressible)

        val highEntropy = ByteArray(2048).also { Random(42).nextBytes(it) }
        val h = PayloadFormatV2.decode(PayloadFormatV2.encode(listOf(Section.of(SectionType.ABE_SEALED, highEntropy))))
        assertThat(h.compressed).isFalse()
        assertThat(h.first(SectionType.ABE_SEALED)!!).isEqualTo(highEntropy)
    }

    @Test fun `CRC-32 trailer detects tampering`() {
        val blob = PayloadFormatV2.encode(listOf(Section.of(SectionType.SDJWT_VC, b("authentic"))))
        blob[blob.size - 6] = (blob[blob.size - 6].toInt() xor 0x01).toByte() // flip a body byte before the CRC
        val ex = assertThrows(IllegalArgumentException::class.java) { PayloadFormatV2.decode(blob) }
        assertThat(ex).hasMessageThat().contains("CRC")
    }

    @Test fun `an unknown section type is preserved, not rejected`() {
        val out = PayloadFormatV2.decode(PayloadFormatV2.encode(listOf(Section.raw(0x7F, b("future")))))
        assertThat(out.sections).hasSize(1)
        assertThat(out.sections[0].typeCode).isEqualTo(0x7F)
        assertThat(out.sections[0].type).isNull()
        assertThat(out.sections[0].value).isEqualTo(b("future"))
    }

    @Test fun `isV2 discriminates from v1 and legacy`() {
        assertThat(PayloadFormatV2.isV2(hex(serverGolden))).isTrue()
        val v1 = ByteArray(1026).also { it[0] = 0x01 }
        assertThat(PayloadFormatV2.isV2(v1)).isFalse()
        assertThat(PayloadFormatV2.isV2(ByteArray(1024))).isFalse()
        assertThat(PayloadFormatV2.isV2(byteArrayOf('J'.code.toByte(), 'A'.code.toByte(), 'C'.code.toByte()))).isFalse()
        assertThat(PayloadFormatV2.isV2(null)).isFalse()
    }

    @Test fun `malformed input is rejected`() {
        val good = PayloadFormatV2.encode(listOf(Section.of(SectionType.META, b("data"))))
        assertThrows(IllegalArgumentException::class.java) { PayloadFormatV2.decode(good.copyOf(good.size - 3)) }
        val ex = assertThrows(IllegalArgumentException::class.java) { PayloadFormatV2.encode(emptyList()) }
        assertThat(ex).hasMessageThat().contains("at least one section")
    }
}
