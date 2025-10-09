package com.jabcode.tools;

import com.jabcode.internal.JABCodeNative;
import com.jabcode.internal.JABCodeNativePtr;
import org.bytedeco.javacpp.Loader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DecodeJABCodes {
    public static void main(String[] args) throws Exception {
        Loader.load(JABCodeNative.class);

        List<File> inputs = new ArrayList<>();
        if (args.length == 0) {
//            inputs.add(new File("test-output/junit"));
            inputs.add(new File("javacpp-wrapper/test-output/junit"));
//            inputs.add(new File("test-output"));
//            inputs.add(new File("palette"));
        } else {
            for (String a : args) inputs.add(new File(a));
        }

        File outDir = new File("test-output/decoded");
        if (!outDir.exists()) outDir.mkdirs();

        List<File> images = new ArrayList<>();
        for (File f : inputs) collectPngs(f, images);
        if (images.isEmpty()) {
            System.err.println("No PNGs found to decode.");
            return;
        }

        int decoded = 0, failed = 0;
        for (File img : images) {
            try {
                decodeOne(img, outDir);
                decoded++;
            } catch (Exception ex) {
                System.err.println("FAIL: " + img.getPath() + " -> " + ex.getMessage());
                failed++;
            }
        }
        System.out.printf("Decoded %d, failed %d\n", decoded, failed);
    }

    private static void collectPngs(File f, List<File> out) {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            if (f.getName().toLowerCase().endsWith(".png")) out.add(f);
            return;
        }
        File[] files = f.listFiles();
        if (files == null) return;
        for (File c : files) collectPngs(c, out);
    }

    private static final int NORMAL_DECODE = 0;
    private static final int COMPATIBLE_DECODE = 1;

    private static void decodeOne(File image, File outDir) throws IOException {
        // Try original, then upscaled 2x, 3x, 4x, 5x. For each, try NORMAL then COMPATIBLE.
        File[] candidates = new File[] {
                image,
                upscaleToTemp(image, 2.0),
                upscaleToTemp(image, 3.0),
                upscaleToTemp(image, 4.0),
                upscaleToTemp(image, 5.0)
        };

        IOException lastErr = null;
        for (File cand : candidates) {
            if (cand == null) continue;
            int[] status = new int[1];
            byte[] bytes = tryDecode(cand.getPath(), NORMAL_DECODE, status);
            if (bytes == null) {
                bytes = tryDecode(cand.getPath(), COMPATIBLE_DECODE, status);
            }
            if (bytes != null) {
                writePayloadFiles(image, outDir, bytes);
                System.out.println("OK: " + image.getPath() + " -> " + new File(outDir, baseName(image) + ".txt").getPath());
                return;
            } else {
                // Print decoder state for troubleshooting (side_version, Nc, ECC, etc.)
                debugMetadata(cand.getPath(), COMPATIBLE_DECODE);
                lastErr = new IOException("decode failed for candidate: " + cand.getPath());
            }
        }
        if (lastErr != null) throw lastErr;
        throw new IOException("decode failed");
    }

    private static byte[] tryDecode(String path, int mode, int[] statusOut) {
        long bmp = JABCodeNativePtr.readImagePtr(path);
        if (bmp == 0L) return null;
        long dataPtr = JABCodeNativePtr.decodeJABCodePtr(bmp, mode, statusOut);
        if (dataPtr == 0L) return null;
        return JABCodeNativePtr.getDataBytes(dataPtr);
    }

    // Debug helper: pointer-based fast path without JavaCPP struct allocations
    private static void debugMetadata(String path, int mode) {
        try {
            long bmpPtr = JABCodeNativePtr.readImagePtr(path);
            if (bmpPtr == 0L) {
                System.err.println("DBG: readImagePtr failed for " + path);
                return;
            }
            int[] info = JABCodeNativePtr.debugDecodeExInfoPtr(bmpPtr, mode);
            // info layout: [status, default_mode, svx, svy, Nc, ecx, ecy, module, ssx, ssy, data_ok]
            System.err.printf(
                    "DBG: path=%s mode=%d status=%d default_mode=%d side_version=(%d,%d) Nc=%d ecl=(%d,%d) module=%d side_size=(%d,%d) data=%s\n",
                    path, mode,
                    info[0], info[1], info[2], info[3], info[4], info[5], info[6], info[7], info[8], info[9], (info[10] != 0 ? "ok" : "null")
            );
        } catch (Throwable t) {
            System.err.println("DBG: debugMetadata error for " + path + ": " + t.getMessage());
        }
    }

    private static void writePayloadFiles(File image, File outDir, byte[] bytes) throws IOException {
        String base = baseName(image);
        File txt = new File(outDir, base + ".txt");
        File bin = new File(outDir, base + ".bin");
        try (FileOutputStream fos = new FileOutputStream(bin)) {
            fos.write(bytes);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(txt), StandardCharsets.UTF_8)) {
            w.write(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static String baseName(File f) {
        String base = f.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base;
    }

    private static File upscaleToTemp(File image, double scale) {
        try {
            BufferedImage src = ImageIO.read(image);
            if (src == null) return null;
            int w = (int)Math.round(src.getWidth() * scale);
            int h = (int)Math.round(src.getHeight() * scale);
            if (w <= 0 || h <= 0) return null;
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            File tmpDir = new File("test-output/decoded/tmp");
            if (!tmpDir.exists()) Files.createDirectories(tmpDir.toPath());
            File out = new File(tmpDir, baseName(image) + String.format("_x%.0f.png", scale));
            ImageIO.write(dst, "png", out);
            out.deleteOnExit();
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
