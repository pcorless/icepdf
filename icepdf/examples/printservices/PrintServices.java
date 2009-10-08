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

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.ri.common.PrintHelper;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;

import javax.print.*;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The PrintServices class is an example of how to use JDK 1.4+ print
 * services with ICEpdf.  The example first finds printers available to the
 * client machine and asks the user which one they whish to print to.  Once the
 * user enters their choice the printing process is started.</p>
 * <p/>
 * <p>Jusing JDK 1.4 or higher should reduce the print spools size when compared
 * to earlier Sun JDK's.  This example requires JDK 1.5 or higher</p>
 * <p/>
 * <p>A PDF documents full path must be specified when the application starts.
 * The following is an example of how the applications is started</p>
 * <p/>
 * <p>>java examples.printServices.PrintServices "F:\PDF Test
 * Cases\support\test_doc.pdf</p>
 *
 * @author ICEsoft Technologies, Inc.
 */
public class PrintServices {

    private static final Logger logger =
            Logger.getLogger(PrintServices.class.toString());

    /**
     * Attempts to Print PDF documents which are specified as application
     * arguments.
     *
     * @param args list of files which should be printed by the application
     */
    public static void main(String[] args) {

        // setup for input from command line
        BufferedReader stdin =
                new BufferedReader(new InputStreamReader(System.in));

        /**
         * Find Available printers
         */
        PrintService[] services =
                PrintServiceLookup.lookupPrintServices(
                        DocFlavor.SERVICE_FORMATTED.PAGEABLE, null);

        int selectedPrinter = 0;
        // ask the user which printer they want, only quite when they type
        // q, otherwise just keep aksing them which printer to use.
        while (!(selectedPrinter > 0 && selectedPrinter <= services.length)) {
            System.out.println(
                    "Please select the printer your wish to print to (q to quit):");
            int printerIndex = 1;
            for (int i = 0, max = services.length - 1; i < max; i++) {
                System.out.println(
                        "  " + printerIndex++ + ". " + services[i].getName());
            }
            System.out.print("Printer selection? ");
            String input = "";
            // get users choice
            try {
                input = stdin.readLine();
            } catch (IOException e) {
                // purposely left empty;
            }

            if (input.length() == 0) {
                System.out.println("Please select a valid printer number.");
                System.out.println();
            } else if (input.toLowerCase().equals("q")) {
                System.exit(0);
            } else {
                try {
                    selectedPrinter = Integer.parseInt(input);
                    if ((selectedPrinter > 0 &&
                            selectedPrinter <= services.length)) {
                        break;
                    }
                } catch (NumberFormatException e) {
                    // ignore error.
                }
                System.out.println("Please select a valid printer number.");
                System.out.println();
            }
        }

        /**
         * Selected Printer, via user input
         */
        PrintService selectedService = services[selectedPrinter - 1];

        /**
         * Show selected Printer default attributes.
         */
        System.out.println(
                "Supported Job Properties for printer: " +
                        selectedService.getName());
        Class[] supportedAttributes =
                selectedService.getSupportedAttributeCategories();
        for (int i = 0, max = supportedAttributes.length - 1; i < max; i++) {
            System.out.println("   " + supportedAttributes[i].getName() +
                    ":= " +
                    selectedService.getDefaultAttributeValue(
                            supportedAttributes[i]));
        }

        /**
         * Create PrinterJob
         */
        DocPrintJob printerJob = selectedService.createPrintJob();

        // Print and document attributes sets.
        HashPrintRequestAttributeSet printRequestAttributeSet =
                new HashPrintRequestAttributeSet();
        HashDocAttributeSet docAttributeSet = new HashDocAttributeSet();

        // unix compression attribute where applicable
//        printRequestAttributeSet.add(Compression.COMPRESS);
        printRequestAttributeSet.add(PrintQuality.DRAFT);

        // change paper
        printRequestAttributeSet.add(MediaSizeName.NA_LEGAL);
        docAttributeSet.add(MediaSizeName.NA_LEGAL);

        // setting margins to full paper size as PDF have their own margins
        MediaSize mediaSize =
                MediaSize.getMediaSizeForName(MediaSizeName.NA_LEGAL);
        float[] size = mediaSize.getSize(MediaSize.INCH);
        printRequestAttributeSet
                .add(new MediaPrintableArea(0, 0, size[0], size[1],
                        MediaPrintableArea.INCH));
        docAttributeSet.add(new MediaPrintableArea(0, 0, size[0], size[1],
                MediaPrintableArea.INCH));

        // display paper size.
        if (logger.isLoggable(Level.INFO)){
            logger.info("Paper Size: " + MediaSizeName.NA_LEGAL.getName() +
                " " + size[0] + " x " + size[1]);
        }

        printRequestAttributeSet.add(new PageRanges(1, 5 ));

        // Open the document, create a PrintHelper and finally print the document
        Document pdf = new Document();

        try {
            // load the file specified by the command line
            pdf.setFile(args[0]);
            SwingController sc = new SwingController();
            DocumentViewController vc = new DocumentViewControllerImpl(sc);
            vc.setDocument(pdf);

            // create a new print helper
            PrintHelper printHelper = new PrintHelper(vc, pdf.getPageTree());
            printHelper.setupPrintService(0, 10, 1, true, false);
            // print the document
            printHelper.print();


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PDFSecurityException e) {
            e.printStackTrace();
        } catch (PDFException e) {
            e.printStackTrace();
        } catch (PrintException e) {
            e.printStackTrace();
        } finally {
            pdf.dispose();
        }
    }
}
