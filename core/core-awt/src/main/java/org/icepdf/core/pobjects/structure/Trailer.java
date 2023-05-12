package org.icepdf.core.pobjects.structure;


import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.util.ByteBufferUtil;

import java.nio.ByteBuffer;

/**
 * The trailer of a PDF file enables a conforming reader to quickly find the cross-reference table and certain special
 * objects. Conforming readers should read a PDF file from its end. The last line of the file shall contain only the
 * end-of-file marker, %%EOF. The two preceding lines shall contain, one per line and in order, the keyword startxref
 * and the byte offset in the decoded stream from the beginning of the file to the beginning of the xref keyword in
 * the last cross-reference section. The startxref line shall be preceded by the trailer dictionary, consisting of the
 * keyword trailer followed by a series of key-value pairs enclosed in double angle brackets (<<…>>)
 * (using LESS-THAN SIGNs (3Ch) and GREATER-THAN SIGNs (3Eh)). Thus, the trailer has the following overall structure:
 * <pre>
 *  trailer
 *        << key1 value1
 *           key2 value2
 *           …
 *           keyn valuen
 *         >>
 *        startxref
 * Byte_offset_of_last_cross-reference_section
 * %%EOF
 * </pre>
 */
public class Trailer {
    //                                                      %   %   E   O   F
    private static final byte[] PDF_EOF_MARKER = new byte[]{37, 37, 69, 79, 70};
    private static final byte[] PDF_EOF_MARKER_MAILFORMED = new byte[]{37, 69, 79, 70};

    //                                                              s    t    a   r    t    x    r    e    f
    private static final byte[] PDF_START_XREF_MARKER = new byte[]{115, 116, 97, 114, 116, 120, 114, 101, 102};

    private boolean usingCrossReferenceStreams;
    private boolean usingCrossReferenceHybridStream;
    private int startXref;
    private boolean lazyInitializationFailed;

    public void parseXrefOffset(ByteBuffer byteBuffer) throws CrossReferenceStateException {
        try {
            // go back 3k as we have two files that have a bunch of crud at the end but are otherwise OK.
            parseXrefOffset(byteBuffer, Math.min(byteBuffer.limit(), 1028));
        } catch (CrossReferenceStateException e) {
            int limit = byteBuffer.limit();
            // must be lots of garbage at the end of file, so lets search further into the file.
            if (limit > 32000) {
                parseXrefOffset(byteBuffer, 32000);
            } else {
                parseXrefOffset(byteBuffer, byteBuffer.limit());
            }
        }
    }

    private void parseXrefOffset(ByteBuffer byteBuffer, int bufferSize) throws CrossReferenceStateException {
        // find xref offset.
        byteBuffer.position(byteBuffer.limit() - bufferSize);
        byteBuffer.limit(byteBuffer.capacity());
        ByteBuffer footerBuffer = byteBuffer.slice();
        byteBuffer.limit(byteBuffer.capacity());

        // find end of file marker and startxref so we can parse the xref offset.
        int offsetEnd = ByteBufferUtil.findReverseString(footerBuffer, footerBuffer.limit(), PDF_EOF_MARKER);
        if (offsetEnd == footerBuffer.limit()) {
            offsetEnd = ByteBufferUtil.findReverseString(footerBuffer, footerBuffer.limit(), PDF_EOF_MARKER_MAILFORMED);
        }
        int offsetStart = ByteBufferUtil.findReverseString(footerBuffer, offsetEnd,
                PDF_START_XREF_MARKER) + PDF_START_XREF_MARKER.length;
        int length = offsetEnd - offsetStart;
        if (length <= 0) {
            throw new CrossReferenceStateException();
        }
        // set up the xref byte offset parse
        byte[] xrefOffsetBytes = new byte[length];
        footerBuffer.position(offsetStart);
        footerBuffer.get(xrefOffsetBytes);
        String value = new String(xrefOffsetBytes).trim();
        startXref = Integer.parseInt(value);
    }


    public int getStartXref() {
        return startXref;
    }

    public boolean isLazyInitializationFailed() {
        return lazyInitializationFailed;
    }

    public void setLazyInitializationFailed(boolean lazyInitializationFailed) {
        this.lazyInitializationFailed = lazyInitializationFailed;
    }

    public boolean isUsingCrossReferenceStreams() {
        return usingCrossReferenceStreams;
    }

    public boolean isUsingCrossReferenceHybridStream() {
        return usingCrossReferenceHybridStream;
    }
}