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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import javax.swing.*;
import java.util.ResourceBundle;


/**
 * Utility class for parsing Strings to alternative types.  Errors are represented
 * with internationalized dialogs and corresponding error messages.
 *
 * @since 1.0
 */
final class Parse {

    private final static String[] booleanNames = {"yes", "no", "true", "false"};
    private final static boolean[] booleans = {true, false, true, false};

    public static Integer parseInteger(String s, ResourceBundle messageBundle) {
        s = s.trim();
        try {
            return new Integer(s);
        } catch (NumberFormatException ex) {
            if (messageBundle != null) {
                Resources.showMessageDialog(null,
                        JOptionPane.INFORMATION_MESSAGE, messageBundle,
                        "parse.title",
                        "parse.integer",
                        s);
            }
        }
        return null;
    }

    public static Long parseLong(String s, ResourceBundle messageBundle) {
        s = s.trim();
        try {
            return new Long(s);
        } catch (NumberFormatException ex) {
            if (messageBundle != null) {
                Resources.showMessageDialog(null,
                        JOptionPane.INFORMATION_MESSAGE, messageBundle,
                        "parse.title",
                        "parse.float",
                        s);
            }
        }
        return null;
    }

    /**
     * Parse a string into a double number.  Error is added to errorShower.
     *
     * @param s string to be coverted to double if possible
     * @return a null if the string could not be converted to double, otherwise
     *         return the Double value of the string.
     */
    public static Double parseDouble(String s, ResourceBundle messageBundle) {
        s = s.trim();
        try {
            return new Double(s);
        } catch (NumberFormatException ex) {
            if (messageBundle != null) {
                Resources.showMessageDialog(null,
                        JOptionPane.INFORMATION_MESSAGE, messageBundle,
                        "parse.title",
                        "parse.double",
                        s);
            }
        }
        return null;
    }

    public static Boolean parseBoolean(String s, ResourceBundle messageBundle) {
        s = s.trim();
        for (int i = 0; i < booleanNames.length; i++) {
            if (s.equalsIgnoreCase(booleanNames[i])) {
                return booleans[i] ? Boolean.TRUE : Boolean.FALSE;
            }
        }
        if (messageBundle != null) {
            Resources.showMessageDialog(null,
                    JOptionPane.INFORMATION_MESSAGE, messageBundle,
                    "parse.title",
                    "parse.choice",
                    s);
        }
        return null;
    }

    public static String parseLookAndFeel(String s, ResourceBundle messageBundle) {
        s = s.trim();
        UIManager.LookAndFeelInfo[] looks = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo look : looks) {
            if (s.equalsIgnoreCase(look.getName())) {
                return look.getClassName();
            }
        }
        if (messageBundle != null) {
            Resources.showMessageDialog(null,
                    JOptionPane.INFORMATION_MESSAGE, messageBundle,
                    "parse.title",
                    "parse.laf",
                    s);
        }
        return null;
    }

}


