/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.fx.print;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.SheetCollate;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/**
 * Prints a document with Java2D: each page is painted by core straight into the printer's graphics,
 * so text and vector art stay vector (no page images, small spool files).  Every page gets its own
 * page format, so mixed page sizes and orientations each fit their sheet.  No JavaFX types; usable
 * headless (print to a {@code StreamPrintService} for a PostScript file).
 * <p>
 * JavaFX's own {@code javafx.print} is a wrapper around the same AWT {@code PrinterJob}, but can only
 * print scene-graph nodes, which for a PDF page means a page-sized bitmap; so printing goes to AWT
 * directly.  {@link #print} blocks: run it off the FX thread ({@code PdfView.print} does).
 */
public class DocumentPrinter {

    /** Print resolution used when the document permits only low-quality printing. */
    public static final int LOW_RESOLUTION_DPI = 150;

    /** Runs a page's paint under the lock the viewer takes for annotation edits on that page. */
    @FunctionalInterface
    public interface PageLock {
        void withPage(int pageIndex, Runnable paint);
    }

    /** Told as each page starts printing. */
    @FunctionalInterface
    public interface Progress {
        /**
         * @param page  one-based number of the page being printed, within this job
         * @param total pages in the job (one copy)
         */
        void printing(int page, int total);
    }

    private final Document document;
    private final PrintSettings settings;
    private PageLock pageLock = (pageIndex, paint) -> paint.run();
    private volatile PrinterJob job;
    private volatile boolean cancelled;

    public DocumentPrinter(Document document, PrintSettings settings) {
        this.document = document;
        this.settings = settings;
    }

    public void setPageLock(PageLock pageLock) {
        this.pageLock = pageLock != null ? pageLock : (pageIndex, paint) -> paint.run();
    }

    /**
     * Prints the job, blocking until it has been handed to the printer.  Returns quietly if
     * {@link #cancel()} is called meanwhile.
     *
     * @throws PrinterException no printer, or the printer failed
     */
    public void print(Progress progress) throws PrinterException {
        int[] pages = settings.getPages();
        if (pages.length == 0 || cancelled) return;
        PrintService service = settings.getPrinter() != null ? settings.getPrinter()
                : PrintServiceLookup.lookupDefaultPrintService();
        if (service == null) throw new PrinterException("No printer is available.");
        PrinterJob printerJob = PrinterJob.getPrinterJob();
        printerJob.setPrintService(service);
        printerJob.setJobName(jobName());
        PrintRequestAttributeSet attributes = attributes(settings);
        // the paper and its printable area as this printer reports them for the chosen media.
        PageFormat paper = printerJob.getPageFormat(attributes);
        printerJob.setPageable(new Pages(pages, paper, progress));
        job = printerJob;
        if (cancelled) return;
        try {
            printerJob.print(attributes);
        } catch (PrinterAbortException e) {
            // cancelled
        }
    }

