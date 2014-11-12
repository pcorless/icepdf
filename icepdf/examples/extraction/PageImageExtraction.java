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
import org.icepdf.core.pobjects.Page;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The <code>PageImageExtraction</code> class is an example of how to extract
 * images from a PDF document.  A file specified at the command line is opened
 * and any images that are embedded in a documents page is written to file.
 *
 * @since 5.0
 */
public class PageImageExtraction {

    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        PageImageExtraction pageImageExtraction = new PageImageExtraction();

        pageImageExtraction.pageImageExtraction(filePath);
    }

    public void pageImageExtraction(String filePath) {
        // open the url
        Document document = new Document();

        // setup two threads to handle image extraction.
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            document.setFile(filePath);
            // create a list of callables.
            int pages = document.getNumberOfPages();
            List<Callable<Void>> callables = new ArrayList<Callable<Void>>(pages);
            for (int i = 0; i <= pages; i++) {
                callables.add(new CapturePageImages(document, i));
            }
            executorService.invokeAll(callables);

            executorService.submit(new DocumentCloser(document)).get();
        } catch (InterruptedException e) {
            System.out.println("Error parsing PDF document " + e);
        } catch (ExecutionException e) {
            System.out.println("Error parsing PDF document " + e);
        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        }
        executorService.shutdown();
    }

    /**
     * Captures images found in a page  parse to file.
     */
    public class CapturePageImages implements Callable<Void> {
        private Document document;
        private int pageNumber;

        private CapturePageImages(Document document, int pageNumber) {
            this.document = document;
            this.pageNumber = pageNumber;
        }

        public Void call() {
            try {
                Page currentPage = document.getPageTree().getPage(pageNumber);
                int count = 0;
                RenderedImage rendImage;
                List<Image> images = currentPage.getImages();
                for (Image image : images) {
                    count++;
                    if (image != null) {
                        rendImage = (BufferedImage) image;
                        System.out.println("Capture page " + pageNumber + " image " + count);
                        File file = new File("imageCapture_" + pageNumber + "_" + count + ".png");
                        ImageIO.write(rendImage, "png", file);
                        image.flush();
                    }
                }
                // clears most resource.
                images.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Disposes the document.
     */
    public class DocumentCloser implements Callable<Void> {
        private Document document;

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
