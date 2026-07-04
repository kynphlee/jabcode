package com.jabauth.jabcode

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity guard: the Android [CoaProfile] must pin the exact same (colour, ECC) pairs as the server enum
 * (`org.nexus.jabauth.jabcode.secure.v2.CoaProfile`). If these drift, a profile means different things on
 * the two sides and v2 interop breaks silently.
 */
class CoaProfileTest {

    @Test
    fun `profiles pin the server's colour and ECC pairs`() {
        assertEquals(ColorMode.COLOR_16, CoaProfile.FIELD.colorMode)
        assertEquals(5, CoaProfile.FIELD.eccLevel)

        assertEquals(ColorMode.COLOR_4, CoaProfile.FIELD_HOSTILE.colorMode)
        assertEquals(5, CoaProfile.FIELD_HOSTILE.eccLevel)

        assertEquals(ColorMode.COLOR_64, CoaProfile.CONTROLLED.colorMode)
        assertEquals(3, CoaProfile.CONTROLLED.eccLevel)

        assertEquals(ColorMode.COLOR_256, CoaProfile.SERVER.colorMode)
        assertEquals(3, CoaProfile.SERVER.eccLevel)
    }

    @Test
    fun `the safe default is FIELD`() {
        assertEquals(CoaProfile.FIELD, CoaProfile.defaultProfile())
    }
}
