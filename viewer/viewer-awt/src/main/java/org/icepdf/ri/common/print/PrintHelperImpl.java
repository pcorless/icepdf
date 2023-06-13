/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.print;

import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.GraphicsRenderingHints;

import javax.print.*;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.PrintQuality;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The <code>PrintHelper</code> class is utility class to aid developers in
 * printing PDF document content.  The PrintHelper takes advantage of the
 * Pageable and Printable interfaces availabe in Java 2.</p>
 *
 * @since 2.0
 */
public class PrintHelperImpl extends PrintHelper implements Printable {

    private static final Logger logger =
            Logger.getLogger(PrintHelperImpl.class.toString());

    private final PageTree pageTree;
    private final Container container;
    private final float userRotation;


    /**
     * Creates a new <code>PrintHelper</code> instance using the specified
     * media sized and print quality.
     *
     * @param container     parent container uses to center print dialog.
     * @param pageTree      document page tree.
     * @param rotation      rotation at witch to paint document.
     * @param paperSizeName MediaSizeName constant of paper size to print to.
     * @param printQuality  quality of the print job, draft, quality etc.
     */
    public PrintHelperImpl(Container container, PageTree pageTree,
                           float rotation,
                           MediaSizeName paperSizeName,
                           PrintQuality printQuality) {
        this(container, pageTree, rotation, createDocAttributeSet(paperSizeName),
                createPrintRequestAttributeSet(printQuality, paperSizeName));
    }


    /**
     * Creates a new <code>PrintHelper</code> instance using the specified
     * doc and print attribute sets.  This constructor offers the most flexibility
     * as it allows the attributes sets to be pre configured.  This method
     * should only be used by advanced users.
     *
     * @param container                parent container uses to center print dialog.
     * @param pageTree                 document page tree.
     * @param userRotation             rotation of view
     * @param docAttributeSet          MediaSizeName constant of paper size to print to.
     * @param printRequestAttributeSet quality of the print job, draft, quality etc.
     */
    public PrintHelperImpl(Container container, PageTree pageTree,
                           float userRotation,
                           DocAttributeSet docAttributeSet,
                           PrintRequestAttributeSet printRequestAttributeSet) {
        super(docAttributeSet, printRequestAttributeSet);
        this.container = container;
        this.pageTree = pageTree;
        this.userRotation = userRotation;
        // find available printers
        // default setup, all pages, shrink to fit and no dialog.
        setupPrintService(0, this.pageTree.getNumberOfPages(), 1, true, false);
    }

    @Override
    public void showPrintSetupDialog() {
        PrinterJob pj = PrinterJob.getPrinterJob();
        try {
            pj.setPrintService(getPrintServiceOrDefault());
            // Step 2: Pass the settings to a page dialog and print dialog.
            pj.pageDialog(getPrintRequestAttributeSet());
        } catch (HeadlessException e) {
            logger.log(Level.WARNING, "Headless environment detected, cannot show print dialog", e);
        } catch (PrinterException e) {
            logger.log(Level.WARNING, "Printer does not support print service and or Java2d", e);
        }
    }


    /**
     * Users rotation specified for the print job.
     *
     * @return float value representing rotation, 0 is 0 degrees.
     */
    public float getUserRotation() {
        return userRotation;
    }

