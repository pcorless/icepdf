/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.fonts;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.SfntProgram;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /FontFile3 /Type1C should hold bare CFF, but some producers embed a whole TrueType sfnt under it.  The CFF parser
 * refused it ("OpenType fonts containing a true type font are not supported") and the font fell back to a
 * substitute with the wrong glyphs and spacing (retrait_sea.pdf, four fonts).
 */
public class FontFile3SfntRoutingTest {

    private static final Path ROBOTO = Path.of("../core-fonts/src/main/resources/org/icepdf/core/fonts/Roboto-Regular.ttf");

    @DisplayName("a TrueType program declared /Type1C loads as TrueType")
    @Test
    public void trueTypeDeclaredAsType1CLoadsAsTrueType() throws Exception {
        byte[] trueType = Files.readAllBytes(ROBOTO);
        assertTrue(SfntProgram.isTrueTypeOutlines(trueType));
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Subtype"), new Name("Type1C"));
        Stream fontStream = new Stream(new Library(), entries, trueType);

        FontFile font = FontFactory.getInstance().createFontFile(fontStream, FontFactory.FONT_TYPE_1C, new Name("Type1C"));

        assertInstanceOf(ZFontTrueType.class, font);
        assertEquals("Roboto-Regular", font.getName());
    }

    @DisplayName("sfnt sniffing tells TrueType, OpenType/CFF and bare CFF apart")
    @Test
    public void sniffing() throws Exception {
        byte[] trueType = Files.readAllBytes(ROBOTO);
        assertTrue(SfntProgram.isTrueTypeOutlines(trueType));
        assertFalse(SfntProgram.isOpenTypeCff(trueType));

        byte[] otto = {'O', 'T', 'T', 'O', 0, 0, 0, 0, 0, 0, 0, 0};
        assertTrue(SfntProgram.isOpenTypeCff(otto));
        assertFalse(SfntProgram.isTrueTypeOutlines(otto));

        // a bare CFF program starts with its header (major version 1), not an sfnt version
        byte[] bareCff = {1, 0, 4, 1, 0, 1, 1, 1, 0, 0, 0, 0};
        assertFalse(SfntProgram.isTrueTypeOutlines(bareCff));
        assertFalse(SfntProgram.isOpenTypeCff(bareCff));
        assertFalse(SfntProgram.isTrueTypeOutlines(null));
    }
}
