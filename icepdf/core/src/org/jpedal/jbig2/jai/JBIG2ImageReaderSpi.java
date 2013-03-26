/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
package org.jpedal.jbig2.jai;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Arrays;

public class JBIG2ImageReaderSpi extends ImageReaderSpi {

    public JBIG2ImageReaderSpi() {
        super("JPedal", // vendorName
                "1.0", // version
                new String[]{"JBIG2"}, // names
                new String[]{"jb2", "jbig2"}, // suffixes
                new String[]{"image/x-jbig2"}, // MIMETypes
                "org.jpedal.jbig2.jai.JBIG2ImageReader", // readerClassName
                new Class[]{ImageInputStream.class}, // inputTypes
                null, // writerSpiNames
                false, // supportsStandardStreamMetadataFormat
                null, // nativeStreamMetadataFormatName
                null, // nativeStreamMetadataFormatClassName
                null, // extraStreamMetadataFormatNames
                null, // extraStreamMetadataFormatClassNames
                false, // supportsStandardImageMetadataFormat
                null, // nativeImageMetadataFormatName
                null, // nativeImageMetadataFormatClassName
                null, // extraImageMetadataFormatNames
                null); // extraImageMetadataFormatClassNames

    }

    public boolean canDecodeInput(Object input) throws IOException {

        // The input source must be an ImageInputStream because the constructor
        // passes STANDARD_INPUT_TYPE (an array consisting of ImageInputStream)
        // as the only type of input source that it will deal with to its
        // superclass.

        if (!(input instanceof ImageInputStream))
            return false;

        ImageInputStream stream = (ImageInputStream) input;

        /** Read and validate the input source's header. */
        byte[] header = new byte[8];
        try {
            // The input source's current position must be preserved so that
            // other ImageReaderSpis can determine if they can decode the input
            // source's format, should this input source be unable to handle the
            // decoding. Because the input source is an ImageInputStream, its
            // mark() and reset() methods are called to preserve the current
            // position.

            stream.mark();
            stream.read(header);
            stream.reset();
        } catch (IOException e) {
            return false;
        }

        byte[] controlHeader = new byte[]{(byte) 151, 74, 66, 50, 13, 10, 26, 10};

        return Arrays.equals(controlHeader, header);
    }

    public ImageReader createReaderInstance(Object extension) throws IOException {
        // Inform the JBIG2 image reader that this JBIG2 image reader SPI is the
        // originating provider -- the object that creates the JBIG2 image
        // reader.
        return new JBIG2ImageReader(this);
    }

    public String getDescription(java.util.Locale locale) {
        return "JPedal JBIG2 Image Decoder provided by IDRsolutions.  See http://www.jpedal.org/jbig.php";
    }
}
