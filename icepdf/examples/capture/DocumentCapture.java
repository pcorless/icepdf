import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sets up a listener on a files system directory.  As files are added to the directory captures are taken of each
 * page in the uploaded PDF.  There is a stub for the data needed to upload the file to the cloud storage.
 * <p/>
 * PDF page captures to the JPEG image format with a width of 500px;
 */
public class DocumentCapture {

    public static final String FOLDER_PATH = "c:/ftp/";
    public static final float IMAGE_FIXED_WIDTH = 500;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        // star the file watcher on
        new DocumentCapture().startFileWatcher();
    }

    @SuppressWarnings("unchecked")
    public void startFileWatcher() {
        try {
            // listen for a particular file
            Path ftpFolder = Paths.get(FOLDER_PATH);
            WatchService watchService = FileSystems.getDefault().newWatchService();
            ftpFolder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            boolean running;
            do {
                // Returns a queued key. If no queued key is available, this method waits.
                // poll(long, TimeUnit) is another valid option
                WatchKey watchKey = watchService.take();

                // we have one or more newly created files.
                for (WatchEvent event : watchKey.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();

                    if (StandardWatchEventKinds.ENTRY_CREATE.equals(kind)) {
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path path = ftpFolder.resolve(ev.context());
                        System.out.println("File Created: " + path);
                        // start capture and save to external source.
                        executorService.execute(new CapturePages(path));
                    } // ignore but log an overflow
                    else if (StandardWatchEventKinds.OVERFLOW.equals(kind)) {
                        System.out.println("Event maybe have been lost ");
                    }
                }
                // rest the key to receive further notifications.
                running = watchKey.reset();

            } while (running);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // shutdown the pool
        executorService.shutdown();

    }

    public class CapturePages implements Runnable {
        private Path path;
        private float scale = 1f;
        private float rotation = 0f;

        public CapturePages(Path path) {
            this.path = path;
        }

        public void run() {
            Document document = new Document();
            try {

                File pdfFile = path.toFile();

                // REMOVE if Unix, this is for windows only, as there is an extended file lock
                // on the copy of a file
                if (!pdfFile.renameTo(pdfFile)) {
                    Thread.sleep(10);
                }

                document.setInputStream(new FileInputStream(pdfFile), path.toString());

                System.out.println("Capturing file: " + path);
                for (int pageNumber = 0, max = document.getNumberOfPages(); pageNumber < max; pageNumber++) {
                    Page page = document.getPageTree().getPage(pageNumber);
                    page.init();
                    PDimension sz = page.getSize(Page.BOUNDARY_CROPBOX, rotation, scale);

                    int pageWidth = (int) sz.getWidth();
                    int pageHeight = (int) sz.getHeight();

                    // scale the image so that width is always 500px.
                    scale = IMAGE_FIXED_WIDTH / pageWidth;
                    pageWidth = Math.round(pageWidth * scale);
                    pageHeight = Math.round(pageHeight * scale);

                    BufferedImage image = new BufferedImage(pageWidth,
                            pageHeight,
                            BufferedImage.TYPE_INT_RGB);
                    Graphics g = image.createGraphics();

                    page.paint(g, GraphicsRenderingHints.PRINT,
                            Page.BOUNDARY_CROPBOX, rotation, scale);
                    g.dispose();
                    // capture the page image to file
                    try {
                        // write the image to the outputStream
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", outputStream);
                        // create an output stream and pass it off the the cloud storage insert method.
                        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
//                        long byteCount = outputStream.size();
                        // make the insert call, https://cloud.google.com/storage/docs/json_api/v1/objects/insert
                        // insert(inputStream, byteCount, fileName);

                        // clean up the streams
                        outputStream.close();
                        inputStream.close();

                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    image.flush();
                }
                System.out.println("Finished capturing file: " + path);

            } catch (PDFException ex) {
                System.out.println("Error parsing PDF document " + ex);
                ex.printStackTrace();
            } catch (PDFSecurityException ex) {
                System.out.println("Error encryption not supported " + ex);
                ex.printStackTrace();
            } catch (FileNotFoundException ex) {
                System.out.println("Error file not found " + ex);
                ex.printStackTrace();
            } catch (IOException ex) {
                System.out.println("Error handling PDF document " + ex);
                ex.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                document.dispose();
            }
        }
    }
}
