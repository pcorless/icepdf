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
package org.icepdf.qa.utilities;

import java.awt.image.BufferedImage;

/**
 * Pure (no JavaFX) image comparison engine shared by the batch
 * {@code ImageCompareTask} and the interactive compare pane.  Because the
 * batch score and the on-screen diff mask are produced by the exact same code
 * path, the number recorded in the results table and the highlight the user
 * sees behind the fuzz slider can never disagree.
 * <p>
 * Two design choices address the "mostly-white page" blind spot of a naive
 * pixel-equality count:
 * <ol>
 *     <li><b>Fuzz tolerance</b> – two pixels are considered equal when no
 *     colour channel differs by more than {@code fuzz} (0-255).  This discards
 *     the 1-LSB anti-aliasing noise along glyph edges that a different
 *     rasteriser produces but a human cannot see.</li>
 *     <li><b>Ink weighting</b> – differences are also measured against the
 *     union of non-background pixels, so a dropped paragraph scores as a large
 *     fraction of the content rather than a tiny fraction of the page.</li>
 * </ol>
 */
public final class ImageCompare {

    /** Per-channel colour tolerance (0-255). A pixel pair within this distance is "equal". */
    public static final int DEFAULT_FUZZ = 8;

    /**
     * Luminance at or above this value (0-255) is treated as background and
     * ignored by the ink-weighted metric. 250 keeps faint anti-aliased greys
     * as ink while discarding pure/near white paper.
     */
    public static final int BACKGROUND_LUMINANCE = 250;

    // SSIM stabilisation constants for an 8-bit dynamic range (L = 255).
    private static final double C1 = (0.01 * 255) * (0.01 * 255);
    private static final double C2 = (0.03 * 255) * (0.03 * 255);
    private static final int SSIM_WINDOW = 8;

    private ImageCompare() {
    }

    /**
     * Compares two images and returns the full set of similarity scores.
     *
     * @param a    first image
     * @param b    second image
     * @param fuzz per-channel colour tolerance, 0-255 (see {@link #DEFAULT_FUZZ})
     * @return populated metrics, never {@code null}
     */
    public static CompareMetrics compare(BufferedImage a, BufferedImage b, int fuzz) {
        return compare(a, b, fuzz, structuralSimilarity(a, b));
    }

    /**
     * Compares two images using a pre-computed structural similarity score.
     * Only the fuzz-dependent metrics (AE, ink, diff mask counts) are
     * recalculated here, so an interactive fuzz slider can re-score on every
     * tick without repeating the comparatively expensive, fuzz-independent SSIM
     * pass.
     *
     * @param a                        first image
     * @param b                        second image
     * @param fuzz                     per-channel colour tolerance, 0-255
     * @param structuralSimilarityPercent SSIM (%) from {@link #structuralSimilarity}
     * @return populated metrics, never {@code null}
     */
    public static CompareMetrics compare(BufferedImage a, BufferedImage b, int fuzz,
                                         double structuralSimilarityPercent) {
        // Use the union of the two canvases; any area present in one image but
        // not the other is, by definition, a difference (and ink).
        int width = Math.max(a.getWidth(), b.getWidth());
        int height = Math.max(a.getHeight(), b.getHeight());

        int diffPixels = 0;
        int unionInkPixels = 0;
        int diffInkPixels = 0;

        int[] rowA = new int[width];
        int[] rowB = new int[width];

        for (int y = 0; y < height; y++) {
            readRow(a, y, rowA);
            readRow(b, y, rowB);
            for (int x = 0; x < width; x++) {
                int argbA = rowA[x];
                int argbB = rowB[x];

                boolean differ = !equalWithinFuzz(argbA, argbB, fuzz);
                if (differ) {
                    diffPixels++;
                }

                boolean ink = isInk(argbA) || isInk(argbB);
                if (ink) {
                    unionInkPixels++;
                    if (differ) {
                        diffInkPixels++;
                    }
                }
            }
        }

        int totalPixels = width * height;
        double aeSimilarity = totalPixels == 0
                ? 100d : 100d * (totalPixels - diffPixels) / totalPixels;
        double inkSimilarity = unionInkPixels == 0
                ? 100d : 100d * (unionInkPixels - diffInkPixels) / unionInkPixels;

        return new CompareMetrics(aeSimilarity, inkSimilarity, structuralSimilarityPercent,
                totalPixels, unionInkPixels, diffPixels, diffInkPixels);
    }

    /**
     * Mean windowed structural similarity, expressed as a percentage in
     * [0, 100].  This is fuzz-independent, so callers driving an interactive
     * fuzz slider can compute it once per image pair and feed it back into
     * {@link #compare(BufferedImage, BufferedImage, int, double)}.
     */
    public static double structuralSimilarity(BufferedImage a, BufferedImage b) {
        return meanSsim(a, b) * 100d;
    }

