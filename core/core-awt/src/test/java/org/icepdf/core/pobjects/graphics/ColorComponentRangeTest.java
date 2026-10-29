/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A shading or tint-transform function can overshoot 0..1.  Handed straight to {@code new Color(float...)} that
 * throws IllegalArgumentException, and the whole gradient is dropped (2013_MTB_9001438_BOMBER.pdf, 18 per render).
 */
public class ColorComponentRangeTest {

    @DisplayName("a named Separation colour clamps an out-of-range tint")
    @Test
    public void separationTintIsClamped() {
        Separation black = new Separation(new Library(), new DictionaryEntries(), new Name("Black"),
                new Name("DeviceGray"), null);
        assertEquals(Color.BLACK, assertDoesNotThrow(() -> black.getColor(new float[]{1.5f}, true)),
                "tint above 1 is full colour");
        assertEquals(Color.WHITE, assertDoesNotThrow(() -> black.getColor(new float[]{-0.5f}, true)),
                "tint below 0 is no colour");
        assertEquals(new Color(128, 128, 128), black.getColor(new float[]{0.5f}, true),
                "in-range tints are unchanged");
    }

    @DisplayName("a Separation Cyan takes its tint transform and alternate, not the screen colour cyan")
    @Test
    public void separationCyanUsesAlternate() {
        Library library = new Library();
        DictionaryEntries tint = new DictionaryEntries();
        tint.put(new Name("FunctionType"), 2);
        tint.put(new Name("Domain"), Arrays.asList(0f, 1f));
        tint.put(new Name("C0"), Arrays.asList(0f, 0f, 0f, 0f));
        tint.put(new Name("C1"), Arrays.asList(1f, 0f, 0f, 0f));
        tint.put(new Name("N"), 1f);
        Separation cyan = new Separation(library, new DictionaryEntries(), new Name("Cyan"),
                new Name("DeviceCMYK"), tint);
        assertFalse(cyan.isNamedColor());
        // the chevron shading on Chevron_2010.pdf's cover: a 24% tint came out as (194, 255, 255)
        Color expected = new DeviceCMYK(library, null).getColor(new float[]{0.24f, 0f, 0f, 0f}, true);
        assertEquals(expected, cyan.getColor(new float[]{0.24f}, true));
    }

    @DisplayName("Separation Red, Blue, Brown and Auto take their tint transform and alternate, not a screen colour")
    @Test
    public void separationNamedColoursUseAlternate() {
        // the inks each name is usually given in DeviceCMYK; the screen colours were (255,0,0), (0,0,255),
        // (165,42,42) and black
        String[] names = {"Red", "Blue", "Brown", "Auto"};
        float[][] inks = {{0f, 1f, 1f, 0f}, {1f, 1f, 0f, 0f}, {0f, 0.6f, 1f, 0.5f}, {0f, 0f, 0f, 1f}};
        Library library = new Library();
        for (int i = 0; i < names.length; i++) {
            DictionaryEntries tint = new DictionaryEntries();
            tint.put(new Name("FunctionType"), 2);
            tint.put(new Name("Domain"), Arrays.asList(0f, 1f));
            tint.put(new Name("C0"), Arrays.asList(0f, 0f, 0f, 0f));
            tint.put(new Name("C1"), Arrays.asList(inks[i][0], inks[i][1], inks[i][2], inks[i][3]));
            tint.put(new Name("N"), 1f);
            Separation separation = new Separation(library, new DictionaryEntries(), new Name(names[i]),
                    new Name("DeviceCMYK"), tint);
            assertFalse(separation.isNamedColor(), names[i]);
            float[] half = {inks[i][0] / 2, inks[i][1] / 2, inks[i][2] / 2, inks[i][3] / 2};
            Color expected = new DeviceCMYK(library, null).getColor(half, true);
            assertEquals(expected, separation.getColor(new float[]{0.5f}, true), names[i]);
        }
    }

    @DisplayName("CalRGB clamps out-of-range components")
    @Test
    public void calRgbComponentsAreClamped() {
        CalRGB calRgb = new CalRGB(new Library(), new DictionaryEntries());
        assertEquals(new Color(255, 0, 255),
                assertDoesNotThrow(() -> calRgb.getColor(new float[]{1.2f, -0.3f, 1.0f}, true)));
    }
}
