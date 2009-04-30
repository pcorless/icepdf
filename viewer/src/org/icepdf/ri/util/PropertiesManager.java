/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.util;

import org.icepdf.core.pobjects.Document;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;


/**
 * <p>This class provides a very basic Properties Management system for the
 * viewer application.  Settings such as window location and temporary file
 * information is managed by this class.</p>
 *
 * @since 1.0
 */
public class PropertiesManager {

    private static final Logger logger =
            Logger.getLogger(PropertiesManager.class.toString());

    private static final String DEFAULT_HOME_DIR = ".icesoft/icepdf_viewer";
    private static final String LOCK_FILE = "_syslock";
    private final static String USER_FILENAME = "pdfviewerri.properties";
    private final static String BACKUP_FILENAME = "old_pdfviewerri.properties";

    //default file for all not specified properties
    private static final String DEFAULT_PROP_FILE = "ICEpdfDefault.properties";
    private static final String DEFAULT_PROP_FILE_PATH = "org/icepdf/ri/viewer/res/";
    public static final String DEFAULT_MESSAGE_BUNDLE = "org.icepdf.ri.resources.MessageBundle";

    private static final String PROPERTY_DEFAULT_FILE_PATH = "application.defaultFilePath";
    private static final String PROPERTY_DEFAULT_URL = "application.defaultURL";

    //the version name, used in about dialog and start-up message
    String versionName = Document.getLibraryVersion();

    private boolean unrecoverableError;

    Properties sysProps;

    private ResourceBundle messageBundle;

    File userHome;

    //the swingri home directory
    private File dataDir;

    //not to save the bookmarks and properties if lockDir == null, that is
    //when we do not own the lock
    private File lockDir;

    private File propertyFile;
    private Date myLastModif = new Date();

    private Properties props;
    private Properties defaultProps;

    private boolean userRejectedCreatingLocalDataDir;
    private boolean thisExecutionTriedCreatingLocalDataDir;


    public PropertiesManager(Properties sysProps, ResourceBundle messageBundle) {
        unrecoverableError = true;
        this.sysProps = sysProps;

        this.messageBundle = messageBundle;

        if (!setupDefaultProperties()) {
            return;
        }

        setupHomeDir(null);

        recordMofifTime();

        setupLock();

        loadProperties();

        unrecoverableError = false;
    }

