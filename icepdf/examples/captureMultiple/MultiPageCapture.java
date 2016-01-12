/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.*;
import javax.media.jai.operator.ErrorDiffusionDescriptor;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * The <code>MultiPageCapture</code> class is an example of how to save page
 * captures to disk.  A PDF, specified at the command line, is opened, and
 * every page in the document is captured as an image and saved into one
 * multi-page group 4 fax TIFF graphics file.
 * <p/>
 * More information on different compression types can be found at this forum
 * thread, http://www.icesoft.org/JForum/posts/list/17504.page
 *
 * @since 4.0
 */
public class MultiPageCapture {

    public static final double PRINTER_RESOLUTION = 200; //150, 200, 300, 600, 1200

    // This compression type may be wpecific to JAI ImageIO Tools
    public static final String COMPRESSION_TYPE_GROUP4FAX = "CCITT T.4";

    public static void main(String[] args) {

        // read system fonts from cached list, speeds up initial application startup
        // for systems with a large number of fonts.
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        Properties sysProps = System.getProperties();
        PropertiesManager propertiesManager = new PropertiesManager(sysProps, messageBundle);
        new FontPropertiesManager(propertiesManager, sysProps, messageBundle);


        // Verify that ImageIO can output TIFF
        Iterator<ImageWriter> iterator = ImageIO.getImageWritersByFormatName("tiff");
        if (!iterator.hasNext()) {
            System.out.println(
                    "ImageIO missing required plug-in to write TIFF files. " +
                            "You can download the JAI ImageIO Tools from: " +
                            "https://jai-imageio.dev.java.net/");
            return;
        }
        boolean foundCompressionType = false;
        for (String type : iterator.next().getDefaultWriteParam().getCompressionTypes()) {
            if (COMPRESSION_TYPE_GROUP4FAX.equals(type)) {
                foundCompressionType = true;
                break;
            }
        }
        if (!foundCompressionType) {
            System.out.println(
                    "TIFF ImageIO plug-in does not support Group 4 Fax " +
                            "compression type (" + COMPRESSION_TYPE_GROUP4FAX + ")");
            return;
        }

        long intime = System.currentTimeMillis();

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

        try {
            // save page caputres to file.
            File file = new File("imageCapture.tif");
            ImageOutputStream ios = ImageIO.createImageOutputStream(file);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
            writer.setOutput(ios);

            // Paint each pages content to an image and write the image to file
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                final double targetDPI = PRINTER_RESOLUTION;
                float scale = 1.0f;
                float rotation = 0f;

                // Given no initial zooming, calculate our natural DPI when
                // printed to standard US Letter paper
                PDimension size = document.getPageDimension(i, rotation, scale);
                double dpi = Math.sqrt((size.getWidth() * size.getWidth()) +
                        (size.getHeight() * size.getHeight())) /
                        Math.sqrt((8.5 * 8.5) + (11 * 11));

                // Calculate scale required to achieve at least our target DPI
                if (dpi < (targetDPI - 0.1)) {
                    scale = (float) (targetDPI / dpi);
                    size = document.getPageDimension(i, rotation, scale);
                }

                int pageWidth = (int) size.getWidth();
                int pageHeight = (int) size.getHeight();

                BufferedImage image = new BufferedImage(
                        pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
                Graphics g = image.createGraphics();
                document.paintPage(
                        i, g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX,
                        rotation, scale);
                g.dispose();


                // JAI filter code
                PlanarImage surrogateImage = PlanarImage.wrapRenderedImage(image);
                LookupTableJAI lut = new LookupTableJAI(new byte[][]{{(byte) 0x00,
                        (byte) 0xff}, {(byte) 0x00, (byte) 0xff}, {(byte) 0x00, (byte) 0xff}});
                ImageLayout layout = new ImageLayout();
                byte[] map = new byte[]{(byte) 0x00, (byte) 0xff};
                ColorModel cm = new IndexColorModel(1, 2, map, map, map);
                layout.setColorModel(cm);
                SampleModel sm = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE,
                        surrogateImage.getWidth(),
                        surrogateImage.getHeight(),
                        1);
                layout.setSampleModel(sm);
                RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
                PlanarImage op = ErrorDiffusionDescriptor.create(surrogateImage, lut,
                        KernelJAI.ERROR_FILTER_FLOYD_STEINBERG, hints);
                BufferedImage dst = op.getAsBufferedImage();

                // capture the page image to file
                IIOImage img = new IIOImage(dst, null, null);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType(COMPRESSION_TYPE_GROUP4FAX);
                if (i == 0) {
                    writer.write(null, img, param);
                } else {
                    writer.writeInsert(-1, img, param);
                }
                image.flush();
            }

            ios.flush();
            ios.close();
            writer.dispose();
        } catch (IOException e) {
            System.out.println("Error saving file  " + e);
            e.printStackTrace();
        }

        // clean up resources
        document.dispose();

        System.out.println("total execution time taken was : ");
        System.out.println(System.currentTimeMillis() - intime);
    }
}
