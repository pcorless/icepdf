/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.graphics.images;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CMYK-sample preservation is process-global (decodes run on pool threads), and CMYK groups on
 * different pages render concurrently.  A save/restore of one flag interleaved badly: the first group
 * to finish switched preservation off under the second, and the second then restored it to "on" for
 * good, so every later CMYK decode kept a duplicate raster.
 */
public class PreserveCmykHoldTest {

    @Test
    public void overlappingGroupsKeepPreservationUntilTheLastOneEnds() {
        assertFalse(ImageUtility.isPreserveCmyk());
        ImageUtility.beginPreserveCmyk();   // page A's group
        ImageUtility.beginPreserveCmyk();   // page B's group
        ImageUtility.endPreserveCmyk();     // A finishes first
        assertTrue(ImageUtility.isPreserveCmyk(), "B is still rasterising");
        ImageUtility.endPreserveCmyk();     // B finishes
        assertFalse(ImageUtility.isPreserveCmyk(), "nothing may leave preservation stuck on");
    }

    @Test
    public void unbalancedEndDoesNotGoNegative() {
        ImageUtility.endPreserveCmyk();
        ImageUtility.beginPreserveCmyk();
        assertTrue(ImageUtility.isPreserveCmyk());
        ImageUtility.endPreserveCmyk();
        assertFalse(ImageUtility.isPreserveCmyk());
    }
}
