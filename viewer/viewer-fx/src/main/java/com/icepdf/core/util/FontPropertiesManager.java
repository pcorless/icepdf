package com.icepdf.core.util;

import org.icepdf.core.pobjects.fonts.FontManager;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * \
 */
public class FontPropertiesManager {

    private static final Logger logger = Logger.getLogger(FontPropertiesManager.class.toString());

    private static Preferences prefs = Preferences.userNodeForPackage(FontPropertiesManager.class);

    private static FontPropertiesManager fontPropertiesManager;

    private static FontManager fontManager = FontManager.getInstance();

    private FontPropertiesManager() {

    }

    public static FontPropertiesManager getInstance() {
        if (fontPropertiesManager == null) {
            fontPropertiesManager = new FontPropertiesManager();
        }
        return fontPropertiesManager;
    }

    public void readDefaultProperties(String... paths) {
        try {
            fontManager.readSystemFonts(paths);
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error reading system fonts path: ", e);
            }
        }
    }

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

    public void loadProperties() {
        fontManager.setFontProperties(prefs);
    }

    public void clearProperties() {
        try {
            prefs.clear();
        } catch (BackingStoreException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error reading system paths:", e);
            }
        }
    }

    public void updateProperties() {
        Properties fontProps = fontManager.getFontProperties();
        for (Object key : fontProps.keySet()) {
            prefs.put((String) key, fontProps.getProperty((String) key));
        }
    }

    public boolean isPropertiesEmpty() {
        try {
            return prefs.keys().length == 0;
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static FontManager getFontManager() {
        return FontManager.getInstance();
    }
}
