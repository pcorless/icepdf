package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.updater.writeables.image.ImageEncoder;
import org.icepdf.core.util.updater.writeables.image.ImageEncoderFactory;

import java.io.IOException;

/**
 * ImageStreamWriter takes care of writing images stream as one would probably expect.  This class is needed for
 * writing redacted ImageStreams.
 *
 * @since 7.2.0
 */
public class ImageStreamWriter extends StreamWriter {

    public ImageStreamWriter(SecurityManager securityManager) {
        super(securityManager);
    }

    public void write(ImageStream imageStream, SecurityManager securityManager, CountingOutputStream output) throws IOException {
        byte[] outputData;
        // decoded image is only set if the image was touch via a redaction burn.
        if (imageStream.getDecodedImage() != null) {
            ImageEncoder imageEncoder = ImageEncoderFactory.createEncodedImage(imageStream);
            imageStream = imageEncoder.encode();
            outputData = imageStream.getRawBytes();

            // check if we need to encrypt the stream
            if (securityManager != null) {
                outputData = encryptStream(imageStream);
            }
        } else {
            // no modification, just write out the image unaltered.
            outputData = imageStream.getRawBytes();
        }
        writeStreamObject(output, imageStream, outputData);
    }
}
