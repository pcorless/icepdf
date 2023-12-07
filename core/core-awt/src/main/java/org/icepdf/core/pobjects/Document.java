/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects;

import org.icepdf.core.SecurityCallback;
import org.icepdf.core.application.ProductInfo;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.WatermarkCallback;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.Header;
import org.icepdf.core.pobjects.structure.Trailer;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.DocumentBuilder;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.core.util.updater.modifiers.ModifierFactory;
import org.icepdf.core.util.updater.modifiers.PageRemovalModifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The <code>Document</code> class represents a PDF document and provides
 * access to the hierarchy of objects contained in the body section of the
 * PDF document.  Most of the objects in the hierarchy are dictionaries which
 * contain references to page content and other objects such such as annotations.
 * For more information on the document object hierarchy, see the <i>ICEpdf
 * Developer's Guide</i>.</p>
 * <br>
 * <p>The <code>Document</code> class also provides access to methods responsible
 * for rendering PDF document content.  Methods are available to capture page
 * content to a graphics context or extract image and text data on a page-by-page
 * basis.</p>
 * <br>
 * <p>If your PDF rendering application will be accessing encrypted documents,
 * it is important to implement the SecurityCallback.  This interface provides
 * methods for getting password data from a user if needed.<p>
 *
 * @since 1.0
 */
public class Document {

    private static final Logger logger =
            Logger.getLogger(Document.class.toString());

    /**
     * Gets the version number of ICEpdf rendering core.  This is not the version
     * number of the PDF format used to encode this document.
     *
     * @return version number of ICEpdf's rendering core.
     */
    public static String getLibraryVersion() {
        return ProductInfo.VERSION +
                (ProductInfo.RELEASE_TYPE != null && !ProductInfo.RELEASE_TYPE.isEmpty() ?
                        "-" + ProductInfo.RELEASE_TYPE : "");
    }

    // optional watermark callback
    private WatermarkCallback watermarkCallback;

    // core catalog, root of the document hierarchy.
    private Catalog catalog;

    // state manager for tracking object that have been touched in some way
    // for editing purposes,
    private StateManager stateManager;

    // This is the original file or url path of where the PDF document was load
    // from
    private String origin;

    // This is the location of the file when it is saved to the hard drive.  This
    // is usually only different from the origin if the the PDF document
    // was loaded from a URL
    private String cachedFilePath;

    // callback for password dialogs, or command line access.
    private SecurityCallback securityCallback;

    // disable/enable file caching when downloading url data streams
    private static boolean isCachingEnabled;

    private final Library library;

    private FileChannel documentFileChannel;
    private RandomAccessFile randomAccessFile;
    private ByteBuffer documentByteBuffer;
    private CrossReferenceRoot crossReferenceRoot;

    static {
        // sets if file caching is enabled or disabled.
        isCachingEnabled =
                Defs.sysPropertyBoolean("org.icepdf.core.streamcache.enabled",
                        true);
    }

    /**
     * Creates a new instance of a Document.  A Document class represents
     * one PDF document.
     */
    public Document() {
        library = new Library();
    }

    /**
     * Sets a page watermark implementation to be painted on top of the page
     * content.  Watermark can be specified for each page or once by calling
     * document.setWatermark().
     *
     * @param watermarkCallback watermark implementation.
     */
    public void setWatermarkCallback(WatermarkCallback watermarkCallback) {
        this.watermarkCallback = watermarkCallback;
    }

    /**
     * Utility method for setting the origin (filepath or URL) of this Document
     *
     * @param o new origin value
     *          {@link #getDocumentOrigin}
     */
    private void setDocumentOrigin(String o) {
        origin = o;
        if (logger.isLoggable(Level.FINE)) {
            logger.config(
                    "MEMFREE: " + Runtime.getRuntime().freeMemory() + " of " +
                            Runtime.getRuntime().totalMemory());
            logger.config("LOADING: " + o);
        }
    }

    /**
     * Sets the cached file path in the case of opening a file from a URL.
     *
     * @param o new cached file path value
     *          {@link #getDocumentCachedFilePath}
     */
    private void setDocumentCachedFilePath(String o) {
        cachedFilePath = o;
    }

