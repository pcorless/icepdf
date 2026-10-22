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

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Provenance: ORIGINAL.
 * <p>
 * Access to the generated JPEG 2000 fixtures in {@code src/test/resources/jpx} (see
 * {@code core/core-awt/tools/jpx-fixtures/README.md}) and a reader-agnostic comparison of an ImageIO decode
 * against the OpenJPEG reference components.
 * <p>
 * The reader under test is the first JPEG 2000 {@link ImageReader} that ImageIO offers, or the one
 * whose SPI class name contains the {@code jpx.reader} system property.
 */
public final class Jpeg2000Fixtures {

    public static final String ROOT = "/jpx/";

    private Jpeg2000Fixtures() {
    }

    /** One row of references.tsv: a fixture plus one decode of it. */
    public static final class Reference {
        public final String name;
        public final String file;
        public final String tag;
        public final int components;
        public final int reduce;
        public final int[] region;
        public final boolean lossless;

        Reference(String[] f) {
            name = f[0];
            file = f[1];
            tag = f[2];
            components = Integer.parseInt(f[3]);
            reduce = Integer.parseInt(f[4]);
            if (f[5].isEmpty()) {
                region = null;
            } else {
                String[] r = f[5].split(",");
                region = new int[]{Integer.parseInt(r[0]), Integer.parseInt(r[1]),
                        Integer.parseInt(r[2]), Integer.parseInt(r[3])};
            }
            lossless = "lossless".equals(f[6]);
        }

