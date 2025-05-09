package org.icepdf.core.util.updater;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.signature.DocumentSigner;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncrementalUpdater {

    private static final Logger logger =
            Logger.getLogger(IncrementalUpdater.class.toString());

    /**
     * Appends modified objects to the specified output stream.
     *
     * @param document       The Document that is being saved
     * @param documentByteBuffer ByteBuffer of the original document
     * @param outputStream   OutputStream to write the incremental update to
     * @param documentLength start of appender bytes,  can be zero if storing the bytes to another source.
     * @return The number of bytes written in the incremental update
     * @throws java.io.IOException error writing stream.
     */
    public long appendIncrementalUpdate(
            Document document, ByteBuffer documentByteBuffer, OutputStream outputStream, long documentLength)
            throws IOException {

        Library library = document.getCatalog().getLibrary();
        SignatureManager signatureManager = library.getSignatureDictionaries();
        StateManager stateManager = document.getStateManager();
        CrossReferenceRoot crossReferenceRoot = stateManager.getCrossReferenceRoot();
        if (stateManager.isNoChange() && !signatureManager.hasSignatureDictionary()) {
            return 0L;
        }

        // create a temp document so that it can sign it after the incremental update
        Path tmpFilePath = Files.createTempFile(null, null);
        File tempFile = tmpFilePath.toFile();
        OutputStream newDocumentOutputStream = new FileOutputStream(tempFile);
        try {
            // copy original file data
            WritableByteChannel channel = Channels.newChannel(newDocumentOutputStream);
            channel.write(documentByteBuffer);
        } catch (IOException e) {
            logger.log(Level.FINE, "Error writing PDF output stream during incremental write.", e);
            throw e;
        }

        SecurityManager securityManager = document.getSecurityManager();
        CountingOutputStream output = new CountingOutputStream(newDocumentOutputStream);

        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, output, documentLength);
        writer.initializeWriters();
        writer.writeNewLine();
        Iterator<StateManager.Change> changes = stateManager.iteratorSortedByObjectNumber();
        while (changes.hasNext()) {
            StateManager.Change change = changes.next();
            if (change.getType() != StateManager.Type.DELETE) {
                PObject pobject = change.getPObject();
                writer.writePObject(pobject);
            }
        }

        // todo, may need updating as I don't think it handles hybrid mode properly
        PTrailer trailer = crossReferenceRoot.getTrailerDictionary();
        if (trailer.isCompressedXref()) {
            writer.writeIncrementalCompressedXrefTable();
        } else {
            writer.writeXRefTable();
            writer.writeIncrementalUpdateTrailer();
        }
        output.close();

        // sign the document using the first signature, this could be reworked to handle more signatures, like
        // certification followed by other approvals.  But for now it will be assumed this is done as seperate steps
        Document tmpDocument = new Document();
        try {
            if (signatureManager.hasSignatureDictionary()) {
                // open new incrementally updated tmp file
                tmpDocument.setFile(tempFile.toString());
                // size of new file, this won't change as SignatureDictionary has padding to account for content and
                // offsets
                DocumentSigner.signDocument(tmpDocument, tempFile,
                        signatureManager.getCurrentSignatureDictionary());
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to sign document.", e);
            throw new RuntimeException(e);
        } finally {
            tmpDocument.dispose();
        }

        // copy the temp file to the outputStream and cleanup.
        Files.copy(tmpFilePath, outputStream);
        Files.delete(tmpFilePath);

        return writer.getBytesWritten();
    }
}
