/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;


/**
 * <p>This class provides a very basic Properties Management system for the
 * viewer application.  Settings such as window location and temporary file
 * information is managed by this class.</p>
 *
 * @since 6.3
 */
// todo rename class to ViewerPropertiesManager
public class PropertiesManager {

    private static final Logger logger =
            Logger.getLogger(PropertiesManager.class.toString());

    //default file for all not specified properties
    private static final String DEFAULT_PROP_FILE = "ICEpdfDefault.properties";
    public static final String DEFAULT_PROP_FILE_PATH = "org/icepdf/ri/viewer/res/";
    public static final String DEFAULT_MESSAGE_BUNDLE = "org.icepdf.ri.resources.MessageBundle";

    public static final String PROPERTY_DEFAULT_FILE_PATH = "application.default.filepath";
    public static final String PROPERTY_DEFAULT_URL = "application.default.url";

    // window properties
    public static final String PROPERTY_DIVIDER_LOCATION = "application.divider.location";
    // default page fit mode
    public static final String PROPERTY_DEFAULT_PAGEFIT = "document.pagefit.mode";
    public static final String PROPERTY_DEFAULT_ROTATION = "document.rotation";
    // page rotation
    public static final String PROPERTY_DEFAULT_VIEW_TYPE = "document.viewtype";
    // default print media size.
    public static final String PROPERTY_PRINT_MEDIA_SIZE_WIDTH = "document.print.mediasize.width";
    public static final String PROPERTY_PRINT_MEDIA_SIZE_HEIGHT = "document.print.mediasize.height";
    public static final String PROPERTY_PRINT_MEDIA_SIZE_UNIT = "document.print.mediasize.unit";
    // system properties
    public static final String SYSPROPERTY_HIGHLIGHT_COLOR = "org.icepdf.core.views.page.text.highlight.color";
    // properties used to hide/show toolbars
    public static final String PROPERTY_SHOW_TOOLBAR_UTILITY = "application.toolbar.show.utility";
    public static final String PROPERTY_SHOW_TOOLBAR_PAGENAV = "application.toolbar.show.pagenav";
    public static final String PROPERTY_SHOW_TOOLBAR_ZOOM = "application.toolbar.show.zoom";
    public static final String PROPERTY_SHOW_TOOLBAR_FIT = "application.toolbar.show.fit";
    public static final String PROPERTY_SHOW_TOOLBAR_ROTATE = "application.toolbar.show.rotate";
    public static final String PROPERTY_SHOW_TOOLBAR_TOOL = "application.toolbar.show.tool";
    public static final String PROPERTY_SHOW_TOOLBAR_ANNOTATION = "application.toolbar.show.annotation";
    public static final String PROPERTY_SHOW_TOOLBAR_FORMS = "application.toolbar.show.forms";
    // properties used to hide/show status bar buttons
    public static final String PROPERTY_SHOW_STATUSBAR = "application.statusbar";
    // properties used to hide/show status bar status label
    public static final String PROPERTY_SHOW_STATUSBAR_STATUSLABEL = "application.statusbar.show.statuslabel";
    // properties used to hide/show status bar buttons
    public static final String PROPERTY_SHOW_STATUSBAR_VIEWMODE = "application.statusbar.show.viewmode";
    public static final String PROPERTY_SHOW_STATUSBAR_VIEWMODE_SINGLE = "application.statusbar.show.viewmode.singlepage";
    public static final String PROPERTY_SHOW_STATUSBAR_VIEWMODE_SINGLE_CONTINUOUS = "application.statusbar.show.viewmode.single.page.continuous";
    public static final String PROPERTY_SHOW_STATUSBAR_VIEWMODE_DOUBLE = "application.statusbar.show.viewmode.double.page";
    public static final String PROPERTY_SHOW_STATUSBAR_VIEWMODE_DOUBLE_CONTINUOUS = "application.statusbar.show.viewmode.double.page.continuous";
    // properties used to hide/show the utility buttons (open, print, etc.)
    public static final String PROPERTY_SHOW_UTILITY_OPEN = "application.toolbar.show.utility.open";
    public static final String PROPERTY_SHOW_UTILITY_SAVE = "application.toolbar.show.utility.save";
    public static final String PROPERTY_SHOW_UTILITY_PRINT = "application.toolbar.show.utility.print";
    public static final String PROPERTY_SHOW_UTILITY_SEARCH = "application.toolbar.show.utility.search";
    public static final String PROPERTY_SHOW_UTILITY_UPANE = "application.toolbar.show.utility.upane";
    // properties used to hide/show utility pane tabs
    public static final String PROPERTY_HIDE_UTILITYPANE = "application.utilitypane.hide";
    public static final String PROPERTY_SHOW_UTILITYPANE_BOOKMARKS = "application.utilitypane.show.bookmarks";
    public static final String PROPERTY_SHOW_UTILITYPANE_ATTACHMENTS = "application.utilitypane.show.attachments";
    public static final String PROPERTY_SHOW_UTILITYPANE_SEARCH = "application.utilitypane.show.search";
    public static final String PROPERTY_SHOW_UTILITYPANE_THUMBNAILS = "application.utilitypane.show.thumbs";
    public static final String PROPERTY_SHOW_UTILITYPANE_LAYERS = "application.utilitypane.show.layers";
    public static final String PROPERTY_SHOW_UTILITYPANE_ANNOTATION = "application.utilitypane.show.annotation";
    public static final String PROPERTY_SHOW_UTILITYPANE_ANNOTATION_FLAGS = "application.utilitypane.show.annotation.flags";
    public static final String PROPERTY_SHOW_UTILITYPANE_SIGNATURES = "application.utilitypane.show.signatures";
    // properties use dot hide/show preferences pane tabs.
    public static final String PROPERTY_SHOW_PREFERENCES_GENERAL = "application.preferences.show.general";
    public static final String PROPERTY_SHOW_PREFERENCES_ANNOTATIONS = "application.preferences.show.annotations";
    public static final String PROPERTY_SHOW_PREFERENCES_IMAGING = "application.preferences.show.imaging";
    public static final String PROPERTY_SHOW_PREFERENCES_FONTS = "application.preferences.show.fonts";
    public static final String PROPERTY_SHOW_PREFERENCES_ADVANCED = "application.preferences.show.advanced";
    // default utility pane thumbnail zoom size for non-embedded files
    public static final String PROPERTY_UTILITYPANE_THUMBNAILS_ZOOM = "application.utilitypane.thumbnail.zoom";
    // properties used for default zoom levels
    public static final String PROPERTY_DEFAULT_ZOOM_LEVEL = "application.zoom.factor.default";
    public static final String PROPERTY_ZOOM_RANGES = "application.zoom.range.default";
    // property to hide/show menu keyboard accelerator shortcuts
    public static final String PROPERTY_SHOW_KEYBOARD_SHORTCUTS = "application.menuitem.show.keyboard.shortcuts";
    // properties used for overriding ViewerPreferences pulled from the document
    public static final String PROPERTY_VIEWPREF_HIDETOOLBAR = "application.viewerpreferences.hidetoolbar";
    public static final String PROPERTY_VIEWPREF_HIDEMENUBAR = "application.viewerpreferences.hidemenubar";
    public static final String PROPERTY_VIEWPREF_FITWINDOW = "application.viewerpreferences.fitwindow";
    public static final String PROPERTY_VIEWPREF_FORM_HIGHLIGHT = "application.viewerpreferences.form.highlight";

