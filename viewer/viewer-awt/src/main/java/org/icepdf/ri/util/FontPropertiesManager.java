/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.util;

import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.util.font.FontCache;

import java.io.File;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;


/**
 * <p>This class provides a basic Font Properties Management system.  In order for font substitution to work more
 * reliable it is beneficial that it has read and cached all system fonts.  The scanning of system fonts can be
 * time-consuming and negatively effect the startup time of the library.  To speed up subsequent launches of the PDF
 * library the fonts are stored using the Preferences API using a backing store determined by the JVM.</p>
 *
 * // read/store the font cache.
 * FontPropertiesManager.getInstance().loadOrReadSystemFonts();
 *
 * <p>NOTE:  This class was significantly simplified in version 6.3 of ICEpdf and the release notes should be
 * consulted if any custom font loading was implemented by the end user.</p>
 *
 * @since 6.3
 */
public class FontPropertiesManager {

    private static final Logger logger = Logger.getLogger(FontPropertiesManager.class.getName());

    // can't use system level cache on window as of JDK 1.8_14, but should work in 9.
    private final Preferences prefs;

    public static final String PREFERENCES_KEY_CLASS = "org.icepdf.ri.util.FontPreferencesKey";

    /**
     * Version of the cached font list held in the backing store.  Raise it whenever {@link FontManager}
     * learns to discover fonts it previously skipped, so existing installations re-scan once instead
     * of loading a list that predates the change.
     * <p>
     * 1 -&gt; 2: TrueType/OpenType collections (.ttc/.otc) are now read, one entry per contained face.
     */
    private static final int FONT_CACHE_VERSION = 2;
    /**
     * Held in a child node, not alongside the font entries: {@link FontManager#setFontProperties} reads
     * every key of the font node and parses its value as {@code family|decorations|path}, so a key
     * that isn't a font would make the whole cache fail to load.
     */
    private final Preferences cacheMeta;
    private static final String FONT_CACHE_VERSION_KEY = "version";
    private static final String FONT_CACHE_COUNT_KEY = "count";

