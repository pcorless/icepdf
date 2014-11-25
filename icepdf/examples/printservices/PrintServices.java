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

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.PrintHelper;

import javax.print.*;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.PrintQuality;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The PrintServices class is an example of how to use JDK 1.4+ print
 * services with ICEpdf.  The example first finds printers available to the
 * client machine and asks the user which one they whish to print to.  Once the
 * user enters their choice the printing process is started.</p>
 * <p/>
 * <p>As of ICEpdf 3.0 the PrintHelper class was implemented using print
 * services. This examples show how the to configue and print using the
 * PrintHelper class.  Gernally the page settings are defined with the page
 * constructor followed by a call to printHelper.setupPrintService(...) to
 * setup the printing job.  The Print helper can be used in a headless mode
 * or in a GUI.</p>
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

    static {
        Defs.setProperty("java.awt.headless", "true");
        Defs.setProperty("org.icepdf.core.scaleImages", "false");
        Defs.setProperty("org.icepdf.core.print.disableAlpha", "true");

        // set the graphic rendering hints for speed, we loose quite a bit of quality
        // when converting to TIFF, so no point painting with the extra quality
        Defs.setProperty("org.icepdf.core.print.alphaInterpolation", "VALUE_ALPHA_INTERPOLATION_SPEED");
        Defs.setProperty("org.icepdf.core.print.antiAliasing", "VALUE_ANTIALIAS_ON");
        Defs.setProperty("org.icepdf.core.print.textAntiAliasing", "VALUE_TEXT_ANTIALIAS_OFF");
        Defs.setProperty("org.icepdf.core.print.colorRender", "VALUE_COLOR_RENDER_SPEED");
        Defs.setProperty("org.icepdf.core.print.dither", "VALUE_DITHER_DEFAULT");
        Defs.setProperty("org.icepdf.core.print.fractionalmetrics", "VALUE_FRACTIONALMETRICS_OFF");
        Defs.setProperty("org.icepdf.core.print.interpolation", "VALUE_INTERPOLATION_NEAREST_NEIGHBOR");
        Defs.setProperty("org.icepdf.core.print.render", "VALUE_RENDER_SPEED");
        Defs.setProperty("org.icepdf.core.print.stroke", "VALUE_STROKE_PURE");
    }

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

        MultiDocPrintService mdps[] =
                PrintServiceLookup.lookupMultiDocPrintServices(
                        new DocFlavor[]{DocFlavor.SERVICE_FORMATTED.PAGEABLE}, null);

        MultiDocPrintJob mdpj = mdps[0].createMultiDocPrintJob();
        System.out.println(mdpj);


        int selectedPrinter = 0;
        // ask the user which printer they want, only quite when they type
        // q, otherwise just keep asking them which printer to use.
        while (!(selectedPrinter > 0 && selectedPrinter <= services.length)) {
            System.out.println(
                    "Please select the printer number your wish to print to (q to quit):");
            int printerIndex = 1;
            for (int i = 0, max = services.length - 1; i <= max; i++) {
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
        for (Class supportedAttribute : supportedAttributes) {
            System.out.println("   " + supportedAttribute.getName() +
                    ":= " +
                    selectedService.getDefaultAttributeValue(
                            supportedAttribute));
        }

        // Open the document, create a PrintHelper and finally print the document
        Document pdf = new Document();

        try {
            // load the file specified by the command line
            String filePath;
            if (args.length > 0) {
                filePath = args[0];
            } else {
                throw new FileNotFoundException("Specify a PDF document.");
            }
            pdf.setFile(filePath);

            // create a new print helper with a specified paper size and print
            // quality
            PrintHelper printHelper = new PrintHelper(null, pdf.getPageTree(),
                    0f, MediaSizeName.NA_LEGAL, PrintQuality.DRAFT);
            // try and print pages 1 - 10, 1 copy, scale to fit paper.
            printHelper.setupPrintService(selectedService, 0, 0, 1, true);
            // print the document
            printHelper.print();

        } catch (FileNotFoundException e) {
            logger.log(Level.WARNING, "PDF file not found.", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading PDF file", e);
        } catch (PDFSecurityException e) {
            logger.log(Level.WARNING,
                    "PDF security exception, unspported encryption type.", e);
        } catch (PDFException e) {
            logger.log(Level.WARNING, "Error loading PDF document.", e);
        } catch (PrintException e) {
            logger.log(Level.WARNING, "Error Printing document.", e);
        } finally {
            pdf.dispose();
        }
    }
}
