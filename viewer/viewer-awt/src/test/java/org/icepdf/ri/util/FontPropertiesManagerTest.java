/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.ri.util;

import org.icepdf.core.pobjects.fonts.FontManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The font cache is the one piece of on-disk state a 7.4.x installation hands to 7.5.0, so these tests
 * pin down the upgrade path: a cache without a version is rescanned once and rewritten, a current cache
 * is trusted, and a cache that can't be parsed heals itself instead of failing every launch.
 * <p>
 * Each test works against its own preferences node, never the user-global cache.
 */
public class FontPropertiesManagerTest {

    private static final String UNINSTALLED_FONT = "icepdf-test-uninstalled-font";
    private static final String SENTINEL_FONT = "icepdf-test-cache-sentinel";

    private static Properties savedFontList;

    private Preferences fontNode;
    private FontPropertiesManager manager;

    @BeforeAll
    public static void saveFontManagerState() {
        savedFontList = FontManager.getInstance().getFontProperties();
    }

    @AfterAll
    public static void restoreFontManagerState() throws Exception {
        // other test classes in this JVM share the FontManager singleton
        Preferences restore = Preferences.userRoot().node("icepdf-test/fontcache-restore-" + UUID.randomUUID());
        try {
            for (String name : savedFontList.stringPropertyNames()) {
                restore.put(name, savedFontList.getProperty(name));
            }
            FontManager.getInstance().setFontProperties(restore);
        } finally {
            restore.removeNode();
        }
    }

    @BeforeEach
    public void createIsolatedNode() {
        fontNode = Preferences.userRoot().node("icepdf-test/fontcache-" + UUID.randomUUID());
        manager = new FontPropertiesManager(fontNode);
    }

    @AfterEach
    public void removeIsolatedNode() throws Exception {
        fontNode.removeNode();
    }

    @Test
    public void emptyCacheIsScannedAndVersioned() throws Exception {
        manager.loadOrReadSystemFonts();

        assertEquals(2, cacheVersion());
        assertEquals(FontManager.getInstance().getFontProperties().size(), fontNode.keys().length,
                "the stored cache should be exactly what the scan found");
    }

    @Test
    public void cacheWrittenBy741IsRescannedAndRewritten() throws Exception {
        seedFrom741Fixture();
        fontNode.put(UNINSTALLED_FONT, "bogus|0|/nonexistent/icepdf/bogus.ttf");
        assertEquals(0, cacheVersion(), "7.4.x writes no version");

        manager.loadOrReadSystemFonts();

        assertEquals(2, cacheVersion());
        assertNull(fontNode.get(UNINSTALLED_FONT, null),
                "a rescan must replace the stale cache, not layer on top of it");
        assertFalse(availableNames().contains(UNINSTALLED_FONT));
        assertEquals(FontManager.getInstance().getFontProperties().size(), fontNode.keys().length);
    }

    @Test
    public void currentCacheIsLoadedWithoutRescan() throws Exception {
        fontNode.put(SENTINEL_FONT, "sentinel|0|/nonexistent/icepdf/sentinel.ttf");
        fontNode.node("cache").putInt("version", 2);

        manager.loadOrReadSystemFonts();

        assertTrue(availableNames().contains(SENTINEL_FONT), "entries should come from the cache");
        assertEquals(1, fontNode.keys().length, "a trusted cache must not be rescanned or rewritten");
    }

    @Test
    public void newerCacheVersionIsTrusted() throws Exception {
        // a downgrade from a later release: that release's cache is a superset, keep using it
        fontNode.put(SENTINEL_FONT, "sentinel|0|/nonexistent/icepdf/sentinel.ttf");
        fontNode.node("cache").putInt("version", 3);

        manager.loadOrReadSystemFonts();

        assertTrue(availableNames().contains(SENTINEL_FONT));
        assertEquals(3, cacheVersion(), "an older build must not stamp its own version over a newer one");
    }

    @Test
    public void corruptCacheIsRebuiltInsteadOfFailing() throws Exception {
        fontNode.put(SENTINEL_FONT, "sentinel|0|/nonexistent/icepdf/sentinel.ttf");
        // empty family: StringTokenizer skips it and parseInt meets the path
        fontNode.put("icepdf-test-empty-family", "|0|/nonexistent/icepdf/x.ttf");
        fontNode.put("icepdf-test-garbage", "garbage");
        fontNode.node("cache").putInt("version", 2);

        assertDoesNotThrow(manager::loadOrReadSystemFonts);

        assertNull(fontNode.get("icepdf-test-empty-family", null));
        assertNull(fontNode.get("icepdf-test-garbage", null));
        assertEquals(2, cacheVersion());
        assertEquals(FontManager.getInstance().getFontProperties().size(), fontNode.keys().length);
    }

    @Test
    public void cacheRewrittenByOlderReleaseIsRescanned() throws Exception {
        // 7.5 wrote version + count; a downgraded 7.4 then cleared the entries (it can't see the
        // version node) and wrote its own smaller scan.
        fontNode.put(SENTINEL_FONT, "sentinel|0|/nonexistent/icepdf/sentinel.ttf");
        fontNode.node("cache").putInt("version", 2);
        fontNode.node("cache").putInt("count", 400);

        manager.loadOrReadSystemFonts();

        assertFalse(availableNames().contains(SENTINEL_FONT), "a count mismatch must force a rescan");
        assertEquals(fontNode.keys().length, fontNode.node("cache").getInt("count", -1));
    }

    @Test
    public void rescanKeepsApplicationFontsThatStillExist() throws Exception {
        // an application may have saved fonts from its own paths; an upgrade rescan must not drop them
        java.io.File appFont = java.io.File.createTempFile("icepdf-app-font", ".ttf");
        appFont.deleteOnExit();
        fontNode.put("icepdf-test-app-font", "appfont|0|" + appFont.getAbsolutePath());

        manager.loadOrReadSystemFonts();

        assertNotNull(fontNode.get("icepdf-test-app-font", null));
        assertTrue(availableNames().contains("icepdf-test-app-font"));
    }

    @Test
    public void clearPropertiesRemovesEntriesAndVersion() throws Exception {
        seedFrom741Fixture();
        fontNode.node("cache").putInt("version", 2);

        manager.clearProperties();

        assertEquals(0, fontNode.keys().length);
        assertEquals(0, cacheVersion());
        assertTrue(manager.isFontPropertiesEmpty());
    }

    private void seedFrom741Fixture() throws Exception {
        Properties fixture = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/fontcache/v7.4.1-linux.properties")) {
            assertNotNull(in, "7.4.1 cache fixture missing");
            fixture.load(in);
        }
        for (String name : fixture.stringPropertyNames()) {
            fontNode.put(name, fixture.getProperty(name));
        }
    }

    private int cacheVersion() {
        return fontNode.node("cache").getInt("version", 0);
    }

    private static List<String> availableNames() {
        String[] names = FontManager.getInstance().getAvailableNames();
        return names == null ? List.of() : Arrays.asList(names);
    }
}
