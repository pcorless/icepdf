package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * Simple builder to handle the multiple ways of writing document changes to an output stream.  Which are
 * incremental and full rewrites.
 *
 * @since 7.2
 */
public class DocumentBuilder {

    private static final Logger logger =
            Logger.getLogger(Document.class.toString());

    public long createDocument(
            WriteMode writeMode,
            Document document,
            ByteBuffer documentByteBuffer,
            OutputStream out,
            long documentLength) throws IOException, InterruptedException {

        long length = -1;
        if (writeMode == WriteMode.FULL_UPDATE) {
            // kick of a full rewrite of the document, replacing any updates objects with new data
            length = new FullUpdater().writeDocument(
                    document,
                    out);
        } else if (writeMode == WriteMode.INCREMENT_UPDATE) {
            // append the data from the incremental updater
            long appendedLength = new IncrementalUpdater().appendIncrementalUpdate(
                    document,
                    documentByteBuffer,
                    out,
                    documentLength);
            length = documentLength + appendedLength;
        }
        return length;
    }
}
