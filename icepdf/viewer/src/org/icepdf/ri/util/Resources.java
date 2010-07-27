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
import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * This is a utility class for ldisplaying internationalized dialogs.
 *
 * @since 1.0
 */
public final class Resources extends StringResource {

    public static void showMessageDialog(Component parent,
                                         final int dialogType,
                                         ResourceBundle messageBundle,
                                         String titleKey,
                                         String messageKey) {

        showMessageDialog(parent, dialogType, messageBundle, titleKey, messageKey,
                null, null, null, null);
    }

    public static void showMessageDialog(Component parent,
                                         final int dialogType,
                                         ResourceBundle messageBundle,
                                         String titleKey,
                                         String messageKey,
                                         Object messageArg1) {

        showMessageDialog(parent, dialogType, messageBundle, titleKey, messageKey,
                messageArg1, null, null, null);
    }

    public static void showMessageDialog(Component parent,
                                         final int dialogType,
                                         ResourceBundle messageBundle,
                                         String titleKey,
                                         String messageKey,
                                         Object messageArg1,
                                         Object messageArg2) {

        showMessageDialog(parent, dialogType, messageBundle, titleKey, messageKey,
                messageArg1, messageArg2, null, null);
    }

    public static void showMessageDialog(Component parent,
                                         final int dialogType,
                                         ResourceBundle messageBundle,
                                         String titleKey,
                                         String messageKey,
                                         Object messageArg1,
                                         Object messageArg2,
                                         Object messageArg3) {

        showMessageDialog(parent, dialogType, messageBundle, titleKey, messageKey,
                messageArg1, messageArg2, messageArg3, null);
    }

    public static void showMessageDialog(Component parent,
                                         final int dialogType,
                                         ResourceBundle messageBundle,
                                         String titleKey,
                                         String messageKey,
                                         Object messageArg1,
                                         Object messageArg2,
                                         Object messageArg3,
                                         Object messageArg4) {
        // setup a patterned message
        Object[] messageArguments = {messageArg1, messageArg2, messageArg3,
                messageArg4
        };

        MessageFormat formatter = new MessageFormat(
                messageBundle.getString(messageKey));

        JOptionPane.showMessageDialog(
                parent,
                formatter.format(messageArguments),
                messageBundle.getString(titleKey),
                dialogType);
    }

    public static boolean showConfirmDialog(Component parent,
                                            ResourceBundle messageBundle,
                                            String titleKey,
                                            String messageKey) {

        return showConfirmDialog(parent, messageBundle, titleKey, messageKey,
                null, null, null, null);
    }

    public static boolean showConfirmDialog(Component parent,
                                            ResourceBundle messageBundle,
                                            String titleKey,
                                            String messageKey,
                                            Object messageArg1) {

        return showConfirmDialog(parent, messageBundle, titleKey, messageKey,
                messageArg1, null, null, null);
    }

    public static boolean showConfirmDialog(Component parent,
                                            ResourceBundle messageBundle,
                                            String titleKey,
                                            String messageKey,
                                            Object messageArg1,
                                            Object messageArg2) {

        return showConfirmDialog(parent, messageBundle, titleKey, messageKey,
                messageArg1, messageArg2, null, null);
    }

    public static boolean showConfirmDialog(Component parent,
                                            ResourceBundle messageBundle,
                                            String titleKey,
                                            String messageKey,
                                            Object messageArg1,
                                            Object messageArg2,
                                            Object messageArg3) {

        return showConfirmDialog(parent, messageBundle, titleKey, messageKey,
                messageArg1, messageArg2, messageArg3, null);
    }

    public static boolean showConfirmDialog(Component parent,
                                            ResourceBundle messageBundle,
                                            String titleKey,
                                            String messageKey,
                                            Object messageArg1,
                                            Object messageArg2,
                                            Object messageArg3,
                                            Object messageArg4) {
        // setup a patterned message
        Object[] messageArguments = {messageArg1, messageArg2, messageArg3,
                messageArg4
        };

        MessageFormat formatter = new MessageFormat(
                messageBundle.getString(messageKey));

        return (JOptionPane.showConfirmDialog(
                parent,
                formatter.format(messageArguments),
                messageBundle.getString(titleKey),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
    }


}

