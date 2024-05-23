package org.icepdf.ri.common.print;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.util.Defs;

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

public abstract class PrintHelper implements Printable {

    public static final PrintService[] EMPTY_PRINTSERVICE_ARRAY = new PrintService[0];
    private static final Pattern END_NEWLINE_PATTERN = Pattern.compile("[\r\n]+$");
    private static final Pattern CARET_COLON_PATTERN = Pattern.compile("[^:]*?[:]");
    public static final String PRINTER_NAME_ATTRIBUTE = "printer-name";
    // private final as we execute this on teh host system and it must be immutable.
    private static final String PRINTER_STATUS_COMMAND = "lpstat -d";
    protected static final boolean CLIPPING_FIX_ENABLED =
            Defs.sysPropertyBoolean("org.icepdf.ri.common.printHelper.clippingFix", false);
    private static final float DPI = 72f;

    private static FutureTask<PrintService[]> SERVICES_TASK;

    private PrintService printService;
    private DocAttributeSet docAttributeSet;
    private PrintRequestAttributeSet printRequestAttributeSet;
    private boolean printFitToMargin;
    private int currentPage;
    private int numberOfPages;
    private boolean paintAnnotation = true;
    private boolean paintSearchHighlight = true;

    protected PrintHelper(final DocAttributeSet docAttributeSet,
                          final PrintRequestAttributeSet printRequestAttributeSet) {
        this.docAttributeSet = docAttributeSet;
        this.printRequestAttributeSet = printRequestAttributeSet;
    }

    protected PrintHelper(final PrintQuality printQuality,
                          final MediaSizeName paperSizeName) {
        this(createDocAttributeSet(paperSizeName),
                createPrintRequestAttributeSet(printQuality, paperSizeName));
    }

    public static synchronized void preloadServices() {
        if (SERVICES_TASK == null || SERVICES_TASK.isCancelled() || !SERVICES_TASK.isDone()) {
            reloadServices();
        }
    }

    public static synchronized void reloadServices() {
        if (SERVICES_TASK != null) {
            SERVICES_TASK.cancel(true);
        }
        SERVICES_TASK = new FutureTask<>(PrintHelper::lookForPrintServices);
        new Thread(SERVICES_TASK).start();
    }

    protected static DocAttributeSet createDocAttributeSet(final MediaSizeName paperSizeName) {
        final DocAttributeSet docAttributeSet = new HashDocAttributeSet();
        docAttributeSet.add(paperSizeName);
        // setting margins to full paper size as PDF have their own margins
        final MediaSize mediaSize =
                MediaSize.getMediaSizeForName(paperSizeName);
        final float[] size = mediaSize.getSize(MediaSize.INCH);
        docAttributeSet.add(new MediaPrintableArea(0, 0, size[0], size[1], MediaPrintableArea.INCH));
        return docAttributeSet;
    }

    protected static PrintRequestAttributeSet createPrintRequestAttributeSet(final PrintQuality printQuality,
                                                                             final MediaSizeName paperSizeName) {
        // default printing properties.
        final PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();

        // assign print quality.
        printRequestAttributeSet.add(printQuality);

        // change paper
        printRequestAttributeSet.add(paperSizeName);
        // setting margins to full paper size as PDF have their own margins
        final MediaSize mediaSize =
                MediaSize.getMediaSizeForName(paperSizeName);
        final float[] size = mediaSize.getSize(MediaSize.INCH);
        printRequestAttributeSet.add(new MediaPrintableArea(0, 0, size[0], size[1], MediaPrintableArea.INCH));
        return printRequestAttributeSet;
    }

    /**
     * Number of total pages being printed.
     *
     * @return total pages being printed.
     */
    public int getNumberOfPages() {
        return numberOfPages;
    }

