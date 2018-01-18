/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.filters.CCITTFax;
import org.icepdf.core.pobjects.filters.CCITTFaxDecoder;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FaxDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JBig2Decoder.class.toString());

    public static final Name K_KEY = new Name("K");
    public static final Name ENCODED_BYTE_ALIGN_KEY = new Name("EncodedByteAlign");
    public static final Name COLUMNS_KEY = new Name("Columns");
    public static final Name ROWS_KEY = new Name("Rows");

    /**
     * Gets the value of the system property "org.icepdf.core.ccittfax.checkParentBlackIs1".
     */
    public static boolean CHECK_PARENT_BLACK_IS_1 =
            Defs.booleanProperty("org.icepdf.core.ccittfax.checkParentBlackIs1", false);


    public FaxDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {

        BufferedImage decodedImage = null;

        ImageParams imageParams = imageStream.getImageParams();
        HashMap decodeParms = imageParams.getDecodeParams();

        if (decodeParms == null) {
            logger.warning("CCITTFax decode params could not be found. ");
        }

        int k = imageParams.getInt(decodeParms, K_KEY);

        boolean encodedByteAlign = false;
        Object encodedByteAlignObject = imageParams.getObject(decodeParms, ENCODED_BYTE_ALIGN_KEY);
        if (encodedByteAlignObject instanceof Boolean) {
            encodedByteAlign = (Boolean) encodedByteAlignObject;
        }
        int columns = imageParams.getWidth();
        int rows = imageParams.getHeight();
        if (columns <= 0) imageParams.getInt(decodeParms, COLUMNS_KEY);
        if (rows <= 0) rows = imageParams.getInt(decodeParms, ROWS_KEY);

        int size = rows * ((columns + 7) >> 3);

        byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
        byte[] decodedStreamData = null;
        try {
            // try and load the image via twelve monkeys
            decodedStreamData = ccittFaxDecodeTwelveMonkeys(data, k, encodedByteAlign, columns, rows, size);
        } catch (Throwable e) {
            try {
                // on a failure then fall back on our implementation.
                logger.warning("Error during decode falling back on alternative fax decode.");
                data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
                decodedStreamData = ccittFaxDecodeCCITTFaxDecoder(data, k, encodedByteAlign, columns, rows, size);
            } catch (Throwable f) {
                // on a failure then fall back to JAI
                logger.warning("Error during decode falling back on JAI decode.");
                decodedImage = ccittFaxDecodeJAI(imageStream, imageStream.getLibrary(),
                        imageStream.getEntries(), graphicsState.getFillColor());
            }
        }

        if (decodedStreamData != null) {
            // check the black is value flag, no one likes inverted colours.
            // default value is always false
            decodedStreamData = applyBlackIsOne(decodedStreamData, imageParams, decodeParms);
            try {
                decodedImage = ImageUtility.makeImageWithRasterFromBytes(decodedStreamData, graphicsState, imageParams);
            } catch (Exception e) {
                logger.log(Level.FINE, "Error building image raster.", e);
                logger.warning("Error during decode falling back on JAI decode.");
                decodedImage = ccittFaxDecodeJAI(imageStream, imageStream.getLibrary(),
                        imageStream.getEntries(), graphicsState.getFillColor());
            }
        }
        return decodedImage;
    }

    private byte[] ccittFaxDecodeTwelveMonkeys(byte[] streamData, int k, Boolean encodedByteAlign,
                                               int columns, int rows, int size)
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException, IOException {

        byte[] decodedStreamData = new byte[size];
        int compression;
        long options = 0;
        if (k == 0) {
            compression = 3; // Group 3 1D
            if (streamData[0] != 0 || (streamData[1] >> 4 != 1 && streamData[1] != 1)) {
                // leading EOL (0b000000000001) not found, search further and try RLE if not found
                compression = 2;
                short b = (short) (((streamData[0] << 8) + streamData[1]) >> 4);
                for (int i = 12; i < 160; i++) {
                    b = (short) ((b << 1) + ((streamData[(i / 8)] >> (7 - (i % 8))) & 0x01));
                    if (b == 1) {
                        compression = 3;
                        break;
                    }
                }
            }
        } else if (k > 0) {
            // Group 3 2D
            compression = 3;
            options = 1;
        } else {
            // Group 4
            compression = 4;
        }

        Class<?> tmDecoder = Class.forName("com.twelvemonkeys.imageio.plugins.tiff.CCITTFaxDecoderStream");
        Constructor tmDecoderConst = tmDecoder.getConstructor(
                InputStream.class, int.class, int.class, int.class, long.class, boolean.class);
        tmDecoderConst.setAccessible(true);

        ByteArrayInputStream bis = new ByteArrayInputStream(streamData);
        InputStream decoderStream = (InputStream) tmDecoderConst.newInstance(
                bis, columns, compression, 1, options, encodedByteAlign);

        DataInputStream dis = new DataInputStream(decoderStream);
        dis.readFully(decodedStreamData);

        return decodedStreamData;
    }

    private byte[] ccittFaxDecodeCCITTFaxDecoder(byte[] streamData, int k, Boolean encodedByteAlign,
                                                 int columns, int rows, int size) {

        CCITTFaxDecoder decoder = new CCITTFaxDecoder(1, columns, rows);
        decoder.setAlign(encodedByteAlign);
        byte[] decodedStreamData = new byte[size];
        // pick three three possible fax encoding.
        try {
            if (k == 0) {
                decoder.decodeT41D(decodedStreamData, streamData, 0, rows);
            } else if (k > 0) {
                decoder.decodeT42D(decodedStreamData, streamData, 0, rows);
            } else {
                decoder.decodeT6(decodedStreamData, streamData, 0, rows);
            }
        } catch (Exception e2) {
            logger.warning("Error decoding CCITTFax image k: " + k);
            // IText 5.03 doesn't correctly assign a k value for the deocde,
            // as  result we can try one more time using the T6.
            decoder.decodeT6(decodedStreamData, streamData, 0, rows);
        }
        return decodedStreamData;
    }

    public static byte[] applyBlackIsOne(byte[] decodedStreamData, ImageParams imageParams, HashMap decodeParms) {
        boolean blackIs1 = imageParams.getBlackIs1(decodeParms);
        // double check for blackIs1 in the main dictionary.
        if (!blackIs1 && CHECK_PARENT_BLACK_IS_1) {
            blackIs1 = imageParams.getBlackIs1(imageParams.getEntries());
        }
        if (!blackIs1) {
            // toggle the byte data invert colour, not bit operand.
            for (int i = 0; i < decodedStreamData.length; i++) {
                decodedStreamData[i] = (byte) ~decodedStreamData[i];
            }
        }
        return decodedStreamData;
    }

    public BufferedImage ccittFaxDecodeJAI(ImageStream stream, Library library, HashMap streamDictionary, Color fill) {
        try {
            return CCITTFax.attemptDeriveBufferedImageFromBytes(
                    stream, library, streamDictionary, fill);
        } catch (Throwable e) {
            logger.warning("Error decoding using JAI CCITTFax decode.");
        }
        return null;
    }
}
