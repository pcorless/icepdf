/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageDecoder;
import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.ConservativeSizingByteArrayOutputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.filters.*;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.tag.Tagger;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.ImageCache;
import org.icepdf.core.util.Library;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Stream class is responsible for decoding stream contents and returning
 * either an images object or a byte array depending on the content.  The Stream
 * object worker method is decode which is responsible for decoding the content
 * stream, which is if the first step of the rendering process.  Once a Stream
 * is decoded it is either returned as an image object or a byte array that is
 * then processed further by the ContentParser.
 *
 * @since 1.0
 */
public class Stream extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Stream.class.toString());

    // original byte stream that has not been decoded
    private SeekableInputConstrainedWrapper streamInput;

    // Images object created from stream
    private ImageCache image = null;
    private final Object imageLock = new Object();

    private boolean isCCITTFaxDecodeWithoutEncodedByteAlign = false;
    private int CCITTFaxDecodeColumnWidthMismatch = 0;

    // reference of stream, needed for encryption support
    private Reference pObjectReference = null;

    // Inline image, from a content stream
    private boolean inlineImage;

    // minimum dimension which will enable image scaling
    private static boolean scaleImages;

    static {
        // decide if large images will be scaled
        scaleImages =
                Defs.sysPropertyBoolean("org.icepdf.core.scaleImages",
                        true);
    }

    private static final int[] GRAY_1_BIT_INDEX_TO_RGB_REVERSED = new int[]{
            0xFFFFFFFF,
            0xFF000000
    };

    private static final int[] GRAY_1_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFFFFFFFF
    };

    private static final int[] GRAY_2_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF555555,
            0xFFAAAAAA,
            0xFFFFFFFF
    }; // 0. 1 2 3 4 5. 6 7 8 9 A. B C D E F.     0/3, 1/3, 2/3, 3/3

    private static final int[] GRAY_4_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF111111,
            0xFF222222,
            0xFF333333,
            0xFF444444,
            0xFF555555,
            0xFF666666,
            0xFF777777,
            0xFF888888,
            0xFF999999,
            0xFFAAAAAA,
            0xFFBBBBBB,
            0xFFCCCCCC,
            0xFFDDDDDD,
            0xFFEEEEEE,
            0xFFFFFFFF
    };

    private static final int JPEG_ENC_UNKNOWN_PROBABLY_YCbCr = 0;
    private static final int JPEG_ENC_RGB = 1;
    private static final int JPEG_ENC_CMYK = 2;
    private static final int JPEG_ENC_YCbCr = 3;
    private static final int JPEG_ENC_YCCK = 4;
    private static final int JPEG_ENC_GRAY = 5;

    private static String[] JPEG_ENC_NAMES = new String[]{
            "JPEG_ENC_UNKNOWN_PROBABLY_YCbCr",
            "JPEG_ENC_RGB",
            "JPEG_ENC_CMYK",
            "JPEG_ENC_YCbCr",
            "JPEG_ENC_YCCK",
            "JPEG_ENC_GRAY"
    };

    /**
     * Create a new instance of a Stream.
     *
     * @param l                  library containing a hash of all document objects
     * @param h                  hashtable of parameters specific to the Stream object.
     * @param streamInputWrapper Accessor to stream byte data
     */
    public Stream(Library l, Hashtable h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h);
        streamInput = streamInputWrapper;
    }

    /**
     * Sets the PObject referece for this stream.  The reference number and
     * generation is need by the encryption algorithm.
     */
    public void setPObjectReference(Reference reference) {
        pObjectReference = reference;
    }

    /**
     * Gets the parent PObject reference for this stream.
     *
     * @return Reference number of parent PObject.
     * @see #setPObjectReference(org.icepdf.core.pobjects.Reference)
     */
    public Reference getPObjectReference() {
        return pObjectReference;
    }

    /**
     * Marks this stream as being constructed from an inline image
     * definition in a content stream
     */
    public void setInlineImage(boolean inlineImage) {
        this.inlineImage = inlineImage;
    }

    /**
     * @return Whether this stream was constructed from an inline image
     *         definition in a content stream
     */
    public boolean isInlineImage() {
        return inlineImage;
    }

    boolean isImageSubtype() {
        Object subtype = library.getObject(entries, "Subtype");
        return subtype != null && subtype.equals("Image");
    }

    /**
     * Utility Method to check if the <code>memoryNeeded</code> can be allowcated,
     * and if not, try and free up the needed amount of memory
     *
     * @param memoryNeeded
     */
    private boolean checkMemory(int memoryNeeded) {
        return library.memoryManager.checkMemory(memoryNeeded);
    }

    /**
     * Utility method for decoding the byte stream using the decode algorithem
     * specified by the filter parameter
     * <p/>
     * The memory manger is called every time a stream is being decoded with an
     * estimated size of the decoded stream.  Because many of the Filter
     * algorithms use compression,  further research must be done to try and
     * find the average amount of memory used by each of the algorithms.
     */
    public InputStream getInputStreamForDecodedStreamBytes() {
        // Make sure that the stream actually has data to decode, if it doesn't
        // make it null and return.
        if (streamInput == null || streamInput.getLength() < 1) {
            return null;
        }

        long streamLength = streamInput.getLength();
        int memoryNeeded = (int) streamLength;
        checkMemory(memoryNeeded);
        streamInput.prepareForCurrentUse();
        InputStream input = streamInput;

        int bufferSize = Math.min(Math.max((int) streamLength, 64), 16 * 1024);
        input = new java.io.BufferedInputStream(input, bufferSize);

        if (library.securityManager != null) {
            input = library.getSecurityManager().getEncryptionInputStream(
                    getPObjectReference(), library.getSecurityManager().getDecryptionKey(), input, true);
        }

        // Get the filter name for the encoding type, which can be either
        // a Name or Vector.
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return input;

        // Decode the stream data based on the filter names.
        // Loop through the filterNames and apply the filters in the order
        // in which they where found.
        for (int i = 0; i < filterNames.size(); i++) {
            // grab the name of the filter
            String filterName = filterNames.elementAt(i).toString();
            //System.out.println("  Decoding: " + filterName);

            if (filterName.equals("FlateDecode")
                    || filterName.equals("/Fl")
                    || filterName.equals("Fl")) {
                input = new FlateDecode(library, entries, input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("LZWDecode")
                            || filterName.equals("/LZW")
                            || filterName.equals("LZW")) {
                input = new LZWDecode(new BitStream(input), library, entries);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("ASCII85Decode")
                            || filterName.equals("/A85")
                            || filterName.equals("A85")) {
                input = new ASCII85Decode(input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("ASCIIHexDecode")
                            || filterName.equals("/AHx")
                            || filterName.equals("AHx")) {
                input = new ASCIIHexDecode(input);
                memoryNeeded /= 2;
            } else if (
                    filterName.equals("RunLengthDecode")
                            || filterName.equals("/RL")
                            || filterName.equals("RL")) {
                input = new RunLengthDecode(input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("CCITTFaxDecode")
                            || filterName.equals("/CCF")
                            || filterName.equals("CCF")) {
                // Leave empty so our else clause works
            } else if (
                    filterName.equals("DCTDecode")
                            || filterName.equals("/DCT")
                            || filterName.equals("DCT")) {
                // Leave empty so our else clause works
            } else if ( // No short name, since no JBIG2 for inline images
                    filterName.equals("JBIG2Decode")) {
                // Leave empty so our else clause works
            } else if ( // No short name, since no JPX for inline images
                    filterName.equals("JPXDecode")) {
                // Leave empty so our else clause works
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("UNSUPPORTED:" + filterName + " " + entries);
                }
            }
        }

        checkMemory(memoryNeeded);

        return input;
    }

    private byte[] getDecodedStreamBytes() {
        InputStream input = getInputStreamForDecodedStreamBytes();
        if (input == null)
            return null;
        try {
            int outLength = Math.max(1024, (int) streamInput.getLength());
            ConservativeSizingByteArrayOutputStream out = new
                    ConservativeSizingByteArrayOutputStream(outLength, library.memoryManager);
            byte[] buffer = new byte[(outLength > 1024) ? 4096 : 1024];
            while (true) {
                int read = input.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            // removes this thread from current read,  pottential entry for other thread
            input.close();

            byte[] ret = out.toByteArray();
            return ret;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
        }

        return null;
    }

    /**
     * This is similar to getDecodedStreamBytes(), except that the returned byte[]
     * is not necessarily exactly sized, and may be larger. Therefore the returned
     * Integer gives the actual valid size
     *
     * @return Object[] { byte[] data, Integer sizeActualData }
     */
    private Object[] getDecodedStreamBytesAndSize(int presize) {
        InputStream input = getInputStreamForDecodedStreamBytes();
        if (input == null)
            return null;
        try {
            int outLength;
            if (presize > 0)
                outLength = presize;
            else
                outLength = Math.max(1024, (int) streamInput.getLength());
            ConservativeSizingByteArrayOutputStream out = new
                    ConservativeSizingByteArrayOutputStream(outLength, library.memoryManager);
            byte[] buffer = new byte[(outLength > 1024) ? 4096 : 1024];
            while (true) {
                int read = input.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            input.close();

            int size = out.size();
            boolean trimmed = out.trim();
            byte[] data = out.relinquishByteArray();
            Object[] ret = new Object[]{data, size};
            return ret;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
        }

        return null;
    }

    private void copyDecodedStreamBytesIntoRGB(int[] pixels) {
        byte[] rgb = new byte[3];
        try {
            InputStream input = getInputStreamForDecodedStreamBytes();
            for (int pixelIndex = 0; pixelIndex < pixels.length; pixelIndex++) {
                int argb = 0xFF000000;
                if (input != null) {
                    final int toRead = 3;
                    int haveRead = 0;
                    while (haveRead < toRead) {
                        int currRead = input.read(rgb, haveRead, toRead - haveRead);
                        if (currRead < 0)
                            break;
                        haveRead += currRead;
                    }
                    if (haveRead >= 1)
                        argb |= ((((int) rgb[0]) << 16) & 0x00FF0000);
                    if (haveRead >= 2)
                        argb |= ((((int) rgb[1]) << 8) & 0x0000FF00);
                    if (haveRead >= 3)
                        argb |= (((int) rgb[2]) & 0x000000FF);
                }
                pixels[pixelIndex] = argb;
            }
            if (input != null)
                input.close();
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem copying decoding stream bytes: ", e);
        }
    }

    private boolean shouldUseCCITTFaxDecode() {
        return containsFilter(new String[]{"CCITTFaxDecode", "/CCF", "CCF"});
    }

    private boolean shouldUseDCTDecode() {
        return containsFilter(new String[]{"DCTDecode", "/DCT", "DCT"});
    }

    private boolean shouldUseJBIG2Decode() {
        return containsFilter(new String[]{"JBIG2Decode"});
    }

    private boolean shouldUseJPXDecode() {
        return containsFilter(new String[]{"JPXDecode"});
    }

    private boolean containsFilter(String[] searchFilterNames) {
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return false;
        for (int i = 0; i < filterNames.size(); i++) {
            String filterName = filterNames.elementAt(i).toString();
            for (String search : searchFilterNames) {
                if (search.equals(filterName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vector getFilterNames() {
        Vector filterNames = null;
        Object o = library.getObject(entries, "Filter");
        if (o instanceof Name) {
            filterNames = new Vector(1);
            filterNames.addElement(o);
        } else if (o instanceof Vector) {
            filterNames = (Vector) o;
        }
        return filterNames;
    }

    private Vector getNormalisedFilterNames() {
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return null;

        for (int i = 0; i < filterNames.size(); i++) {
            String filterName = filterNames.elementAt(i).toString();

            if (filterName.equals("FlateDecode")
                    || filterName.equals("/Fl")
                    || filterName.equals("Fl")) {
                filterName = "FlateDecode";
            } else if (
                    filterName.equals("LZWDecode")
                            || filterName.equals("/LZW")
                            || filterName.equals("LZW")) {
                filterName = "LZWDecode";
            } else if (
                    filterName.equals("ASCII85Decode")
                            || filterName.equals("/A85")
                            || filterName.equals("A85")) {
                filterName = "ASCII85Decode";
            } else if (
                    filterName.equals("ASCIIHexDecode")
                            || filterName.equals("/AHx")
                            || filterName.equals("AHx")) {
                filterName = "ASCIIHexDecode";
            } else if (
                    filterName.equals("RunLengthDecode")
                            || filterName.equals("/RL")
                            || filterName.equals("RL")) {
                filterName = "RunLengthDecode";
            } else if (
                    filterName.equals("CCITTFaxDecode")
                            || filterName.equals("/CCF")
                            || filterName.equals("CCF")) {
                filterName = "CCITTFaxDecode";
            } else if (
                    filterName.equals("DCTDecode")
                            || filterName.equals("/DCT")
                            || filterName.equals("DCT")) {
                filterName = "DCTDecode";
            }
            // There aren't short names for JBIG2Decode or JPXDecode
            filterNames.set(i, filterName);
        }
        return filterNames;
    }

    private byte[] decodeCCITTFaxDecodeOrDCTDecodeOrJBIG2DecodeOrJPXDecodeImage(
            int width, int height, PColorSpace colourSpace, int bitspercomponent, Color fill,
            BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        byte[] data = null;

        if (shouldUseCCITTFaxDecode()) {
            if (Tagger.tagging)
                Tagger.tagImage("CCITTFaxDecode");
            // InputStream getInputStreamForStreamBytes();
            boolean worked = nonDecodeCCITTMakeImage(fill);
            if (!worked) {
                data = ccittfaxDecode(getInputStreamForDecodedStreamBytes());
            }
        } else if (shouldUseDCTDecode()) {
            if (Tagger.tagging)
                Tagger.tagImage("DCTDecode");
            dctDecode(width, height, colourSpace, bitspercomponent,
                    smaskImage, maskImage, maskMinRGB, maskMaxRGB);
        } else if (shouldUseJBIG2Decode()) {
            if (Tagger.tagging)
                Tagger.tagImage("JBIG2Decode");
            jbig2Decode(width, height);
        } else if (shouldUseJPXDecode()) {
            if (Tagger.tagging)
                Tagger.tagImage("JPXDecode");
            jpxDecode(width, height, colourSpace, bitspercomponent, fill,
                smaskImage, maskImage, maskMinRGB, maskMaxRGB);
        }


        /*
         * Since we now have the code in place for regetting images
         * from the original bytes, I don't think we should throw
         * away the bytes in favour of the image anymore
         
        // clear this byte cache if we have our image
        if( image != null ) {
            try {
                streamInput.dispose();
            }
            catch(IOException e) {
                if( Debug.ex )
                    Debug.ex( e );
            }
        }
        */

        return data;
    }

    /**
     * Despose of references to images and decoded byte streams.
     * Memory optimization
     */
    public void dispose(boolean cache) {
        if (streamInput != null) {
            if (!cache) {
                try {
                    streamInput.dispose();
                }
                catch (IOException e) {
                    logger.log(Level.FINE, "Error disposing stream.", e);
                }
                streamInput = null;
            }
        }
        synchronized (imageLock) {
            if (image != null) {
                image.dispose(cache, (streamInput != null));
                if (!cache || !image.isCachedSomehow()) {
                    image = null;
                    isCCITTFaxDecodeWithoutEncodedByteAlign = false;
                    CCITTFaxDecodeColumnWidthMismatch = 0;
                }
            }
        }
    }

    /**
     * The DCTDecode filter decodes grayscale or color image data that has been
     * encoded in the JPEG baseline format.  Because DCTDecode only deals
     * with images, the instance of image is update instead of decoded
     * stream.
     */
    private void dctDecode(
            int width, int height, PColorSpace colourSpace, int bitspercomponent,
            BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        // BIS's buffer size should be equal to mark() size, and greater than data size (below)
        InputStream input = getInputStreamForDecodedStreamBytes();
        // Used to just read 1000, but found a PDF that included thumbnails first
        final int MAX_BYTES_TO_READ_FOR_ENCODING = 2048;
        BufferedInputStream bufferedInput = new BufferedInputStream(
                input, MAX_BYTES_TO_READ_FOR_ENCODING);
        bufferedInput.mark(MAX_BYTES_TO_READ_FOR_ENCODING);

        // We don't use the PColorSpace to determine how to decode the JPEG, because it tends to be wrong
        // Some files say DeviceCMYK, or ICCBased, when neither would work, because it's really YCbCrA
        // What does work though, is to look into the JPEG headers themself, via getJPEGEncoding()

        int jpegEncoding = Stream.JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;
        try {
            byte[] data = new byte[MAX_BYTES_TO_READ_FOR_ENCODING];
            int dataRead = bufferedInput.read(data);
            bufferedInput.reset();
            if (dataRead > 0)
                jpegEncoding = getJPEGEncoding(data, dataRead);
        }
        catch (IOException ioe) {
            logger.log(Level.FINE, "Problem determining JPEG type: ", ioe);
        }
        if (Tagger.tagging)
            Tagger.tagImage("DCTDecode_JpegEncoding=" + JPEG_ENC_NAMES[jpegEncoding]);
        //System.out.println("Stream.dctDecode()  objectNumber: " + getPObjectReference().getObjectNumber());
        //System.out.println("Stream.dctDecode()  jpegEncoding: " + JPEG_ENC_NAMES[jpegEncoding]);

        //System.out.println("Stream.dctDecode()  smask: " + smaskImage);
        //System.out.println("Stream.dctDecode()  mask: " + maskImage);
        //System.out.println("Stream.dctDecode()  maskMinRGB: " + maskMinRGB);
        //System.out.println("Stream.dctDecode()  maskMaxRGB: " + maskMaxRGB);

        //long beginUsedMem = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        //long beginTime = System.currentTimeMillis();
        BufferedImage tmpImage = null;

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  JPEGImageDecoder");
                JPEGImageDecoder imageDecoder = JPEGCodec.createJPEGDecoder(bufferedInput);

                int bizarreFudge = 64 * 1024 + (int) streamInput.getLength();
                checkMemory(width * height * 8 + bizarreFudge);

                if (jpegEncoding == JPEG_ENC_RGB && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_RGB");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    alterRasterRGB2PColorSpace(wr, colourSpace);
                    tmpImage = makeRGBBufferedImage(wr);
                } else if (jpegEncoding == JPEG_ENC_CMYK && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_CMYK");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    alterRasterCMYK2BGRA(wr, smaskImage, maskImage); //TODO Use maskMinRGB, maskMaxRGB or orig comp version here
                    tmpImage = makeRGBABufferedImage(wr);
                } else if (jpegEncoding == JPEG_ENC_YCbCr && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_YCbCr");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    alterRasterYCbCr2RGB(wr);
                    tmpImage = makeRGBBufferedImage(wr);
                } else if (jpegEncoding == JPEG_ENC_YCCK && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_YCCK");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    alterRasterYCCK2BGRA(wr, smaskImage, maskImage); //TODO Use maskMinRGB, maskMaxRGB or orig comp version here
                    tmpImage = makeRGBABufferedImage(wr);
                } else if (jpegEncoding == JPEG_ENC_GRAY && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_GRAY");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    // In DCTDecode with ColorSpace=DeviceGray, the samples are gray values (2000_SID_Service_Info.core)
                    // In DCTDecode with ColorSpace=Separation, the samples are Y values (45-14550BGermanForWeb.core AKA 4570.core)
                    // Instead of assuming that Separation is special, I'll assume that DeviceGray is
                    if (!(colourSpace instanceof DeviceGray)) {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=Y");
                        alterRasterY2Gray(wr); //TODO Use smaskImage, maskImage, maskMinRGB, maskMaxRGB or orig comp version here
                    }
                    tmpImage = makeGrayBufferedImage(wr);
                } else {
                    //System.out.println("Stream.dctDecode()    Other");
                    //tmpImage = imageDecoder.decodeAsBufferedImage();
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    if (imageDecoder.getJPEGDecodeParam().getEncodedColorID() ==
                            com.sun.image.codec.jpeg.JPEGDecodeParam.COLOR_ID_YCbCrA) {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=YCbCrA");
                        // YCbCrA, which is slightly different than YCCK
                        alterRasterYCbCrA2RGBA_new(wr, smaskImage, maskImage); //TODO Use maskMinRGB, maskMaxRGB or orig comp version here
                        tmpImage = makeRGBABufferedImage(wr);
                    } else {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=YCbCr");
                        alterRasterYCbCr2RGB(wr);
                        tmpImage = makeRGBBufferedImage(wr);
                    }
                    //alterRasterBGRA( wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB );
                    //tmpImage = makeRGBABufferedImage( wr );
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via JPEGImageDecoder: ", e);
            }
            if (tmpImage != null) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=DCTDecode_SunJPEGImageDecoder");
            }
        }

        try {
            bufferedInput.close();
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error closing image stream.", e);
        }

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  JAI");
                Object javax_media_jai_RenderedOp_op = null;
                try {
                    // Have to reget the data
                    input = getInputStreamForDecodedStreamBytes();

                    /*
                    com.sun.media.jai.codec.SeekableStream s = com.sun.media.jai.codec.SeekableStream.wrapInputStream( new ByteArrayInputStream(data), true );
                    ParameterBlock pb = new ParameterBlock();
                    pb.add( s );
                    javax.media.jai.RenderedOp op = javax.media.jai.JAI.create( "jpeg", pb );
                    */
                    Class ssClass = Class.forName("com.sun.media.jai.codec.SeekableStream");
                    Method ssWrapInputStream = ssClass.getMethod("wrapInputStream", InputStream.class, Boolean.TYPE);
                    Object com_sun_media_jai_codec_SeekableStream_s =
                            ssWrapInputStream.invoke(null, input, Boolean.TRUE);
                    ParameterBlock pb = new ParameterBlock();
                    pb.add(com_sun_media_jai_codec_SeekableStream_s);
                    Class jaiClass = Class.forName("javax.media.jai.JAI");
                    Method jaiCreate = jaiClass.getMethod("create", String.class, ParameterBlock.class);
                    javax_media_jai_RenderedOp_op = jaiCreate.invoke(null, "jpeg", pb);
                }
                catch (Exception e) {
                }

                if (javax_media_jai_RenderedOp_op != null) {
                    if (jpegEncoding == JPEG_ENC_CMYK && bitspercomponent == 8) {
                        /*
                         * With or without alterRasterCMYK2BGRA(), give blank image
                        Raster r = op.copyData();
                        WritableRaster wr = (r instanceof WritableRaster)
                                ? (WritableRaster) r : r.createCompatibleWritableRaster();
                        alterRasterCMYK2BGRA( wr );
                        tmpImage = makeRGBABufferedImage( wr );
                        */
                        /*
                         * With alterRasterCMYK2BGRA() colors gibbled, without is blank
                         * Slower, uses more memory, than JPEGImageDecoder
                        BufferedImage img = op.getAsBufferedImage();
                        WritableRaster wr = img.getRaster();
                        alterRasterCMYK2BGRA( wr );
                        tmpImage = img;
                        */
                    } else if (jpegEncoding == JPEG_ENC_YCCK && bitspercomponent == 8) {
                        /*
                         * This way, with or without alterRasterYCbCrA2BGRA(), give blank image
                        Raster r = op.getData();
                        WritableRaster wr = (r instanceof WritableRaster)
                                ? (WritableRaster) r : r.createCompatibleWritableRaster();
                        alterRasterYCbCrA2BGRA( wr );
                        tmpImage = makeRGBABufferedImage( wr );
                        */
                        /*
                         * With alterRasterYCbCrA2BGRA() colors gibbled, without is blank
                         * Slower, uses more memory, than JPEGImageDecoder
                        BufferedImage img = op.getAsBufferedImage();
                        WritableRaster wr = img.getRaster();
                        alterRasterYCbCrA2BGRA( wr );
                        tmpImage = img;
                        */
                    } else {
                        //System.out.println("Stream.dctDecode()    Other");
                        /* tmpImage = op.getAsBufferedImage(); */
                        Class roClass = Class.forName("javax.media.jai.RenderedOp");
                        Method roGetAsBufferedImage = roClass.getMethod("getAsBufferedImage");
                        tmpImage = (BufferedImage) roGetAsBufferedImage.invoke(javax_media_jai_RenderedOp_op);
                        if (tmpImage != null) {
                            if (Tagger.tagging)
                                Tagger.tagImage("HandledBy=DCTDecode_JAI");
                        }
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via JAI: ", e);
            }

            try {
                input.close();
            }
            catch (IOException e) {
                logger.log(Level.FINE, "Problem closing image stream. ", e);
            }
        }

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  Toolkit");
                byte[] data = getDecodedStreamBytes();
                if (data != null) {
                    Image img = Toolkit.getDefaultToolkit().createImage(data);
                    if (img != null) {
                        tmpImage = makeRGBABufferedImageFromImage(img);
                        if (Tagger.tagging)
                            Tagger.tagImage("HandledBy=DCTDecode_ToolkitCreateImage");
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via Toolkit: ", e);
            }
        }

        //long endUsedMem = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        //long endTime = System.currentTimeMillis();
        //System.out.println("Mem used: " + (endUsedMem-beginUsedMem) + ",\ttime: " + (endTime-beginTime));

        // write tmpImage to the cache
        synchronized (imageLock) {
            if (image == null) {
                image = new ImageCache(library);
            }
            image.setImage(tmpImage);
        }
    }

    /**
     * Utility method to decode JBig2 images.
     *
     * @param width  width of image
     * @param height height of image
     */
    private void jbig2Decode(int width, int height) {
        BufferedImage tmpImage = null;

        try {
            checkMemory(108 * 1024);
            org.jpedal.jbig2.JBIG2Decoder decoder = new org.jpedal.jbig2.JBIG2Decoder();

            Hashtable decodeparms = library.getDictionary(entries, "DecodeParms");
            if (decodeparms != null) {
                Stream globalsStream = (Stream) library.getObject(decodeparms, "JBIG2Globals");
                byte[] globals = globalsStream.getDecodedStreamBytes();
                if (globals != null && globals.length > 0) {
                    decoder.setGlobalData(globals);
                    globals = null;
                }
            }

            byte[] data = getDecodedStreamBytes();
            checkMemory((width + 8) * height * 22 / 10); // Between 0.5 and 2.2
            decoder.decodeJBIG2(data);
            data = null;
            // From decoding, memory usage inceases more than (width*height/8),
            // due to intermediate JBIG2Bitmap objects, used to build the final
            // one, still hanging around. Cleanup intermediate data-structures.  
            decoder.cleanupPostDecode();
            checkMemory((width + 8) * height / 8);
            tmpImage = decoder.getPageAsBufferedImage(0);
            decoder = null;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JBIG2 image: ", e);
        }
        catch (org.jpedal.jbig2.JBIG2Exception e) {
            logger.log(Level.FINE, "Problem loading JBIG2 image: ", e);
        }

        if (tmpImage != null) {
            // write tmpImage to the cache
            synchronized (imageLock) {
                if (image == null) {
                    image = new ImageCache(library);
                }
                image.setImage(tmpImage);
            }
        }
    }

    /**
     * Creates a new instance of a Dictionary.
     *
     * @param library document library.
     * @param entries dictionary entries.
     */
    public Stream(Library library, Hashtable entries) {
        super(library, entries);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Utility method to decode JPEG2000 images.
     */
    private void jpxDecode(int width, int height, PColorSpace colourSpace,
                           int bitspercomponent, Color fill,
                           BufferedImage smaskImage, BufferedImage maskImage,
                           int[] maskMinRGB, int[] maskMaxRGB ) {

        BufferedImage tmpImage = null;
        try {
            // Verify that ImageIO can read JPEG2000
            Iterator<ImageReader> iterator = ImageIO.getImageReadersByFormatName("JPEG2000");
            if (!iterator.hasNext()) {
                logger.info(
                        "ImageIO missing required plug-in to read JPEG 2000 images. " +
                                "You can download the JAI ImageIO Tools from: " +
                                "https://jai-imageio.dev.java.net/");
                return;
            }

            // decode the image.
            byte[] data = getDecodedStreamBytes();
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(data));
            tmpImage = ImageIO.read(imageInputStream);
            // check for a mask value
            if (maskImage != null){
                applyExplicitMask(tmpImage, maskImage);
            }

        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JPEG2000 image: ", e);
        }

        if (tmpImage != null) {
            // write tmpImage to the cache
            synchronized (imageLock) {
                if (image == null) {
                    image = new ImageCache(library);
                }
                image.setImage(tmpImage);
            }
        }
    }

    private static void alterRasterCMYK2BGRA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int[] values = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);
                int cValue = values[0];
                int mValue = values[1];
                int yValue = values[2];
                int kValue = values[3];

                int maxOrig = Math.max(cValue, Math.max(mValue, yValue));
                kValue = ((255 - maxOrig) * kValue) / 255;
                cValue += kValue;
                mValue += kValue;
                yValue += kValue;
                cValue = Math.max(0, Math.min(255, cValue));
                mValue = Math.max(0, Math.min(255, mValue));
                yValue = Math.max(0, Math.min(255, yValue));
                //cValue = (int) ( 255 * Math.pow(cValue/255.0, 0.65) );
                //mValue = (int) ( 255 * Math.pow(mValue/255.0, 0.65) );
                //yValue = (int) ( 255 * Math.pow(yValue/255.0, 0.65) );
                int rValue = 255 - cValue;
                int gValue = 255 - mValue;
                int bValue = 255 - yValue;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null)
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }
                values[0] = bValue;
                values[1] = gValue;
                values[2] = rValue;
                values[3] = alpha;
                wr.setPixel(x, y, values);
            }
        }
    }

    private static void alterRasterYCbCr2RGB(WritableRaster wr) {
        int[] values = new int[3];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);

                int Y = values[0];
                int Cb = values[1];
                int Cr = values[2];

                int Cr_128 = Cr - 128;
                int Cb_128 = Cb - 128;

                int rVal = Y + (1370705 * Cr_128 / 1000000);
                int gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                int bVal = Y + (1732446 * Cb_128 / 1000000);

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;

                values[0] = rByte;
                values[1] = gByte;
                values[2] = bByte;

                wr.setPixel(x, y, values);
            }
        }
    }

    private static void alterRasterYCCK2BGRA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int[] origValues = new int[4];
        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, origValues);

                int Y = origValues[0];
                int Cb = origValues[1];
                int Cr = origValues[2];
                int K = origValues[3];

                Y -= K;
                int Cr_128 = Cr - 128;
                int Cb_128 = Cb - 128;

                int rVal = Y + (1370705 * Cr_128 / 1000000);
                int gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                int bVal = Y + (1732446 * Cb_128 / 1000000);

                /*
                // Formula used in JPEG standard. Gives pretty similar results
                //int rVal = Y + (1402000 * Cr_128/ 1000000);
                //int gVal = Y - (344140 * Cb_128 / 1000000) - (714140 * Cr_128 / 1000000);
                //int bVal = Y + (1772000 * Cb_128 / 1000000);
                */

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null)
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }

                rgbaValues[0] = bByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = rByte;
                rgbaValues[3] = alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    private static void alterRasterYCbCrA2RGBA_new(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int[] origValues = new int[4];
        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, origValues);

                int Y = origValues[0];
                int Cb = origValues[1];
                int Cr = origValues[2];
                int K = origValues[3];
                Y = K - Y;
                int Cr_128 = Cr - 128;
                int Cb_128 = Cb - 128;

                int rVal = Y + (1370705 * Cr_128 / 1000000);
                int gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                int bVal = Y + (1732446 * Cb_128 / 1000000);

                /*
                // Formula used in JPEG standard. Gives pretty similar results
                //int rVal = Y + (1402000 * Cr_128/ 1000000);
                //int gVal = Y - (344140 * Cb_128 / 1000000) - (714140 * Cr_128 / 1000000);
                //int bVal = Y + (1772000 * Cb_128 / 1000000);
                */

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                int alpha = K;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null)
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }

                rgbaValues[0] = rByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = bByte;
                rgbaValues[3] = alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    /**
     * Explicit Masking algorithm, as of PDF 1.3.  The entry in an image dictionary
     * may be an image mask, as described under "Stencil Masking", which serves as
     * an explicit mask fo rthe primary or base image.  The base image and the
     * image mask need not have the same resolution (width, height), but since
     * all images are defined on the unit square in user space, their boundaries on the
     * page will conincide; that is, they will overlay each other.
     * <p/>
     * The image mask indicates indicates which places on the page are to be painted
     * and which are to be masked out (left unchanged).  Unmasked areas are painted
     * with the corresponding portions of the base image; masked areas are not.
     *
     * @param baseImage
     * @param maskImage
     */
    private static void applyExplicitMask(BufferedImage baseImage, BufferedImage maskImage) {
        // check to see if we need to scale the mask to match the size of the
        // base image.
        int baseWidth =  baseImage.getWidth();
        int baseHeight = baseImage.getHeight();
        int maskWidth = maskImage.getWidth();
        int maskHeight = maskImage.getHeight();

        if (baseWidth != maskWidth || baseHeight != maskHeight){
            // calculate scale factors.
            double scaleX = baseWidth / (double)maskWidth;
            double scaleY = baseHeight / (double)maskHeight;
            // scale the mask to match the base image.
            AffineTransform tx = new AffineTransform();
            tx.scale(scaleX, scaleY);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage sbim = op.filter(maskImage, null);
            maskImage.flush();
            maskImage = sbim;
        }
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour. 
        for (int y = 0; y < baseHeight; y++) {
            for (int x = 0; x < baseWidth; x++) {
                int maskPixel = maskImage.getRGB(x, y);
                if (maskPixel == -1){
                    baseImage.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

//        int width = maskImage.getWidth();
//        int height = maskImage.getHeight();
//        final BufferedImage bi = new BufferedImage(width,height, BufferedImage.TYPE_INT_RGB);
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                if (maskImage.getRGB(x,y) != -1){
//                    bi.setRGB(x, y, Color.red.getRGB());
//                }
//                else{
//                    bi.setRGB(x, y, Color.green.getRGB());
//                }
//            }
//        }
//        final JFrame f = new JFrame("Test");
//        f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//
//        JComponent image = new JComponent() {
//            @Override
//            public void paint(Graphics g_) {
//                super.paint(g_);
//                g_.drawImage(bi, 0, 0, f);
//            }
//        };
//        image.setPreferredSize(new Dimension(bi.getWidth(), bi.getHeight()));
//        image.setSize(new Dimension(bi.getWidth(), bi.getHeight()));
//
//        JPanel test  =new JPanel();
//        test.setPreferredSize(new Dimension(1200,1200));
//        JScrollPane tmp = new JScrollPane(image);
//        tmp.revalidate();
//        f.setSize(new Dimension(800, 800));
//        f.getContentPane().add(tmp);
//        f.validate();
//        f.setVisible(true);
        
    }

    private static void alterBufferedImage(BufferedImage bi, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null && (maskMinRGB == null || maskMaxRGB == null))
            return;

        int width = bi.getWidth();
        int height = bi.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean gotARBG = false;
                int argb = 0;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    // Alpha equals greyscale value of smask
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                } else {
                    gotARBG = true;
                    argb = bi.getRGB(x, y);
                    int red = ((argb >> 16) & 0xFF);
                    int green = ((argb >> 8) & 0xFF);
                    int blue = (argb & 0xFF);
                    if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                            green >= maskMinGreen && green <= maskMaxGreen &&
                            red >= maskMinRed && red <= maskMaxRed) {
                        alpha = 0x00;
                    }
                }
                if (alpha != 0xFF) {
                    if (!gotARBG)
                        argb = bi.getRGB(x, y);
                    argb &= 0x00FFFFFF;
                    argb |= ((alpha << 24) & 0xFF000000);
                    bi.setRGB(x, y, argb);
                }
            }
        }
    }

    private static void alterRasterRGBA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null && (maskMinRGB == null || maskMaxRGB == null))
            return;

        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, rgbaValues);
                int red = rgbaValues[0];
                int green = rgbaValues[1];
                int blue = rgbaValues[2];

                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    // Alpha equals greyscale value of smask
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                } else if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                        green >= maskMinGreen && green <= maskMaxGreen &&
                        red >= maskMinRed && red <= maskMaxRed) {
                    alpha = 0x00;
                }
                if (alpha != 0xFF) {
                    rgbaValues[3] = alpha;
                    wr.setPixel(x, y, rgbaValues);
                }
            }
        }
    }

    private static void alterRasterRGB2PColorSpace(WritableRaster wr, PColorSpace colorSpace) {
//System.out.println("alterRasterRGB2PColorSpace() colorSpace: " + colorSpace);
        if (colorSpace instanceof DeviceRGB)
            return;
        float[] values = new float[3];
        int[] rgbValues = new int[3];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, rgbValues);

                PColorSpace.reverseInPlace(rgbValues);
                colorSpace.normaliseComponentsToFloats(rgbValues, values, 255.0f);
                Color c = colorSpace.getColor(values);
                rgbValues[0] = c.getRed();
                rgbValues[1] = c.getGreen();
                rgbValues[2] = c.getBlue();

                wr.setPixel(x, y, rgbValues);
            }
        }
    }

    private static void alterRasterY2Gray(WritableRaster wr) {
        int[] values = new int[1];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);

                int Y = values[0];

                byte yByte = (Y < 0) ? (byte) 0 : (Y > 255) ? (byte) 0xFF : (byte) Y;

                values[0] = 255 - (int) yByte;

                wr.setPixel(x, y, values);
            }
        }
    }

    private static int getJPEGEncoding(byte[] data, int dataLength) {
        int jpegEncoding = JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;

        boolean foundAPP14 = false;
        byte compsTypeFromAPP14 = 0;
        boolean foundSOF = false;
        int numCompsFromSOF = 0;
        boolean foundSOS = false;
        int numCompsFromSOS = 0;

        int index = 0;
        while (true) {
            if (index >= dataLength)
                break;
            if (data[index] != ((byte) 0xFF))
                break;
            if (foundAPP14 && foundSOF)
                break;
            byte segmentType = data[index + 1];
            index += 2;
            if (segmentType == ((byte) 0xD8)) {
                //System.out.println("Found SOI (0xD8)");
                continue;
            }

            //System.out.println("Segment: " + Integer.toHexString( ((int)segmentType)&0xFF ));
            int length = (((data[index] << 8)) & 0xFF00) + (((int) data[index + 1]) & 0xFF);
            //System.out.println("   Length: " + length + "    Index: " + index);

            // APP14 (Might be Adobe file)
            if (segmentType == ((byte) 0xEE)) {
                //System.out.println("Found APP14 (0xEE)");
                if (length >= 14) {
                    foundAPP14 = true;
                    compsTypeFromAPP14 = data[index + 13];
                    //System.out.println("APP14 format: " + compsTypeFromAPP14);
                }
            } else if (segmentType == ((byte) 0xC0)) {
                foundSOF = true;
                //System.out.println("Found SOF (0xC0)  Start Of Frame");
                //int bitsPerSample = ( ((int)data[index+2]) & 0xFF );
                //int imageHeight = ( ((int)(data[index+3] << 8)) & 0xFF00 ) + ( ((int)data[index+4]) & 0xFF );
                //int imageWidth = ( ((int)(data[index+5] << 8)) & 0xFF00 ) + ( ((int)data[index+6]) & 0xFF );
                numCompsFromSOF = (((int) data[index + 7]) & 0xFF);
                //System.out.println("   bitsPerSample: " + bitsPerSample + ", imageWidth: " + imageWidth + ", imageHeight: " + imageHeight + ", numComps: " + numCompsFromSOF);
                //int[] compIds = new int[numCompsFromSOF];
                //for(int i = 0; i < numCompsFromSOF; i++) {
                //    compIds[i] = ( ((int)data[index+8+(i*3)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            } else if (segmentType == ((byte) 0xDA)) {
                foundSOS = true;
                //System.out.println("Found SOS (0xDA)  Start Of Scan");
                numCompsFromSOS = (((int) data[index + 2]) & 0xFF);
                //int[] compIds = new int[numCompsFromSOS];
                //for(int i = 0; i < numCompsFromSOS; i++) {
                //    compIds[i] = ( ((int)data[index+3+(i*2)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            }

            //System.out.println("   Data: " + org.icepdf.core.util.Utils.convertByteArrayToHexString( data, index+2, Math.min(length-2,dataLength-index-2), true, 20, '\n' ));
            index += length;
        }

        if (foundAPP14 && foundSOF) {
            if (compsTypeFromAPP14 == 0) {       // 0 seems to indicate no conversion
                if (numCompsFromSOF == 1)
                    jpegEncoding = JPEG_ENC_GRAY;
                if (numCompsFromSOF == 3)        // Most assume RGB. DesignJava_times_roman_substitution.PDF supports this.
                    jpegEncoding = JPEG_ENC_RGB;
                else if (numCompsFromSOF == 4)   // CMYK
                    jpegEncoding = JPEG_ENC_CMYK;
            } else if (compsTypeFromAPP14 == 1) {  // YCbCr
                jpegEncoding = JPEG_ENC_YCbCr;
            } else if (compsTypeFromAPP14 == 2) {  // YCCK
                jpegEncoding = JPEG_ENC_YCCK;
            }
        } else if (foundSOS) {
            if (numCompsFromSOS == 1)
                jpegEncoding = JPEG_ENC_GRAY; // Y
        }
        return jpegEncoding;
    }

    private static BufferedImage makeRGBABufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[4];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, true, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    private static BufferedImage makeRGBBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[3];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    private static BufferedImage makeGrayBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        int[] bits = new int[1];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    // This method returns a buffered image with the contents of an image from
    // java almanac
    private BufferedImage makeRGBABufferedImageFromImage(Image image) {

        if (image instanceof BufferedImage) {
            return (BufferedImage)image;
        }
        // This code ensures that all the pixels in the image are loaded
        image = new ImageIcon(image).getImage();
        // Determine if the image has transparent pixels; for this method's
        // implementation, see Determining If an Image Has Transparent Pixels
        boolean hasAlpha = hasAlpha(image);
        // Create a buffered image with a format that's compatible with the screen
        BufferedImage bImage = null;
        try {
            // graphics environment calls can through headless exceptions so
            // proceed with caution. 
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gs.getDefaultConfiguration();
            // Determine the type of transparency of the new buffered image
            int transparency = Transparency.OPAQUE;
            if (hasAlpha) {
                transparency = Transparency.BITMASK;
            }
            // Create the buffered image
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1 ){
                return null;
            }
            bImage = gc.createCompatibleImage(
                image.getWidth(null), image.getHeight(null), transparency);
        } catch (HeadlessException e) {
            // The system does not have a screen
        }
        if (bImage == null) {
            // Create a buffered image using the default color model
            int type = BufferedImage.TYPE_INT_RGB;
            if (hasAlpha) {
                type = BufferedImage.TYPE_INT_ARGB;
            }
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1 ){
                return null;
            }
            bImage = new BufferedImage(width, height, type);
        }
        // Copy image to buffered image
        Graphics g = bImage.createGraphics();
        // Paint the image onto the buffered image
        g.drawImage(image, 0, 0, null);
        g.dispose();
        image.flush();
        return bImage;
    }

    // returns true if the specified image has transparent pixels, from
    // java almanac
    private static boolean hasAlpha(Image image) {
        // If buffered image, the color model is readily available
        if (image instanceof BufferedImage) {
            BufferedImage bimage = (BufferedImage)image;
            return bimage.getColorModel().hasAlpha();
        }
        // Use a pixel grabber to retrieve the image's color model;
        // grabbing a single pixel is usually sufficient
        PixelGrabber pg = new PixelGrabber(image, 0, 0, 1, 1, false);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
        }
        // Get the image's color model
        ColorModel cm = pg.getColorModel();
        if (cm != null){
            return cm.hasAlpha();
        }else {
            return true;
        }
    }

    private boolean nonDecodeCCITTMakeImage(Color fill) {
        BufferedImage tmpImage =
                CCITTFax.attemptDeriveBufferedImageFromBytes(this, library, entries, fill);
        // Either we have a fully ready RenderedImage...
        if (tmpImage != null) {
            // write tmpImage to the cache
            synchronized (imageLock) {
                if (image == null) {
                    image = new ImageCache(library);
                }
                image.setImage(tmpImage);
            }
            return true;
        }
        return false;
    }

    /**
     * CCITT fax decode algorithm.
     *
     * @param in stream to decode
     * @return decoded stream
     */
    private byte[] ccittfaxDecode(InputStream in) {
        // get decode parameters from stream properties
        Hashtable decodeparms = library.getDictionary(entries, "DecodeParms");
        float k = library.getFloat(decodeparms, "K");
        // default value is always false
        boolean blackIs1 = getBlackIs1(library, decodeparms);
        // get value of key if it is available.
        boolean encodedByteAlign = false;
        Object encodedByteAlignObject = library.getObject(decodeparms, "EncodedByteAlign");
        if (encodedByteAlignObject instanceof Boolean) {
            encodedByteAlign = (Boolean) encodedByteAlignObject;
        }
        int columns = library.getInt(decodeparms, "Columns");
        int width = library.getInt(entries, "Width");
        int height = library.getInt(entries, "Height");

        // setup streams based on stream properties
        int memoryNeeded = width * height / 8;
        checkMemory(memoryNeeded);
        ByteArrayOutputStream out = new ByteArrayOutputStream(memoryNeeded);

        if (k < 0) {
            CCITTFax.Group4Decode(in, out, columns, blackIs1);
            if (Tagger.tagging) {
                Tagger.tagImage("HandledBy=CCITTFaxDecode_InternalGroup4");
                Tagger.tagImage("CCITTFaxDecode_DecodeParms_BlackIs1=" + getBlackIs1OrNull(library, decodeparms));
                Tagger.tagImage("CCITTFaxDecode_DecodeParms_K=" + k);
                Tagger.tagImage("CCITTFaxDecode_DecodeParms_EncodedByteAlign=" + encodedByteAlignObject);
            }
        }
        try {
            out.close();
            out.flush();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        if (!encodedByteAlign)
            isCCITTFaxDecodeWithoutEncodedByteAlign = true;
        if (columns > width)
            CCITTFaxDecodeColumnWidthMismatch = columns - width;
        return out.toByteArray();
    }

    /**
     * Gets the decoded Byte stream of the Stream object.
     *
     * @return decoded Byte stream
     */
    public byte[] getBytes() {
        byte[] data = getDecodedStreamBytes();
        if (data == null)
            data = new byte[0];
        return data;
    }

    /**
     * Gets the image object for the given resource.
     *
     * @param fill      color value of image
     * @param resources resouces containing image reference
     * @return new image object
     */
    // was synchronized, not think it is needed?
    public BufferedImage getImage(Color fill, Resources resources, boolean allowScaling) {
        if (Tagger.tagging)
            Tagger.beginImage(pObjectReference, inlineImage);
        //String debugFill = (fill == null) ? "null" : Integer.toHexString(fill.getRGB());
        //System.out.println("Stream.getImage()  for: " + pObjectReference + "  fill: " + debugFill + "\n  stream: " + this);

        if (Tagger.tagging)
            Tagger.tagImage("Filter=" + getNormalisedFilterNames());

        // parse colour space
        PColorSpace colourSpace = null;
        Object o = library.getObject(entries, "ColorSpace");
        if (resources != null) {
            colourSpace = resources.getColorSpace(o);
        }
        //colorSpace = PColorSpace.getColorSpace(library, o);
        // assume b&w image is no colour space
        if (colourSpace == null) {
            colourSpace = new DeviceGray(library, null);
            if (Tagger.tagging)
                Tagger.tagImage("ColorSpace_Implicit_DeviceGray");
        }
        if (Tagger.tagging)
            Tagger.tagImage("ColorSpace=" + colourSpace.getDescription());

        boolean imageMask = isImageMask();
        if (Tagger.tagging)
            Tagger.tagImage("ImageMask=" + imageMask);
        if (imageMask)
            allowScaling = false;

        // find out the number of bits in the image
        int bitspercomponent = library.getInt(entries, "BitsPerComponent");
        if (imageMask && bitspercomponent == 0) {
            bitspercomponent = 1;
            if (Tagger.tagging)
                Tagger.tagImage("BitsPerComponent_Implicit_1");
        }
        if (Tagger.tagging)
            Tagger.tagImage("BitsPerComponent=" + bitspercomponent);

        // get dimension of image stream
        int width = library.getInt(entries, "Width");
        int height = library.getInt(entries, "Height");

        // check for available memory, get colour space and bit count
        // to better estimate size of image in memory
        int colorSpaceCompCount = colourSpace.getNumComponents();

        // parse decode information
        Vector decode = (Vector) library.getObject(entries, "Decode");
        if (decode == null) {
            decode = new Vector(2);
            decode.addElement(new Float(0));
            decode.addElement(new Float(1));
            if (Tagger.tagging)
                Tagger.tagImage("Decode_Implicit_01");
        }
        if (Tagger.tagging)
            Tagger.tagImage("Decode=" + decode);

        BufferedImage smaskImage = null;
        BufferedImage maskImage = null;
        int[] maskMinRGB = null;
        int[] maskMaxRGB = null;
        int maskMinIndex = -1;
        int maskMaxIndex = -1;
        Object smaskObj = library.getObject(entries, "SMask");
        Object maskObj = library.getObject(entries, "Mask");
        if (smaskObj instanceof Stream) {
            if (Tagger.tagging)
                Tagger.tagImage("SMaskStream");
            Stream smaskStream = (Stream) smaskObj;
            if (smaskStream.isImageSubtype())
                smaskImage = smaskStream.getImage(fill, resources, false);
        }
        if (smaskImage != null) {
            if (Tagger.tagging)
                Tagger.tagImage("SMaskImage");
            allowScaling = false;
        }
        if (maskObj != null && smaskImage == null) {
            if (maskObj instanceof Stream) {
                if (Tagger.tagging)
                    Tagger.tagImage("MaskStream");
                Stream maskStream = (Stream) maskObj;
                if (maskStream.isImageSubtype()) {
                    maskImage = maskStream.getImage(fill, resources, false);
                    if (maskImage != null) {
                        if (Tagger.tagging)
                            Tagger.tagImage("MaskImage");
                    }
                }
            } else if (maskObj instanceof Vector) {
                if (Tagger.tagging)
                    Tagger.tagImage("MaskVector");
                Vector maskVector = (Vector) maskObj;
                int[] maskMinOrigCompsInt = new int[colorSpaceCompCount];
                int[] maskMaxOrigCompsInt = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++) {
                    if ((i * 2) < maskVector.size())
                        maskMinOrigCompsInt[i] = ((Number) maskVector.get(i * 2)).intValue();
                    if ((i * 2 + 1) < maskVector.size())
                        maskMaxOrigCompsInt[i] = ((Number) maskVector.get(i * 2 + 1)).intValue();
                }
                if (colourSpace instanceof Indexed) {
                    Indexed icolourSpace = (Indexed) colourSpace;
                    Color[] colors = icolourSpace.accessColorTable();
                    if (colors != null &&
                            maskMinOrigCompsInt.length >= 1 &&
                            maskMaxOrigCompsInt.length >= 1) {
                        maskMinIndex = maskMinOrigCompsInt[0];
                        maskMaxIndex = maskMaxOrigCompsInt[0];
                        if (maskMinIndex >= 0 && maskMinIndex < colors.length &&
                                maskMaxIndex >= 0 && maskMaxIndex < colors.length) {
                            Color minColor = colors[maskMinOrigCompsInt[0]];
                            Color maxColor = colors[maskMaxOrigCompsInt[0]];
                            maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                            maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
                        }
                    }
                } else {
                    PColorSpace.reverseInPlace(maskMinOrigCompsInt);
                    PColorSpace.reverseInPlace(maskMaxOrigCompsInt);
                    float[] maskMinOrigComps = new float[colorSpaceCompCount];
                    float[] maskMaxOrigComps = new float[colorSpaceCompCount];
                    colourSpace.normaliseComponentsToFloats(maskMinOrigCompsInt, maskMinOrigComps, (1 << bitspercomponent) - 1);
                    colourSpace.normaliseComponentsToFloats(maskMaxOrigCompsInt, maskMaxOrigComps, (1 << bitspercomponent) - 1);

                    Color minColor = colourSpace.getColor(maskMinOrigComps);
                    Color maxColor = colourSpace.getColor(maskMaxOrigComps);
                    PColorSpace.reverseInPlace(maskMinOrigComps);
                    PColorSpace.reverseInPlace(maskMaxOrigComps);
                    maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                    maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
                }
            }
        }

        BufferedImage img = getImage(
                colourSpace, fill,
                width, height,
                colorSpaceCompCount,
                bitspercomponent,
                imageMask,
                decode,
                smaskImage,
                maskImage,
                maskMinRGB, maskMaxRGB,
                maskMinIndex, maskMaxIndex);
        if (img != null) {
            img = putIntoImageCache(img, width, height, allowScaling);
        }
//String title = "Image: " + getPObjectReference();
//CCITTFax.showRenderedImage(img, title);
        if (Tagger.tagging)
            Tagger.endImage(pObjectReference);
        return img;
    }

    private BufferedImage getImage(
            PColorSpace colourSpace, Color fill,
            int width, int height,
            int colorSpaceCompCount,
            int bitspercomponent,
            boolean imageMask,
            Vector decode,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB,
            int maskMinIndex, int maskMaxIndex) {
        byte[] baCCITTFaxData = null;

        // decode the stream is, if value and image are null, the image has
        // has not yet been decoded
        if (image == null) {
            baCCITTFaxData = decodeCCITTFaxDecodeOrDCTDecodeOrJBIG2DecodeOrJPXDecodeImage(
                    width, height, colourSpace, bitspercomponent, fill,
                    smaskImage, maskImage, maskMinRGB, maskMaxRGB);
        }

        // return cached image
        if (image != null) {
            // If Stream.dispose(true) was called since last call to Stream.getPageImage(),
            //   then might have to read from file
            checkMemory(width * height * Math.max(colorSpaceCompCount, 4));
            //TODO There's a race-ish condition here
            //  checkMemory() could have called Page.reduceMemory(), which
            //  could have called Stream.dispose(), which could have nulled
            //  out our "image" field
            BufferedImage img = null;
            synchronized (imageLock) {
                if (image != null)
                    img = image.readImage();
            }
            if (img != null)
                return img;
        }

        if (baCCITTFaxData == null) {
            try {
                BufferedImage img = makeImageWithRasterFromBytes(
                        colourSpace, fill,
                        width, height,
                        colorSpaceCompCount,
                        bitspercomponent,
                        imageMask,
                        decode,
                        smaskImage,
                        maskImage,
                        maskMinRGB, maskMaxRGB,
                        maskMinIndex, maskMaxIndex);
                if (img != null)
                    return img;
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Error building image raster.", e);
            }
        }

        // decodes the image stream and returns an image object
        BufferedImage im = parseImage(
                width,
                height,
                colourSpace,
                imageMask,
                fill,
                bitspercomponent,
                decode,
                baCCITTFaxData,
                smaskImage,
                maskImage,
                maskMinRGB, maskMaxRGB);
        return im;
    }


    private BufferedImage makeImageWithRasterFromBytes(
            PColorSpace colourSpace,
            Color fill,
            int width, int height,
            int colorSpaceCompCount,
            int bitspercomponent,
            boolean imageMask,
            Vector decode,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB,
            int maskMinIndex, int maskMaxIndex) {
        BufferedImage img = null;
        if (colourSpace instanceof DeviceGray) {
            //System.out.println("Stream.makeImageWithRasterFromBytes()  DeviceGray");
            if (imageMask && bitspercomponent == 1) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_1_ImageMask");
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, data_length);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));

                // From PDF 1.6 spec, concerning ImageMask and Decode array:
                // [0 1] (the default for an image mask), a sample value of 0 marks
                //       the page with the current color, and a 1 leaves the previous
                //       contents unchanged.
                // [1 0] Is the reverse

                // In case alpha transparency doesn't work, it'll paint white opaquely
                boolean defaultDecode = (0.0f == ((Number) decode.elementAt(0)).floatValue());
                //int a = Color.white.getRGB();
                int a = 0x00FFFFFF; // Clear if alpha supported, else white
                int[] cmap = new int[]{
                        (defaultDecode ? fill.getRGB() : a),
                        (defaultDecode ? a : fill.getRGB())
                };
                int transparentIndex = (defaultDecode ? 1 : 0);
                IndexColorModel icm = new IndexColorModel(
                        bitspercomponent,       // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        true,                   // indicates whether alpha values are contained in the cmap array
                        transparentIndex,       // the index of the fully transparent pixel
                        db.getDataType());      // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(icm, wr, false, null);
            } else if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_124");
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, data_length);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                int[] cmap = null;
                if (bitspercomponent == 1) {
                    boolean defaultDecode = (0.0f == ((Number) decode.elementAt(0)).floatValue());
                    cmap = defaultDecode ? GRAY_1_BIT_INDEX_TO_RGB : GRAY_1_BIT_INDEX_TO_RGB_REVERSED;
                } else if (bitspercomponent == 2)
                    cmap = GRAY_2_BIT_INDEX_TO_RGB;
                else if (bitspercomponent == 4)
                    cmap = GRAY_4_BIT_INDEX_TO_RGB;
                ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_8");
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, data_length);
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                ColorModel cm = new ComponentColorModel(cs, new int[]{bitspercomponent}, false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
        } else if (colourSpace instanceof DeviceRGB) {
            //System.out.println("Stream.makeImageWithRasterFromBytes()  DeviceRGB");
            //System.out.println("Mem BEGIN free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
            if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceRGB_8");
                //System.out.println("Mem  bpc8 free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                checkMemory(width * height * 4);
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_DeviceRGB_8_alpha=" + usingAlpha);
                int type = usingAlpha ? BufferedImage.TYPE_INT_ARGB :
                        BufferedImage.TYPE_INT_RGB;
                img = new BufferedImage(width, height, type);
                int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                copyDecodedStreamBytesIntoRGB(data);
                if (usingAlpha)
                    alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
            }
        } else if (colourSpace instanceof DeviceCMYK) {
            //System.out.println("Stream.makeImageWithRasterFromBytes()  DeviceCMYK");
            //System.out.println("Mem BEGIN free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
            if (false && bitspercomponent == 8) {//TODO Look at doing CMYK properly
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceCMYK_8");
                //System.out.println("Mem  bpc8 free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                //System.out.println("data_length: " + data_length);
                //System.out.println("Mem  tba  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                DataBuffer db = new DataBufferByte(data, data_length);
                //System.out.println("Mem   db  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                int[] bandOffsets = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bandOffsets[i] = i;
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, colorSpaceCompCount, colorSpaceCompCount * width, bandOffsets);
                //System.out.println("Mem   sm  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                //WritableRaster wr = Raster.createInterleavedRaster( db, width, height, colorSpaceCompCount*width, colorSpaceCompCount, bandOffsets, new Point(0,0) );
                //System.out.println("Mem   wr  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                ColorSpace cs = null;
                try {
                    //cs = new ColorSpaceCMYK(); //ColorSpace.getInstance( ColorSpace.CS_PYCC );//ColorSpace.TYPE_CMYK );
                    ///cs = ColorSpaceWrapper.getICCColorSpaceInstance("C:\\Documents and Settings\\Mark Collette\\IdeaProjects\\TestJAI\\CMYK.pf");
                }
                catch (Exception csex) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Problem loading CMYK ColorSpace");
                    }
                }
                //System.out.println("Mem   cs  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                int[] bits = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bits[i] = bitspercomponent;
                ColorModel cm = new ComponentColorModel(cs, bits, false, false, ColorModel.OPAQUE, db.getDataType());
                //System.out.println("Mem   cm  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                img = new BufferedImage(cm, wr, false, null);
                //System.out.println("Mem  img  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
            }
        } else if (colourSpace instanceof Indexed) {
            //System.out.println("Stream.makeImageWithRasterFromBytes()  Indexed");
            //System.out.println("Mem BEGIN free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
            if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_124");
                //System.out.println("Mem  bpc< free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                //System.out.println("          data_length: " + data_length);
                //System.out.println("Mem  tba  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                DataBuffer db = new DataBufferByte(data, data_length);
                //System.out.println("Mem   db  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                //System.out.println("Mem   wr  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
//System.out.println("cmap: " + ((colors == null) ? -1 : colors.length));
                int[] cmap = new int[(colors == null) ? 0 : colors.length];
                for (int i = 0; i < cmap.length; i++) {
                    cmap[i] = colors[i].getRGB();
//System.out.println("cmap["+i+"]: " + Integer.toHexString(cmap[i]));
                }
                int cmapMaxLength = 1 << bitspercomponent;
                if (cmap.length > cmapMaxLength) {
                    int[] cmapTruncated = new int[cmapMaxLength];
                    System.arraycopy(cmap, 0, cmapTruncated, 0, cmapMaxLength);
                    cmap = cmapTruncated;
                }
                //System.out.println("          cmap.length: " + cmap.length);
                ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                //System.out.println("Mem   cm  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                img = new BufferedImage(cm, wr, false, null);
                //System.out.println("Mem  img  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_8");
                //System.out.println("Stream.makeImageWithRasterFromBytes()  Indexed 8");
                //System.out.println("Mem  bpc8 free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                Object[] dataAndSize = getDecodedStreamBytesAndSize(
                        width * height * colourSpace.getNumComponents() * bitspercomponent / 8);
                byte[] data = (byte[]) dataAndSize[0];
                int data_length = (Integer) dataAndSize[1];