    // properties used to control visibility of annotation controls on main utility panel.
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_HIGHLIGHT = "application.toolbar.annotation.show.highlight";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_UNDERLINE = "application.toolbar.annotation.show.underline";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_STRIKE_OUT = "application.toolbar.annotation.show.strikeout";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_LINE = "application.toolbar.annotation.show.line";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_LINK = "application.toolbar.annotation.show.link";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_ARROW = "application.toolbar.annotation.show.arrow";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_RECTANGLE = "application.toolbar.annotation.show.rectangle";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_CIRCLE = "application.toolbar.annotation.show.circle";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_INK = "application.toolbar.annotation.show.ink";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_FREE_TEXT = "application.toolbar.annotation.show.freetext";
    public static final String PROPERTY_SHOW_UTILITY_ANNOTATION_TEXT = "application.toolbar.annotation.show.text";
    // Individual controls for the annotation toolbar button commands
    public static final String PROPERTY_SHOW_TOOLBAR_ANNOTATION_SELECTION = "application.toolbar.show.annotation.selection";
    public static final String PROPERTY_SHOW_TOOLBAR_ANNOTATION_HIGHLIGHT = "application.toolbar.show.annotation.highlight";
    public static final String PROPERTY_SHOW_TOOLBAR_ANNOTATION_TEXT = "application.toolbar.show.annotation.text";
    // Individual control of the markup annotation context menu
    public static final String PROPERTY_SHOW_ANNOTATION_MARKUP_REPLY_TO = "application.annotation.show.markup.replyTo";
    public static final String PROPERTY_SHOW_ANNOTATION_MARKUP_SET_STATUS = "application.annotation.show.markup.setStatus";

