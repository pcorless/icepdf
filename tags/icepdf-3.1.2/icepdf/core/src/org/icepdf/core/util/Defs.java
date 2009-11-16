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
package org.icepdf.core.util;

import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;


public class Defs {

    private static final Logger logger =
            Logger.getLogger(Defs.class.toString());

    /**
     * Equivalent to property(name, null)
     */
    public static String property(String name) {
        return property(name, null);
    }

    /**
     * Return value for system property <code>name</code> or *
     * <code>defaultValue</code> if the property does not exist * or a security
     * manager denies access to it
     */
    public static String property(String name, String defaultValue) {
        try {
            return System.getProperty(name, defaultValue);
        }
        catch (SecurityException ex) {
            // recal method so that property change takes effect
            property(name, defaultValue);
        }
        return defaultValue;
    }


    /**
     * Return value for system property <code>name</code> parsed as int or *
     * <code>defaultValue</code> if the property does not exist * or a security
     * manager denies access to it
     */
    public static int intProperty(String name, int defaultValue) {
        String value = property(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException ex) {
                logger.log(Level.FINE, "Failed to parse property.", ex);
            }
        }
        return defaultValue;
    }

    /**
     * Shortcut for <code>booleanProperty(name, false)</code>
     */
    public static boolean booleanProperty(String name) {
        return booleanProperty(name, false);
    }

    /**
     * If security manager allow access to the system property <b>name</b> * and
     * it exists, then return true if it is set to <i>yes</i>, <i>true</i> * and
     * false if set to <i>no</i>, <i>false</i>. Otherwise returns *
     * <b>defaultValue</b>
     */
    public static boolean booleanProperty(String name, boolean defaultValue) {
        String value = property(name);
        if (value != null) {
            switch (value.length()) {
                case 2:
                    if ("no".equals(value.toLowerCase())) return false;
                    break;
                case 3:
                    if ("yes".equals(value.toLowerCase())) return true;
                    break;
                case 4:
                    if ("true".equals(value.toLowerCase())) return true;
                    break;
                case 5:
                    if ("false".equals(value.toLowerCase())) return false;
                    break;
            }
        }
        return defaultValue;
    }

    /**
     * Alias to property(String name)
     */
    public static String sysProperty(String name) {
        return property(name);
    }

    /**
     * Alias to property(String name, String defaultValue)
     */
    public static String sysProperty(String name, String defaultValue) {
        return property(name, defaultValue);
    }

    /**
     * Alias to intProperty(String name, int defaultValue)
     */
    public static int sysPropertyInt(String name, int defaultValue) {
        return intProperty(name, defaultValue);
    }

    /**
     * Alias to booleanProperty(String name)
     */
    public static boolean sysPropertyBoolean(String name) {
        return booleanProperty(name);
    }

    /**
     * Alias to booleanProperty(String name, boolean defaultValue)
     */
    public static boolean sysPropertyBoolean(String name,
                                             boolean defaultValue) {
        return booleanProperty(name, defaultValue);
    }

    /**
     * Set system property to <code>value</code>. * If SecurityManager denies
     * property modification, silently ignore * property change. * if value is
     * null, property won't be set.
     */
    public static void setProperty(String property, Object value) {
        try {
            Properties prop = System.getProperties();
            if (value != null) {
                prop.put(property, value);
            }
        }
        catch (SecurityException ex) {
            // recall method so that property change takes effect
            setProperty(property, value);
        }
    }


    /**
     * Set system property to <code>value</code>. * If SecurityManager denies
     * property modification, print debug trace
     */
    public static void setSystemProperty(String name, String value) {
        setProperty(name, value);
    }


}
