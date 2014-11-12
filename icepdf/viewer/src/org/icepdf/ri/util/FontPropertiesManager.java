/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.fonts.FontManager;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * <p>This class provides a very basic Font Properties Management system.  When this
 * class is initiated, the properites file "pdfviewerfontcache.properties" is
 * read from the default application file path.  If the file cannot be found then
 * all system fonts are read from the operating system and are written to the
 * "pdfviewerfontcache.properties" file.</p>
 * <p/>
 * <p>This class is designed to speed up the load time of the viewer application
 * by reading already parsed font information from the properties file.  If new
 * fonts are added to the system, the "pdfviewerfontcache.properties" file can
 * be deleted to trigger this class to re-read the System fonts and re-create
 * a new "pdfviewerfontcache.properties" properites file.
 *
 * @since 2.0
 */
public class FontPropertiesManager {

    private static final Logger logger =
            Logger.getLogger(FontPropertiesManager.class.toString());

    private static final String DEFAULT_HOME_DIR = ".icesoft/icepdf_viewer";
    private static final String LOCK_FILE = "_syslock";
    private final static String USER_FILENAME = "pdfviewerfontcache.properties";

    private FontManager fontManager;

    //the version name, used in about dialog and start-up message
    String versionName = Document.getLibraryVersion();

    private Properties sysProps;
    private PropertiesManager props;
    private Properties fontProps;

    File userHome;

    //the swingri home directory
    private File dataDir;

    //not to save the bookmarks and properties if lockDir == null, that is
    //when we do not own the lock
    private File lockDir;

    private File propertyFile;

    private ResourceBundle messageBundle;


    public FontPropertiesManager(PropertiesManager appProps, Properties sysProps,
                                 ResourceBundle messageBundle) {
        this.sysProps = sysProps;
        this.props = appProps;
        this.fontProps = new Properties();
        this.messageBundle = messageBundle;
        // create a new Font Manager.
        this.fontManager = FontManager.getInstance();

        setupHomeDir(null);

        recordMofifTime();

        setupLock();

        loadProperties();
    }

