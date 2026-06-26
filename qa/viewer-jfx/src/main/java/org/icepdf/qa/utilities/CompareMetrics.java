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

/**
 * Immutable bag of similarity scores produced by {@link ImageCompare}.  All
 * scores are expressed as a percentage in the range [0, 100] where 100 means
 * the two images are identical.  Higher is more similar, matching the existing
 * {@code Result.difference} semantics used throughout the qa harness.
 *
 * <ul>
 *     <li>{@link #aeSimilarity} – fuzz-tolerant absolute-error over the whole
 *     page.  This is the classic metric (differing pixels / total pixels) but
 *     with a colour tolerance so sub-pixel anti-aliasing noise no longer
 *     counts.  It is still diluted by the large white background.</li>
 *     <li>{@link #inkSimilarity} – the same differing-pixel count, but measured
 *     against the union of "ink" (non-background) pixels rather than the whole
 *     page.  Missing content now shows up as a large fraction of the
 *     <em>content</em> instead of a tiny fraction of a mostly-white page, which
 *     is the headline regression number.</li>
 *     <li>{@link #structuralSimilarity} – mean windowed SSIM on luminance.  A
 *     local, perceptual measure that drops sharply where structure (text,
 *     lines) is lost even when that region is a small share of the page.</li>
 * </ul>
 */
public class CompareMetrics {

    private final double aeSimilarity;
    private final double inkSimilarity;
    private final double structuralSimilarity;

    private final int totalPixels;
    private final int unionInkPixels;
    private final int diffPixels;
    private final int diffInkPixels;

    public CompareMetrics(double aeSimilarity, double inkSimilarity, double structuralSimilarity,
                          int totalPixels, int unionInkPixels, int diffPixels, int diffInkPixels) {
        this.aeSimilarity = aeSimilarity;
        this.inkSimilarity = inkSimilarity;
        this.structuralSimilarity = structuralSimilarity;
        this.totalPixels = totalPixels;
        this.unionInkPixels = unionInkPixels;
        this.diffPixels = diffPixels;
        this.diffInkPixels = diffInkPixels;
    }

    public double getAeSimilarity() {
        return aeSimilarity;
    }

    public double getInkSimilarity() {
        return inkSimilarity;
    }

    public double getStructuralSimilarity() {
        return structuralSimilarity;
    }

    public int getTotalPixels() {
        return totalPixels;
    }

    public int getUnionInkPixels() {
        return unionInkPixels;
    }

    public int getDiffPixels() {
        return diffPixels;
    }

    public int getDiffInkPixels() {
        return diffInkPixels;
    }

    @Override
    public String toString() {
        return String.format("ink %.2f%%  ae %.2f%%  ssim %.2f%%  (%d/%d ink px differ)",
                inkSimilarity, aeSimilarity, structuralSimilarity, diffInkPixels, unionInkPixels);
    }
}
