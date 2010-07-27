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
package org.icepdf.ri.common;

import javax.print.CancelablePrintJob;
import javax.print.PrintException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The <code>PrinterTask</code> class is responsible for starting a
 * PrinterJob's print function in a new thread.  This class assumes that the
 * PrinterJob is pre-configured and ready for its print() method to be called.</p>
 *
 * @since 2.0
 */
public class PrinterTask implements Runnable {

    private static final Logger logger =
            Logger.getLogger(PrinterTask.class.toString());

    // PrinterJob to print
    private PrintHelper printHelper;
    private CancelablePrintJob cancelablePrintJob;

    /**
     * Create a new instance of a PrinterTask.
     *
     * @param printHelper print helper
     */
    public PrinterTask(PrintHelper printHelper) {
        this.printHelper = printHelper;
    }

    /**
     * Threads Runnable method.
     */
    public void run() {
        try {
            if (printHelper != null) {
                cancelablePrintJob = printHelper.cancelablePrint();
            }
        } catch (PrintException ex) {
            logger.log(Level.FINE, "Error during printing.", ex);
        }
    }

    /**
     * Cancel the PrinterTask by calling the PrinterJob's cancel() method.
     */
    public void cancel() {
        try {
            if (cancelablePrintJob != null) {
                cancelablePrintJob.cancel();
            }
        } catch (PrintException ex) {
            logger.log(Level.FINE, "Error during printing, " + ex.getMessage());
        }
    }
}