    // private/public annotation property, last used stored, default is true or public.
    public static final String PROPERTY_ANNOTATION_VISIBILITY_PERMISSION = "application.viewer.preference.annotation.permission.public";

    // highlight annotation default colour as defined by the last used colour for each type.
    public static final String PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR = "application.viewer.preference.annotation.highlight.button.color";
    public static final String PROPERTY_ANNOTATION_HIGHLIGHT_COLOR = "application.viewer.preference.annotation.highlight.color";
    public static final String PROPERTY_ANNOTATION_HIGHLIGHT_OPACITY = "application.viewer.preference.annotation.highlight.opacity";
    public static final String PROPERTY_ANNOTATION_STRIKE_OUT_COLOR = "application.viewer.preference.annotation.strikeout.color";
    public static final String PROPERTY_ANNOTATION_STRIKE_OUT_OPACITY = "application.viewer.preference.annotation.strikeout.opacity";
    public static final String PROPERTY_ANNOTATION_UNDERLINE_COLOR = "application.viewer.preference.annotation.underline.color";
    public static final String PROPERTY_ANNOTATION_UNDERLINE_OPACITY = "application.viewer.preference.annotation.underline.opacity";
    public static final String PROPERTY_ANNOTATION_SQUIGGLY_COLOR = "application.viewer.preference.annotation.squiggly.color";
    public static final String PROPERTY_ANNOTATION_SQUIGGLY_OPACITY = "application.viewer.preference.annotation.squiggly.opacity";
    public static final String PROPERTY_ANNOTATION_TEXT_COLOR = "application.viewer.preference.annotation.text.color";
    public static final String PROPERTY_ANNOTATION_TEXT_OPACITY = "application.viewer.preference.annotation.text.opacity";
    public static final String PROPERTY_ANNOTATION_TEXT_ICON = "application.viewer.preference.annotation.text.icon";
    public static final String PROPERTY_ANNOTATION_INK_COLOR = "application.viewer.preference.annotation.ink.color";
    public static final String PROPERTY_ANNOTATION_INK_OPACITY = "application.viewer.preference.annotation.ink.opacity";
    // annotation types with stroke and fill colours
    public static final String PROPERTY_ANNOTATION_SQUARE_COLOR = "application.viewer.preference.annotation.square.color";
    public static final String PROPERTY_ANNOTATION_SQUARE_FILL_COLOR = "application.viewer.preference.annotation.square.fill.color";
    public static final String PROPERTY_ANNOTATION_SQUARE_OPACITY = "application.viewer.preference.annotation.square.fill.opacity";
    public static final String PROPERTY_ANNOTATION_CIRCLE_COLOR = "application.viewer.preference.annotation.circle.color";
    public static final String PROPERTY_ANNOTATION_CIRCLE_FILL_COLOR = "application.viewer.preference.annotation.circle.fill.color";
    public static final String PROPERTY_ANNOTATION_CIRCLE_OPACITY = "application.viewer.preference.annotation.circle.fill.opacity";
    public static final String PROPERTY_ANNOTATION_LINE_COLOR = "application.viewer.preference.annotation.line.color";
    public static final String PROPERTY_ANNOTATION_LINE_FILL_COLOR = "application.viewer.preference.annotation.line.fill.color";
    public static final String PROPERTY_ANNOTATION_LINE_OPACITY = "application.viewer.preference.annotation.line.fill.opcity";
    public static final String PROPERTY_ANNOTATION_LINE_ARROW_COLOR = "application.viewer.preference.annotation.arrow.color";
    public static final String PROPERTY_ANNOTATION_LINE_ARROW_FILL_COLOR = "application.viewer.preference.annotation.arrow.fill.color";
    public static final String PROPERTY_ANNOTATION_LINE_ARROW_OPACITY = "application.viewer.preference.annotation.arrow.fill.opacity";
    // free text, quite a lot of properties
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_COLOR = "application.viewer.preference.annotation.freetext.color";
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_SIZE = "application.viewer.preference.annotation.freetext.size";
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_FONT = "application.viewer.preference.annotation.freetext.font";
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_OPACITY = "application.viewer.preference.annotation.freetext.opacity";
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_FILL_COLOR = "application.viewer.preference.annotation.freetext.fill.color";
    public static final String PROPERTY_ANNOTATION_FREE_TEXT_BORDER_COLOR = "application.viewer.preference.annotation.freetext.border.color";

