import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The <code>PortfolioCapture</code> class is an example of how to save page
 * captures to disk when the file in question is a PDF that contains collections
 * or in other words a PDF Portfolio or package.
 * <p/>
 * A portfolio as well as regular document can have document attachments.  These attachments can be of any file
 * type so the file list should be correctly filtered.
 * <p/>
 * A file specified at the command line is opened and every embedded portfolio
 * file it iterated over and every page is captured as an image and saved to disk
 * as a PNG graphic file.
 *
 * @since 5.1.0
 */
public class PortfolioCapture {

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
        PortfolioCapture portfolioCapture = new PortfolioCapture();
        portfolioCapture.capturePages(filePath);
    }

    public void capturePages(String filePath) {
        // open the url
        Document document = new Document();

        // setup threads to handle image capture.
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        try {
            document.setFile(filePath);

            // executable list to capture.
            List<Callable<Void>> callables = new ArrayList<Callable<Void>>();

            /**
             * If we have a collection the PDF won't have any content we want to capture, the page is generally just
             * a placeholder letting the end user know that the document is a collection.
             * If it isn't a collection then we want to capture the document's page before moving on to the
             * embedded files.
             */
            if (!(document.getCatalog().getCollection() != null &&
                    document.getCatalog().getCollection().size() > 0)) {
                System.out.println("Loading root document file " + document.getDocumentOrigin());
                callables.add(new CaptureDocument(document, 0, document.getDocumentOrigin()));
            }

            // Capture any embedded files that have a file name that ends in '.pdf'.
            if (document.getCatalog().getEmbeddedFilesNameTree() != null) {
                NameTree embeddedFilesNameTree = document.getCatalog().getEmbeddedFilesNameTree();
                Library library = document.getCatalog().getLibrary();
                List filePairs = embeddedFilesNameTree.getNamesAndValues();
                if (filePairs != null) {

                    // queue up the embedded documents
                    for (int i = 0, max = filePairs.size(); i < max; i += 2) {
                        // file name and file specification pairs.
                        Object rawFileName = library.getObject(filePairs.get(i));
                        Object rawFileProperties = library.getObject(filePairs.get(i + 1));
                        if (rawFileName != null && rawFileName instanceof LiteralStringObject &&
                                rawFileProperties != null && rawFileProperties instanceof HashMap) {
                            String fileAttachmentName = Utils.convertStringObject(library, (LiteralStringObject) rawFileName);
                            // file specification has the document stream
                            FileSpecification fileSpecification = new FileSpecification(library, (HashMap) rawFileProperties);

                            // create the stream instance from the embedded file streams File entry.
                            EmbeddedFileStream embeddedFileStream = fileSpecification.getEmbeddedFileStream();
                            InputStream fileInputStream = embeddedFileStream.getDecodedStreamData();

                            String fileName = fileSpecification.getUnicodeFileSpecification() != null ?
                                    fileSpecification.getUnicodeFileSpecification() :
                                    fileSpecification.getFileSpecification() != null ?
                                            fileSpecification.getFileSpecification() : "";

                            // queue the embedded document for page capture
                            System.out.println("Loading embedded file " + fileAttachmentName + " : " + fileName);
                            if (fileName.toLowerCase().endsWith(".pdf")) {
                                Document embeddedDocument = new Document();
                                embeddedDocument.setInputStream(fileInputStream, fileAttachmentName);
                                int index = (int) Math.round((i / 2.0) + 1.0);
                                callables.add(new CaptureDocument(embeddedDocument, index, fileAttachmentName));
                            }
                        }
                    }
                }
            }

            // execute the page captures.
            executorService.invokeAll(callables);
            executorService.submit(new DocumentCloser(document)).get();

        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        } catch (InterruptedException e) {
            System.out.println("Error parsing PDF document " + e);
        } catch (ExecutionException e) {
            System.out.println("Error parsing PDF document " + e);
        }
        executorService.shutdown();
    }

    /**
     * Captures images found in a page  parse to file.
     */
    public class CaptureDocument implements Callable<Void> {
        private Document document;
        private String fileName;
        private int fileIndex;
        private float scale = 1f;
        private float rotation = 0f;

        private CaptureDocument(Document document, int fileIndex, String fileName) {
            this.document = document;
            this.fileName = fileName;
            this.fileIndex = fileIndex;
        }

        public Void call() {
            try {
                PageTree pageTree = document.getPageTree();
                // iterate over the document pages.
                for (int j = 0, maxPage = pageTree.getNumberOfPages(); j < maxPage; j++) {
                    // initialize the page.
                    Page page = document.getPageTree().getPage(j);
                    page.init();
                    PDimension sz = page.getSize(Page.BOUNDARY_CROPBOX, rotation, scale);
                    int pageWidth = (int) sz.getWidth();
                    int pageHeight = (int) sz.getHeight();

                    // prep the page capture
                    BufferedImage image = new BufferedImage(pageWidth,
                            pageHeight,
                            BufferedImage.TYPE_INT_RGB);
                    Graphics g = image.createGraphics();
                    page.paint(g, GraphicsRenderingHints.PRINT,
                            Page.BOUNDARY_CROPBOX, rotation, scale);
                    g.dispose();

                    // capture the page image to file

                    String imageFileName = "imageCapture_" + fileIndex + "_" + j + ".png";
                    System.out.println("Page image capture: " + imageFileName);
                    File file = new File(imageFileName);
                    ImageIO.write(image, "png", file);

                    image.flush();
                }
                document.dispose();
            } catch (Throwable e) {
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
