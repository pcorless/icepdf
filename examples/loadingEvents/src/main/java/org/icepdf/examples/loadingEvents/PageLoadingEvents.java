package org.icepdf.examples.loadingEvents;
/*
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.ri.common.views.listeners.MetricsPageLoadingListener;
import org.icepdf.ri.util.FontPropertiesManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The <code>PageLoadingEvents</code> class is an example of how to hook up a
 * PageLoadingListener to a page instance.
 * <p/>
 * A file specified at the command line is opened and every page in the document
 * is loaded and performance data of page load is displayed on the console.
 *
 * @since 5.0
 */
public class PageLoadingEvents {
    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        // read/store the font cache.
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        // start the capture
        PageLoadingEvents pageLoading = new PageLoadingEvents();
        pageLoading.loadPages(filePath);

    }

    public void loadPages(String filePath) {
        // open the url
        Document document = new Document();

        // setup two threads to handle image extraction.
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            document.setFile(filePath);
            // create a list of callables.
            int pages = document.getNumberOfPages();
            java.util.List<Callable<Void>> callables = new ArrayList<>(pages);
            for (int i = 0; i <= pages; i++) {
                callables.add(new CapturePage(document, i));
            }
            executorService.invokeAll(callables);
            executorService.submit(new DocumentCloser(document)).get();

        } catch (Exception e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }

    /**
     * Captures images found in a page  parse to file.
     */
    public static class CapturePage implements Callable<Void> {
        private final Document document;
        private final int pageNumber;

        private CapturePage(Document document, int pageNumber) {
            this.document = document;
            this.pageNumber = pageNumber;
        }

        public Void call() {
            try {
                Page page = document.getPageTree().getPage(pageNumber);
                // assign a metrics page loading listener
                page.addPageProcessingListener(new MetricsPageLoadingListener(
                        document.getNumberOfPages()));
                page.init();
                float rotation = 0f;
                float scale = 1f;
                PDimension sz = page.getSize(Page.BOUNDARY_CROPBOX, rotation, scale);

                int pageWidth = (int) sz.getWidth();
                int pageHeight = (int) sz.getHeight();

                BufferedImage image = new BufferedImage(pageWidth,
                        pageHeight,
                        BufferedImage.TYPE_INT_RGB);
                Graphics g = image.createGraphics();

                page.paint(g, GraphicsRenderingHints.PRINT,
                        Page.BOUNDARY_CROPBOX, rotation, scale);
                g.dispose();
                // capture the page image to file
                System.out.println("Capturing page " + pageNumber);
                File file = new File("imageCapture_" + pageNumber + ".png");
                ImageIO.write(image, "png", file);
                image.flush();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Disposes the document.
     */
    public static class DocumentCloser implements Callable<Void> {
        private final Document document;

        private DocumentCloser(Document document) {
            this.document = document;
        }

        public Void call() {
            if (document != null) {
                document.dispose();
                System.out.println("Document disposed");
            }
            return null;
        }
    }
}
