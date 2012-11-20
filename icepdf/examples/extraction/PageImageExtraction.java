/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Defs;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * The <code>PageImageExtraction</code> class is an example of how to extract
 * images from a PDF document.  A file specified at the command line is opened
 * and any images that are embedded in the first page's content are
 * saved to disk as PNG graphic files.
 *
 * @since 2.0
 */
public class PageImageExtraction {

    public static void main(String[] args) {

        // setup the memory manager to avoid keeping a lot of data around
        Defs.setProperty("org.icepdf.core.maxSize", 1);
        Defs.setProperty("org.icepdf.core.scaleImages", "false");

        // Get a file from the command line to open
        String filePath = args[0];

        // open the url
        Document document = new Document();
        try {
            document.setFile(filePath);
        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        }

        // Get images from the first page of the document, asuming that there
        // is at least one image to extract.
        try {
            int count = 0;

            Page currentPage;
            List<Image> images;
            RenderedImage rendImage;
            for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                currentPage = document.getPageTree().getPage(i);

                images = currentPage.getImages();
                for (Image image: images) {
                    count++;
                    if (image != null) {
                        rendImage = (BufferedImage) image;
                        File file = new File("imageCapture1_" + count + ".png");
                        ImageIO.write(rendImage, "png", file);
                        image.flush();
                    }
                }
                // clears most resource.
                images.clear();
            }

            // clean up resources
            document.dispose();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
