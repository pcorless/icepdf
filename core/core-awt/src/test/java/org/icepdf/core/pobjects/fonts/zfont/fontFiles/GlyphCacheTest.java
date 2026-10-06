/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.icepdf.core.pobjects.fonts.FontFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-535 caches glyph outlines per font instance.  The cache is only correct while three things hold:
 * a repeat lookup reuses the outline, a derived font (which may map codes to different glyphs) never
 * sees its parent's cache, and a glyph that fails is remembered as empty rather than retried.
 */
public class GlyphCacheTest {

    private static ZFontTrueType font;

    @BeforeAll
    public static void loadFont() throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("../core-fonts/src/main/resources/org/icepdf/core/fonts/Roboto-Regular.ttf"));
        font = new ZFontTrueType(bytes);
    }

    @Test
    public void repeatLookupsReuseTheOutline() {
        GlyphCache cache = font.getGlyphCache();
        Shape first = cache.getPathForCharacterCode('A');
        assertNotNull(first);
        assertSame(first, cache.getPathForCharacterCode('A'));
        assertSame(cache, font.getGlyphCache(), "the cache is created once per instance");
    }

    @Test
    public void derivedFontGetsItsOwnCache() {
        font.getGlyphCache().getPathForCharacterCode('A');
        FontFile derived = font.deriveFont(new float[]{500}, 65, 500, 0, 0, null, null);
        assertTrue(derived instanceof ZSimpleFont);
        assertNotSame(font.getGlyphCache(), ((ZSimpleFont) derived).getGlyphCache(),
                "a derived font's encoding may differ, so it must not read its parent's outlines");
    }

    @Test
    public void failingGlyphIsCachedAsEmpty() throws Exception {
        int[] calls = {0};
        ZFontTrueType failing = new ZFontTrueType(font) {
            @Override
            public Shape getGlphyShape(char estr) {
                calls[0]++;
                throw new IllegalStateException("no glyf table");
            }
        };
        Shape outline = failing.getGlyphCache().getPathForCharacterCode('x');
        assertTrue(outline.getBounds2D().isEmpty());
        failing.getGlyphCache().getPathForCharacterCode('x');
        assertEquals(1, calls[0], "a bad glyph should cost one throw, not one per paint");
    }

    @Test
    public void concurrentLookupsAgreeWithSerial() throws Exception {
        ZFontTrueType fresh = new ZFontTrueType(font);
        List<Rectangle2D> expected = new ArrayList<>();
        ZFontTrueType serial = new ZFontTrueType(font);
        for (char c = 32; c < 256; c++) {
            expected.add(serial.getGlyphCache().getPathForCharacterCode(c).getBounds2D());
        }
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Rectangle2D>>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    List<Rectangle2D> bounds = new ArrayList<>();
                    for (char c = 32; c < 256; c++) {
                        bounds.add(fresh.getGlyphCache().getPathForCharacterCode(c).getBounds2D());
                    }
                    return bounds;
                }));
            }
            for (Future<List<Rectangle2D>> result : results) {
                assertEquals(expected, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