    /**
     * Builds a transparent overlay the size of the union canvas with every
     * differing pixel painted in {@code highlightArgb}.  Matching pixels are
     * left fully transparent so the mask can be drawn over either source image.
     * Used by the interactive fuzz slider.
     *
     * @param a             first image
     * @param b             second image
     * @param fuzz          per-channel colour tolerance, 0-255
     * @param highlightArgb packed ARGB colour for differing pixels
     * @return an ARGB overlay image
     */
    public static BufferedImage differenceMask(BufferedImage a, BufferedImage b, int fuzz, int highlightArgb) {
        int width = Math.max(a.getWidth(), b.getWidth());
        int height = Math.max(a.getHeight(), b.getHeight());
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int[] rowA = new int[width];
        int[] rowB = new int[width];
        int[] rowOut = new int[width];

        for (int y = 0; y < height; y++) {
            readRow(a, y, rowA);
            readRow(b, y, rowB);
            for (int x = 0; x < width; x++) {
                rowOut[x] = equalWithinFuzz(rowA[x], rowB[x], fuzz) ? 0 : highlightArgb;
            }
            mask.setRGB(0, y, width, 1, rowOut, 0, 1);
        }
        return mask;
    }

    /**
     * Reads one row of ARGB pixels into {@code dest}, zero-filling any portion
     * of the row that lies outside the image (so a smaller image reads as a
     * transparent/background region rather than throwing).
     */
    private static void readRow(BufferedImage image, int y, int[] dest) {
        int w = image.getWidth();
        if (y < image.getHeight() && w > 0) {
            image.getRGB(0, y, w, 1, dest, 0, w);
        } else {
            w = 0;
        }
        for (int x = w; x < dest.length; x++) {
            dest[x] = 0;
        }
    }

    /** True when no colour channel of the two packed ARGB pixels differs by more than {@code fuzz}. */
    private static boolean equalWithinFuzz(int argbA, int argbB, int fuzz) {
        if (argbA == argbB) {
            return true;
        }
        // Flatten any transparency onto white so a transparent pixel and a
        // white pixel are treated as the same background.
        int a = flattenOntoWhite(argbA);
        int b = flattenOntoWhite(argbB);
        if (a == b) {
            return true;
        }
        int dr = Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff));
        int dg = Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff));
        int db = Math.abs((a & 0xff) - (b & 0xff));
        return dr <= fuzz && dg <= fuzz && db <= fuzz;
    }

    /** A pixel is "ink" when its luminance is below the background threshold. */
    private static boolean isInk(int argb) {
        return luminance(flattenOntoWhite(argb)) < BACKGROUND_LUMINANCE;
    }

    /** Rec. 601 luma of an opaque packed RGB pixel. */
    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /** Alpha-composite a packed ARGB pixel over an opaque white background. */
    private static int flattenOntoWhite(int argb) {
        int alpha = (argb >> 24) & 0xff;
        if (alpha == 0xff) {
            return argb & 0xffffff;
        }
        if (alpha == 0) {
            return 0xffffff;
        }
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        int inv = 255 - alpha;
        r = (r * alpha + 255 * inv) / 255;
        g = (g * alpha + 255 * inv) / 255;
        b = (b * alpha + 255 * inv) / 255;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Mean SSIM over non-overlapping {@value #SSIM_WINDOW}x{@value #SSIM_WINDOW}
     * luminance windows of the overlapping region.  Returns a value in [0, 1].
     */
    private static double meanSsim(BufferedImage a, BufferedImage b) {
        int width = Math.min(a.getWidth(), b.getWidth());
        int height = Math.min(a.getHeight(), b.getHeight());
        if (width < SSIM_WINDOW || height < SSIM_WINDOW) {
            return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() ? 1d : 0d;
        }
        double[][] lumA = luminanceField(a, width, height);
        double[][] lumB = luminanceField(b, width, height);

        double ssimSum = 0;
        int windows = 0;
        for (int wy = 0; wy + SSIM_WINDOW <= height; wy += SSIM_WINDOW) {
            for (int wx = 0; wx + SSIM_WINDOW <= width; wx += SSIM_WINDOW) {
                ssimSum += windowSsim(lumA, lumB, wx, wy);
                windows++;
            }
        }
        return windows == 0 ? 1d : ssimSum / windows;
    }

    private static double windowSsim(double[][] a, double[][] b, int ox, int oy) {
        int n = SSIM_WINDOW * SSIM_WINDOW;
        double sumA = 0, sumB = 0;
        for (int y = 0; y < SSIM_WINDOW; y++) {
            for (int x = 0; x < SSIM_WINDOW; x++) {
                sumA += a[oy + y][ox + x];
                sumB += b[oy + y][ox + x];
            }
        }
        double meanA = sumA / n;
        double meanB = sumB / n;

        double varA = 0, varB = 0, cov = 0;
        for (int y = 0; y < SSIM_WINDOW; y++) {
            for (int x = 0; x < SSIM_WINDOW; x++) {
                double da = a[oy + y][ox + x] - meanA;
                double db = b[oy + y][ox + x] - meanB;
                varA += da * da;
                varB += db * db;
                cov += da * db;
            }
        }
        varA /= (n - 1);
        varB /= (n - 1);
        cov /= (n - 1);

        return ((2 * meanA * meanB + C1) * (2 * cov + C2))
                / ((meanA * meanA + meanB * meanB + C1) * (varA + varB + C2));
    }

    private static double[][] luminanceField(BufferedImage image, int width, int height) {
        double[][] lum = new double[height][width];
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                lum[y][x] = luminance(flattenOntoWhite(row[x]));
            }
        }
        return lum;
    }
}
