package com.jabcode.tools;

import com.jabcode.internal.JABCodeNative;
import org.bytedeco.javacpp.Loader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DecodeJABCodes {
    public static void main(String[] args) throws Exception {
        Loader.load(JABCodeNative.class);

        List<File> inputs = new ArrayList<>();
        if (args.length == 0) {
            inputs.add(new File("test-output/junit"));
            inputs.add(new File("test-output"));
            inputs.add(new File("palette"));
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

    private static void decodeOne(File image, File outDir) throws IOException {
        long bmp = JABCodeNative.readImagePtr(image.getPath());
        if (bmp == 0L) throw new IOException("readImagePtr returned 0");
        int[] status = new int[1];
        long dataPtr = JABCodeNative.decodeJABCodePtr(bmp, 0 /* NORMAL_DECODE */, status);
        if (dataPtr == 0L) throw new IOException("decodeJABCodePtr failed, status=" + status[0]);
        byte[] bytes = JABCodeNative.getDataBytes(dataPtr);
        if (bytes == null) throw new IOException("getDataBytes returned null");

        String base = image.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        File txt = new File(outDir, base + ".txt");
        File bin = new File(outDir, base + ".bin");

        try (FileOutputStream fos = new FileOutputStream(bin)) {
            fos.write(bytes);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(txt), StandardCharsets.UTF_8)) {
            w.write(new String(bytes, StandardCharsets.UTF_8));
        }
        System.out.println("OK: " + image.getPath() + " -> " + txt.getPath());
    }
}