    // we use the same recent colour list for all annotation types
    public static final String PROPERTY_ANNOTATION_RECENT_COLORS = "application.viewer.preference.annotation.color.recent";
    // resent colour and labels, enabled automatically if there is more then one.
    public static final String PROPERTY_ANNOTATION_RECENT_COLOR_LABEL = "application.viewer.preference.annotation.recent.color.labels";

    private static PropertiesManager propertiesManager;

    private static Preferences preferences = Preferences.userNodeForPackage(PropertiesManager.class);

    private static Properties defaultProps;

    private PropertiesManager() {

    }

    /**
     * Gets singleton instance of the the Properties manager instance.
     *
     * @return singleton instance.
     */
    public static PropertiesManager getInstance() {
        if (propertiesManager == null) {
            propertiesManager = new PropertiesManager();
        }
        // load default properties from viewer jar and assigned to defaultProps.
        setupDefaultProperties();
        return propertiesManager;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public boolean checkAndStoreBooleanProperty(String propertyName) {
        return checkAndStoreBooleanProperty(propertyName, true);
    }

    /**
     * Method to check the value of a boolean property
     * This is meant to be used for configuration via the properties file
     * After the property has been checked, it will be stored back into the Properties
     * object (using a default value if none was found)
     *
     * @param propertyName to check for
     * @param defaultVal   to default to if no value is found on a property
     * @return true if property is true, otherwise false
     */
    public boolean checkAndStoreBooleanProperty(String propertyName, boolean defaultVal) {
        // Get the desired property, defaulting to the defaultVal parameter
        boolean returnValue = preferences.getBoolean(propertyName, defaultVal);
        // Set the property back into the manager
        // This is necessary in the cases where a property didn't exist, but needs to be added to the file
        preferences.putBoolean(propertyName, returnValue);
        return returnValue;
    }

    public double checkAndStoreDoubleProperty(String propertyName) {
        return checkAndStoreDoubleProperty(propertyName, 1.0f);
    }

    /**
     * Method to check the value of a double property
     * This is meant to be used for configuration via the properties file
     * After the property has been checked, it will be stored back into the Properties
     * object (using a default value if none was found)
     *
     * @param propertyName to check for
     * @param defaultVal   to default to if no value is found on a property
     * @return double property value
     */
    public double checkAndStoreDoubleProperty(String propertyName, double defaultVal) {
        // Get the desired property, defaulting to the defaultVal parameter
        double returnValue = preferences.getDouble(propertyName, defaultVal);
        // Set the property back into the manager
        // This is necessary in the cases where a property didn't exist, but needs to be added to the file
        preferences.putDouble(propertyName, returnValue);
        return returnValue;
    }

    public int checkAndStoreIntProperty(String propertyName) {
        return checkAndStoreIntProperty(propertyName, 1);
    }

    /**
     * Method to check the value of an int property
     * This is meant to be used for configuration via the properties file
     * After the property has been checked, it will be stored back into the Properties
     * object (using a default value if none was found)
     *
     * @param propertyName to check for
     * @param defaultVal   to default to if no value is found on a property
     * @return int value of property
     */
    public int checkAndStoreIntProperty(String propertyName, int defaultVal) {
        // Get the desired property, defaulting to the defaultVal parameter
        int returnValue = preferences.getInt(propertyName, defaultVal);
        // Set the property back into the manager
        // This is necessary in the cases where a property didn't exist, but needs to be added to the file
        preferences.putInt(propertyName, returnValue);
        return returnValue;
    }

    public float checkAndStoreFloatProperty(String propertyName) {
        return checkAndStoreFloatProperty(propertyName, 1);
    }

    /**
     * Method to check the value of an int property
     * This is meant to be used for configuration via the properties file
     * After the property has been checked, it will be stored back into the Properties
     * object (using a default value if none was found)
     *
     * @param propertyName to check for
     * @param defaultVal   to default to if no value is found on a property
     * @return int value of property
     */
    public float checkAndStoreFloatProperty(String propertyName, float defaultVal) {
        // Get the desired property, defaulting to the defaultVal parameter
        float returnValue = preferences.getFloat(propertyName, defaultVal);
        // Set the property back into the manager
        // This is necessary in the cases where a property didn't exist, but needs to be added to the file
        preferences.putFloat(propertyName, returnValue);
        return returnValue;
    }

    /**
     * Method to check the value of a comma separate list of floats property
     * For example we will convert "0.4f, 0.5f, 0.6f" to a size 3 array with the values as floats
     * This is meant to be used for configuration via the properties file
     * After the property has been checked, it will be stored back into the Properties
     * object (using a default value if none was found)
     *
     * @param propertyName to check for
     * @param defaultVal   to default to if no value is found on a property
     * @return array of floats from the property
     */
    public float[] checkAndStoreFloatArrayProperty(String propertyName, float[] defaultVal) {

        // Get the desired property, defaulting to the defaultVal parameter
        String propertyString = preferences.get(propertyName, null);
        float[] toReturn = defaultVal;
        try {
            // Ensure we have a property string to parse
            // Then we'll convert the comma separated property to a list of floats
            if ((propertyString != null) &&
                    (propertyString.trim().length() > 0)) {
                String[] split = propertyString.split(",");
                toReturn = new float[split.length];

                for (int i = 0; i < split.length; i++) {
                    try {
                        toReturn[i] = Float.parseFloat(split[i]);
                    } catch (NumberFormatException failedValue) {
                        /* ignore as we'll just automatically put a '0' in the invalid space */
                    }
                }
            }
            // Otherwise convert the defaultVal into a comma separated list
            // This is done so it can be stored back into the properties file
            else {
                StringBuilder commaBuffer = new StringBuilder(defaultVal.length * 2);
                for (int i = 0; i < defaultVal.length; i++) {
                    commaBuffer.append(defaultVal[i]);
                    // Check whether we need a comma
                    if ((i + 1) < defaultVal.length) {
                        commaBuffer.append(",");
                    }
                }

                // Set the property back into the manager
                // This is necessary in the cases where a property didn't exist, but needs to be added to the file
                preferences.put(propertyName, commaBuffer.toString());
            }
        } catch (Exception failedProperty) {
            /* ignore on failure as we'll just return defaultVal */
        }
        return toReturn;
    }

    /**
     * Allows users to set the default look and feel of the
     *
     * @param propertyName  look and feel class and package name.
     * @param defaultValue  default value
     * @param messageBundle message bundle.
     * @return class name used to set the look and feel
     */
    public String getLookAndFeel(String propertyName, String defaultValue, ResourceBundle messageBundle) {
        String value = preferences.get(propertyName, null);
        if (value != null) {
            String result = Parse.parseLookAndFeel(value, messageBundle);
            if (result != null) {
                return result;
            }
            preferences.remove(propertyName);
        }
        if (defaultProps != null) {
            value = (String) defaultProps.get(propertyName);
            if (value != null) {
                String result = Parse.parseLookAndFeel(value, null);
                if (result != null) {
                    return result;
                }
                preferences.remove(propertyName);
                Resources.showMessageDialog(null,
                        JOptionPane.ERROR_MESSAGE, messageBundle,
                        "manager.properties.title",
                        "manager.properties.lafError", value);
            }
        }
        return defaultValue;
    }

    /**
     * Reads the properties file that ships with the viewer jar and stores the default properties in the preferences
     * backing store. This is only done on first launch.
     */
    private static void setupDefaultProperties() {
        // check if we have a default key file path key, if not we need to load the default properties
        // and add them to the preferences backing store.
        if (preferences.get(PROPERTY_DEFAULT_FILE_PATH, null) == null) {
            try {
                InputStream in = getResourceAsStream(DEFAULT_PROP_FILE_PATH, DEFAULT_PROP_FILE);
                try {
                    defaultProps = new Properties();
                    defaultProps.load(in);
                    Enumeration keys = defaultProps.keys();
                    while (keys.hasMoreElements()) {
                        String key = (String) keys.nextElement();
                        preferences.put(key, defaultProps.getProperty(key));
                    }
                } finally {
                    in.close();
                }
            } catch (IOException ex) {
                // log the error
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Error loading default properties cache", ex);
                }
            }
        }
    }

    private static InputStream getResourceAsStream(String prefix, String resourcePath) {
        int colon = resourcePath.indexOf(':');
        if (colon >= 0) {
            if (resourcePath.lastIndexOf(colon - 1, '/') < 0) {
                try {
                    return (new URL(resourcePath)).openStream();
                } catch (IOException e) {
                    // eat the exception
                }
                return null;
            }
        }
        resourcePath = makeResPath(prefix, resourcePath);
        ClassLoader cl = prefix.getClass().getClassLoader();
        if (cl != null) {
            InputStream result = cl.getResourceAsStream(resourcePath);
            if (result != null) {
                return result;
            }
        }
        return ClassLoader.getSystemResourceAsStream(resourcePath);
    }

    private static String makeResPath(String prefix, String base_name) {
        if (base_name.length() != 0 && base_name.charAt(0) == '/') {
            return base_name.substring(1, base_name.length());
        } else if (prefix == null) {
            return base_name;
        } else {
            return prefix + base_name;
        }
    }

}