    /**
     * Returns the cached file path in the case of opening a file from a URL.
     *
     * @return file path
     */
    private String getDocumentCachedFilePath() {
        return cachedFilePath;
    }

    /**
     * Load a PDF file from the given path and initiates the document's Catalog.
     *
     * @param filepath path of PDF document.
     * @throws PDFSecurityException if a security provider cannot be found
     *                              or there is an error decrypting the file.
     * @throws IOException          if a problem setting up, or parsing the file.
     */
    public void setFile(String filepath)
            throws PDFSecurityException, IOException {
        setDocumentOrigin(filepath);

        File file = new File(filepath);
        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            documentFileChannel = randomAccessFile.getChannel();
            long fileSize = documentFileChannel.size();
            // Create an in memory file opy
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            documentFileChannel.read(buffer);
            buffer.flip();
            setInputStream(buffer);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set document file path", e);
            throw e;
        }
    }

    /**
     * Load a PDF file from the given URL and initiates the document's Catalog.
     * If the system property org.icepdf.core.streamcache.enabled=true, the file
     * will be cached to a temp file; otherwise, the complete document stream will
     * be stored in memory.
     *
     * @param url location of file.
     * @throws PDFSecurityException if a security provider can not be found
     *                              or there is an error decrypting the file.
     * @throws IOException          if a problem downloading, setting up, or parsing the file.
     */
    public void setUrl(URL url)
            throws PDFSecurityException, IOException {
        InputStream in = null;
        try {
            // make a connection
            URLConnection urlConnection = url.openConnection();

            // Create a stream on the URL connection
            in = urlConnection.getInputStream();

            String pathOrURL = url.toString();

            setInputStream(in, pathOrURL);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Load a PDF file from the given input stream and initiates the document's Catalog.
     * If the system property org.icepdf.core.streamcache.enabled=true, the file
     * will be cached to a temp file; otherwise, the complete document stream will
     * be stored in memory.
     *
     * @param inputStream input stream containing PDF data
     * @param pathOrURL   value assigned to document origin
     * @throws PDFSecurityException if a security provider can not be found
     *                              or there is an error decrypting the file.
     * @throws IOException          if a problem setting up, or parsing the SeekableInput.
     */
    public void setInputStream(InputStream inputStream, String pathOrURL)
            throws PDFSecurityException, IOException {
        setDocumentOrigin(pathOrURL);

        if (!isCachingEnabled) {
            documentByteBuffer = ByteBuffer.wrap(inputStream.readAllBytes());
            setInputStream(documentByteBuffer);
        }
        // if caching is allowed cache the url to file
        else {
            // create tmp file and write bytes to it.
            File tempFile = File.createTempFile(
                    "ICEpdfTempFile" + getClass().hashCode(),
                    ".tmp");
            // Delete temp file on exit
            tempFile.deleteOnExit();

            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            setDocumentCachedFilePath(tempFile.getAbsolutePath());

            try {
                randomAccessFile = new RandomAccessFile(tempFile, "r");
                documentFileChannel = randomAccessFile.getChannel();
                long fileSize = documentFileChannel.size();
                // Create an in memory file opy
                ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
                documentFileChannel.read(buffer);
                buffer.flip();
                setInputStream(buffer);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to set document input stream", e);
                throw e;
            }
        }
    }

    /**
     * Load a PDF file from the given byte array and initiates the document's Catalog.
     * If the system propertyorg.icepdf.core.streamcache.enabled=true, the file
     * will be cached to a temp file; otherwise, the complete document stream will
     * be stored in memory.
     * The given byte array is not necessarily copied, and will try to be directly
     * used, so do not modify it after passing it to this method.
     *
     * @param data      byte array containing PDF data
     * @param offset    the index into the byte array where the PDF data begins
     * @param length    the number of bytes in the byte array belonging to the PDF data
     * @param pathOrURL value assigned to document origin
     * @throws PDFSecurityException if a security provider can not be found
     *                              or there is an error decrypting the file.
     * @throws IOException          if a problem setting up, or parsing the SeekableInput.
     */
    public void setByteArray(byte[] data, int offset, int length, String pathOrURL)
            throws PDFSecurityException, IOException {   // security, state, io?
        setDocumentOrigin(pathOrURL);

        if (!isCachingEnabled) {
            documentByteBuffer = ByteBuffer.wrap(data, offset, length);
            setInputStream(documentByteBuffer);
        }
        // if caching is allowed cache the url to file
        else {
            // create tmp file and write bytes to it.
            File tempFile = File.createTempFile(
                    "ICEpdfTempFile" + getClass().hashCode(),
                    ".tmp");
            // Delete temp file on exit
            tempFile.deleteOnExit();

            // Write the data to the temp file.
            try {
                Files.copy(new ByteArrayInputStream(data), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.log(Level.FINE, "Error writing PDF output stream.", e);
                throw e;
            }

            setDocumentCachedFilePath(tempFile.getAbsolutePath());

            try {
                randomAccessFile = new RandomAccessFile(tempFile, "r");
                documentFileChannel = randomAccessFile.getChannel();
                long fileSize = documentFileChannel.size();
                // Create an in memory file opy
                ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
                documentFileChannel.read(buffer);
                buffer.flip();
                setInputStream(buffer);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to set document input stream", e);
                throw e;
            }
        }
    }

    /**
     * Sets the input stream of the PDF file to be rendered.
     *
     * @param input ByteBuffer containing PDF data stream
     * @throws PDFSecurityException security error
     * @throws IOException          io error during stream handling
     */
    private void setInputStream(ByteBuffer input)
            throws PDFSecurityException, IOException, IllegalStateException {
        try {
            // load the head
            // repository of all PDF object associated with this document.
            Header header = new Header();
            input = header.parseHeader(input);

            library.setDocumentByteBuffer(input);
            library.setFileHeader(header);

            // create instance of CrossReferenceRoot as we may need it the trailer can't be correctly decoded.
            crossReferenceRoot = new CrossReferenceRoot(library);
            // header created
            Trailer trailer = new Trailer();
            try {
                trailer.parseXrefOffset(input);
            } catch (Exception e) {
                trailer.setLazyInitializationFailed(true);
                logger.log(Level.WARNING, "Trailer loading failed, reindexing file.", e);
            }

            if (!trailer.isLazyInitializationFailed()) {
                try {
                    crossReferenceRoot.setTrailer(trailer);
                    crossReferenceRoot.initialize(input);
                    library.setCrossReferenceRoot(crossReferenceRoot);
                } catch (Exception e) {
                    crossReferenceRoot.setInitializationFailed(true);
                    logger.log(Level.WARNING, "Cross reference loading failed, reindexing file.", e);
                }
            }

            // linear traversal of file.
            if (trailer.isLazyInitializationFailed() || crossReferenceRoot.isInitializationFailed()) {
                crossReferenceRoot = library.rebuildCrossReferenceTable();
                library.setCrossReferenceRoot(crossReferenceRoot);
            }

            PTrailer trailerDictionary = crossReferenceRoot.getTrailerDictionary();

            // finalized security
            boolean madeSecurityManager = library.makeSecurityManager(trailerDictionary);
            if (madeSecurityManager) {
                library.attemptAuthorizeSecurityManager(this, securityCallback);
            }
            // set up a signature permission dictionary
            library.configurePermissions();

            catalog = trailerDictionary.getRootCatalog();
            catalog.init();
            library.setCatalog(catalog);

            // create new instance of state manager and add it to the library
            stateManager = new StateManager(crossReferenceRoot);
            library.setStateManager(stateManager);
        } catch (PDFSecurityException | IOException e) {
            dispose();
            logger.log(Level.SEVERE, "Failed to load PDF Document.", e);
            throw e;
        } catch (Exception e) {
            dispose();
            logger.log(Level.SEVERE, "Error loading PDF Document.", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Gets the page dimension of the indicated page number using the specified
     * rotation factor.
     *
     * @param pageNumber   Page number for the given dimension.  The page
     *                     number is zero-based.
     * @param userRotation Rotation, in degrees, that has been applied to page
     *                     when calculating the dimension.
     * @return page dimension for the specified page number
     * {@link #getPageDimension(int, float, float)}
     */
    public PDimension getPageDimension(int pageNumber, float userRotation) {
        Page page = catalog.getPageTree().getPage(pageNumber);
        return page.getSize(userRotation);
    }

    /**
     * Gets the page dimension of the indicated page number using the specified
     * rotation and zoom settings.  If the page does not exist then a zero
     * dimension is returned.
     *
     * @param pageNumber   Page number for the given dimension.  The page
     *                     number is zero-based.
     * @param userRotation Rotation, in degrees, that has been applied to page
     *                     when calculating the dimension.
     * @param userZoom     Any deviation from the page's actual size, by zooming in or out.
     * @return page dimension for the specified page number.
     * {@link #getPageDimension(int, float)}
     */
    public PDimension getPageDimension(int pageNumber, float userRotation, float userZoom) {
        Page page = catalog.getPageTree().getPage(pageNumber);
        if (page != null) {
            return page.getSize(userRotation, userZoom);
        } else {
            return new PDimension(0, 0);
        }
    }

    /**
     * Returns the origin (filepath or URL) of this Document.  This is the original
     * location of the file where the method getDocumentLocation returns the actual
     * location of the file.  The origin and location of the document will only
     * be different if it was loaded from a URL or an input stream.
     *
     * @return file path or URL
     * {@link #getDocumentLocation}
     */
    public String getDocumentOrigin() {
        return origin;
    }

    /**
     * Returns the file location or URL of this Document. This location may be different
     * from the file origin if the document was loaded from a URL or input stream.
     * If the file was loaded from a URL or input stream the file location is
     * the path to where the document content is cached.
     *
     * @return file path
     * {@link #getDocumentOrigin()}
     */
    public String getDocumentLocation() {
        if (cachedFilePath != null)
            return cachedFilePath;
        return origin;
    }

    /**
     * Gets an instance of the the document state manager which stores references
     * of object that need to be written to file.
     *
     * @return stateManager instance for this document.
     */
    public StateManager getStateManager() {
        return stateManager;
    }

    /**
     * Returns the total number of pages in this document.
     *
     * @return number of pages in the document
     */
    public int getNumberOfPages() {
        try {
            return catalog.getPageTree().getNumberOfPages();
        } catch (Exception e) {
            logger.log(Level.FINE, "Error getting number of pages.", e);
            throw e;
        }
    }

    /**
     * Paints the contents of the given page number to the graphics context using
     * the specified rotation, zoom, rendering hints and page boundary.
     *
     * @param pageNumber     Page number to paint.  The page number is zero-based.
     * @param g              graphics context to which the page content will be painted.
     * @param renderHintType Constant specified by the GraphicsRenderingHints class.
     *                       There are two possible entries, SCREEN and PRINT, each with configurable
     *                       rendering hints settings.
     * @param pageBoundary   Constant specifying the page boundary to use when
     *                       painting the page content.
     * @param userRotation   Rotation factor, in degrees, to be applied to the rendered page.
     * @param userZoom       Zoom factor to be applied to the rendered page.
     * @throws InterruptedException thread interrupted.
     */
    public void paintPage(int pageNumber, Graphics g, final int renderHintType,
                          final int pageBoundary, float userRotation, float userZoom) throws InterruptedException {
        Page page = catalog.getPageTree().getPage(pageNumber);
        page.init();
        PDimension sz = page.getSize(userRotation, userZoom);
        int pageWidth = (int) sz.getWidth();
        int pageHeight = (int) sz.getHeight();

        Graphics gg = g.create(0, 0, pageWidth, pageHeight);
        page.paint(gg, renderHintType, pageBoundary, userRotation, userZoom);

        gg.dispose();
    }

    /**
     * Dispose of Document, freeing up all used resources.
     */
    public void dispose() {
        // clean up file it will clean up any file channels and file descriptors too
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
                logger.log(Level.FINE, "Error closing document random access file.", e);
            }
            randomAccessFile = null;
            documentFileChannel = null;
        }

        if (documentByteBuffer != null) {
            documentByteBuffer.clear();
        }

        String fileToDelete = getDocumentCachedFilePath();
        if (fileToDelete != null) {
            File file = new File(fileToDelete);
            boolean success = file.delete();
            if (!success) {
                logger.log(Level.WARNING, () -> "Error deleting URL cached to file " + fileToDelete);
            }
        }
    }

    /**
     * Takes the internal PDF data, which may be in a file or in RAM,
     * and write it to the provided OutputStream.
     * The OutputStream is not flushed or closed, in case this method's
     * caller requires otherwise.
     *
     * @param out OutputStream to which the PDF file bytes are written.
     * @return The length of the PDF file copied
     * @throws IOException if there is some problem reading or writing the PDF data
     */
    public long writeToOutputStream(OutputStream out) throws IOException {
        return writeToOutputStream(out, WriteMode.INCREMENT_UPDATE);
    }

    /**
     * Takes the internal PDF data, which may be in a file or in RAM,
     * and write it to the provided OutputStream.
     * The OutputStream is not flushed or closed, in case this method's
     * caller requires otherwise.
     *
     * @param out OutputStream to which the PDF file bytes are written.
     * @return The length of the PDF file copied
     * @throws IOException if there is some problem reading or writing the PDF data
     */
    public long writeToOutputStream(OutputStream out, WriteMode writeMode) throws IOException {
        if (documentFileChannel != null) {
            synchronized (library.getMappedFileByteBufferLock()) {
                ByteBuffer documentByteBuffer = library.getMappedFileByteBuffer();
                documentByteBuffer.position(0);
                int documentLength = documentByteBuffer.remaining();
                long newDocumentLength = new DocumentBuilder().createDocument(
                        writeMode,
                        this,
                        library.getMappedFileByteBuffer(),
                        out,
                        documentLength);
                return newDocumentLength;
            }
        } else if (documentByteBuffer != null) {
            synchronized (library.getMappedFileByteBufferLock()) {
                documentByteBuffer.position(0);
                int documentLength = documentByteBuffer.remaining();
                long newDocumentLength = new DocumentBuilder().createDocument(
                        writeMode,
                        this,
                        library.getMappedFileByteBuffer(),
                        out,
                        documentLength);
                return newDocumentLength;
            }
        } else {
            return 0;
        }
    }

    /**
     * Copies the pre-existing PDF file, and appends an incremental update for
     * any edits, to the specified OutputStream.
     *
     * @param out OutputStream to which the PDF file bytes are written.
     * @return The length of the PDF file saved
     * @throws IOException if there is some problem reading or writing the PDF data
     */
    public long saveToOutputStream(OutputStream out) throws IOException {
        return writeToOutputStream(out, WriteMode.INCREMENT_UPDATE);
    }

    /**
     * Copies the pre-existing PDF file, and applies any updates to the specified OutputStream using the specified
     * write model.
     *
     * @param out       OutputStream to which the PDF file bytes are written.
     * @param writeMode write mode used to update the file with changes.
     * @return The length of the PDF file saved
     * @throws IOException if there is some problem reading or writing the PDF data
     */
    public long saveToOutputStream(OutputStream out, WriteMode writeMode) throws IOException {
        return writeToOutputStream(out, writeMode);
    }

    /**
     * Gets an Image of the specified page.  The image size is automatically
     * calculated given the page boundary, user rotation and zoom.  The rendering
     * quality is defined by GraphicsRenderingHints.SCREEN.
     *
     * @param pageNumber     Page number of the page to capture the image rendering.
     *                       The page number is zero-based.
     * @param renderHintType Constant specified by the GraphicsRenderingHints class.
     *                       There are two possible entries, SCREEN and PRINT each with configurable
     *                       rendering hints settings.
     * @param pageBoundary   Constant specifying the page boundary to use when
     *                       painting the page content. Typically use Page.BOUNDARY_CROPBOX.
     * @param userRotation   Rotation factor, in degrees, to be applied to the rendered page.
     *                       Arbitrary rotations are not currently supported for this method,
     *                       so only the following values are valid: 0.0f, 90.0f, 180.0f, 270.0f.
     * @param userZoom       Zoom factor to be applied to the rendered page.
     * @return an Image object of the current page.
     * @throws InterruptedException thread interrupted.
     */
    public Image getPageImage(int pageNumber,
                              final int renderHintType, final int pageBoundary,
                              float userRotation, float userZoom) throws InterruptedException {
        Page page = catalog.getPageTree().getPage(pageNumber);
        page.init();
        PDimension sz = page.getSize(pageBoundary, userRotation, userZoom);

        int pageWidth = (int) sz.getWidth();
        int pageHeight = (int) sz.getHeight();

        BufferedImage image = ImageUtility.createCompatibleImage(pageWidth, pageHeight);
        Graphics g = image.createGraphics();

        page.paint(g, renderHintType,
                pageBoundary, userRotation, userZoom);
        g.dispose();

        return image;
    }

    /**
     * Exposes a page's PageText object which can be used to get text with
     * in the PDF document.  The PageText.toString() is the simplest way to
     * get a pages text.  This utility call does not parse the whole stream
     * and is best suited for text extraction functionality as it faster then
     * #getPageViewText(int).
     *
     * @param pageNumber Page number of page in which text extraction will act on.
     *                   The page number is zero-based.
     * @return page PageText data Structure.
     * {@link #getPageViewText(int)}
     * @throws InterruptedException thread interrupted.
     */
    public PageText getPageText(int pageNumber) throws InterruptedException {
        PageTree pageTree = catalog.getPageTree();
        if (pageNumber >= 0 && pageNumber < pageTree.getNumberOfPages()) {
            Page pg = pageTree.getPage(pageNumber);
            return pg.getText();
        } else {
            return null;
        }
    }

    /**
     * Exposes a page's PageText object which can be used to get text with
     * in the PDF document.  The PageText.toString() is the simplest way to
     * get a pages text.  The pageText hierarchy can be used to search for
     * selected text or used to set text as highlighted.
     *
     * @param pageNumber Page number of page in which text extraction will act on.
     *                   The page number is zero-based.
     * @return page PageText data Structure.
     * @throws InterruptedException thread interrupted.
     */
    public PageText getPageViewText(int pageNumber) throws InterruptedException {
        PageTree pageTree = catalog.getPageTree();
        if (pageNumber >= 0 && pageNumber < pageTree.getNumberOfPages()) {
            Page pg = pageTree.getPage(pageNumber);
            return pg.getViewText();
        } else {
            return null;
        }
    }

    /**
     * Gets the security manager for this document. If the document has no
     * security manager null is returned.
     *
     * @return security manager for document if available.
     */
    public SecurityManager getSecurityManager() {
        return library.getSecurityManager();
    }

    /**
     * Sets the security callback to be used for this document.  The security
     * callback allows a mechanism for prompting a user for a password if the
     * document is password protected.
     *
     * @param securityCallback a class which implements the SecurityCallback
     *                         interface.
     */
    public void setSecurityCallback(SecurityCallback securityCallback) {
        this.securityCallback = securityCallback;
    }

    /**
     * Gets the document's information as specified in the PTrailer in the document
     * hierarchy.
     *
     * @return document information
     * {@link PInfo}
     */
    public PInfo getInfo() {
        PTrailer pTrailer = crossReferenceRoot.getTrailerDictionary();
        if (pTrailer == null)
            return null;
        return pTrailer.getInfo();
    }

    public void deletePage(Page page) {
        if (page == null) {
            throw new IllegalStateException("Page must not be null");
        }
        PageRemovalModifier pageRemovalModifier = (PageRemovalModifier) ModifierFactory.getModifier(catalog, page);
        if (pageRemovalModifier != null) {
            pageRemovalModifier.modify(page);
        }
    }

    /**
     * Returns the document's information or create and set it if it doesn't exist
     *
     * @return The document information
     */
    public PInfo createOrGetInfo() {
        final PInfo info = getInfo();
        return Objects.requireNonNullElseGet(info, this::createInfo);
    }

    private PInfo createInfo() {
        final PInfo pInfo = new PInfo(library, new DictionaryEntries());
        final Reference pInfoReference = stateManager.getNewReferenceNumber();
        pInfo.setPObjectReference(pInfoReference);
        library.addObject(pInfo.getEntries(), pInfoReference);
        PTrailer pTrailer = crossReferenceRoot.getTrailerDictionary();
        if (pTrailer != null) {
            pTrailer.entries.put(PTrailer.INFO_KEY, pInfoReference);
        }
        return pInfo;
    }

    /**
     * Enables or disables the form widget annotation highlighting.  Generally not use for print but can be very
     * useful for highlight input fields in a Viewer application.
     *
     * @param highlight true to enable highlight mode, otherwise; false.
     */
    public void setFormHighlight(boolean highlight) {
        // iterate over the document annotations and set the appropriate highlight value.
        if (catalog != null && catalog.getInteractiveForm() != null) {
            InteractiveForm interactiveForm = catalog.getInteractiveForm();
            ArrayList<Object> widgets = interactiveForm.getFields();
            if (widgets != null) {
                for (Object widget : widgets) {
                    descendFormTree(widget, highlight);
                }
            }
        }
    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param formNode root form node.
     */
    private void descendFormTree(Object formNode, boolean highLight) {
        if (formNode instanceof AbstractWidgetAnnotation) {
            ((AbstractWidgetAnnotation<?>) formNode).setEnableHighlightedWidget(highLight);
        } else if (formNode instanceof FieldDictionary) {
            // iterate over the kid's array.
            FieldDictionary child = (FieldDictionary) formNode;
            formNode = child.getKids();
            if (formNode != null) {
                ArrayList kidsArray = (ArrayList) formNode;
                for (Object kid : kidsArray) {
                    if (kid instanceof Reference) {
                        kid = library.getObject((Reference) kid);
                    }
                    if (kid instanceof AbstractWidgetAnnotation) {
                        ((AbstractWidgetAnnotation<?>) kid).setEnableHighlightedWidget(highLight);
                    } else if (kid instanceof FieldDictionary) {
                        descendFormTree(kid, highLight);
                    }
                }
            }

        }
    }

    /**
     * Gets a vector of Images where each index represents an image  inside
     * the specified page.  The images are returned in the size in which they
     * where embedded in the PDF document, which may be different than the
     * size displayed when the complete PDF page is rendered.
     *
     * @param pageNumber page number to act on.  Zero-based page number.
     * @return vector of Images inside the current page
     * @throws InterruptedException thread interrupted.
     */
    public List<Image> getPageImages(int pageNumber) throws InterruptedException {
        Page pg = catalog.getPageTree().getPage(pageNumber);
        pg.init();
        return pg.getImages();
    }

    /**
     * Gets the Document Catalog's PageTree entry as specified by the Document
     * hierarchy.  The PageTree can be used to obtain detailed information about
     * the Page object which makes up the document.
     *
     * @return PageTree specified by the document hierarchy. Null if the document
     * has not yet loaded or the catalog can not be found.
     */
    public PageTree getPageTree() {
        if (catalog != null) {
            PageTree pageTree = catalog.getPageTree();
            if (pageTree != null) {
                pageTree.setWatermarkCallback(watermarkCallback);
            }
            return pageTree;
        } else {
            return null;
        }
    }

    /**
     * Gets the Document's Catalog as specified by the Document hierarchy. The
     * Catalog can be used to traverse the Document's hierarchy.
     *
     * @return document's Catalog object; null, if one does not exist.
     */
    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * Sets the caching mode when handling file loaded by an URI.  If enabled
     * URI streams will be cached to disk, otherwise they will be stored in
     * memory. This method must be set before a call to setByteArray() or
     * setInputStream() is called.
     *
     * @param cachingEnabled true to enable, otherwise false.
     */
    public static void setCachingEnabled(boolean cachingEnabled) {
        isCachingEnabled = cachingEnabled;
    }
}