    private boolean setupDefaultProperties() {
        defaultProps = new Properties();

        // create program properties with default
        try {

            InputStream in = getResourceAsStream(DEFAULT_PROP_FILE_PATH, DEFAULT_PROP_FILE);
            try {
                defaultProps.load(in);
            } finally {
                in.close();
            }
        } catch (IOException ex) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.session.readError",
                    DEFAULT_PROP_FILE);
            return false;
        }

        props = defaultProps;
        return true;
    }


    boolean hasUserRejectedCreatingLocalDataDir() {
        return userRejectedCreatingLocalDataDir;
    }

    void setUserRejectedCreatingLocalDataDir() {
        userRejectedCreatingLocalDataDir = true;
    }

    boolean unrecoverableError() {
        return unrecoverableError;
    }

    private boolean ownLock() {
        return lockDir != null;
    }


    private void setupHomeDir(String homeString) {
        if (homeString == null) {
            homeString = sysProps.getProperty("swingri.home");
        }

        if (homeString != null) {
            dataDir = new File(homeString);
        } else {
            userHome = new File(sysProps.getProperty("user.home"));
            String dataDirStr = props.getProperty("application.datadir", DEFAULT_HOME_DIR);
            dataDir = new File(userHome, dataDirStr);
        }

        if (!dataDir.isDirectory()) {
            String path = dataDir.getAbsolutePath();
            boolean create;
            if (hasUserRejectedCreatingLocalDataDir()) {
                create = false;
            } else if ((getBoolean("application.showLocalStorageDialogs", true))) {
                create = Resources.showConfirmDialog(null,
                        messageBundle, "manager.properties.title",
                        "manager.properties.createNewDirectory", path);
                if (!create)
                    setUserRejectedCreatingLocalDataDir();
            } else {
                // Always create local-storage directory if show user prompt dialog setting is false.
                create = true;
            }

            if (!create) {
                dataDir = null;
            } else {
                dataDir.mkdirs();
                if (!dataDir.isDirectory()) {
                    Resources.showMessageDialog(null,
                            JOptionPane.ERROR_MESSAGE, messageBundle,
                            "manager.properties.title",
                            "manager.properties.failedCreation",
                            dataDir.getAbsolutePath());
                    dataDir = null;
                }
                thisExecutionTriedCreatingLocalDataDir = true;
            }
        }
    }


    private void setupLock() {
        if (dataDir == null) {
            lockDir = null;
        } else {
            File dir = new File(dataDir, LOCK_FILE);
            if (!dir.mkdir()) {
                // Removed dialog window, always assume we can remove another lock - 12/04/2003 - kfyten
                // boolean removeIt = res.displayYesOrNo("session.anotherlock", session_date);

                dir.delete();
                if (!dir.mkdir()) {
                    dir = null;
                    Resources.showMessageDialog(null,
                            JOptionPane.ERROR_MESSAGE, messageBundle,
                            "manager.properties.title",
                            "manager.properties.session.nolock", LOCK_FILE);
                }

            }
            lockDir = dir;
        }
    }

    public synchronized void loadProperties() {

        if (dataDir != null) {
            propertyFile = new File(dataDir, USER_FILENAME);
            // load properties from last invocation
            if (propertyFile.exists()) {
                try {
                    InputStream in = new FileInputStream(propertyFile);
                    try {
                        props.load(in);
                    } finally {
                        in.close();
                    }
                } catch (IOException ex) {
                    Resources.showMessageDialog(null,
                            JOptionPane.ERROR_MESSAGE, messageBundle,
                            "manager.properties.title",
                            "manager.properties.session.readError", propertyFile.getAbsolutePath());
                }
            }
        }
    }


    public synchronized void saveAndEnd() {
        if (dataDir != null) {
            saveProperties();
            lockDir.delete();
        }
    }

    public synchronized void saveProperties() {
        if (ownLock()) {

            long lastModified = propertyFile.lastModified();
            boolean saveIt = true;

            if (thisExecutionTriedCreatingLocalDataDir) {
                saveIt = true;
            } else if (getBoolean("application.showLocalStorageDialogs", true)) {
                if (lastModified == 0L) {//file does not exist
                    saveIt = Resources.showConfirmDialog(null,
                            messageBundle,
                            "manager.properties.title",
                            "manager.properties.deleted", propertyFile.getAbsolutePath());
                } else if (myLastModif.before(new Date(lastModified))) {
                    saveIt = Resources.showConfirmDialog(null,
                            messageBundle,
                            "manager.properties.title",
                            "manager.properties.modified", myLastModif);
                }
            }

            if (!saveIt) {
                return;
            }

            try {
                FileOutputStream out = new FileOutputStream(propertyFile);
                try {
                    props.store(out, "-- ICEbrowser properties --");
                } finally {
                    out.close();
                }
                recordMofifTime();
            } catch (IOException ex) {
                Resources.showMessageDialog(null,
                        JOptionPane.ERROR_MESSAGE, messageBundle,
                        "manager.properties.title",
                        "manager.properties.saveError", ex);
            }
        }
    }

    private void recordMofifTime() {
        Calendar c = new GregorianCalendar();
        c.setTime(new Date());
        c.set(Calendar.MINUTE, c.get(Calendar.MINUTE) + 1);
        c.set(Calendar.SECOND, 0);
        myLastModif = new Date((c.getTime().getTime() / 1000) * 1000);
    }

    public boolean backupProperties() {
        boolean result = false;
        if (ownLock()) {
            File backupFile = new File(dataDir, BACKUP_FILENAME);
            try {
                FileOutputStream out = new FileOutputStream(backupFile);
                try {
                    props.store(out, "-- ICEbrowser properties backup --");
                    result = true;
                } finally {
                    out.close();
                }
            } catch (IOException ex) {
                Resources.showMessageDialog(null,
                        JOptionPane.ERROR_MESSAGE, messageBundle,
                        "manager.properties.title",
                        "manager.properties.saveError", ex);
            }
        }
        return result;
    }


    public void set(String propertyName, String value) {
        props.put(propertyName, value);
    }

    public void remove(String propertyName) {
        props.remove(propertyName);
    }

    public String getString(String propertyName, String defaultValue) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            return value.trim();
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            return value.trim();
        }
        return defaultValue;
    }

    public String getString(String propertyName) {
        String value = getString(propertyName, null);
        if (value == null) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.missingProperty", propertyName, value);
        }
        return value;
    }

    public int getInt(String propertyName, int defaultValue) {
        Integer result = getIntImpl(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result.intValue();
    }

    public int getInt(String propertyName) {
        Integer result = getIntImpl(propertyName);
        if (result == null) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.missingProperty", propertyName, result);
            return 0;
        }
        return result.intValue();
    }

    private Integer getIntImpl(String propertyName) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            Integer result = Parse.parseInteger(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            Integer result = Parse.parseInteger(value, null);
            if (result != null) {
                return result;
            }
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.brokenProperty ", propertyName, value);
        }
        return null;
    }

    public void setInt(String propertyName, int value) {
        set(propertyName, Integer.toString(value));
    }

    /**
     * Return a double value for the respective <code>propertyName</code>.
     * If there is no <code>propertyName</code> then return the
     * <code>defaultValue</code>.
     *
     * @param propertyName Name of property from the ICEdefault.properties file.
     * @param defaultValue default value if the <code>propertyName</code>can not be found.
     * @return double value of the <code>propertyName</code>.
     * @since 6.0
     */
    public double getDouble(String propertyName, double defaultValue) {
        Double result = getDoubleImpl(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result.doubleValue();
    }

    /**
     * Return a double value for the respective <code>propertyName</code>.
     *
     * @param propertyName Name of property from the ICEdefault.properties file.
     * @return double value of the <code>propertyName</code>
     * @since 6.0
     */
    public double getDouble(String propertyName) {
        Double result = getDoubleImpl(propertyName);
        if (result == null) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.missingProperty", propertyName, result);
            return 0;
        }
        return result.doubleValue();
    }

    /**
     * Return the a double value for the respective <code>propertyName</code>.
     * If the property value is null then the <code>propertyName</code> is removed
     * from the properties object.
     *
     * @param propertyName Name of propertie from the ICEdefault.properites file.
     * @return double value of the <code>propertyName</code>
     * @since 6.0
     */
    private Double getDoubleImpl(String propertyName) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            Double result = Parse.parseDouble(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            Double result = Parse.parseDouble(value, messageBundle);
            if (result != null) {
                return result;
            }
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.brokenProperty ", propertyName, value);
        }
        return null;
    }

    public void setDouble(String propertyName, double value) {
        set(propertyName, Double.toString(value));
    }

    public long getLong(String propertyName, long defaultValue) {
        Long result = getLongImpl(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result.longValue();
    }

    public long getLong(String propertyName) {
        Long result = getLongImpl(propertyName);
        if (result == null) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.missingProperty", propertyName, result);
            return 0;
        }
        return result.longValue();
    }

    private Long getLongImpl(String propertyName) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            Long result = Parse.parseLong(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            Long result = Parse.parseLong(value, null);
            if (result != null) {
                return result;
            }
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.brokenProperty ", propertyName, value);
        }
        return null;
    }

    public void setLong(String propertyName, long value) {
        set(propertyName, Long.toString(value));
    }

    public boolean getBoolean(String propertyName, boolean defaultValue) {
        Boolean result = getBooleanImpl(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result == Boolean.TRUE;
    }

    public boolean getBoolean(String propertyName) {
        Boolean result = getBooleanImpl(propertyName);
        if (result == null) {
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.missingProperty", propertyName, result);
        }
        return result == Boolean.TRUE;
    }

    private Boolean getBooleanImpl(String propertyName) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            Boolean result = Parse.parseBoolean(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            Boolean result = Parse.parseBoolean(value, null);
            if (result != null) {
                return result;
            }
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.brokenProperty ", propertyName, value);
        }
        return null;
    }

    public void setBoolean(String propertyName, boolean value) {
        set(propertyName, value ? "true" : "false");
    }

    public String getSystemEncoding() {
        return (new OutputStreamWriter(new ByteArrayOutputStream())).getEncoding();
    }

    public String getLookAndFeel(String propertyName, String defaultValue) {
        String value = (String) props.get(propertyName);
        if (value != null) {
            String result = Parse.parseLookAndFeel(value, messageBundle);
            if (result != null) {
                return result;
            }
            props.remove(propertyName);
        }
        value = (String) defaultProps.get(propertyName);
        if (value != null) {
            String result = Parse.parseLookAndFeel(value, null);
            if (result != null) {
                return result;
            }
            defaultProps.remove(propertyName);
            Resources.showMessageDialog(null,
                    JOptionPane.ERROR_MESSAGE, messageBundle,
                    "manager.properties.title",
                    "manager.properties.lafError", value);
        }
        return defaultValue;
    }

    public String getDefaultFilePath() {
        return getString(PROPERTY_DEFAULT_FILE_PATH, null);
    }

    public String getDefaultURL() {
        return getString(PROPERTY_DEFAULT_URL, null);
    }

    public void setDefaultFilePath(String defaultFilePath) {
        if (defaultFilePath == null)
            remove(PROPERTY_DEFAULT_FILE_PATH);
        else
            set(PROPERTY_DEFAULT_FILE_PATH, defaultFilePath);
    }

    public void setDefaultURL(String defaultURL) {
        if (defaultURL == null)
            remove(PROPERTY_DEFAULT_URL);
        else
            set(PROPERTY_DEFAULT_URL, defaultURL);
    }

    public InputStream getResourceAsStream(String prefix, String resourcePath) {
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
        ClassLoader cl = getClass().getClassLoader();
        if (cl != null) {
            InputStream result = cl.getResourceAsStream(resourcePath);
            if (result != null) {
                return result;
            }
        }
        return ClassLoader.getSystemResourceAsStream(resourcePath);
    }

    public static String makeResPath(String prefix, String base_name) {
        if (base_name.length() != 0 && base_name.charAt(0) == '/') {
            return base_name.substring(1, base_name.length());
        } else if (prefix == null) {
            return base_name;
        } else {
            return prefix + base_name;
        }
    }


}

