/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.images.jpx;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provenance: ORIGINAL (the requirement list is core/core-awt/tools/jpx-fixtures/IMAGEIO-CONTRACT.md).
 * <p>
 * The ImageIO plug-in contract a JPEG 2000 reader must meet to be a drop-in for ICEpdf and for
 * plain {@code ImageIO.read} users. Each check is PASS or FAIL and pinned per reader in
 * {@code baseline/contract-<reader>.tsv}: for JJ2000 the FAILs document where the old reader falls
 * short; for a new reader every line is expected to read PASS.
 */
public class Jpeg2000ImageReaderContractTest {

    private static final String RGB = "rev53-rgb.j2k";
    private static final String JP2 = "jp2-rgb.jp2";
    private static final String TILED = "tiles.j2k"; // 100x70, 32x32 tiles
    private static final String PALETTE = "jp2-pclr-rgb.jp2";

    private static final List<String> FORMAT_NAMES = Arrays.asList("jpeg2000", "jpeg 2000", "jp2", "j2k", "jpx");
    private static final List<String> SUFFIXES = Arrays.asList("jp2", "j2k", "j2c", "jpx", "jpf");
    private static final List<String> MIME_TYPES = Arrays.asList("image/jp2", "image/jpx");

    private static Jpeg2000Baseline baseline;
    private static final Map<String, Callable<String>> CHECKS = new LinkedHashMap<>();

