package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.updater.writeables.image.ImageEncoder;
import org.icepdf.core.util.updater.writeables.image.ImageEncoderFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageStreamWriter extends StreamWriter {
    public void write(ImageStream imageStream, SecurityManager securityManager, CountingOutputStream output) throws IOException {
        byte[] outputData;
        // decoded image is only set if the image was touch via a redaction burn.
        if (imageStream.getDecodedImage() != null) {
            ImageEncoder imageEncoder = ImageEncoderFactory.createEncodedImage(imageStream);
            imageStream = imageEncoder.encode();
            outputData = imageStream.getRawBytes();

            // check if we need to encrypt the stream
            if (securityManager != null) {
                DictionaryEntries decodeParams;
                if (imageStream.getEntries().get(Stream.DECODEPARAM_KEY) != null) {
                    // needed to check for a custom crypt filter
                    decodeParams = imageStream.getLibrary().getDictionary(imageStream.getEntries(),
                            Stream.DECODEPARAM_KEY);
                } else {
                    decodeParams = new DictionaryEntries();
                }
                InputStream decryptedStream = securityManager.encryptInputStream(
                        imageStream.getPObjectReference(),
                        securityManager.getDecryptionKey(),
                        decodeParams,
                        new ByteArrayInputStream(outputData), true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = decryptedStream.read(data, 0, data.length)) != -1) {
                    out.write(data, 0, nRead);
                }
                outputData = out.toByteArray();
            }
        } else {
            // no modification, just write out the image unaltered.
            outputData = imageStream.getRawBytes();
        }
        writeStreamObject(output, imageStream, outputData);
    }
}