//System.out.println("data_length: " + data_length);
//System.out.println( org.icepdf.core.util.Utils.convertByteArrayToHexString(data, true) );
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;

                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int colorsLength = (colors == null) ? 0 : colors.length;
//System.out.println("colorsLength: " + ((colors == null) ? -1 : colors.length));
                int[] cmap = new int[256];
                for (int i = 0; i < colorsLength; i++) {
                    cmap[i] = colors[i].getRGB();
//System.out.println("cmap["+i+"]: " + Integer.toHexString(cmap[i]));
                }
                for (int i = colorsLength; i < cmap.length; i++)
                    cmap[i] = 0xFF000000;

                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_Indexed_8_alpha=" + (usingIndexedAlpha ? "indexed" : (usingAlpha ? "alpha" : "false")));
                if (usingIndexedAlpha) {
                    for (int i = maskMinIndex; i <= maskMaxIndex; i++)
                        cmap[i] = 0x00000000;
                    DataBuffer db = new DataBufferByte(data, data_length);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, true, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else if (usingAlpha) {
                    checkMemory(width * height * 4);
                    int[] rgbaData = new int[width * height];
                    int end = data_length;
                    for (int index = 0; index < end; index++) {
                        int cmapIndex = (data[index] & 0xFF);
                        rgbaData[index] = cmap[cmapIndex];
                    }
                    DataBuffer db = new DataBufferInt(rgbaData, rgbaData.length);
                    int[] masks = new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};
                    //SampleModel sm = new SinglePixelPackedSampleModel(
                    //    db.getDataType(), width, height, masks );
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, width, masks, new Point(0, 0));
                    alterRasterRGBA(wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    ColorModel cm = new DirectColorModel(cs, 32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000, false, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else {
                    //System.out.println("          data.length: " + data.length);
                    //System.out.println("Mem  tba  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                    DataBuffer db = new DataBufferByte(data, data_length);
                    //System.out.println("Mem   db  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    //System.out.println("Mem   wr  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                    //System.out.println("          cmap.length: " + cmap.length);
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    //System.out.println("Mem   cm  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                    img = new BufferedImage(cm, wr, false, null);
                    //System.out.println("Mem  img  free: " + Runtime.getRuntime().freeMemory() + ",\ttotal:" + Runtime.getRuntime().totalMemory() + ",\tused: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) + ",\ttime: " + System.currentTimeMillis());
                }
            }
        }
        return img;
    }

    /**
     * Parses the image stream and creates a Java Images object based on the
     * the given stream and the supporting paramaters.
     *
     * @param width         dimension of new image
     * @param height        dimension of new image
     * @param colorSpace    colour space of image
     * @param imageMask     true if the image has a imageMask, false otherwise
     * @param fill          colour pased in via graphic state, used to fill in background
     * @param bitsPerColour number of bits used in a colour
     * @param decode        Decode attribute values from PObject
     * @return valid java image from the PDF stream
     */
    private BufferedImage parseImage(
            int width,
            int height,
            PColorSpace colorSpace,
            boolean imageMask,
            Color fill,
            int bitsPerColour,
            Vector decode,
            byte[] baCCITTFaxData,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB) {
        if (Tagger.tagging)
            Tagger.tagImage("HandledBy=ParseImage");

        // store for manipulating bits in image
        int[] imageBits = new int[width];

        // RGB value for colour used as fill for image
        int fillRGB = fill.getRGB();

        // Number of colour components in image, should be 3 for RGB or 4
        // for ARGB.
        int colorSpaceCompCount = colorSpace.getNumComponents();
        boolean isDeviceRGB = colorSpace instanceof DeviceRGB;
        boolean isDeviceGray = colorSpace instanceof DeviceGray;

        // Max value used to represent a colour,  usually 255, min is 0
        int maxColourValue = ((1 << bitsPerColour) - 1);

        int f[] = new int[colorSpaceCompCount];
        float ff[] = new float[colorSpaceCompCount];

        // image mask from
        int imageMaskValue = ((Number) decode.elementAt(0)).intValue();

        // decode decode.
        float[] decodeArray = null;
        if (decode != null) {
            decodeArray = new float[decode.size()];
            for (int i = 0; i < decodeArray.length; i++) {
                decodeArray[i] = ((Number) decode.elementAt(i)).floatValue();
            }
        }

//        System.out.println("image size " + width + "x" + height +
//                           "\n\tBitsPerComponent " + bitsPerColour +
//                           "\n\tColorSpace " + colorSpaceCompCount +
//                           "\n\tFill " + fillRGB +
//                           "\n\tDecode " + decode +
//                           "\n\tImageMask " + imageMask +
//                           "\n\tMaxColorValue " + maxColourValue +
//                           "\n\tColourSpace " + colorSpace.toString() +
//                           "\n\tLength " + streamInput.getLength());

        // Do a rough check for memory need for this image, we want to over
        // estimate the memory needed so that we minimize the change of
        // a out of memory error.  Because of image caching, there is no
        // significant performance hit, as loading the image from file is much
        // faster then parsing it from the decoded byte stream.
        int memoryNeeded = (width * height * 4); // ARGB
        checkMemory(memoryNeeded);

        // Create the memory hole where where the buffered image will be writen
        // too, bit by painfull bit.
        BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // If a row of data takes up a fractional number of bytes,
        //  due to each component taking up less than 8 bits, then
        //  (unless CCITTFaxDecode) we want to read past the pad bits
        //  so that the next row starts on a byte boundary again
        int bitsPerRow = width * colorSpaceCompCount * bitsPerColour;
        int extraBitsPerRow = bitsPerRow & 0x7;
        if (CCITTFaxDecodeColumnWidthMismatch > 0) {
            if (Tagger.tagging)
                Tagger.tagImage("ParseImage_CCITTFaxDecodeColumnWidthMismatch=" + CCITTFaxDecodeColumnWidthMismatch);
            int bitsGivenPerRow = (width + CCITTFaxDecodeColumnWidthMismatch)
                    * colorSpaceCompCount * bitsPerColour;
            int bitsRelevant = bitsPerRow;
            extraBitsPerRow = bitsGivenPerRow - bitsRelevant;
        }

        // create the buffer and get the first series of bytes from the cached
        // stream
        BitStream in = null;
        if (baCCITTFaxData != null) {
            in = new BitStream(new ByteArrayInputStream(baCCITTFaxData));
        } else {
            InputStream dataInput = getInputStreamForDecodedStreamBytes();
            if (dataInput == null)
                return null;
            in = new BitStream(dataInput);
        }

        try {
            // Start encoding bit stream into an image,  we work one pixel at
            // a time,  and grap the need bit information for the images
            // colour space and bits per colour
            for (int y = 0; y < height; y++) {

                for (int x = 0; x < width; x++) {

                    // if image has mask apply it
                    if (imageMask) {
                        int bit = in.getBits(bitsPerColour);
                        bit = (bit == imageMaskValue) ? fillRGB : 0x00000000;
                        imageBits[x] = bit;
                    }
                    // other wise start colour bit parsing
                    else {
                        // set some default values
                        int red = 255;
                        int blue = 255;
                        int green = 255;
                        int alpha = 255;

                        // indexed colour
                        if (colorSpaceCompCount == 1) {
                            // get value used for this bit
                            int bit = in.getBits(bitsPerColour);
                            // check decode array if a colour inversion is needed
                            if (decodeArray != null) {
                                // if index 0 > index 1 then we have a need for ainversion
                                if (decodeArray[0] > decodeArray[1]) {
                                    bit = (bit == maxColourValue) ? 0x00000000 : maxColourValue;
                                }
                            }

                            if (isDeviceGray) {
                                if (bitsPerColour == 1)
                                    bit = GRAY_1_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 2)
                                    bit = GRAY_2_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 4)
                                    bit = GRAY_4_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 8) {
                                    bit = ((bit << 24) |
                                            (bit << 16) |
                                            (bit << 8) |
                                            bit);
                                }
                                imageBits[x] = bit;
                            } else {
                                f[0] = bit;
                                colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);

                                Color color = colorSpace.getColor(ff);
                                imageBits[x] = color.getRGB();
                            }
                        }
                        // normal RGB colour
                        else if (colorSpaceCompCount == 3) {
                            // We can have an ICCBased color space that has 3 components,
                            //  but where we can't assume it's RGB.
                            // But, when it is DeviceRGB, we don't want the performance hit
                            //  of converting the pixels via the PColorSpace, so we'll
                            //  break this into the two cases
                            if (isDeviceRGB) {
                                red = in.getBits(bitsPerColour);
                                green = in.getBits(bitsPerColour);
                                blue = in.getBits(bitsPerColour);
                                // combine the colour together
                                imageBits[x] = (alpha << 24) | (red << 16) |
                                        (green << 8) | blue;
                            } else {
                                for (int i = 0; i < colorSpaceCompCount; i++) {
                                    f[i] = in.getBits(bitsPerColour);
                                }
                                PColorSpace.reverseInPlace(f);
                                colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);
                                Color color = colorSpace.getColor(ff);
                                imageBits[x] = color.getRGB();
                            }
                        }
                        // normal aRGB colour,  this could use some more
                        // work for optimizing.
                        else if (colorSpaceCompCount == 4) {
                            for (int i = 0; i < colorSpaceCompCount; i++) {
                                f[i] = in.getBits(bitsPerColour);
                            }
                            PColorSpace.reverseInPlace(f);
                            colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);
                            Color color = colorSpace.getColor(ff);
                            imageBits[x] = color.getRGB();
                        }
                        // else just set pixel with the default values
                        else {
                            // compine the colour together
                            imageBits[x] = (alpha << 24) | (red << 16) |
                                    (green << 8) | blue;
                        }
                    }
                }
                // Assign the new bits for this pixel
                bim.setRGB(0, y, width, 1, imageBits, 0, 1);

                // CCITTFaxDecode does not use pad bits to make rows
                //  start on byte boundaries, so in that case do not
                //  skip the bits, since they are not "extra"
                // If we're not CCITTFaxDecode, or if we are and the
                //  EncodedByteAlign flag is set, then skip any extra
                //  bits to take us to the next byte boundary
                if (extraBitsPerRow > 0 &&
                        (isCCITTFaxDecodeWithoutEncodedByteAlign == false ||
                                CCITTFaxDecodeColumnWidthMismatch > 0)) {
                    in.getBits(extraBitsPerRow);
                }
            }
            // final clean up.
            in.close();
            in = null;

            if (smaskImage != null || maskImage != null || maskMinRGB != null || maskMaxRGB != null) {
                WritableRaster wr = bim.getRaster();
                alterRasterRGBA(wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
            }
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error parsing image.", e);
        }

        return bim;
    }

    private BufferedImage putIntoImageCache(BufferedImage bim, int width, int height, boolean allowScaling) {
        // create new Image cache if needed.
        if (image == null) {
            image = new ImageCache(library);
        }

        boolean setIsScaledOnImageCache = false;
        if (allowScaling && scaleImages && !image.isScaled()) {
            boolean canScale = checkMemory(
                    Math.max(width, bim.getWidth()) *
                            Math.max(height, bim.getHeight()) *
                            Math.max(4, bim.getColorModel().getPixelSize()));
            if (canScale) {
                // To limit the number of image file cache writes this scaling
                //  algorithem works with the buffered image in memory
                bim = ImageCache.scaleBufferedImage(bim, width, height);
                setIsScaledOnImageCache = true;
            }
        }

        // The checkMemory call above can make us lose our ImageCache
        synchronized (imageLock) {
            if (image == null) {
                image = new ImageCache(library);
            }
            // cache the new image
            if (setIsScaledOnImageCache)
                image.setIsScaled(true);
            image.setImage(bim);

            // read the image from the cache and return to caller
            bim = image.readImage();
        }
        return bim;
    }

    /**
     * Does the image have an ImageMask.
     */
    public boolean isImageMask() {
        Object o = library.getObject(entries, "ImageMask");
        return (o != null)
                ? (o.toString().equals("true") ? true : false)
                : false;
    }

    public boolean getBlackIs1(Library library, Hashtable decodeParmsDictionary) {
        Boolean blackIs1 = getBlackIs1OrNull(library, decodeParmsDictionary);
        if (blackIs1 != null)
            return blackIs1.booleanValue();
        return false;
    }

    /**
     * If BlackIs1 was not specified, then return null, instead of the
     * default value of false, so we can tell if it was given or not
     */
    public Boolean getBlackIs1OrNull(Library library, Hashtable decodeParmsDictionary) {
        Object blackIs1Obj = library.getObject(decodeParmsDictionary, "BlackIs1");
        if (blackIs1Obj != null) {
            if (blackIs1Obj instanceof Boolean) {
                return (Boolean) blackIs1Obj;
            } else if (blackIs1Obj instanceof String) {
                String blackIs1String = (String) blackIs1Obj;
                if (blackIs1String.equalsIgnoreCase("true"))
                    return Boolean.TRUE;
                else if (blackIs1String.equalsIgnoreCase("t"))
                    return Boolean.TRUE;
                else if (blackIs1String.equals("1"))
                    return Boolean.TRUE;
                else if (blackIs1String.equalsIgnoreCase("false"))
                    return Boolean.FALSE;
                else if (blackIs1String.equalsIgnoreCase("f"))
                    return Boolean.FALSE;
                else if (blackIs1String.equals("0"))
                    return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Allow access to seakable intput so that pattern object can
     * be corrrectly created.
     *
     * @return stream istnaces SeekableInputConstrainedWrapper
     */
    public SeekableInputConstrainedWrapper getStreamInput() {
        return streamInput;
    }

    /**
     * Return a string description of the object.  Primarly used for debugging.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("STREAM= ");
        sb.append(entries);
        if (getPObjectReference() != null) {
            sb.append("  ");
            sb.append(getPObjectReference());
        }
        return sb.toString();
    }
}
