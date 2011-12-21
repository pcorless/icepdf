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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Vector;
import java.util.Enumeration;

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
        int pagNumber = 0;
        int count = 0;
        Vector images = document.getPageImages(pagNumber);
        Enumeration pageImages = images.elements();
        while (pageImages.hasMoreElements()) {
            count++;
            Image image = (Image) pageImages.nextElement();
            if (image != null) {
                RenderedImage rendImage = (BufferedImage) image;
                try {
                    File file = new File("imageCapture1_" + count + ".png");
                    ImageIO.write(rendImage, "png", file);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                image.flush();
            }
        }

        // clean up resources
        document.dispose();
    }
}
