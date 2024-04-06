package org.icepdf.core.util.updater;

import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.icepdf.core.pobjects.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
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

        try (WritableByteChannel channel = Channels.newChannel(out)) {
            long length;
            if (writeMode == WriteMode.FULL_UPDATE) {
                // kick of a full rewrite of the document, replacing any updates objects with new data
                length = new FullUpdater().writeDocument(
                        document,
                        out);
            } else if (writeMode == WriteMode.INCREMENT_UPDATE) {
                // copy original file data
                channel.write(documentByteBuffer);
                // append the data from the incremental updater
                long appendedLength = new IncrementalUpdater().appendIncrementalUpdate(
                        document,
                        out,
                        documentLength);
                channel.close();
                length = documentLength + appendedLength;
            }

            CMSSignedDataGenerator gen = document.getCatalog().getSignedDataGenerator();
            if (gen != null) {
                // Signer.updateSigature(gen, out);
                // find the /contents pattern regex
                //    get start and end offsets.
                // find the /ByteRange offset regex
                //    replace pattern with proper offset
                // digest the two byte ranges
                // write the signature value to the /contents placeholder
                // document.getCatalog().setSignedDataGenerator(null);
            }

            return length;
        } catch (IOException | InterruptedException e) {
            logger.log(Level.FINE, "Error writing PDF output stream.", e);
            throw e;
        }

        return 0;
    }
}
