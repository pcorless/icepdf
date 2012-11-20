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


import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.util.GraphicsRenderingHints;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.*;

/**
 * The <code>PageCapture</code> class is an example of how to save page
 * captures to disk.  A file specified at the command line is opened and every
 * page in the document is captured as an image and saved to disk as a
 * PNG graphic file.  
 *
 * @since 2.0
 */
public class PageCapture {
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

        // save page caputres to file.
        float scale = .5f;
        float rotation = 0f;

        // Paint each pages content to an image and write the image to file
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
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
            try {
                System.out.println("\t capturing page " + i);
                File file = new File("imageCapture1_" + i + ".png");
                ImageIO.write(image, "png", file);

            } catch (IOException e) {
                e.printStackTrace();
            }
            image.flush();
        }
        // clean up resources
        document.dispose();
    }
}
