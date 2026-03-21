/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        // decoded image is only set if the image was touch via a redaction burn and will always be unencrypted
        if (imageStream.getDecodedImage() != null) {
            ImageEncoder imageEncoder = ImageEncoderFactory.createEncodedImage(imageStream);
            imageStream = imageEncoder.encode();
            outputData = imageStream.getRawBytes();

            // check if we need to encrypt the stream
            if (securityManager != null) {
                outputData = encryptStream(imageStream, outputData);
            }
        } else {
            // no modification, just write out the image unaltered.
            outputData = imageStream.getRawBytes();
        }
        writeStreamObject(output, imageStream, outputData);
    }
}