    private static Class<?> getPreferencesClass() {
        String fontPreferencesKey = Defs.sysProperty(PREFERENCES_KEY_CLASS);
        if (fontPreferencesKey != null) {
            try {
                return Class.forName(fontPreferencesKey);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return FontCache.class;
    }

    private static FontPropertiesManager fontPropertiesManager;

    private static final FontManager fontManager = FontManager.getInstance();

    private FontPropertiesManager() {
        this(Preferences.userNodeForPackage(getPreferencesClass()));
    }

    /**
     * Binds the manager to an arbitrary backing-store node.  Package private so tests can work
     * against an isolated node instead of the user-global font cache.
     *
     * @param fontNode node holding the font entries; its "cache" child holds the version.
     */
    FontPropertiesManager(Preferences fontNode) {
        prefs = fontNode;
        cacheMeta = fontNode.node("cache");
    }

    /**
     * Gets the singleton instance of the FontPropertiesManager.
     *
     * @return instance of FontPropertiesManager.
     */
    public static FontPropertiesManager getInstance() {
        if (fontPropertiesManager == null) {
            fontPropertiesManager = new FontPropertiesManager();
        }
        return fontPropertiesManager;
    }

    /**
     * Checks to see if there is currently any cached properties in the the backing store; if so they are returned,
     * otherwise a full read of the system fonts takes place and the results are stored in the backing store.
     */
    public void loadOrReadSystemFonts() {
        if (isFontPropertiesEmpty() || isFontCacheStale()) {
            rebuildCache();
        } else {
            try {
                // load properties from cache into the fontManager
                loadProperties();
            } catch (IllegalArgumentException e) {
                // one unreadable entry fails the whole load; without this the cache would stay
                // broken and fail the same way on every launch.
                logger.log(Level.WARNING, "Font cache could not be read, rebuilding it.", e);
                rebuildCache();
            }
        }
    }

    /**
     * Rescans the system fonts into the backing store, then drops entries that can no longer be used,
     * and finally reloads the {@link FontManager} from the store so memory and cache agree.
     * <p>
     * Entries are merged rather than the store being cleared first: fonts an application saved from its
     * own extra font paths aren't part of the system scan and would otherwise be lost on upgrade.
     */
    private void rebuildCache() {
        readDefaultFontProperties();
        saveProperties();
        pruneUnusableEntries();
        loadProperties();
    }

    /**
     * Removes entries that {@link FontManager#setFontProperties} can't parse, and entries whose font
     * file has since been removed from the system.
     */
    private void pruneUnusableEntries() {
        try {
            for (String name : prefs.keys()) {
                String path = fontPath(prefs.get(name, null));
                if (path == null || !new File(path).isFile()) {
                    prefs.remove(name);
                }
            }
            stampCache();
        } catch (BackingStoreException e) {
            logger.log(Level.WARNING, "Error pruning the font cache: ", e);
        }
    }

    /**
     * Font path of a cache entry, or null if the entry isn't in the {@code family|decorations|path}
     * form {@link FontManager#setFontProperties} reads (it tokenizes the same way, so empty fields
     * shift the tokens).
     */
    private static String fontPath(String value) {
        if (value == null) {
            return null;
        }
        StringTokenizer tokens = new StringTokenizer(value, "|");
        if (tokens.countTokens() < 3) {
            return null;
        }
        tokens.nextToken();
        try {
            Integer.parseInt(tokens.nextToken());
        } catch (NumberFormatException e) {
            return null;
        }
        return tokens.nextToken();
    }

    /**
     * True when the cached font list was written by a build whose scanner found fewer fonts than this
     * one does.  The cache is keyed only by "has anything been stored", so without this check a
     * scanner improvement would never reach an existing installation: it would keep loading the list
     * it wrote the first time it ran, missing whatever the new scanner can now see.
     * <p>
     * Bump {@link #FONT_CACHE_VERSION} whenever the set of fonts {@link FontManager} can discover
     * changes, so installations re-scan once and then carry on using the cache.
     */
    private boolean isFontCacheStale() {
        if (cacheMeta.getInt(FONT_CACHE_VERSION_KEY, 0) < FONT_CACHE_VERSION) {
            return true;
        }
        // An older release can't see the version node, so after a downgrade its "clear font cache"
        // leaves the version behind and rewrites the entries with its own, smaller scan.  The entry
        // count written alongside the version catches that.
        int count = cacheMeta.getInt(FONT_CACHE_COUNT_KEY, -1);
        try {
            return count >= 0 && count != prefs.keys().length;
        } catch (BackingStoreException e) {
            return false;
        }
    }

    /**
     * Records the version and entry count of the cache as it now stands in the backing store.
     */
    private void stampCache() throws BackingStoreException {
        cacheMeta.putInt(FONT_CACHE_VERSION_KEY, FONT_CACHE_VERSION);
        cacheMeta.putInt(FONT_CACHE_COUNT_KEY, prefs.keys().length);
    }

    /**
     * Reads the default font paths as defined by the {@link FontManager} class.  This method does not save
     * any fonts to the backing store.
     *
     * @param paths any extra paths that should be read as defined by the end user.
     */
    public void readDefaultFontProperties(String... paths) {
        try {
            fontManager.readSystemFonts(paths);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Error reading system fonts path: ", e);
            }
        }
    }

    /**
     * Reads the only font paths defined by the param paths.  This method does not save any fonts to the backing store
     * or read system fonts as defined by {@link FontManager#readSystemFonts(String[])}.
     *
     * @param paths paths that should be read for system fonts.
     */
    public void readFontProperties(String... paths) {
        try {
            // If you application needs to look at other font directories
            // they can be added via the readSystemFonts method.
            fontManager.readFonts(paths);
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error reading system paths:", e);
            }
        }
    }

    /**
     * Loads any font properties stored in the backing store and are passed to the {@link FontManager} class.  No
     * changes are made to the backing store.
     */
    public void loadProperties() {
        fontManager.setFontProperties(prefs);
    }

    /**
     * Clears the backing store of all font properties.
     */
    public void clearProperties() {
        try {
            prefs.clear();
            cacheMeta.clear();
            fontManager.clearFontList();
        } catch (BackingStoreException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error reading system paths:", e);
            }
        }
    }

    /**
     * Saves all fonts properties defined in the {@link FontManager} to the backing store.
     */
    public void saveProperties() {
        Properties fontProps = fontManager.getFontProperties();
        for (Object key : fontProps.keySet()) {
            prefs.put((String) key, fontProps.getProperty((String) key));
        }
        try {
            stampCache();
        } catch (BackingStoreException e) {
            logger.log(Level.WARNING, "Error writing the font cache version: ", e);
        }
    }

    /**
     * Check to see if any font properties are stored in the backing store.
     *
     * @return true if font properties backing store is empty, otherwise false.
     */
    public boolean isFontPropertiesEmpty() {
        try {
            return prefs.keys().length == 0;
        } catch (BackingStoreException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error writing system fonts to backing store: ", e);
            }
        }
        return false;
    }

    /**
     * Gets the underlying fontManger instance which is also a singleton.
     *
     * @return current font manager
     */
    public static FontManager getFontManager() {
        return fontManager;
    }
}