    /** Stops the job; pages already sent to the printer may still print. */
    public void cancel() {
        cancelled = true;
        PrinterJob printerJob = job;
        if (printerJob != null) printerJob.cancel();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * The sheet a printer uses for the settings' paper: its size and printable area, portrait.
     *
     * @return null if the printer can't be used
     */
    public static PageFormat paperFormat(PrintService printer, PrintSettings settings) {
        try {
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(printer);
            return printerJob.getPageFormat(attributes(settings));
        } catch (PrinterException | RuntimeException e) {
            return null;
        }
    }

    static PrintRequestAttributeSet attributes(PrintSettings settings) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(settings.getCopies()));
        attributes.add(settings.isCollate() ? SheetCollate.COLLATED : SheetCollate.UNCOLLATED);
        if (settings.getPaper() != null) attributes.add(settings.getPaper());
        if (settings.getSides() != null) attributes.add(settings.getSides());
        return attributes;
    }

    private String jobName() {
        if (settings.getJobName() != null) return settings.getJobName();
        String title = document.getInfo() != null ? document.getInfo().getTitle() : null;
        return title != null && !title.isBlank() ? title : "PDF document";
    }

    /** Where a page goes on the sheet: its scale and top-left corner, in printer points. */
    record Placement(double scale, double x, double y) {
    }

    /**
     * Places a page of {@code pageW} x {@code pageH} points in a printable area, centred.
     */
    static Placement place(double pageW, double pageH, double areaX, double areaY, double areaW, double areaH,
                           PrintSettings.Scaling scaling) {
        double fit = Math.min(areaW / pageW, areaH / pageH);
        double scale = switch (scaling) {
            case FIT -> fit;
            case SHRINK_LARGE -> Math.min(1, fit);
            case ACTUAL_SIZE -> 1;
        };
        return new Placement(scale, areaX + (areaW - pageW * scale) / 2, areaY + (areaH - pageH * scale) / 2);
    }

    /** Whether a page prints on a landscape sheet. */
    static boolean landscape(double pageW, double pageH, double paperW, double paperH,
                             PrintSettings.Orientation orientation) {
        return switch (orientation) {
            case PORTRAIT -> false;
            case LANDSCAPE -> true;
            // turn the sheet when the page's shape doesn't match the paper's.
            case AUTO -> (pageW > pageH) != (paperW > paperH);
        };
    }

    /** The job's pages, each with its own format, painted by core. */
    private final class Pages implements Pageable, Printable {
        private final int[] pages;
        private final PageFormat paper;
        private final Progress progress;
        private int reported = -1;

        Pages(int[] pages, PageFormat paper, Progress progress) {
            this.pages = pages;
            this.paper = paper;
            this.progress = progress;
        }

        @Override
        public int getNumberOfPages() {
            return pages.length;
        }

        @Override
        public PageFormat getPageFormat(int jobIndex) {
            PageFormat format = (PageFormat) paper.clone();
            PDimension size = pageSize(jobIndex);
            boolean landscape = size != null && landscape(size.getWidth(), size.getHeight(),
                    paper.getPaper().getWidth(), paper.getPaper().getHeight(), settings.getOrientation());
            format.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
            return format;
        }

        @Override
        public Printable getPrintable(int jobIndex) {
            return this;
        }

        private PDimension pageSize(int jobIndex) {
            Page page = document.getPageTree().getPage(pages[jobIndex]);
            return page != null ? page.getSize(settings.getBoundary(), 0, 1f) : null;
        }

        @Override
        public int print(Graphics graphics, PageFormat format, int jobIndex) throws PrinterException {
            if (jobIndex < 0 || jobIndex >= pages.length) return NO_SUCH_PAGE;
            if (cancelled) throw new PrinterAbortException();
            // Java2D may call this more than once per page (a measuring pass, then bands).
            if (jobIndex != reported) {
                reported = jobIndex;
                if (progress != null) progress.printing(jobIndex + 1, pages.length);
            }
            int pageIndex = pages[jobIndex];
            Page page = document.getPageTree().getPage(pageIndex);
            if (page == null) return PAGE_EXISTS;
            PrinterException[] failure = new PrinterException[1];
            pageLock.withPage(pageIndex, () -> {
                try {
                    paintPage((Graphics2D) graphics.create(), format, page);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure[0] = new PrinterAbortException();
                }
            });
            if (failure[0] != null) throw failure[0];
            return PAGE_EXISTS;
        }

        private void paintPage(Graphics2D g, PageFormat format, Page page) throws InterruptedException {
            try {
                page.init();
                PDimension size = page.getSize(settings.getBoundary(), 0, 1f);
                Placement at = place(size.getWidth(), size.getHeight(), format.getImageableX(),
                        format.getImageableY(), format.getImageableWidth(), format.getImageableHeight(),
                        settings.getScaling());
                // never outside the printable area or the page (actual size can overflow the sheet).
                g.clip(new Rectangle2D.Double(format.getImageableX(), format.getImageableY(),
                        format.getImageableWidth(), format.getImageableHeight()));
                g.clip(new Rectangle2D.Double(at.x(), at.y(), size.getWidth() * at.scale(),
                        size.getHeight() * at.scale()));
                g.translate(at.x(), at.y());
                if (settings.isLowResolution()) {
                    paintAsImage(g, page, size, at.scale());
                } else {
                    page.paint(g, GraphicsRenderingHints.PRINT, settings.getBoundary(), 0f, (float) at.scale(),
                            settings.isAnnotations(), false);
                }
            } finally {
                g.dispose();
            }
        }

        /** Low-quality printing: the page as a {@value #LOW_RESOLUTION_DPI} dpi image. */
        private void paintAsImage(Graphics2D g, Page page, PDimension size, double scale)
                throws InterruptedException {
            double imageZoom = scale * LOW_RESOLUTION_DPI / 72.0;
            int w = Math.max(1, (int) Math.ceil(size.getWidth() * imageZoom));
            int h = Math.max(1, (int) Math.ceil(size.getHeight() * imageZoom));
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D ig = image.createGraphics();
            try {
                ig.setColor(Color.WHITE);
                ig.fillRect(0, 0, w, h);
                ig.setClip(0, 0, w, h);
                page.paint(ig, GraphicsRenderingHints.PRINT, settings.getBoundary(), 0f, (float) imageZoom,
                        settings.isAnnotations(), false);
            } finally {
                ig.dispose();
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, java.awt.geom.AffineTransform.getScaleInstance(scale / imageZoom, scale / imageZoom),
                    null);
            image.flush();
        }
    }
}
