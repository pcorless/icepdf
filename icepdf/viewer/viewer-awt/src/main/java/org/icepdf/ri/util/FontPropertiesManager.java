/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
import org.icepdf.ri.util.font.FontCache;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;


/**
 * <p>This class provides a basic Font Properties Management system.  In order for font substitution to work more
 * reliable it is beneficial that it has read and cached all system fonts.  The scanning of system fonts can be time
 * consuming and negatively effect the startup time of the library.  To speed up subsequent launches of the PDF library
 * the fonts are stored using the Preferences API using a backing store determined by the JVM.</p>
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

    private static final Logger logger = Logger.getLogger(FontPropertiesManager.class.toString());

    // can't use system level cache on window as of JDK 1.8_14, but should work in 9.
    private static Preferences prefs = Preferences.userNodeForPackage(FontCache.class);

    private static FontPropertiesManager fontPropertiesManager;

    private static FontManager fontManager = FontManager.getInstance();

    private FontPropertiesManager() {

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
        if (isFontPropertiesEmpty()) {
            readDefaultFontProperties();
            saveProperties();
        }else{
            // load properties from cache into the fontManager
            loadProperties();
        }
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
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error reading system fonts path: ", e);
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