    /**
     * Prints the page at the specified index into the specified
     * java.awt.Graphics context in the specified format.
     *
     * @param printGraphics paper graphics context.
     * @param pageFormat    print attributes translated from PrintService
     * @param pageIndex     page to print, zero based.
     * @return A status code of Printable.NO_SUCH_PAGE or Printable.PAGE_EXISTS
     */
    @Override
    public int print(Graphics printGraphics, PageFormat pageFormat, int pageIndex) {

        // update the pageCount
        if (getCurrentPage() != pageIndex) {
            setCurrentPage(pageIndex + 1);
        }

        // Throws NO_SUCH_PAGE to printable interface,  out of page range
        if (pageIndex < 0 || pageIndex >= pageTree.getNumberOfPages()) {
            return Printable.NO_SUCH_PAGE;
        }
        try {

            // Initiate the Page to print, not adding to the pageTree cache purposely,
            // after we finish using it we'll dispose it.
            Page currentPage = pageTree.getPage(pageIndex);
            currentPage.init();
            PDimension pageDim = currentPage.getSize(userRotation);

            // Grab default page width and height
            float pageWidth = (float) pageDim.getWidth();
            float pageHeight = (float) pageDim.getHeight();

            // Default zoom factor
            float zoomFactor = 1.0f;

            Point imageablePrintLocation = new Point();

            // detect if page is being drawn in landscape, if so then we should
            // be rotating the page so that it prints correctly
            float rotation = userRotation;
            boolean isDefaultRotation = true;
            if ((pageWidth > pageHeight &&
                    pageFormat.getOrientation() == PageFormat.PORTRAIT)
                // autorotation for landscape.
//            (pageHeight > pageFormat.getImageableWidth() &&
//                pageFormat.getOrientation() == PageFormat.LANDSCAPE )
            ) {
                // rotate clockwise 90 degrees
                isDefaultRotation = false;
                rotation -= 90;
            }

            Rectangle pageBoundaryClip = null;
            if (isPrintFitToMargin()) {
                // find page size including any popup annotations.
                Dimension dim = pageDim.toDimension();
                Rectangle2D.Float rect = new Rectangle2D.Float(0, 0, dim.width, dim.height);
                List<Annotation> annotations = currentPage.getAnnotations();
                for (Annotation annot : annotations) {
                    Rectangle2D.union(
                            rect,
                            annot.calculatePageSpaceRectangle(currentPage, Page.BOUNDARY_MEDIABOX, rotation, zoomFactor),
                            rect);
                }

                // Get location of imageable area from PageFormat object
                Dimension imageablePrintSize;
                // correct scale to fit calculation for a possible automatic rotation.
                if (isDefaultRotation) {
                    imageablePrintSize = new Dimension(
                            (int) pageFormat.getImageableWidth(),
                            (int) pageFormat.getImageableHeight());
                } else {
                    imageablePrintSize = new Dimension(
                            (int) pageFormat.getImageableHeight(),
                            (int) pageFormat.getImageableWidth());
                }
                float zw = imageablePrintSize.width / rect.width;
                float zh = imageablePrintSize.height / rect.height;
                zoomFactor = Math.min(zw, zh);

                AffineTransform zoomAf = new AffineTransform();
                zoomAf.setToScale(zoomFactor, zoomFactor);
                pageBoundaryClip = zoomAf.createTransformedShape(rect).getBounds();

                imageablePrintLocation.x = -(int) (pageBoundaryClip.x);
                imageablePrintLocation.y = -(int) (pageBoundaryClip.y);

            }
            // apply imageablePrintLocation, normally (0,0)
            printGraphics.translate(imageablePrintLocation.x, imageablePrintLocation.y);
            // apply the new clip is popup printing is active
            printGraphics.setClip(pageBoundaryClip);

            // Paint the page content
            currentPage.paint(printGraphics,
                    GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_MEDIABOX,
                    rotation, zoomFactor, isPaintAnnotation(), isPaintSearchHighlight());

            // Painting a little rectangle seems to fix a strange clipping issue where some images are
            // clipped by about 75%.  Found this by workaround by accident with our trial version.  The clip
            // issue only seems to happen if the PDF is make up many image with none paint primitives.
            if (CLIPPING_FIX_ENABLED) {
                printGraphics.setColor(Color.WHITE);
                printGraphics.drawRect(-9, -9, 10, 10);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.FINEST, "Printing: Page initialization and painting was interrupted: " + pageIndex);
        }

        // Paint content to page buffer to reduce spool size but quality will suffer.
//        Image image = viewController.getDocument().getPageImage(pageIndex,
//                GraphicsRenderingHints.PRINT,
//                Page.BOUNDARY_CROPBOX,
//                rotation, zoomFactor);
//        printGraphics.drawImage(image,0,0,null);
//        image.flush();

        return Printable.PAGE_EXISTS;
    }

    @Override
    public void print() throws PrintException {
        if (getPrintServiceOrDefault() != null) {

            // calculate total pages being printed
            calculateTotalPagesToPrint();

            getPrintServiceOrDefault().createPrintJob().print(
                    new SimpleDoc(this,
                            DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                            null),
                    getPrintRequestAttributeSet());
        } else {
            logger.fine("No print could be found to print to.");
        }

    }

    @Override
    public CancelablePrintJob cancelablePrint() throws PrintException {

        // make sure we have a service, if not we assign the default printer
        if (getPrintServiceOrDefault() != null) {

            // calculate total pages being printed
            calculateTotalPagesToPrint();

            DocPrintJob printerJob = getPrintServiceOrDefault().createPrintJob();
            if (printerJob instanceof CancelablePrintJob) {
                CancelablePrintJob cancelablePrintJob = (CancelablePrintJob) printerJob;
                cancelablePrintJob.print(
                        new SimpleDoc(this,
                                DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                                null),
                        getPrintRequestAttributeSet());

                return cancelablePrintJob;
            }
        }
        return null;
    }

    private void calculateTotalPagesToPrint() {
        // iterate over page ranges to find out how many pages are to
        // be printed
        final PageRanges pageRanges = (PageRanges)
                getPrintRequestAttributeSet().get(PageRanges.class);
        setNumberOfPages(0);
        // we need to loop over the multiple ranges as commas can be used
        // to specify more then one range.  Make sure the specified pages
        // fall with in the range allowed by the document.
        int start, end;
        for (final int[] ranges : pageRanges.getMembers()) {
            start = ranges[0];
            end = ranges[1];
            if (start < 1) {
                start = 1;
            }
            if (end > pageTree.getNumberOfPages()) {
                end = pageTree.getNumberOfPages();
            }
            setNumberOfPages(getNumberOfPages() + end - start + 1);
        }
    }

    @Override
    public void print(PrintJobWatcher printJobWatcher) throws PrintException {

        // make sure we have a service, if not we assign the default printer
        if (getPrintServiceOrDefault() != null) {

            // calculate total pages being printed
            calculateTotalPagesToPrint();

            DocPrintJob printerJob = getPrintServiceOrDefault().createPrintJob();
            printJobWatcher.setPrintJob(printerJob);

            printerJob.print(
                    new SimpleDoc(this,
                            DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                            null),
                    getPrintRequestAttributeSet());

            printJobWatcher.waitForDone();
        } else {
            logger.fine("No print could be found to print to.");
        }
    }

    @Override
    protected PrintService getSetupDialog() {
        final int offset = 50;
        // find graphic configuration for the window the viewer is in.
        Window window = SwingUtilities.getWindowAncestor(
                container);
        GraphicsConfiguration graphicsConfiguration =
                window == null ? null : window.getGraphicsConfiguration();
        // try and trim the getServices() list.
        int baseX = window != null ? window.getX() : container.getX();
        int baseY = window != null ? window.getY() : container.getY();


        return ServiceUI.printDialog(graphicsConfiguration,
                baseX + offset,
                baseY + offset,
                getServices(), getPrintServiceOrDefault(),
                DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                getPrintRequestAttributeSet());
    }
}
