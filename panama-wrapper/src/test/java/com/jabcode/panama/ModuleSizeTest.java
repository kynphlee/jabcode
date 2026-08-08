package com.jabcode.panama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code moduleSize} must reach the rendered image.
 *
 * <p>It did not. The Builder validated it, the Config stored it and exposed a getter, and nothing
 * ever read that getter — {@code jab_encode.module_size} was mapped in a struct comment and never
 * written. Sweeping 1..1024 produced a byte-identical 252x252 image every time, because
 * {@code createEncode} leaves the field at 12 and the encoder reads it verbatim.
 *
 * <p>These tests assert GEOMETRY, not acceptance. That the encoder accepts a module size says
 * nothing about whether it used it — which is exactly how the parameter stayed dead through a
 * validated setter and a public getter.
 */
@DisplayName("Module size reaches the rendered image")
class ModuleSizeTest {

    /** Side-version 1 is 4*1 + 17 = 21 modules per side. */
    private static final int MODULES_PER_SIDE = 21;

    private static BufferedImage encode(int moduleSize) throws Exception {
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(8)
                .eccLevel(5)
                .moduleSize(moduleSize)
                .symbolNumber(1)
                .symbolVersions(List.of(new SymbolVersion(1, 1)))
                .build();
        byte[] png = new JABCodeEncoder().encodeBytes(
                "module size".getBytes(StandardCharsets.UTF_8), config);
        assertNotNull(png, "encode must succeed at moduleSize " + moduleSize);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** The regression test: the image must be exactly modules x moduleSize on each side. */
    @ParameterizedTest(name = "moduleSize {0} renders {0}px per module")
    @ValueSource(ints = {1, 2, 4, 8, 12, 16, 24})
    @DisplayName("The rendered image scales with moduleSize")
    void imageScalesWithModuleSize(int moduleSize) throws Exception {
        BufferedImage image = encode(moduleSize);
        assertEquals(MODULES_PER_SIDE * moduleSize, image.getWidth(),
                "width should be " + MODULES_PER_SIDE + " modules x " + moduleSize + "px");
        assertEquals(MODULES_PER_SIDE * moduleSize, image.getHeight(),
                "height should be " + MODULES_PER_SIDE + " modules x " + moduleSize + "px");
    }

    /**
     * The specific shape of the old defect: every size collapsed onto the default. Two different
     * sizes producing the same dimensions is the signature, so it is asserted directly rather than
     * left implied by the parameterised test above.
     */
    @Test
    @DisplayName("Two different module sizes do not render identically")
    void differentModuleSizesDifferInSize() throws Exception {
        BufferedImage small = encode(4);
        BufferedImage large = encode(16);
        assertNotEquals(small.getWidth(), large.getWidth(),
                "moduleSize 4 and 16 rendered the same width — the field is being ignored again");
        assertEquals(4 * large.getWidth(), 16 * small.getWidth(),
                "widths must be in the same ratio as the module sizes");
    }

    /** The default is unchanged, so existing callers see exactly what they saw before. */
    @Test
    @DisplayName("The default is still 12px per module")
    void defaultIsTwelve() throws Exception {
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(8)
                .eccLevel(5)
                .symbolNumber(1)
                .symbolVersions(List.of(new SymbolVersion(1, 1)))
                .build();
        assertEquals(12, config.getModuleSize(), "the documented default");

        byte[] png = new JABCodeEncoder().encodeBytes(
                "default".getBytes(StandardCharsets.UTF_8), config);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(MODULES_PER_SIDE * 12, image.getWidth(),
                "the pre-existing 252px render must be unchanged");
    }

    /** A rendered code must still decode — scaling should not disturb the payload. */
    @Test
    @DisplayName("A non-default module size still round-trips")
    void nonDefaultModuleSizeRoundTrips() {
        byte[] data = "module size round trip".getBytes(StandardCharsets.UTF_8);
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
                .colorNumber(8)
                .eccLevel(5)
                .moduleSize(6)
                .symbolNumber(1)
                .symbolVersions(List.of(new SymbolVersion(3, 3)))
                .build();
        byte[] png = new JABCodeEncoder().encodeBytes(data, config);
        assertNotNull(png);
        assertArrayEquals(data, new JABCodeDecoder().decodeBytes(png),
                "a 6px-module code must decode to the exact payload");
    }
}