    protected void setNumberOfPages(final int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    /**
     * Gets the page number of the page currently being spooled by the Printable
     * interface.
     *
     * @return current page being spooled by printer.
     */
    public int getCurrentPage() {
        return currentPage;
    }

    protected void setCurrentPage(final int currentPage) {
        this.currentPage = currentPage;
    }

    /**
     * Gets the fit to margin property.  If enabled the page is scaled to fit
     * the paper size maxing out on the smallest paper dimension.
     *
     * @return true if fit to margin is enabled.
     */
    public boolean isPrintFitToMargin() {
        return printFitToMargin;
    }

    protected void setPrintFitToMargin(final boolean fitToMargin) {
        this.printFitToMargin = fitToMargin;
    }

    /**
     * Are page annotations going to be printed?
     *
     * @return true if annotation are to be printed, false otherwise
     */
    public boolean isPaintAnnotation() {
        return paintAnnotation;
    }

    /**
     * Manually enable or disable the printing of annotation for a print job
     *
     * @param paintAnnotation true to paint annotation; otherwise false.
     */
    public void setPaintAnnotation(final boolean paintAnnotation) {
        this.paintAnnotation = paintAnnotation;
    }

    /**
     * Are page search highlight's going to be printed?
     *
     * @return true if highlights are to be printed, false otherwise
     */
    public boolean isPaintSearchHighlight() {
        return paintSearchHighlight;
    }

    /**
     * Manually enable or disable the printing of search highlights for a print job
     *
     * @param paintSearchHighlight true to paint search highlights; otherwise false.
     */
    public void setPaintSearchHighlight(final boolean paintSearchHighlight) {
        this.paintSearchHighlight = paintSearchHighlight;
    }


    /**
     * Utility for showing print dialog for the current printService.  If no
     * print service is assigned the first print service is used to create
     * the print dialog.
     */
    public abstract void showPrintSetupDialog();

    /**
     * Configures the PrinterJob instance with the specified parameters.
     *
     * @param printService          print service to print document to.
     * @param startPage             start of page range, zero-based index.
     * @param endPage               end of page range, one-based index.
     * @param copies                number of copies of pages in print range.
     * @param shrinkToPrintableArea true, to enable shrink to fit printable area;
     *                              false, otherwise.
     */
    public void setupPrintService(final PrintService printService,
                                  final int startPage,
                                  final int endPage,
                                  final int copies,
                                  final boolean shrinkToPrintableArea) {

        // make sure our printable doc knows how many pages to print
        // Has to be set before printerJob.printDialog(), so it can show to
        //  the user which pages it can print
        printFitToMargin = shrinkToPrintableArea;

        // set the number of pages
        printRequestAttributeSet.add(new PageRanges(startPage + 1, endPage + 1));
        // setup number of
        printRequestAttributeSet.add(new Copies(copies));

        this.printService = printService;
    }

    /**
     * Configures the PrinterJob instance with the specified parameters.
     *
     * @param startPage             start of page range, zero-based index.
     * @param endPage               end of page range, one-based index.
     * @param copies                number of copies of pages in print range.
     * @param shrinkToPrintableArea true, to enable shrink to fit printable area;
     *                              false, otherwise.
     * @param showPrintDialog       true, to display a print setup dialog when this method
     *                              is initiated; false, otherwise.  This dialog will be shown after the
     *                              page dialog if it is visible.
     * @return true if print setup should continue, false if printing was cancelled
     * by user interaction with optional print dialog.
     */
    public boolean setupPrintService(final int startPage,
                                     final int endPage,
                                     final int copies,
                                     final boolean shrinkToPrintableArea,
                                     final boolean showPrintDialog) {
        // make sure our printable doc knows how many pages to print
        // Has to be set before printerJob.printDialog(), so it can show to
        // the user which pages it can print
        setPrintFitToMargin(shrinkToPrintableArea);

        // set the number of pages
        getPrintRequestAttributeSet().add(new PageRanges(startPage + 1, endPage + 1));
        // setup number of
        getPrintRequestAttributeSet().add(new Copies(copies));

        // show the print dialog, return false if the user cancels/closes the
        // dialog.
        if (showPrintDialog) {
            setPrintService(getSetupDialog());
            return getPrintService() != null;
        } else {// no dialog and thus printing will continue.
            return true;
        }
    }

    /**
     * Configures the PrinterJob instance with the specified parameters.  this
     * method should only be used by advanced users.
     *
     * @param printService             print service to print document to.
     * @param printRequestAttributeSet print job attribute set.
     * @param shrinkToPrintableArea    true, to enable shrink to fit printable area;
     *                                 false, otherwise.
     */
    public void setupPrintService(final PrintService printService,
                                  final PrintRequestAttributeSet printRequestAttributeSet,
                                  final boolean shrinkToPrintableArea) {
        setPrintFitToMargin(shrinkToPrintableArea);
        setPrintService(printService);
        setPrintRequestAttributeSet(printRequestAttributeSet);
    }

    public abstract CancelablePrintJob cancelablePrint() throws PrintException;

    public abstract void print(PrintJobWatcher printJobWatcher) throws PrintException;

    public abstract int print(Graphics graphics, PageFormat pageFormat, int i) throws PrinterException;

    /**
     * Print a range of pages from the document as specified by #setupPrintService.
     *
     * @throws PrintException if a default printer could not be found or some
     *                        other printing related error.
     */
    public abstract void print() throws PrintException;

    public static PrintService[] getServices() {
        try {
            return SERVICES_TASK.get();
        } catch (final InterruptedException | ExecutionException e) {
            return EMPTY_PRINTSERVICE_ARRAY;
        }
    }

    /**
     * Gets the document attributes currently in use.
     *
     * @return current document attributes.
     */
    public DocAttributeSet getDocAttributeSet() {
        return docAttributeSet;
    }

    protected void setDocAttributeSet(final DocAttributeSet docAttributeSet) {
        this.docAttributeSet = docAttributeSet;
    }

    /**
     * Gets the print request attribute sets.
     *
     * @return attribute set
     */
    public PrintRequestAttributeSet getPrintRequestAttributeSet() {
        return printRequestAttributeSet;
    }

    protected void setPrintRequestAttributeSet(final PrintRequestAttributeSet printRequestAttributeSet) {
        this.printRequestAttributeSet = printRequestAttributeSet;
    }

    /**
     * Gets the currently assigned print service, or gets the default print service.
     *
     * @return current print service, can be null.
     */
    public PrintService getPrintServiceOrDefault() {
        if (printService == null) {
            printService = getServices().length > 0 ? getServices()[0] : null;
        }
        return printService;
    }

    public PrintService getPrintService() {
        return printService;
    }

    protected void setPrintService(final PrintService printService) {
        this.printService = printService;
    }

    /**
     * Returns the MediaSizeName corresponding to the first page in a document
     *
     * @param document The document
     * @return The MediaSizeName if found, A4 by default
     */
    public static MediaSizeName guessMediaSizeName(final Document document) {
        if (document != null) {
            final PageTree pt = document.getPageTree();
            if (pt.getNumberOfPages() > 0) {
                final Rectangle2D.Float pdim = pt.getPage(0).getMediaBox();
                final float width = pdim.width / DPI;
                final float height = pdim.height / DPI;
                return MediaSize.findMedia(width, height, Size2DSyntax.INCH);
            }
        }
        return MediaSizeName.ISO_A4;
    }

    /**
     * Checks that the given printer exists
     *
     * @param printer The printer name
     * @return True if it exists or printer equals 'default'
     */
    public static boolean hasPrinter(final String printer) {
        return printer.equals("default") || Arrays.stream(getServices()).map(PrintService::getName).anyMatch(n -> n.equals(printer));
    }

    /**
     * Sets the printer defined by the given name as current one
     *
     * @param name The name of the printer
     */
    public void setPrinter(final String name) {
        Arrays.stream(getServices()).filter(ps -> ps.getName().equals(name)).findAny().ifPresent(service -> printService = service);
    }

    protected static PrintService[] lookForPrintServices() {
        final PrintService[] services = PrintServiceLookup.lookupPrintServices(
                DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
        // List of printer found services.
        List<PrintService> list = new ArrayList<>();
        // check for a default service and make sure it is at index 0. the lookupPrintServices does not
        // aways put the default printer first in the array.
        final PrintService defaultService = lookupDefaultPrintService();
        if (defaultService != null && services.length > 0) {
            for (final PrintService printService : services) {
                if (printService.equals(defaultService)) {
                    // found the default printer, now swap it with the first index.
                    list.add(0, printService);
                } else {
                    list.add(printService);
                }
            }
        } else {
            list = Arrays.asList(services);
        }
        return list.toArray(EMPTY_PRINTSERVICE_ARRAY);
    }

    /**
     * Finds the default printer for a given system.
     *
     * @return system default print service.
     */
    private static PrintService lookupDefaultPrintService() {
        PrintService printService = null;
        final String defPrinter = getUserPrinterProperty();
        if (!defPrinter.isEmpty()) {
            final PrintService[] services = PrintServiceLookup.lookupPrintServices(
                    DocFlavor.SERVICE_FORMATTED.PAGEABLE, null);
            for (final PrintService service : services) {
                final AttributeSet attributes = service.getAttributes();
                for (final Attribute attribute : attributes.toArray()) {
                    final String name = attribute.getName();
                    final String value = attributes.get(attribute.getClass()).toString();
                    if (PRINTER_NAME_ATTRIBUTE.equals(name) && value.equalsIgnoreCase(defPrinter)) {
                        printService = service;
                        break;
                    }
                }
            }
            return printService;
        } else {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
    }

    /**
     * Executes the PRINTER_STATUS_COMMAND command to get a list of unix printer properties.
     *
     * @return default printer if command ran successfully
     */
    private static String getUserPrinterProperty() {
        StringBuilder ret = new StringBuilder();
        try {
            final Process child = Runtime.getRuntime().exec(PRINTER_STATUS_COMMAND);
            // Get the input stream and read from it
            try (final InputStream in = child.getInputStream()) {
                int c;
                while ((c = in.read()) != -1) {
                    ret.append((char) c);
                }
            }
            ret = new StringBuilder(END_NEWLINE_PATTERN.matcher(ret.toString()).replaceAll(""));
            ret = new StringBuilder(CARET_COLON_PATTERN.matcher(ret.toString()).replaceAll("").trim());
        } catch (final IOException | SecurityException e) {
            // ignore as we may be ona non unix system,  and life goes on.
        }
        ret = new StringBuilder(END_NEWLINE_PATTERN.matcher(ret.toString()).replaceAll(""));
        ret = new StringBuilder(CARET_COLON_PATTERN.matcher(ret.toString()).replaceAll("").trim());
        return ret.toString();
    }

    /**
     * Utility for creating a print setup dialog.
     *
     * @return print service selected by the user, or null if the user
     * cancelled the dialog.
     */
    protected abstract PrintService getSetupDialog();

}
