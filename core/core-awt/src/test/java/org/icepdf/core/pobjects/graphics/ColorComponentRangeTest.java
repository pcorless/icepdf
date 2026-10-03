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

import static org.junit.jupiter.api.Assertions.*;

/**
 * A shading or tint-transform function can overshoot 0..1.  Handed straight to {@code new Color(float...)} that
 * throws IllegalArgumentException, and the whole gradient is dropped (2013_MTB_9001438_BOMBER.pdf, 18 per render).
 */
public class ColorComponentRangeTest {

    @DisplayName("a named Separation colour clamps an out-of-range tint")
    @Test
    public void separationTintIsClamped() {
        Separation red = new Separation(new Library(), new DictionaryEntries(), new Name("Red"),
                new Name("DeviceRGB"), null);
        assertEquals(new Color(255, 0, 0), assertDoesNotThrow(() -> red.getColor(new float[]{1.5f}, true)),
                "tint above 1 is full colour");
        assertEquals(Color.WHITE, assertDoesNotThrow(() -> red.getColor(new float[]{-0.5f}, true)),
                "tint below 0 is no colour");
        assertEquals(new Color(255, 128, 128), red.getColor(new float[]{0.5f}, true), "in-range tints are unchanged");
    }

    @DisplayName("CalRGB clamps out-of-range components")
    @Test
    public void calRgbComponentsAreClamped() {
        CalRGB calRgb = new CalRGB(new Library(), new DictionaryEntries());
        assertEquals(new Color(255, 0, 255),
                assertDoesNotThrow(() -> calRgb.getColor(new float[]{1.2f, -0.3f, 1.0f}, true)));
    }
}
