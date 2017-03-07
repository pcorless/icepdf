package org.icepdf.os.examples.svg;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;
import org.w3c.dom.DOMImplementation;

import java.io.*;
import java.lang.InterruptedException;
import java.util.ResourceBundle;

/**
 * The <code>org.icepdf.os.examples.SvgCapture</code> class is an example of how to save a PDF page as an SVG document.
 * This examples leverages Java2D and the Batik library to convert Java2D operations into SVG.
 * <p>
 * A file specified at the command line and the first page is capture to a SVG document.
 *
 * @since 1.0
 */
public class SvgCapture {

    // Enable SVG CSS style attribute
    private static boolean SVG_CSS = true;

    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        // read/store the font cache.
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        PropertiesManager properties = new PropertiesManager(System.getProperties(),
                ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE));
        new FontPropertiesManager(properties, System.getProperties(), messageBundle);

        // start the capture
        SvgCapture pageCapture = new SvgCapture();
        pageCapture.captureFirstPage(filePath);

    }

    public void captureFirstPage(String filePath) {
        // open the url
        Document document = new Document();

        try {
            document.setFile(filePath);
            // Get a DOMImplementation
            DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
            // Create an instance of org.w3c.dom.Document
            org.w3c.dom.Document svgDocument = domImpl.createDocument(null, "svg",
                    null);
            // Create an instance of the SVG Generator
            SVGGraphics2D svgGenerator = new SVGGraphics2D(svgDocument);
            float userRotation = 0;
            float userZoom = 1;
            int pageNumber = 0;

            PDimension pdfDimension = document.getPageDimension(pageNumber, userRotation, userZoom);
            svgGenerator.setSVGCanvasSize(pdfDimension.toDimension());

            // paint the page to the Batik svgGenerator graphics context.
            document.paintPage(pageNumber, svgGenerator,
                    GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_CROPBOX,
                    userRotation, userZoom);

            File file = new File("svgCapture_" + pageNumber + ".svg");
            // Finally, stream out SVG to the standard output using UTF-8character to byte encoding
            Writer fileWriter = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            svgGenerator.stream(fileWriter, SVG_CSS);

        } catch (org.apache.batik.svggen.SVGGraphics2DIOException ex) {
            System.out.println("Error creating svg document." + ex);
        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        } catch (InterruptedException ex) {
            System.out.println("Error handling PDF document " + ex);
        }
    }

}
