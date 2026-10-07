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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.ImageDrawCmd;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decoded images belong to the pool, which holds them softly: the page's draw commands keep their
 * {@link ImageReference}s for as long as the page is initialised, so a reference that also kept the
 * image would pin every decoded image of every open page (1.pdf: ~300 MB the heap could never
 * reclaim).  Also: an image off the clip is not decoded at all.
 */
public class ImageRetentionTest {

    private static ImageDrawCmd firstImage(Page page) {
        for (DrawCmd cmd : page.getShapes().getShapes()) {
            if (cmd instanceof ImageDrawCmd) return (ImageDrawCmd) cmd;
        }
        fail("no image on the page");
        return null;
    }

    private static void paint(Page page, int w, int h) throws InterruptedException {
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setClip(0, 0, w, h);
        page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, false, false);
        g.dispose();
    }

    @DisplayName("after a paint the page does not pin the decoded image; the pool alone holds it")
    @Test
    public void pageDoesNotPinDecodedImage() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/flate_image.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            paint(page, 800, 800);
            ImageStream stream = firstImage(page).getImageStream();
            ImagePool pool = stream.getLibrary().getImagePool();
            BufferedImage decoded = pool.get(stream.getPObjectReference());
            assertNotNull(decoded, "the paint decoded the image into the pool");
            assertNull(stream.getDecompressedBytes(), "the inflated samples are released once the image is pooled");

            WeakReference<BufferedImage> watch = new WeakReference<>(decoded);
            decoded = null;
            // stand-in for the GC clearing the pool's soft reference under memory pressure.
            pool.put(stream.getPObjectReference(), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            for (int i = 0; i < 10 && watch.get() != null; i++) {
                System.gc();
                Thread.sleep(20);
            }
            assertNull(watch.get(), "nothing but the pool may hold the decoded image while the page is alive");
            assertNotNull(page.getShapes(), "the page is still initialised");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an image wholly outside the clip is culled; one overlapping it is not")
    @Test
    public void culling() {
        BufferedImage canvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setClip(0, 0, 100, 100);
        // an image is drawn in a unit square scaled by its CTM.
        g.translate(150, 0);
        g.scale(40, 40);
        assertTrue(ImageReference.isOutsideClip(g, 0, 0, 1, 1), "image at x 150..190, clip 0..100");
        g.dispose();

        g = canvas.createGraphics();
        g.setClip(0, 0, 100, 100);
        g.translate(80, 80);
        g.scale(40, 40);
        assertFalse(ImageReference.isOutsideClip(g, 0, 0, 1, 1), "image at 80..120 overlaps the clip");
        g.dispose();

        g = canvas.createGraphics();
        g.translate(150, 0);
        g.scale(40, 40);
        assertFalse(ImageReference.isOutsideClip(g, 0, 0, 1, 1), "no clip: nothing is culled");
        g.dispose();
    }
}
