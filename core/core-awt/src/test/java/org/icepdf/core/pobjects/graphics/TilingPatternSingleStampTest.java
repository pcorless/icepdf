/*
 * Copyright 2026 Patrick Corless
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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tiling pattern whose step is far larger than its cell is drawn once, as a single stamp.  Its
 * buffer must be bounded by what is visible, at the device's resolution - not the stamp's full
 * size in user space.  The CanmoreAlberta map has a page-sized stamp (BBox 11305x5060, step
 * 32730) that took a 9000x5060 buffer, 182 MB, on every paint at any zoom.
 */
public class TilingPatternSingleStampTest {

    private static final int PAGE = 5000;

    /** A 5000-unit page filled with a page-sized single-stamp pattern: a red square at its centre. */
    private static byte[] pdf() {
        String pattern = "1 0 0 rg 2000 2000 1000 1000 re f";
        String content = "/Pattern cs /P0 scn 0 0 " + PAGE + " " + PAGE + " re f";
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE + " " + PAGE + "]"
                + " /Resources << /Pattern << /P0 5 0 R >> >> /Contents 4 0 R >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /Pattern /PatternType 1 /PaintType 1 /TilingType 1 /BBox [0 0 " + PAGE + " " + PAGE
                + "] /XStep 32730 /YStep 32730 /Resources << >> /Length " + pattern.length() + " >>\nstream\n"
                + pattern + "\nendstream");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Root 1 0 R /Size ").append(objects.size() + 1)
                .append(" >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Paints a 100x100 viewport centred on the page; returns {allocated bytes, centre pixel}. */
    private static long[] paintCentre(float zoom) throws Exception {
        byte[] bytes = pdf();
        Document document = new Document();
        try {
            document.setByteArray(bytes, 0, bytes.length, "single-stamp.pdf");
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 100, 100);
            g.setClip(0, 0, 100, 100);
            double centre = PAGE * zoom / 2;
            g.translate(-(centre - 50), -(centre - 50));
            com.sun.management.ThreadMXBean threads =
                    (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long before = threads.getCurrentThreadAllocatedBytes();
            page.paint(g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, 0f, zoom);
            long allocated = threads.getCurrentThreadAllocatedBytes() - before;
            g.dispose();
            return new long[]{allocated, image.getRGB(50, 50) & 0xFFFFFF};
        } finally {
            document.dispose();
        }
    }

    @DisplayName("zoomed out, the stamp's buffer is the viewport's size, not the stamp's")
    @Test
    public void zoomedOut() throws Exception {
        long[] result = paintCentre(0.1f);
        assertEquals(0xFF0000, result[1], "the stamp is drawn");
        // the stamp in user space is 5000x5000: a buffer that size is ~100 MB.
        assertTrue(result[0] < 16L << 20, "allocated " + (result[0] >> 20) + " MB");
    }

    @DisplayName("zoomed in, it is still bounded by the viewport, at device resolution")
    @Test
    public void zoomedIn() throws Exception {
        long[] result = paintCentre(2f);
        assertEquals(0xFF0000, result[1], "the stamp is drawn");
        assertTrue(result[0] < 16L << 20, "allocated " + (result[0] >> 20) + " MB");
    }
}
