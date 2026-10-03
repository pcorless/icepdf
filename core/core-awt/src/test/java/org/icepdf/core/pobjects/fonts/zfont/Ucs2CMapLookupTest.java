/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.cmap.CMap;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Type0 font without /ToUnicode falls back to its character collection's {@code <Registry>-<Ordering>-UCS2}
 * CMap.  Only the Adobe CJK collections have one; asking for anything else ({@code Adobe-UCS-UCS2} turned up in
 * the QA logs) used to log a SEVERE stack trace for every font instance and achieve nothing.
 */
public class Ucs2CMapLookupTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final Logger[] loggers = {Logger.getLogger(CMapFactory.class.getName()),
            Logger.getLogger(CompositeFont.class.getName())};

    @BeforeEach
    public void captureLogs() {
        for (Logger logger : loggers) {
            logger.addHandler(capture);
        }
    }

    @AfterEach
    public void releaseLogs() {
        for (Logger logger : loggers) {
            logger.removeHandler(capture);
        }
    }

    @DisplayName("the Adobe CJK collections still get their UCS2 CMap")
    @Test
    public void cjkCollectionsResolve() {
        for (String ordering : new String[]{"Japan1", "GB1", "CNS1", "Korea1"}) {
            CMap ucs2 = font("Adobe", ordering).getUcs2CMap();
            assertNotNull(ucs2, "Adobe-" + ordering + "-UCS2 should load");
        }
        assertTrue(warningsOrWorse().isEmpty(), warningsOrWorse().toString());
    }

    @DisplayName("collections without a UCS2 CMap are not looked up, and log nothing")
    @Test
    public void otherCollectionsAreNotLookedUp() {
        String[][] collections = {{"Adobe", "UCS"}, {"Adobe", "Identity"}, {"Adobe", "Identity-H"},
                {"Acme", "Japan1"}, {"Adobe", "KR"}, {"Adobe", "Custom"}};
        for (String[] collection : collections) {
            assertNull(font(collection[0], collection[1]).getUcs2CMap(), String.join("-", collection));
        }
        assertTrue(warningsOrWorse().isEmpty(), "unexpected log noise: " + warningsOrWorse());
    }

    @DisplayName("a predefined CMap that doesn't exist is reported once, without a stack trace")
    @Test
    public void missingPredefinedCMapIsReportedOnce() {
        String name = "Adobe-NoSuchCollection-UCS2-" + System.nanoTime();
        assertNull(CMapFactory.getPredefinedCMap(name));
        assertNull(CMapFactory.getPredefinedCMap(name));
        assertNull(CMapFactory.getPredefinedCMap(name));
        List<LogRecord> warnings = warningsOrWorse();
        assertEquals(1, warnings.size(), "should warn once per name: " + warnings);
        assertEquals(Level.WARNING, warnings.get(0).getLevel(), "a missing CMap is not SEVERE");
        assertNull(warnings.get(0).getThrown(), "no stack trace at warning level");
    }

    @DisplayName("a predefined CMap is cached under the name it was asked for")
    @Test
    public void predefinedCMapIsCached() {
        CMap first = CMapFactory.getPredefinedCMap("Adobe-Japan1-UCS2");
        assertNotNull(first);
        assertSame(first, CMapFactory.getPredefinedCMap("Adobe-Japan1-UCS2"));
    }

    private List<LogRecord> warningsOrWorse() {
        List<LogRecord> result = new ArrayList<>();
        for (LogRecord record : records) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                result.add(record);
            }
        }
        return result;
    }

    /** A CID font that names the given character collection, without going through font loading. */
    private static CompositeFont font(String registry, String ordering) {
        TypeCidType2Font font = new TypeCidType2Font(new Library(), new DictionaryEntries());
        font.registry = registry;
        font.ordering = ordering;
        return font;
    }
}
