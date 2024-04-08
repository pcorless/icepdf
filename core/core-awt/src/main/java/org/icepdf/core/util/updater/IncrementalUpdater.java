package org.icepdf.core.util.updater;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.signature.Signer;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.writeables.BaseWriter;

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
     * @param outputStream   OutputStream to write the incremental update to
     * @param documentLength start of appender bytes,  can be zero if storing the bytes to another source.
     * @return The number of bytes written in the incremental update
     * @throws java.io.IOException error writing stream.
     */
    public long appendIncrementalUpdate(
            Document document, ByteBuffer documentByteBuffer, OutputStream outputStream, long documentLength)
            throws IOException {

        Library library = document.getCatalog().getLibrary();
        StateManager stateManager = document.getStateManager();
        CrossReferenceRoot crossReferenceRoot = stateManager.getCrossReferenceRoot();
        if (stateManager.isNoChange() && !library.hasSigners()) {
            return 0L;
        }

        // create a temp document so that it can sign it after the incremental update
        Path tmpFile = Files.createTempFile(null, null);
        OutputStream newDocumentOutputStream = new FileOutputStream(tmpFile.toFile());
        try (WritableByteChannel channel = Channels.newChannel(newDocumentOutputStream)) {
            // copy original file data
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

        // sign the document using the first signature, this could be reworked to handle more signatures.
        Document tmpDocument = new Document();
        try {
            if (library.hasSigners()) {
                // open new incrementally updated tmp file
                tmpDocument.setFile(tmpFile.toString());
                Signer.signDocument(tmpDocument, output, library.getSigner());
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to sign document.", e);
            throw new RuntimeException(e);
        } finally {
            output.close();
            tmpDocument.dispose();
            Files.delete(tmpFile);
        }

        // copy the temp file to the outputStream and cleanup.
        Files.copy(tmpFile, outputStream);

        return writer.getBytesWritten();
    }
}
