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

import org.icepdf.core.pobjects.Page;

import javax.print.PrintService;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What to print and how: the printer, which pages, copies, paper and how pages sit on it.  A plain
 * bean; {@link PdfPrintDialog} fills one in, or an application can build one to print without a
 * dialog.  Has no JavaFX types, so it can be used headless.
 */
public class PrintSettings {

    /** How a page is sized on the paper's printable area. */
    public enum Scaling {
        /** Scaled up or down to fill the printable area. */
        FIT,
        /** Only pages larger than the printable area are shrunk; others print at actual size. */
        SHRINK_LARGE,
        /** 100%: one PDF point is one printer point.  Larger pages are cropped. */
        ACTUAL_SIZE
    }

    /** Paper orientation. */
    public enum Orientation {
        /** Per page: landscape for pages wider than tall, portrait otherwise. */
        AUTO,
        PORTRAIT,
        LANDSCAPE
    }

    private PrintService printer;
    private int[] pages = new int[0];
    private int copies = 1;
    private boolean collate = true;
    private MediaSizeName paper;
    private Scaling scaling = Scaling.FIT;
    private Orientation orientation = Orientation.AUTO;
    private boolean annotations = true;
    private Sides sides;
    private int boundary = Page.BOUNDARY_CROPBOX;
    private boolean lowResolution;
    private String jobName;

    /** The printer; null prints to the system default. */
    public PrintService getPrinter() {
        return printer;
    }

    public void setPrinter(PrintService printer) {
        this.printer = printer;
    }

    /** Zero-based page indexes to print, in order. */
    public int[] getPages() {
        return pages.clone();
    }

    public void setPages(int[] pages) {
        this.pages = pages != null ? pages.clone() : new int[0];
    }

    /** Every page of a document of {@code pageCount} pages. */
    public void setAllPages(int pageCount) {
        pages = new int[Math.max(0, pageCount)];
        for (int i = 0; i < pages.length; i++) pages[i] = i;
    }

    public int getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = Math.max(1, copies);
    }

    /** With several copies, whether each copy is printed whole (1,2,3,1,2,3) rather than 1,1,2,2,3,3. */
    public boolean isCollate() {
        return collate;
    }

    public void setCollate(boolean collate) {
        this.collate = collate;
    }

    /** Paper size; null uses the printer's default. */
    public MediaSizeName getPaper() {
        return paper;
    }

    public void setPaper(MediaSizeName paper) {
        this.paper = paper;
    }

    public Scaling getScaling() {
        return scaling;
    }

    public void setScaling(Scaling scaling) {
        this.scaling = scaling != null ? scaling : Scaling.FIT;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation != null ? orientation : Orientation.AUTO;
    }

    /** Whether annotations and form fields print (those flagged for printing).  True by default. */
    public boolean isAnnotations() {
        return annotations;
    }

    public void setAnnotations(boolean annotations) {
        this.annotations = annotations;
    }

    /** One- or two-sided; null uses the printer's default. */
    public Sides getSides() {
        return sides;
    }

    public void setSides(Sides sides) {
        this.sides = sides;
    }

    /** Page box printed, a {@code Page.BOUNDARY_*} constant; the crop box by default, as Acrobat does. */
    public int getBoundary() {
        return boundary;
    }

    public void setBoundary(int boundary) {
        this.boundary = boundary;
    }

    /**
     * Print each page as a 150 dpi image rather than at the printer's resolution.  Forced when the
     * document permits only low-quality printing.
     */
    public boolean isLowResolution() {
        return lowResolution;
    }

    public void setLowResolution(boolean lowResolution) {
        this.lowResolution = lowResolution;
    }

    /** Name of the job in the printer queue; null uses the document's title or "PDF document". */
    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /**
     * Parses a page range as people type it: "1-3, 5, 8-" (one-based, "8-" meaning to the end, "-3"
     * from the start).  Pages keep the order given; numbers past the end are dropped.
     *
     * @return zero-based page indexes, or null if the text isn't a valid range
     */
    public static int[] parsePageRange(String text, int pageCount) {
        if (text == null || text.isBlank()) return null;
        List<Integer> out = new ArrayList<>();
        for (String part : text.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) continue;
            int from;
            int to;
            try {
                int dash = p.indexOf('-');
                if (dash < 0) {
                    from = to = Integer.parseInt(p);
                } else {
                    String a = p.substring(0, dash).strip();
                    String b = p.substring(dash + 1).strip();
                    from = a.isEmpty() ? 1 : Integer.parseInt(a);
                    to = b.isEmpty() ? pageCount : Integer.parseInt(b);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            if (from < 1 || to < from) return null;
            for (int i = from; i <= Math.min(to, pageCount); i++) out.add(i - 1);
        }
        return out.isEmpty() ? null : out.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String toString() {
        return "PrintSettings{printer=" + (printer != null ? printer.getName() : "default")
                + ", pages=" + Arrays.toString(pages) + ", copies=" + copies + ", collate=" + collate
                + ", paper=" + paper + ", scaling=" + scaling + ", orientation=" + orientation
                + ", annotations=" + annotations + ", sides=" + sides + ", lowResolution=" + lowResolution + '}';
    }
}
