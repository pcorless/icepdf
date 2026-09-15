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
package org.icepdf.core.pobjects.filters;


import org.icepdf.core.io.ZeroPaddedInputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.filters.FlateDecode.COLUMNS_KEY;
import static org.icepdf.core.pobjects.graphics.images.FaxDecoder.K_KEY;

/**
 * Decodes a CCITT fax by handing it to an image reader, which is the last thing
 * {@link org.icepdf.core.pobjects.graphics.images.FaxDecoder} tries: TwelveMonkeys first, then
 * {@link CCITTFaxDecoder}, and only when both have thrown does it come here.  The bits are wrapped
 * in a TIFF header so that a reader will take them, JAI's if it is on the class path and ImageIO's
 * otherwise.
 * <p>
 * CCITT encoding is defined by the T.4 and T.6 standards, better known as Group 3 and Group 4.  This
 * class used to carry a hand written Group 4 decoder of its own beside the reader path, reached by a
 * public Group4Decode method; nothing called it, and it disagreed with {@link CCITTFaxDecoder} about
 * which bit value means black, so it was removed rather than left as a trap for whoever wired it up.
 * The decoding this project does itself lives in {@link CCITTFaxDecoder}.
 */
public class CCITTFax {

    private static final Logger logger =
            Logger.getLogger(CCITTFax.class.getName());


    private static final short TIFF_COMPRESSION_NONE_default = 1;
    private static final short TIFF_COMPRESSION_GROUP3_1D = 2;
    private static final short TIFF_COMPRESSION_GROUP3_2D = 3;
    private static final short TIFF_COMPRESSION_GROUP4 = 4;

    private static final String[] TIFF_COMPRESSION_NAMES = new String[]{
            "",
            "TIFF_COMPRESSION_NONE_default",
            "TIFF_COMPRESSION_GROUP3_1D",
            "TIFF_COMPRESSION_GROUP3_2D",
            "TIFF_COMPRESSION_GROUP4"
    };

    private static final short TIFF_PHOTOMETRIC_INTERPRETATION_WHITE_IS_ZERO_default = 0;
    private static final short TIFF_PHOTOMETRIC_INTERPRETATION_BLACK_IS_ZERO = 1;

    private static boolean USE_JAI_IMAGE_LIBRARY = false;
    private static Method jaiCreate = null;
    private static Method ssWrapInputStream = null;
    private static Method roGetAsBufferedImage = null;

    static {
        try {
            Class<?> jaiClass = Class.forName("javax.media.jai.JAI");
            jaiCreate = jaiClass.getMethod("create", String.class, ParameterBlock.class);
            Class<?> ssClass = Class.forName("com.sun.media.jai.codec.SeekableStream");
            ssWrapInputStream = ssClass.getMethod("wrapInputStream", InputStream.class, Boolean.TYPE);
            Class<?> roClass = Class.forName("javax.media.jai.RenderedOp");
            roGetAsBufferedImage = roClass.getMethod("getAsBufferedImage");
            USE_JAI_IMAGE_LIBRARY = true;
        } catch (Exception e) {
            logger.info("javax.media.jai.JAI could not bef found on the class path");
        }

        if (logger.isLoggable(Level.FINER)) {
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("TIFF");
            ImageReader reader;
            while (iter.hasNext()) {
                reader = iter.next();
                logger.finer("CCITTFaxDecode Image reader: " + reader);
            }
        }
    }