    public synchronized void loadProperties() {

        if (dataDir != null) {
            propertyFile = new File(dataDir, USER_FILENAME);
            // load font properties from last invocation
            if (propertyFile.exists()) {
                try {
                    InputStream in = new FileInputStream(propertyFile);
                    try {
                        fontProps.load(in);
                        fontManager.setFontProperties(fontProps);
                    } finally {
                        in.close();
                    }
                } catch (IOException ex) {
                    // check to make sure the storage relate dialogs can be shown
                    if (getBoolean("application.showLocalStorageDialogs", true)) {
                        Resources.showMessageDialog(null,
                                JOptionPane.ERROR_MESSAGE, messageBundle,
                                "fontManager.properties.title",
                                "manager.properties.session.readError",
                                ex);
                    }
                    // log the error
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Error loading font properties cache", ex);
                    }
                } catch (IllegalArgumentException e) {
                    // propblem parsing fontProps, reread teh file
                    setupDefaultProperties();
                    saveProperties();
                }
            }
            // If no font data, then read font data and save the new file.
            else {
                setupDefaultProperties();
                saveProperties();
            }
        }
    }

    public synchronized void saveProperties() {
        if (ownLock()) {
            try {
                FileOutputStream out = new FileOutputStream(propertyFile);
                try {
                    fontProps.store(out, "-- ICEpf Font properties --");
                } finally {
                    out.close();
                }
                recordMofifTime();
            } catch (IOException ex) {
                // check to make sure the storage relate dialogs can be shown
                if (getBoolean("application.showLocalStorageDialogs", true)) {
                    Resources.showMessageDialog(null,
                            JOptionPane.ERROR_MESSAGE, messageBundle,
                            "fontManager.properties.title",
                            "manager.properties.saveError", ex);
                }
                // log the error
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Error saving font properties cache", ex);
                }
            }
        }
    }

    private boolean ownLock() {
        return lockDir != null;
    }

    private void recordMofifTime() {
        Calendar c = new GregorianCalendar();
        c.setTime(new Date());
        c.set(Calendar.MINUTE, c.get(Calendar.MINUTE) + 1);
        c.set(Calendar.SECOND, 0);
    }

    private void setupLock() {
        if (dataDir == null) {
            lockDir = null;
        } else {
            File dir = new File(dataDir, LOCK_FILE);
            if (!dir.mkdir()) {

                dir.delete();
                if (!dir.mkdir()) {
                    dir = null;
                    if (getBoolean("application.showLocalStorageDialogs", true)) {
                        Resources.showMessageDialog(null,
                                JOptionPane.ERROR_MESSAGE, messageBundle,
                                "fontManager.properties.title",
                                "manager.properties.session.nolock", LOCK_FILE);
                    }
                }

            }
            lockDir = dir;
        }
    }

    private boolean setupDefaultProperties() {
        fontProps = new Properties();

        // create program properties with default
        try {
            // If you application needs to look at other font directories
            // they can be added via the readSystemFonts method.
            fontManager.readSystemFonts(null);
            fontProps = fontManager.getFontProperties();

        } catch (Exception ex) {
            if (getBoolean("application.showLocalStorageDialogs", true)) {
                Resources.showMessageDialog(null,
                        JOptionPane.ERROR_MESSAGE, messageBundle,
                        "fontManager.properties.title",
                        "manager.properties.session.readError",
                        ex);
            }// log the error
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error loading default properties", ex);
            }
            return false;
        }
        return true;
    }

    private void setupHomeDir(String homeString) {
        if (homeString == null) {
            homeString = sysProps.getProperty("swingri.home");
        }

        if (homeString != null) {
            dataDir = new File(homeString);
        } else {
            userHome = new File(sysProps.getProperty("user.home"));
            String dataDirStr = props.getString("application.datadir", DEFAULT_HOME_DIR);
            dataDir = new File(userHome, dataDirStr);
        }

        if (!dataDir.isDirectory()) {
            String path = dataDir.getAbsolutePath();
            boolean create;
            if (props.hasUserRejectedCreatingLocalDataDir()) {
                create = false;
            } else if (getBoolean("application.showLocalStorageDialogs", true)) {
                create = Resources.showConfirmDialog(null,
                        messageBundle, "fontManager.properties.title",
                        "manager.properties.createNewDirectory", path);
                if (!create)
                    props.setUserRejectedCreatingLocalDataDir();
            } else {
                // Always create local-storage directory if show user prompt dialog setting is false.
                create = true;
            }

            if (!create) {
                dataDir = null;
            } else {
                dataDir.mkdirs();
                if (!dataDir.isDirectory()) {
                    // check to make sure that dialog should be shown on the error.
                    if (getBoolean("application.showLocalStorageDialogs", true)) {
                        Resources.showMessageDialog(null,
                                JOptionPane.ERROR_MESSAGE, messageBundle,
                                "fontManager.properties.title",
                                "manager.properties.failedCreation",
                                dataDir.getAbsolutePath());
                    }
                    dataDir = null;
                }
            }
        }
    }

    public boolean getBoolean(String propertyName, boolean defaultValue) {
        Boolean result = getBooleanImpl(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result == Boolean.TRUE;
    }

    private Boolean getBooleanImpl(String propertyName) {
        String value = props.getString(propertyName);
        if (value != null) {
            Boolean result = Parse.parseBoolean(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = props.getString(propertyName);
        if (value != null) {
            Boolean result = Parse.parseBoolean(value, null);
            if (result != null) {
                return result;
            }
            throwBrokenDefault(propertyName, value);
        }
        return null;
    }

    private void throwBrokenDefault(String propertyName, String value) {
        throw new IllegalStateException("Broken default property '" + propertyName + "' value: '" + value + "'");
    }

}
