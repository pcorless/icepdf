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
import org.icepdf.core.io.ByteBufferBackedInputStream;
import org.icepdf.core.io.ConservativeSizingByteArrayOutputStream;
import org.icepdf.core.pobjects.annotations.Annotation;
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

    private static final Logger logger = Logger.getLogger(Stream.class.getName());

    public static final Name FILTER_KEY = new Name("Filter");
    public static final Name DECODEPARAM_KEY = new Name("DecodeParms");

    public static final Name FILTER_FLATE_DECODE = new Name("FlateDecode");
    public static final Name FILTER_DCT_DECODE = new Name("DCTDecode");
    public static final Name FILTER_ASCII85_DECODE = new Name("ASCII85Decode");
    public static final Name FILTER_ASCIIHexDECODE = new Name("ASCIIHexDecode");
    public static final Name FILTER_RUN_LENGTH_DECODE = new Name("RunLengthDecode");
    public static final Name FILTER_CCITT_FAX_DECODE = new Name("CCITTFaxDecode");
    public static final Name FILTER_JBIG2_DECODE = new Name("JBIG2Decode");
    public static final Name FILTER_JPX_DECODE = new Name("JPXDecode");

    // original byte stream that has not been decoded
    protected byte[] rawBytes;
    protected byte[] decompressedBytes;

    // View into the shared, read-only document buffer holding the still-compressed raw bytes. When non-null the
    // stream is in "view mode": rawBytes is not materialized until getRawBytes() is called, avoiding a second copy
    // of bytes that already live (for the document's whole lifetime) in the document buffer. Kept pristine (its
    // position/limit are never mutated); readers duplicate() it for their own cursor. Cleared when setRawBytes()
    // switches the stream to array mode.
    protected ByteBuffer streamDataView;

    protected DictionaryEntries decodeParams;

    // default compression state for a file loaded stream,  for re-saving
    // of form data we want to avoid re-compressing the data or re-encrypting it.
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

    public Stream(Library library, DictionaryEntries dictionaryEntries, ByteBuffer streamDataView) {
        super(library, dictionaryEntries);
        decodeParams = this.library.getDictionary(entries, DECODEPARAM_KEY);
        this.streamDataView = streamDataView;
    }

    /**
     * Returns the still-compressed raw bytes of the stream. In view mode this materializes (copies) the bytes from
     * the shared document buffer on first call and caches them; the hot render path decodes via
     * {@link #getDecodedStreamBytes(int)} and never triggers this copy. Used by the cold save/sign/thumbnail paths
     * that need an actual byte[].
     */
    public byte[] getRawBytes() {
        if (rawBytes == null && streamDataView != null) {
            ByteBuffer view = streamDataView.duplicate();
            byte[] materialized = new byte[view.remaining()];
            view.get(materialized);
            rawBytes = materialized;
        }
        return rawBytes;
    }

    /**
     * Length of the still-compressed raw bytes without materializing them. Use this in preference to
     * {@code getRawBytes().length} so view-mode streams aren't forced to copy just for a length/empty check.
     */
    public int getRawBytesLength() {
        if (rawBytes != null) {
            return rawBytes.length;
        }
        if (streamDataView != null) {
            return streamDataView.remaining();
        }
        return 0;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = decompressedBytes = rawBytes;
        this.streamDataView = null;
        compressed = false;
    }

    public byte[] getDecompressedBytes() {
        return decompressedBytes;
    }

    /**
     * Releases the cached decompressed byte[] when it can be regenerated on demand. This only applies when the
     * stream is still backed by its original compressed source (compressed == true): a subsequent
     * {@link #getDecodedStreamBytes(int)} call will simply re-inflate from rawBytes. Edited streams
     * (compressed == false, set via {@link #setRawBytes(byte[])}) hold their authoritative, not-yet-recompressed
     * content in decompressedBytes and are left untouched so the edit is not lost.
     * <br>
     * Page content streams typically only need their decompressed bytes during content parsing; once the page's
     * Shapes have been built the inflated buffer is dead weight retained for the life of the (weakly reachable)
     * Page. Dropping it here trades a rare re-decode (text extraction, search, print) for lower steady-state heap.
     */
    public void disposeDecompressed() {
        if (compressed) {
            decompressedBytes = null;
        }
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
        if (decompressedBytes != null) {
            // cached decompressed stream and or we have an edited stream which isn't compressed yet, so just return
            // the raw bytes.
            return decompressedBytes;
        }
        // decompress the stream
        if (compressed) {
            try {
                // Decode straight from the document-buffer view when in view mode (no rawBytes copy); a private
                // duplicate() gives this decode its own cursor over the shared, read-only bytes.
                InputStream streamInput;
                long rawStreamLength;
                if (rawBytes == null && streamDataView != null) {
                    ByteBuffer view = streamDataView.duplicate();
                    rawStreamLength = view.remaining();
                    streamInput = new ByteBufferBackedInputStream(view);
                } else {
                    streamInput = new ByteArrayInputStream(rawBytes);
                    rawStreamLength = rawBytes.length;
                }
                InputStream input = getDecodedInputStream(streamInput, rawStreamLength);
                if (input == null) return null;
                // Size the output buffer to at least the raw (still-compressed) length: the decoded result is
                // almost always larger, so this floor skips the early grow-and-copy reallocations that otherwise
                // dominate decode allocation/GC (the default presize of 8192 would start tiny for large streams).
                int outLength = Math.max(Math.max(presize, 8192), (int) rawStreamLength);
                ConservativeSizingByteArrayOutputStream out = new ConservativeSizingByteArrayOutputStream(outLength);
                byte[] buffer = new byte[Math.min(outLength, 32 * 1024)];
                while (true) {
                    int read = input.read(buffer);
                    if (read <= 0) break;
                    out.write(buffer, 0, read);
                }
                input.close();
                out.flush();
                out.close();
                out.trim();
                decompressedBytes = out.relinquishByteArray();
                return decompressedBytes;
            } catch (IOException e) {
                logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
            }
        }
        return null;
    }

    public ByteBuffer getDecodedStreamByteBuffer() {
        return getDecodedStreamByteBuffer(8192);
    }

    public ByteBuffer getDecodedStreamByteBuffer(int presiz) {
        byte[] decodeBytes = getDecodedStreamBytes(presiz);
        return ByteBuffer.wrap(decodeBytes);
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

        int bufferSize = Math.min((int) streamLength, 32 * 1024);
        input = new java.io.BufferedInputStream(input, bufferSize);

        // Search for crypt dictionary entry and decode params so that
        // named filters can be assigned correctly.
        SecurityManager securityManager = library.getSecurityManager();
//        System.out.println("Thread " + Thread.currentThread() + " " + pObjectReference);
        if (securityManager != null) {
            // check see of there is a decodeParams for a crypt filter.
            input = securityManager.decryptInputStream(pObjectReference, securityManager.getDecryptionKey(),
                    decodeParams, input, true);
        }

        // Get the filter name for the encoding type, which can be either
        // a Name or Vector.
        List<String> filterNames = getFilterNames();
        if (filterNames == null) return input;

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
        if (filterNames == null) return null;

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
     * Creates a stream object composed of the given bytes.  Utility use to feed bytes to the content parse
     * that aren't specifically stream, but we want to parse some kind of state from the given bytes.
     *
     * @param contentBytes decompressed bytes to be treated as a stream
     * @param dictionary  parent objects to base new stream from
     * @return mock stream object
     */
    public static Stream[] fromByteArray(byte[] contentBytes, Dictionary dictionary) {
        Stream stream = new Stream(dictionary.getEntries(), null);
        stream.setRawBytes(contentBytes);
        stream.setPObjectReference(dictionary.getPObjectReference());
        return new Stream[]{stream};
    }

    /**
     * Create a stream object from the given byte array.  Stream dictionary is created with FlateDecode filter
     * if the Annotation.compressAppearanceStream flag is set to true.
     *
     * @param library    document library
     * @param streamData data to embed in stream
     * @return stream object
     */
    public static Stream createStream(Library library, byte[] streamData) {
        // load font resource from classpath
        Stream stream = new Stream(library, new DictionaryEntries(), (byte[]) null);
        stream.setRawBytes(streamData);
        // compress the form object stream.
        if (Annotation.isCompressAppearanceStream()) {
            stream.getEntries().put(Stream.FILTER_KEY, new Name("FlateDecode"));
        } else {
            stream.getEntries().remove(Stream.FILTER_KEY);
        }
        return stream;
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
