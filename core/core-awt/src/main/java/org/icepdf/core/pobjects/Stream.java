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
package org.icepdf.core.pobjects;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.ConservativeSizingByteArrayOutputStream;
import org.icepdf.core.pobjects.filters.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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

    public static final Name FILTER_KEY = new Name("Filter");
    public static final Name DECODEPARAM_KEY = new Name("DecodeParms");

    public static final Name FILTER_FLATE_DECODE = new Name("FlateDecode");
    public static final Name FILTER_ASCII85_DECODE = new Name("ASCII85Decode");
    public static final Name FILTER_ASCIIHexDECODE = new Name("ASCIIHexDecode");
    public static final Name FILTER_RUN_LENGTH_DECODE = new Name("RunLengthDecode");
    public static final Name FILTER_CCITT_FAX_DECODE = new Name("CCITTFaxDecode");
    public static final Name FILTER_JBIG2_DECODE = new Name("JBIG2Decode");
    public static final Name FILTER_JPX_DECODE = new Name("JPXDecode");

    // original byte stream that has not been decoded
    protected byte[] rawBytes;

    protected DictionaryEntries decodeParams;

    // default compression state for a file loaded stream,  for re-saving
    // of form data we want to avoid re-compressing the data.
    protected boolean compressed = true;

    public Stream(Library library, DictionaryEntries dictionaryEntries, byte[] rawBytes) {
        super(library, dictionaryEntries);
        decodeParams = this.library.getDictionary(entries, DECODEPARAM_KEY);
        this.rawBytes = rawBytes;
    }

    public Stream(DictionaryEntries dictionaryEntries, byte[] rawBytes) {
        super(null, dictionaryEntries);
        this.rawBytes = rawBytes;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
        compressed = false;
    }

    public boolean isRawBytesCompressed() {
        return compressed;
    }

    protected boolean isImageSubtype() {
        Object subtype = library.getObject(entries, SUBTYPE_KEY);
        return subtype != null && subtype.equals("Image");
    }

    /**
     * Gets the decoded Byte stream of the Stream object.
     *
     * @return decoded Byte stream
     */
    public ByteArrayInputStream getDecodedByteArrayInputStream() {
        return new ByteArrayInputStream(getDecodedStreamBytes(0));
    }

    public byte[] getDecodedStreamBytes() {
        return getDecodedStreamBytes(8192);
    }

    /**
     * This is similar to getDecodedStreamByteArray(), except that the returned byte[]
     * is not necessarily exactly sized, and may be larger. Therefore the returned
     * Integer gives the actual valid size
     *
     * @param presize potential size to associate with byte array.
     * @return Object[] { byte[] data, Integer sizeActualData }
     */
    public byte[] getDecodedStreamBytes(int presize) {
        // decompress the stream
        if (compressed) {
            try {
                // todo, could nio all a little speed up here.
                ByteArrayInputStream streamInput = new ByteArrayInputStream(rawBytes);
                long rawStreamLength = rawBytes.length;
                InputStream input = getDecodedInputStream(streamInput, rawStreamLength);
                if (input == null) return null;
                int outLength;
                if (presize > 0) {
                    outLength = presize;
                } else {
                    outLength = Math.max(4096, (int) rawStreamLength);
                }
                ConservativeSizingByteArrayOutputStream out = new
                        ConservativeSizingByteArrayOutputStream(outLength);
                // todo buffer allocation size seem broken?
                byte[] buffer = new byte[(outLength > 4096) ? 4096 : 8192];
                while (true) {
                    int read = input.read(buffer);
                    if (read <= 0)
                        break;
                    out.write(buffer, 0, read);
                }
                input.close();
                out.flush();
                out.close();
                out.trim();
                return out.relinquishByteArray();
            } catch (IOException e) {
                logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
            }
        }
        // we have an edited stream which isn't compressed yet, so just return
        // the raw bytes.
        else {
            return rawBytes;
        }
        return null;
    }

    public ByteBuffer getDecodedStreamByteBuffer() {
        return getDecodedStreamByteBuffer(8192);
    }

    public ByteBuffer getDecodedStreamByteBuffer(int presiz) {
        byte[] decodeBytes = getDecodedStreamBytes(presiz);
        ByteBuffer decodedByteBuffer = ByteBuffer.allocateDirect(decodeBytes.length);
        decodedByteBuffer.put(decodeBytes);
        decodedByteBuffer.position(0);
        return decodedByteBuffer;
    }

    /**
     * Utility method for decoding the byte stream using the decode algorithem
     * specified by the filter parameter
     * <br>
     * The memory manger is called every time a stream is being decoded with an
     * estimated size of the decoded stream.  Because many of the Filter
     * algorithms use compression,  further research must be done to try and
     * find the average amount of memory used by each of the algorithms.
     *
     * @return inputstream that has been decoded as defined by the streams filters.
     */
    private InputStream getDecodedInputStream(InputStream streamInput, long streamLength) {
        // Make sure that the stream actually has data to decode, if it doesn't
        // make it null and return.
        if (streamInput == null || streamLength < 1) {
            return null;
        }

        InputStream input = streamInput;

        int bufferSize = Math.min(Math.max((int) streamLength, 64), 16 * 1024);
        input = new java.io.BufferedInputStream(input, bufferSize);

        // Search for crypt dictionary entry and decode params so that
        // named filters can be assigned correctly.
        SecurityManager securityManager = library.getSecurityManager();
//        System.out.println("Thread " + Thread.currentThread() + " " + pObjectReference);
        if (securityManager != null) {
            // check see of there is a decodeParams for a crypt filter.
            input = securityManager.decryptInputStream(
                    pObjectReference, securityManager.getDecryptionKey(),
                    decodeParams, input, true);
        }

        // Get the filter name for the encoding type, which can be either
        // a Name or Vector.
        List filterNames = getFilterNames();
        if (filterNames == null)
            return input;

        // Decode the stream data based on the filter names.
        // Loop through the filterNames and apply the filters in the order
        // in which they where found.
        for (Object filterName1 : filterNames) {
            // grab the name of the filter
            String filterName = filterName1.toString();
            //System.out.println("  Decoding: " + filterName);

            switch (filterName) {
                case "FlateDecode":
                case "/Fl":
                case "Fl":
                    input = new FlateDecode(library, entries, input);
                    break;
                case "LZWDecode":
                case "/LZW":
                case "LZW":
                    input = new LZWDecode(new BitStream(input), library, entries);
                    break;
                case "ASCII85Decode":
                case "/A85":
                case "A85":
                    input = new ASCII85Decode(input);
                    break;
                case "ASCIIHexDecode":
                case "/AHx":
                case "AHx":
                    input = new ASCIIHexDecode(input);
                    break;
                case "RunLengthDecode":
                case "/RL":
                case "RL":
                    input = new RunLengthDecode(input);
                    break;
                case "CCITTFaxDecode":
                case "/CCF":
                case "CCF":
                    // Leave empty so our else clause works
                    break;
                case "DCTDecode":
                case "/DCT":
                case "DCT":
                    // Leave empty so our else clause works
                    break;
                case "JBIG2Decode":
// No short name, since no JBIG2 for inline images

                    // Leave empty so our else clause works
                    break;
                case "JPXDecode":
// No short name, since no JPX for inline images

                    // Leave empty so our else clause works
                    break;
                default:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("UNSUPPORTED:" + filterName + " " + entries);
                    }
                    break;
            }
        }
        // Apply  Predictor Filter logic fo LZW or Flate streams.
        if (PredictorDecode.isPredictor(library, entries)) {
            input = new PredictorDecode(input, library, entries);
        }

        return input;
    }

    @SuppressWarnings("unchecked")
    public List<String> getFilterNames() {
        List<String> filterNames = null;
        Object o = library.getObject(entries, FILTER_KEY);
        if (o instanceof Name) {
            filterNames = new ArrayList<>(1);
            filterNames.add(o.toString());
        } else if (o instanceof List) {
            filterNames = (List) o;
        }
        return filterNames;
    }

    protected List<String> getNormalisedFilterNames() {
        List<String> filterNames = getFilterNames();
        if (filterNames == null)
            return null;

        String filterName;
        for (int i = 0; i < filterNames.size(); i++) {
            filterName = filterNames.get(i);

            switch (filterName) {
                case "FlateDecode":
                case "/Fl":
                case "Fl":
                    filterName = "FlateDecode";
                    break;
                case "LZWDecode":
                case "/LZW":
                case "LZW":
                    filterName = "LZWDecode";
                    break;
                case "ASCII85Decode":
                case "/A85":
                case "A85":
                    filterName = "ASCII85Decode";
                    break;
                case "ASCIIHexDecode":
                case "/AHx":
                case "AHx":
                    filterName = "ASCIIHexDecode";
                    break;
                case "RunLengthDecode":
                case "/RL":
                case "RL":
                    filterName = "RunLengthDecode";
                    break;
                case "CCITTFaxDecode":
                case "/CCF":
                case "CCF":
                    filterName = "CCITTFaxDecode";
                    break;
                case "DCTDecode":
                case "/DCT":
                case "DCT":
                    filterName = "DCTDecode";
                    break;
            }
            // There aren't short names for JBIG2Decode or JPXDecode
            filterNames.set(i, filterName);
        }
        return filterNames;
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
