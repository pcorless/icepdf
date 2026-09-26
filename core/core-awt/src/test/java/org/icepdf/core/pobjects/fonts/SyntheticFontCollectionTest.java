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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FontCollectionScanTest} can only run where the host happens to have a .ttc installed, so on a
 * bare CI box collection support goes untested.  This builds a two-face collection out of the Roboto
 * faces in core-fonts and runs the scan, the font-cache round trip and the by-name face selection
 * against it, everywhere.
 */
public class SyntheticFontCollectionTest {

    private static final Path FONT_DIR = Path.of("../core-fonts/src/main/resources/org/icepdf/core/fonts");

    @TempDir
    Path tempDir;

    private Field fontListField;
    private Object savedFontList;

    @BeforeEach
    public void isolateFontList() throws Exception {
        // the scan writes into the singleton's static list; swap in an empty one for the test
        fontListField = FontManager.class.getDeclaredField("fontList");
        fontListField.setAccessible(true);
        savedFontList = fontListField.get(null);
        fontListField.set(null, new ArrayList<Object[]>());
    }

    @AfterEach
    public void restoreFontList() throws Exception {
        fontListField.set(null, savedFontList);
    }

    @Test
    public void everyFaceOfACollectionIsRegisteredAndSelectable() throws Exception {
        String ttc = writeCollection("Roboto-Regular.ttf", "Roboto-Bold.ttf").toString();
        FontManager fontManager = FontManager.getInstance();

        invoke("evaluateFontForInsertion", new Class[]{String.class}, ttc);

        List<String> names = Arrays.asList(fontManager.getAvailableNames());
        assertEquals(List.of("roboto-regular", "roboto-bold"), names, "one entry per face, by PostScript name");

        // the scan records lower-cased names; each must re-select its own face, not the first
        assertEquals("Roboto-Bold", buildFont(ttc, "roboto-bold").getName());
        assertEquals("Roboto-Regular", buildFont(ttc, "roboto-regular").getName());
        assertEquals("Roboto-Regular", buildFont(ttc, "no-such-face").getName(),
                "an unknown name falls back to the collection's first face");
    }

    @Test
    public void collectionFacesSurviveTheFontCacheRoundTrip() throws Exception {
        String ttc = writeCollection("Roboto-Regular.ttf", "Roboto-Bold.ttf").toString();
        FontManager fontManager = FontManager.getInstance();
        invoke("evaluateFontForInsertion", new Class[]{String.class}, ttc);

        Properties cache = fontManager.getFontProperties();
        Preferences node = Preferences.userRoot().node("icepdf-test/ttc-roundtrip-" + UUID.randomUUID());
        try {
            for (String name : cache.stringPropertyNames()) {
                node.put(name, cache.getProperty(name));
            }
            fontListField.set(null, new ArrayList<Object[]>());
            fontManager.setFontProperties(node);
        } finally {
            node.removeNode();
        }

        List<String> names = Arrays.asList(fontManager.getAvailableNames());
        assertTrue(names.containsAll(List.of("roboto-regular", "roboto-bold")), names.toString());
        assertEquals("Roboto-Bold", buildFont(ttc, "roboto-bold").getName());
    }

    @Test
    public void unreadableCachePreservesTheCurrentList() throws Exception {
        String ttc = writeCollection("Roboto-Regular.ttf", "Roboto-Bold.ttf").toString();
        FontManager fontManager = FontManager.getInstance();
        invoke("evaluateFontForInsertion", new Class[]{String.class}, ttc);

        Preferences node = Preferences.userRoot().node("icepdf-test/bad-cache-" + UUID.randomUUID());
        try {
            node.put("good", "good|0|/nonexistent/good.ttf");
            node.put("bad", "|0|/nonexistent/bad.ttf");
            assertThrows(IllegalArgumentException.class, () -> fontManager.setFontProperties(node));
        } finally {
            node.removeNode();
        }
        assertEquals(2, fontManager.getAvailableNames().length,
                "a failed load must not leave the list half replaced");
    }

    private FontFile buildFont(String path, String postScriptName) throws Exception {
        FontFile font = (FontFile) invoke("buildFont", new Class[]{String.class, String.class}, path, postScriptName);
        assertNotNull(font, "collection face failed to load: " + postScriptName);
        return font;
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = FontManager.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(FontManager.getInstance(), args);
    }

    /**
     * Packs single-font sfnt files into a TrueType collection: a {@code ttcf} header, then each font's
     * offset table and directory, then all table data with the directory offsets rebased onto the
     * collection file.
     */
    private Path writeCollection(String... fontFiles) throws IOException {
        List<ByteBuffer> fonts = new ArrayList<>();
        for (String fontFile : fontFiles) {
            fonts.add(ByteBuffer.wrap(Files.readAllBytes(FONT_DIR.resolve(fontFile))));
        }
        int headerSize = 12 + 4 * fonts.size();
        int directoriesSize = 0;
        for (ByteBuffer font : fonts) {
            directoriesSize += 12 + 16 * font.getShort(4);
        }

        ByteArrayOutputStream directories = new ByteArrayOutputStream();
        ByteArrayOutputStream tables = new ByteArrayOutputStream();
        DataOutputStream dir = new DataOutputStream(directories);
        int[] fontOffsets = new int[fonts.size()];
        int dataStart = headerSize + directoriesSize;
        for (int f = 0; f < fonts.size(); f++) {
            ByteBuffer font = fonts.get(f);
            fontOffsets[f] = headerSize + directories.size();
            int numTables = font.getShort(4) & 0xFFFF;
            dir.write(font.array(), 0, 12);
            for (int t = 0; t < numTables; t++) {
                int entry = 12 + 16 * t;
                int offset = font.getInt(entry + 8);
                int length = font.getInt(entry + 12);
                dir.writeInt(font.getInt(entry));       // tag
                dir.writeInt(font.getInt(entry + 4));   // checksum
                dir.writeInt(dataStart + tables.size());
                dir.writeInt(length);
                tables.write(font.array(), offset, length);
                while (tables.size() % 4 != 0) {
                    tables.write(0);
                }
            }
        }

        ByteArrayOutputStream collection = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(collection);
        out.writeBytes("ttcf");
        out.writeInt(0x00010000);
        out.writeInt(fonts.size());
        for (int offset : fontOffsets) {
            out.writeInt(offset);
        }
        directories.writeTo(out);
        tables.writeTo(out);

        File ttc = tempDir.resolve("synthetic.ttc").toFile();
        Files.write(ttc.toPath(), collection.toByteArray());
        return ttc.toPath();
    }
}