    static {
        CHECKS.put("spi.format-names", () -> missing(spi().getFormatNames(), FORMAT_NAMES));
        CHECKS.put("spi.suffixes", () -> missing(spi().getFileSuffixes(), SUFFIXES));
        CHECKS.put("spi.mime-types", () -> missing(spi().getMIMETypes(), MIME_TYPES));
        CHECKS.put("lookup.by-format-name", () -> found(ImageIO.getImageReadersByFormatName("jpeg2000")));
        CHECKS.put("lookup.by-suffix", () -> found(ImageIO.getImageReadersBySuffix("jp2")));
        CHECKS.put("lookup.by-mime-type", () -> found(ImageIO.getImageReadersByMIMEType("image/jp2")));
        CHECKS.put("sniff.j2k", () -> sniff(RGB, true));
        CHECKS.put("sniff.jp2", () -> sniff(JP2, true));
        CHECKS.put("sniff.rejects-png", () -> sniffBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13}, false));
        CHECKS.put("sniff.rejects-jpeg", () -> sniffBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1}, false));
        CHECKS.put("reader.num-images", () -> withReader(RGB, r -> expect(1, r.getNumImages(true))));
        CHECKS.put("reader.size-before-read", () -> withReader(TILED, r -> expect("100x70", r.getWidth(0) + "x" + r.getHeight(0))));
        CHECKS.put("reader.image-types", () -> withReader(RGB, r -> {
            Iterator<ImageTypeSpecifier> it = r.getImageTypes(0);
            return it != null && it.hasNext() ? "ok" : fail("no image types");
        }));
        CHECKS.put("reader.raw-image-type", () -> withReader(RGB, r -> r.getRawImageType(0) != null ? "ok" : fail("null")));
        CHECKS.put("reader.can-read-raster", () -> withReader(RGB, r -> r.canReadRaster() ? "ok" : fail("false")));
        CHECKS.put("reader.read-raster", () -> withReader(RGB, r -> {
            Raster raster = r.readRaster(0, null);
            return expect("64x64x3", raster.getWidth() + "x" + raster.getHeight() + "x" + raster.getNumBands());
        }));
        CHECKS.put("reader.tiling-reported", () -> withReader(TILED, r ->
                expect("true 32x32", r.isImageTiled(0) + " " + r.getTileWidth(0) + "x" + r.getTileHeight(0))));
        CHECKS.put("reader.read-tile", () -> withReader(TILED, r -> {
            BufferedImage t = r.readTile(0, 1, 1);
            return expect("32x32", t.getWidth() + "x" + t.getHeight());
        }));
        CHECKS.put("reader.reusable-after-read", () -> withReader(RGB, r -> {
            r.read(0);
            BufferedImage second = r.read(0);
            return expect("64x64", second.getWidth() + "x" + second.getHeight());
        }));
        CHECKS.put("param.source-region", () -> withReader(TILED, r -> {
            ImageReadParam p = r.getDefaultReadParam();
            p.setSourceRegion(new Rectangle(10, 20, 50, 30));
            BufferedImage i = r.read(0, p);
            return expect("50x30", i.getWidth() + "x" + i.getHeight());
        }));
        CHECKS.put("param.source-subsampling", () -> withReader(TILED, r -> {
            ImageReadParam p = r.getDefaultReadParam();
            p.setSourceSubsampling(4, 4, 0, 0);
            BufferedImage i = r.read(0, p);
            return expect("25x18", i.getWidth() + "x" + i.getHeight());
        }));
        CHECKS.put("param.source-bands", () -> withReader(RGB, r -> {
            ImageReadParam p = r.getDefaultReadParam();
            p.setSourceBands(new int[]{1});
            p.setDestinationBands(new int[]{0});
            Raster raster = r.readRaster(0, p);
            return expect(1, raster.getNumBands());
        }));
        CHECKS.put("param.destination-offset", () -> withReader(RGB, r -> {
            ImageReadParam p = r.getDefaultReadParam();
            BufferedImage dest = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
            p.setDestination(dest);
            p.setDestinationOffset(new Point(8, 8));
            BufferedImage i = r.read(0, p);
            // getRGB of TYPE_INT_RGB always carries alpha FF: compare the colour only
            return i == dest && (dest.getRGB(0, 0) & 0xFFFFFF) == 0 && (dest.getRGB(8, 8) & 0xFFFFFF) != 0 ? "ok"
                    : fail("destination not used at the offset");
        }));
        CHECKS.put("listener.progress", () -> withReader(RGB, r -> {
            AtomicInteger started = new AtomicInteger(), completed = new AtomicInteger();
            r.addIIOReadProgressListener(new ProgressAdapter() {
                @Override
                public void imageStarted(ImageReader source, int imageIndex) {
                    started.incrementAndGet();
                }

                @Override
                public void imageComplete(ImageReader source) {
                    completed.incrementAndGet();
                }
            });
            r.read(0);
            return expect("1/1", started.get() + "/" + completed.get());
        }));
        CHECKS.put("listener.abort", () -> withReader(TILED, r -> {
            AtomicInteger aborted = new AtomicInteger();
            r.addIIOReadProgressListener(new ProgressAdapter() {
                @Override
                public void imageStarted(ImageReader source, int imageIndex) {
                    source.abort();
                }

                @Override
                public void readAborted(ImageReader source) {
                    aborted.incrementAndGet();
                }
            });
            r.read(0);
            return aborted.get() == 1 ? "ok" : fail("readAborted not reported");
        }));
        CHECKS.put("metadata.standard-tree", () -> withReader(JP2, r -> {
            IIOMetadata m = r.getImageMetadata(0);
            if (m == null) return fail("no image metadata");
            return m.isStandardMetadataFormatSupported() && m.getAsTree("javax_imageio_1.0") != null ? "ok"
                    : fail("standard format not supported");
        }));
        CHECKS.put("imageio.read-plain", () -> {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(Jpeg2000Fixtures.bytes(JP2)));
            return i == null ? fail("ImageIO.read returned null") : expect("64x64", i.getWidth() + "x" + i.getHeight());
        });
        CHECKS.put("imageio.read-applies-palette", () -> {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(Jpeg2000Fixtures.bytes(PALETTE)));
            if (i == null) return fail("ImageIO.read returned null");
            Jpeg2000Fixtures.Reference ref = Jpeg2000Fixtures.references().stream()
                    .filter(x -> x.name.equals("jp2-pclr-rgb")).findFirst().orElseThrow(IllegalStateException::new);
            List<Jpeg2000Fixtures.Component> c = Jpeg2000Fixtures.components(ref);
            int rgb = i.getRGB(9, 9) & 0xFFFFFF;
            int p = 9 * 64 + 9;
            int want = (c.get(0).samples[p] << 16) | (c.get(1).samples[p] << 8) | c.get(2).samples[p];
            return expect(Integer.toHexString(want), Integer.toHexString(rgb));
        });
    }

    static List<String> checks() {
        return new ArrayList<>(CHECKS.keySet());
    }

    @BeforeAll
    static void load() {
        baseline = new Jpeg2000Baseline("contract");
    }

    @AfterAll
    static void report() {
        baseline.write();
    }

    @DisplayName("ImageIO contract check matches the reader's baseline")
    @ParameterizedTest(name = "{0}")
    @MethodSource("checks")
    public void contract(String check) {
        assumeTrue(Jpeg2000Fixtures.readerSpi() != null, "no JPEG 2000 reader installed");
        String outcome, detail;
        try {
            detail = Jpeg2000Fixtures.withTimeout(CHECKS.get(check));
            outcome = "PASS";
        } catch (java.util.concurrent.TimeoutException te) {
            outcome = "FAIL";
            detail = "no result after " + Jpeg2000Fixtures.TIMEOUT_SECONDS + "s";
        } catch (CheckFailed f) {
            outcome = "FAIL";
            detail = f.getMessage();
        } catch (Throwable t) {
            outcome = "FAIL";
            detail = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
        baseline.record(check, outcome, detail);
        if (Jpeg2000Baseline.blessing()) return;
        String expected = baseline.expected(check);
        assertNotNull(expected, "no baseline entry for " + check + " (" + outcome + " " + detail
                + "); run with -Djpx.bless=true");
        assertEquals(expected, outcome, check + ": " + detail);
    }

    // ------------------------------------------------------------------------------------------

    private static final class CheckFailed extends RuntimeException {
        CheckFailed(String m) {
            super(m);
        }
    }

    private interface ReaderCheck {
        String run(ImageReader r) throws Exception;
    }

    private static String fail(String why) {
        throw new CheckFailed(why);
    }

    private static String expect(Object want, Object got) {
        if (!want.equals(got)) fail("expected " + want + ", got " + got);
        return String.valueOf(got);
    }

    private static ImageReaderSpi spi() {
        return Jpeg2000Fixtures.readerSpi();
    }

    private static String missing(String[] have, List<String> want) {
        List<String> lower = new ArrayList<>();
        for (String h : have == null ? new String[0] : have) lower.add(h.toLowerCase());
        List<String> miss = new ArrayList<>();
        for (String w : want) if (!lower.contains(w)) miss.add(w);
        return miss.isEmpty() ? String.join(",", lower) : fail("missing " + miss + " (has " + lower + ")");
    }

    private static String found(Iterator<ImageReader> it) {
        while (it.hasNext()) {
            if (it.next().getOriginatingProvider().getClass() == spi().getClass()) return "ok";
        }
        return fail("reader under test not returned");
    }

    private static String sniff(String fixture, boolean want) throws Exception {
        return sniffBytes(Jpeg2000Fixtures.bytes(fixture), want);
    }

    private static String sniffBytes(byte[] data, boolean want) throws Exception {
        try (ImageInputStream in = Jpeg2000Fixtures.stream(data)) {
            long before = in.getStreamPosition();
            boolean got = spi().canDecodeInput(in);
            long after = in.getStreamPosition();
            if (after != before) fail("canDecodeInput moved the stream from " + before + " to " + after);
            return expect(want, got);
        }
    }

    private static String withReader(String fixture, ReaderCheck check) throws Exception {
        try (ImageInputStream in = Jpeg2000Fixtures.stream(Jpeg2000Fixtures.bytes(fixture))) {
            ImageReader r = Jpeg2000Fixtures.reader(in);
            if (r == null) return fail("no reader claims " + fixture);
            try {
                r.setInput(in, false, false);
                return check.run(r);
            } finally {
                r.dispose();
            }
        }
    }

    private static class ProgressAdapter implements IIOReadProgressListener {
        public void sequenceStarted(ImageReader source, int minIndex) {
        }

        public void sequenceComplete(ImageReader source) {
        }

        public void imageStarted(ImageReader source, int imageIndex) {
        }

        public void imageProgress(ImageReader source, float percentageDone) {
        }

        public void imageComplete(ImageReader source) {
        }

        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
        }

        public void thumbnailProgress(ImageReader source, float percentageDone) {
        }

        public void thumbnailComplete(ImageReader source) {
        }

        public void readAborted(ImageReader source) {
        }
    }
}