        public String id() {
            return name + "." + tag;
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /** A decoded reference component. */
    public static final class Component {
        public final int width;
        public final int height;
        public final int precision;
        public final boolean signed;
        public final int[] samples;

        Component(int width, int height, int precision, boolean signed, int[] samples) {
            this.width = width;
            this.height = height;
            this.precision = precision;
            this.signed = signed;
            this.samples = samples;
        }
    }

    public static List<Reference> references() {
        List<Reference> out = new ArrayList<>();
        for (String line : text("references.tsv").split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            out.add(new Reference(line.split("\t", -1)));
        }
        return out;
    }

    public static List<String> hostile() {
        List<String> out = new ArrayList<>();
        for (String line : text("hostile.txt").split("\n")) {
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    public static byte[] bytes(String path) {
        try (InputStream in = Jpeg2000Fixtures.class.getResourceAsStream(ROOT + path)) {
            if (in == null) throw new IllegalArgumentException("no fixture " + path);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String text(String path) {
        return new String(bytes(path), StandardCharsets.US_ASCII);
    }

    public static List<Component> components(Reference ref) {
        List<Component> out = new ArrayList<>();
        for (int c = 0; c < ref.components; c++) {
            out.add(pgx(bytes("ref/" + ref.id() + "." + c + ".pgx")));
        }
        return out;
    }

    /** "PG ML <+|-> prec w h\n" then big-endian samples (1, 2 or 4 bytes). */
    static Component pgx(byte[] b) {
        int nl = 0;
        while (b[nl] != '\n') nl++;
        String[] h = new String(b, 0, nl, StandardCharsets.US_ASCII).trim().split("\\s+");
        boolean signed = "-".equals(h[2]);
        int prec = Integer.parseInt(h[3]);
        int w = Integer.parseInt(h[4]);
        int hh = Integer.parseInt(h[5]);
        int n = prec <= 8 ? 1 : prec <= 16 ? 2 : 4;
        int[] s = new int[w * hh];
        int p = nl + 1;
        for (int i = 0; i < s.length; i++) {
            int v = 0;
            for (int k = 0; k < n; k++) v = (v << 8) | (b[p++] & 0xff);
            if (signed && n < 4) v = (v << (32 - 8 * n)) >> (32 - 8 * n);
            s[i] = v;
        }
        return new Component(w, hh, prec, signed, s);
    }

    // ------------------------------------------------------------------------------------------
    // reader selection
    // ------------------------------------------------------------------------------------------

    public static ImageInputStream stream(byte[] data) throws IOException {
        return ImageIO.createImageInputStream(new ByteArrayInputStream(data));
    }

    /** The reader under test for this stream, or null when no JPEG 2000 reader is installed. */
    public static ImageReader reader(ImageInputStream in) {
        String want = System.getProperty("jpx.reader", "");
        Iterator<ImageReader> it = ImageIO.getImageReaders(in);
        while (it.hasNext()) {
            ImageReader r = it.next();
            if (want.isEmpty() || r.getOriginatingProvider().getClass().getName().contains(want)) {
                return r;
            }
        }
        return null;
    }

    /** The reader under test, by format name: used to label baselines. */
    public static ImageReaderSpi readerSpi() {
        String want = System.getProperty("jpx.reader", "");
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("jpeg2000");
        while (it.hasNext()) {
            ImageReaderSpi spi = it.next().getOriginatingProvider();
            if (want.isEmpty() || spi.getClass().getName().contains(want)) return spi;
        }
        return null;
    }

    /** Short, file-name-safe label for the reader under test, e.g. "j2kimagereaderspi". */
    public static String readerLabel() {
        ImageReaderSpi spi = readerSpi();
        return spi == null ? "none" : spi.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------------
    // comparison
    // ------------------------------------------------------------------------------------------

    /** How a decode relates to the reference. Ordered best to worst. */
    public enum Outcome {
        /** every sample equal */
        EXACT,
        /** equal after a documented representation difference (see {@link Result#notes}) */
        EQUIVALENT,
        /** lossy stream within tolerance (max error at 8-bit scale <= 4) */
        CLOSE,
        /** decoded, but the samples or geometry differ */
        MISMATCH,
        /** the reader threw */
        ERROR,
        /** the reader did not finish within {@link #TIMEOUT_SECONDS} (its thread is abandoned) */
        TIMEOUT
    }

    public static final long TIMEOUT_SECONDS = 10;

    /**
     * Runs a decode on a daemon thread and gives up after {@link #TIMEOUT_SECONDS}. A reader that
     * loops forever can't be stopped, so its thread is left spinning until the test JVM exits; the
     * caller records TIMEOUT and moves on.
     */
    public static <T> T withTimeout(java.util.concurrent.Callable<T> task) throws Exception {
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newSingleThreadExecutor(run -> {
            Thread t = new Thread(run, "jpx-decode");
            t.setDaemon(true);
            return t;
        });
        java.util.concurrent.Future<T> f = ex.submit(task);
        try {
            return f.get(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        } finally {
            f.cancel(true);
            ex.shutdownNow();
        }
    }

    public static final class Result {
        public Outcome outcome;
        public final List<String> notes = new ArrayList<>();
        public long maxDiff;
        public double psnr = Double.POSITIVE_INFINITY;
        public String error;

        public String summary() {
            StringBuilder sb = new StringBuilder(outcome.name());
            if (error != null) sb.append(" ").append(error);
            if (outcome != Outcome.ERROR && maxDiff > 0) {
                sb.append(String.format(Locale.ROOT, " max=%d psnr=%.1f", maxDiff, psnr));
            }
            if (!notes.isEmpty()) sb.append(" [").append(String.join("; ", notes)).append("]");
            return sb.toString();
        }
    }

    /** Decodes the fixture the way ICEpdf does (read(0, param).getRaster()) and compares. */
    public static Result decodeAndCompare(Reference ref) {
        Result r = new Result();
        Object[] decoded;
        try {
            decoded = withTimeout(() -> decode(ref));
        } catch (java.util.concurrent.TimeoutException te) {
            r.outcome = Outcome.TIMEOUT;
            r.error = "no result after " + TIMEOUT_SECONDS + "s";
            return r;
        } catch (Throwable t) {
            r.outcome = Outcome.ERROR;
            r.error = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            return r;
        }
        if (decoded == null) {
            r.outcome = Outcome.ERROR;
            r.error = "no reader claims the stream";
            return r;
        }
        compare(ref, components(ref), (Raster) decoded[0], (ColorModel) decoded[1], r);
        return r;
    }

    private static Object[] decode(Reference ref) throws IOException {
        try (ImageInputStream in = stream(bytes(ref.file))) {
            ImageReader reader = reader(in);
            if (reader == null) return null;
            try {
                reader.setInput(in, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                if (ref.reduce > 0) {
                    int s = 1 << ref.reduce;
                    param.setSourceSubsampling(s, s, 0, 0);
                }
                if (ref.region != null) {
                    param.setSourceRegion(new Rectangle(ref.region[0], ref.region[1],
                            ref.region[2] - ref.region[0], ref.region[3] - ref.region[1]));
                }
                BufferedImage image = reader.read(0, param);
                return new Object[]{image.getRaster(), image.getColorModel()};
            } finally {
                reader.dispose();
            }
        }
    }

    public static void compare(Reference ref, List<Component> comps, Raster raster, ColorModel cm, Result r) {
        boolean equivalent = false;
        int bands = raster.getNumBands();
        int[][] values;
        if (bands == 1 && comps.size() > 1 && cm instanceof IndexColorModel) {
            // the reader kept the palette indices and put the palette in the colour model
            IndexColorModel icm = (IndexColorModel) cm;
            values = expandPalette(raster, icm, comps.size());
            if (values == null) {
                r.outcome = Outcome.MISMATCH;
                r.notes.add("palette has " + icm.getNumComponents() + " channels, reference " + comps.size());
                return;
            }
            r.notes.add("palette applied through IndexColorModel");
            equivalent = true;
            bands = comps.size();
        } else {
            values = null;
        }
        if (bands != comps.size()) {
            r.outcome = Outcome.MISMATCH;
            r.notes.add(bands + " bands, reference has " + comps.size() + " components");
            return;
        }
        long sumSq = 0, count = 0, max = 0;
        int maxPrec = 0;
        boolean geometryNoted = false;
        for (int c = 0; c < comps.size(); c++) {
            Component comp = comps.get(c);
            maxPrec = Math.max(maxPrec, comp.precision);
            int sx = 1, sy = 1;
            if (raster.getWidth() != comp.width || raster.getHeight() != comp.height) {
                // a reader that upsamples subsampled components: compare on the component's grid
                if (raster.getWidth() % comp.width == 0 && raster.getHeight() % comp.height == 0
                        && raster.getWidth() / comp.width <= 4 && raster.getHeight() / comp.height <= 4) {
                    sx = raster.getWidth() / comp.width;
                    sy = raster.getHeight() / comp.height;
                    if (!geometryNoted) {
                        r.notes.add("component " + c + " upsampled " + sx + "x" + sy);
                        geometryNoted = true;
                    }
                    equivalent = true;
                } else {
                    r.outcome = Outcome.MISMATCH;
                    r.notes.add("size " + raster.getWidth() + "x" + raster.getHeight() + ", reference component "
                            + c + " is " + comp.width + "x" + comp.height);
                    return;
                }
            }
            int sampleBits = raster.getSampleModel().getSampleSize(Math.min(c, raster.getNumBands() - 1));
            int shift = values == null && sampleBits < comp.precision ? comp.precision - sampleBits : 0;
            int lift = values == null && sampleBits > comp.precision && sampleBits <= 16
                    && !comp.signed && rescaledUp(raster, c, comp) ? sampleBits - comp.precision : 0;
            if (shift > 0) {
                r.notes.add("component " + c + " " + comp.precision + "-bit returned as " + sampleBits + "-bit");
                equivalent = true;
            }
            if (lift > 0) {
                r.notes.add("component " + c + " " + comp.precision + "-bit scaled up to " + sampleBits + "-bit");
                equivalent = true;
            }
            // signed samples: returned as-is, level-shifted into an unsigned range, or as their
            // two's-complement bits in an unsigned container. Pick whichever the first row matches.
            int signOffset = 0;
            int bitsMask = -1;
            if (comp.signed && values == null) {
                int n = Math.min(comp.width, 64);
                int shifted = 1 << (comp.precision - 1);
                int mask = sampleBits >= 32 ? -1 : (1 << sampleBits) - 1;
                boolean asIs = true, levelShift = true, bits = true;
                for (int x = 0; x < n; x++) {
                    int got = raster.getSample(raster.getMinX() + x * sx, raster.getMinY(), c);
                    int want = comp.samples[x];
                    asIs &= got == want;
                    levelShift &= got == want + shifted;
                    bits &= got == (want & mask);
                }
                if (!asIs && levelShift) {
                    signOffset = shifted;
                    r.notes.add("component " + c + " signed samples level-shifted to unsigned");
                    equivalent = true;
                } else if (!asIs && bits) {
                    bitsMask = mask;
                    r.notes.add("component " + c + " signed samples as two's-complement bits in a "
                            + sampleBits + "-bit unsigned raster");
                    equivalent = true;
                }
            }
            for (int y = 0; y < comp.height; y++) {
                for (int x = 0; x < comp.width; x++) {
                    int want = (comp.samples[y * comp.width + x] + signOffset) & bitsMask;
                    int got;
                    if (values != null) {
                        got = values[c][(y * sy) * raster.getWidth() + x * sx];
                    } else {
                        got = raster.getSample(raster.getMinX() + x * sx, raster.getMinY() + y * sy, c);
                    }
                    if (shift > 0) want >>= shift;
                    if (lift > 0) got >>= lift;
                    long d = Math.abs((long) got - want);
                    max = Math.max(max, d);
                    sumSq += d * d;
                    count++;
                }
            }
        }
        r.maxDiff = max;
        int prec = Math.min(maxPrec, 16);
        double peak = (1L << prec) - 1;
        r.psnr = sumSq == 0 ? Double.POSITIVE_INFINITY : 10 * Math.log10(peak * peak / ((double) sumSq / count));
        double max8 = max / (double) (1L << Math.max(0, prec - 8));
        if (max == 0) {
            r.outcome = equivalent ? Outcome.EQUIVALENT : Outcome.EXACT;
        } else if (!ref.lossless && max8 <= 4) {
            r.outcome = Outcome.CLOSE;
        } else {
            r.outcome = Outcome.MISMATCH;
        }
    }

    /** True when a wider raster holds the samples bit-replicated or shifted up (e.g. 12 -> 16). */
    private static boolean rescaledUp(Raster raster, int band, Component comp) {
        int max = 0;
        for (int y = 0; y < Math.min(raster.getHeight(), comp.height); y++) {
            for (int x = 0; x < Math.min(raster.getWidth(), comp.width); x++) {
                max = Math.max(max, raster.getSample(raster.getMinX() + x, raster.getMinY() + y, band));
            }
        }
        return max >= (1 << comp.precision);
    }

    private static int[][] expandPalette(Raster raster, IndexColorModel icm, int channels) {
        if (channels != 3 && !(channels == 4 && icm.hasAlpha())) return null;
        int w = raster.getWidth(), h = raster.getHeight();
        int[][] out = new int[channels][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = raster.getSample(raster.getMinX() + x, raster.getMinY() + y, 0);
                int p = y * w + x;
                out[0][p] = icm.getRed(i);
                out[1][p] = icm.getGreen(i);
                out[2][p] = icm.getBlue(i);
                if (channels == 4) out[3][p] = icm.getAlpha(i);
            }
        }
        return out;
    }
}
