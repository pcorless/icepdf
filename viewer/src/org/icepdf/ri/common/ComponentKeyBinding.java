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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.views.DocumentViewController;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Utility for adding key bindings to a view container for common functionality
 * usually handled by the existence of menu key listeners.  This class currently
 * only adds the copy text keyboard command (ctr-c) to view container but can
 * be easily extended to handle other keyboard mappings.
 *
 * @since 4.2.2
 *
 */
public class ComponentKeyBinding {

    /**
     * Installs the component key binding on the specified JComponent.
     *
     * @param controller SwingController used by various keyboard commands
     * @param viewerContainer view container to add keyboard mappings too
     */
    public static void install(final SwingController controller, final JComponent viewerContainer){
        Action copyText = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Document document = controller.getDocument();
                DocumentViewController documentViewController =
                        controller.getDocumentViewController();
                if (document != null &&
                         controller.havePermissionToExtractContent() &&
                         !(documentViewController.getDocumentViewModel().isSelectAll() &&
                         document.getNumberOfPages() > 250)) {
                     // get the text.
                     StringSelection stringSelection = new StringSelection(
                         documentViewController.getSelectedText());
                     Toolkit.getDefaultToolkit().getSystemClipboard().
                             setContents(stringSelection, null);
                 } else {
                     Runnable doSwingWork = new Runnable() {
                         public void run() {
                             org.icepdf.ri.util.Resources.showMessageDialog(
                                     viewerContainer,
                                     JOptionPane.INFORMATION_MESSAGE,
                                     controller.getMessageBundle(),
                                     "viewer.dialog.information.copyAll.title",
                                     "viewer.dialog.information.copyAll.msg",
                                     250);
                         }
                     };
                     SwingUtilities.invokeLater(doSwingWork);
                 }
            }
        };

        // add copy text command to input map
        InputMap inputMap = viewerContainer.getInputMap(
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Event.CTRL_MASK),
                                    "copyText");
        viewerContainer.getActionMap().put("copyText",
                                     copyText);
    }
}