    public static BufferedImage attemptDeriveBufferedImageFromBytes(
            ImageStream stream, Library library, DictionaryEntries streamDictionary, Color fill) {
        if (!USE_JAI_IMAGE_LIBRARY)
            return null;

        ImageParams imageParams = stream.getImageParams();
        boolean imageMask = stream.getImageParams().isImageMask();
        float[] decodeArray = imageParams.getDecode();
        // get decode parameters from stream properties
        DictionaryEntries decodeParmsDictionary = imageParams.getDecodeParams();
        boolean blackIs1 = imageParams.getBlackIs1(decodeParmsDictionary);
        // double check for blackIs1 in the main dictionary.

        int k = imageParams.getInt(decodeParmsDictionary, K_KEY);

        short compression = TIFF_COMPRESSION_NONE_default;
        if (k < 0) compression = TIFF_COMPRESSION_GROUP4;
        else if (k > 0) compression = TIFF_COMPRESSION_GROUP3_2D;
        else compression = TIFF_COMPRESSION_GROUP3_1D;
        boolean hasHeader;

        InputStream input = stream.getDecodedByteArrayInputStream();
        if (input == null)
            return null;
        input = new ZeroPaddedInputStream(input);
        BufferedInputStream bufferedInput = new BufferedInputStream(input, 32 * 1024);
        bufferedInput.mark(4);
        try {
            int hb1 = bufferedInput.read();
            int hb2 = bufferedInput.read();
            bufferedInput.reset();
            if (hb1 < 0 || hb2 < 0) {
                input.close();
                return null;
            }
            hasHeader = ((hb1 == 0x4d && hb2 == 0x4d) || (hb1 == 0x49 && hb2 == 0x49));
        } catch (IOException e) {
            try {
                input.close();
            } catch (IOException ioe) {
                // keep quiet
            }
            return null;
        }
        input = bufferedInput;

        BufferedImage img;

        byte[] fakeHeaderBytes;
        if (!hasHeader) {
            // Apparently if the stream dictionary contains all the necessary info about
            //   the TIFF data in the stream, then some encoders omit the standard
            //   TIFF header in the stream, which confuses some image decoders, like JAI,
            //   in which case we inject a TIFF header which is derived from the stream
            //   dictionary.
            fakeHeaderBytes = new byte[]{
                    // TIFF Header
                    0x4d, 0x4d,                                        // 00 : Big (sane) endian
                    0x00, 0x2a,                                        // 02 : Magic 42
                    0x00, 0x00, 0x00, 0x08,                            // 04 : Offset to first IFD

                    // First IFD
                    0x00, 0x0c,                                        // 08 : Num Directory Entries
                    // Directory Entries: ushort tag, ushort type, uint count, uint valueOrOffset
                    0x00, (byte) 0xfe, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,  // 0a : NewSubfileType
                    0x01, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 16 : ImageWidth
                    0x01, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 22 : ImageLength
                    0x01, 0x02, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,         // 2E : BitsPerSample
                    0x01, 0x03, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,         // 3A : Compression
                    0x01, 0x06, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 46 : PhotometricInterpretation
                    0x01, 0x11, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) 0xAE,  // 52 : StripOffsets
                    0x01, 0x16, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 5E : RowsPerStrip
                    0x01, 0x17, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 6A : StripByteCounts
                    0x01, 0x1A, 0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) 0x9E,  // 76 : XResolution
                    0x01, 0x1B, 0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) 0xA6,  // 82 : YResolution
                    0x01, 0x28, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,         // 8E : ResolutionUnit
                    0x00, 0x00, 0x00, 0x00,                            // 9A : Next IFD
                    // Values from IFD, which don't fit in value field
                    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,   // 9E : XResolution RATIONAL value
                    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01}; // A6 : YResolution RATIONAL value
            // AE : Begin data

            // Have to fill in values for: ImageWidth, ImageLength, BitsPerSample, Compression,
            //   PhotometricIntrerpretation, RowsPerStrip, StripByteCounts

            int width = library.getInt(streamDictionary, ImageParams.WIDTH_KEY);
            int height = library.getInt(streamDictionary, ImageParams.HEIGHT_KEY);

            Object columnsObj = library.getObject(decodeParmsDictionary, COLUMNS_KEY);
            if (columnsObj instanceof Number) {
                int columns = ((Number) columnsObj).intValue();
                if (columns > width)
                    width = columns;
            }

            Utils.setIntIntoByteArrayBE(width, fakeHeaderBytes, 0x1E);       // ImageWidth
            Utils.setIntIntoByteArrayBE(height, fakeHeaderBytes, 0x2A);      // ImageLength
            Object bitsPerComponent =                                          // BitsPerSample
                    library.getObject(streamDictionary, ImageParams.BITS_PER_COMPONENT_KEY);
            if (bitsPerComponent instanceof Number) {
                Utils.setShortIntoByteArrayBE(((Number) bitsPerComponent).shortValue(), fakeHeaderBytes, 0x36);
            }

            Utils.setShortIntoByteArrayBE(compression, fakeHeaderBytes, 0x42);
            short photometricInterpretation = TIFF_PHOTOMETRIC_INTERPRETATION_WHITE_IS_ZERO_default;
            // PDF has default BlackIs1=false               ==> White=1, Black=0
            // TIFF has default PhotometricInterpretation=0 ==> White=0, Black=1
            // So, if PDF doesn't state what black and white are, then use TIFF's default
            if (!blackIs1) {
                photometricInterpretation = TIFF_PHOTOMETRIC_INTERPRETATION_BLACK_IS_ZERO;
            }
            Utils.setShortIntoByteArrayBE(                                     // PhotometricInterpretation
                    photometricInterpretation, fakeHeaderBytes, 0x4E);
            Utils.setIntIntoByteArrayBE(height, fakeHeaderBytes, 0x66);      // RowsPerStrip
            int lengthOfCompressedData = Integer.MAX_VALUE - 1;                // StripByteCounts
            Object lengthValue = library.getObject(streamDictionary, Stream.LENGTH_KEY);
            if (lengthValue instanceof Number)
                lengthOfCompressedData = ((Number) lengthValue).intValue();
            else {
                // JAI's SeekableStream pukes if we give a number too large
                int approxLen = width * height;
                if (approxLen > 0)
                    lengthOfCompressedData = approxLen;
            }
            Utils.setIntIntoByteArrayBE(lengthOfCompressedData, fakeHeaderBytes, 0x72);

            ByteArrayInputStream fakeHeaderBytesIn = new ByteArrayInputStream(fakeHeaderBytes);
            org.icepdf.core.io.SequenceInputStream sin = new org.icepdf.core.io.SequenceInputStream(fakeHeaderBytesIn, input);

            img = deriveBufferedImageFromTIFFBytes(sin, library, lengthOfCompressedData, width, height, compression);
            if (img == null) {
                for (int i = 1; i <= 4; i++) { // Try the three other types of compression (1, 2, 3, 4)
                    compression++;
                    // We don't try the default uncompressed format, because it sometimes
                    //  returns a blank image, which we don't want.  If JAI fails, we
                    //  want it to return null, so that the fallback code can have a try
                    if (compression > TIFF_COMPRESSION_GROUP4)
                        compression = TIFF_COMPRESSION_GROUP3_1D;

                    Utils.setShortIntoByteArrayBE(compression, fakeHeaderBytes, 0x42);
                    input = stream.getDecodedByteArrayInputStream();
                    if (input == null)
                        return null;
                    input = new ZeroPaddedInputStream(input);
                    fakeHeaderBytesIn = new ByteArrayInputStream(fakeHeaderBytes);
                    sin = new org.icepdf.core.io.SequenceInputStream(fakeHeaderBytesIn, input);
                    img = deriveBufferedImageFromTIFFBytes(sin, library, lengthOfCompressedData, width, height, compression);
                    if (img != null) {
                        break;
                    }
                }
            }
        } else {
            int width = library.getInt(streamDictionary, ImageParams.WIDTH_KEY);
            int height = library.getInt(streamDictionary, ImageParams.HEIGHT_KEY);
            int approxLen = width * height;
            img = deriveBufferedImageFromTIFFBytes(input, library, approxLen, width, height, compression);
        }

        if (img != null) {
            img = applyImageMaskAndDecodeArray(img, imageMask, blackIs1, decodeArray, fill);
        }

        return img;
    }

    /**
     * Calling code assumes that this method will trap all exceptions,
     * so that null shows it didn't work
     *
     * @param in InputStream to TIFF byte data
     * @return RenderedImage if could derive one, else null
     */
    private static BufferedImage deriveBufferedImageFromTIFFBytes(
            InputStream in, Library library, int compressedBytes, int width, int height, int compression) {
        BufferedImage img = null;
        try (in) {
            /*
            com.sun.media.jai.codec.SeekableStream s = com.sun.media.jai.codec.SeekableStream.wrapInputStream( in, true );
            ParameterBlock pb = new ParameterBlock();
            pb.add( s );
            javax.media.jai.RenderedOp op = javax.media.jai.JAI.create( "tiff", pb );
            */
            Object com_sun_media_jai_codec_SeekableStream_s = ssWrapInputStream.invoke(null, in, Boolean.TRUE);
            ParameterBlock pb = new ParameterBlock();
            pb.add(com_sun_media_jai_codec_SeekableStream_s);
            Object javax_media_jai_RenderedOp_op = jaiCreate.invoke(null, "tiff", pb);

            /*
             * This was another approach:

             TIFFDecodeParam tiffDecodeParam = new TIFFDecodeParam();
             // tiffDecodeParam.setDecodePaletteAsShorts(true);

             ImageDecoder dec = ImageCodec.createImageDecoder("TIFF", s, tiffDecodeParam );

             NullOpImage op = new NullOpImage( dec.decodeAsRenderedImage(0), null, null, OpImage.OP_IO_BOUND );

             // RenderedImage img = dec.decodeAsRenderedImage();
             // RenderedImageAdapter ria = new RenderedImageAdapter(img);
             // BufferedImage bi = ria.getAsBufferedImage();

             */

            if (javax_media_jai_RenderedOp_op != null) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.fine("Decoding TIFF: " + TIFF_COMPRESSION_NAMES[compression]);
                }
                // This forces the image to decode, so we can see if that fails,
                //   and then potentially try a different compression setting
                /* op.getTile( 0, 0 ); */
                RenderedImage ri = (RenderedImage) javax_media_jai_RenderedOp_op;
                Raster r = ri.getTile(0, 0);

                // Calling op.getAsBufferedImage() causes a spike in memory usage
                // For example, for RenderedOp that's 100KB in size, we spike 18MB,
                //   with 1MB remaining and 17MB getting gc'ed
                // So, we try to build it piecemeal instead
                //System.out.println("Memory free: " + Runtime.getRuntime().freeMemory() + ", total:" + Runtime.getRuntime().totalMemory() + ", used: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
                if (r instanceof WritableRaster) {
                    ColorModel cm = ri.getColorModel();
                    img = new BufferedImage(cm, (WritableRaster) r, false, null);
                } else {
                    /* img = op.getAsBufferedImage(); */
                    img = (BufferedImage) roGetAsBufferedImage.invoke(javax_media_jai_RenderedOp_op);
                }
            }
        } catch (Exception e) {
            // catch and return a null image so we can try again using a different compression method.
            logger.finer("Decoding TIFF: " + TIFF_COMPRESSION_NAMES[compression] + " failed trying alternative");
        }
        // keep quiet
        return img;
    }

    private static BufferedImage applyImageMaskAndDecodeArray(
            BufferedImage img, boolean imageMask, Boolean blackIs1, float[] decode, Color fill) {
        // If the image we actually have is monochrome, and so is useful as an image mask
        ColorModel cm = img.getColorModel();
        if (cm instanceof IndexColorModel && cm.getPixelSize() == 1) {
            // From PDF 1.6 spec, concerning ImageMask and Decode array:
            // [0 1] (the default for an image mask), a sample value of 0 marks
            //       the page with the current color, and a 1 leaves the previous
            //       contents unchanged.
            // [1 0] Is the reverse
            // In case alpha transparency doesn't work, it'll paint white opaquely

            boolean defaultDecode =
                    (decode == null) ||
                            (0.0f == ((Number) decode[0]).floatValue());
            // From empirically testing 6 of the 9 possible combinations of
            //  BlackIs1 {true, false, not given} and Decode {[0 1], [1 0], not given}
            //  this is the rule. Unknown combinations:
            //    BlackIs1=false, Decode=[0 1]
            //    BlackIs1=false, Decode=[1 0]
            //    BlackIs1=true,  Decode=[0 1]
            boolean flag = ((blackIs1 == null) && (!defaultDecode)) ||
                    ((blackIs1 != null) && blackIs1 && (decode == null));
            if (imageMask) {
                int a = 0x00FFFFFF; // Clear if alpha supported, else white
                int[] cmap = new int[]{
                        (flag ? fill.getRGB() : a),
                        (flag ? a : fill.getRGB())
                };
                int transparentIndex = (flag ? 1 : 0);
                IndexColorModel icm = new IndexColorModel(
                        cm.getPixelSize(),      // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        true,                   // indicates whether alpha values are contained in the cmap array
                        transparentIndex,       // the index of the fully transparent pixel
                        cm.getTransferType());  // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(
                        icm, img.getRaster(), img.isAlphaPremultiplied(), null);
            } else {
                int[] cmap = new int[]{
                        (flag ? 0xFF000000 : 0xFFFFFFFF),
                        (flag ? 0xFFFFFFFF : 0xFF000000)
                };
                IndexColorModel icm = new IndexColorModel(
                        cm.getPixelSize(),      // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        false,                  // indicates whether alpha values are contained in the cmap array
                        -1,                     // the index of the fully transparent pixel
                        cm.getTransferType());  // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(
                        icm, img.getRaster(), img.isAlphaPremultiplied(), null);
            }
        }
        return img;
    }
}
