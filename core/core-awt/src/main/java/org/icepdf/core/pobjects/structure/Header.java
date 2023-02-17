package org.icepdf.core.pobjects.structure;

import java.nio.ByteBuffer;

/**
 * Attempts to clean an garbage bites before hitting the version comment.  Position of map is set
 * to start of the %PDF- any garbage bytes before.
 * <p>
 * The first line of a PDF file shall be a header consisting of the 5 characters %PDF– followed by a version number of
 * the form 1.N, where N is a digit between 0 and 7.
 */
public class Header {

    //                                                          %   P   D   F   -
    private static final byte[] PDF_VERSION_MARKER = new byte[]{37, 80, 68, 70, 45};
    private static final int PDF_VERSION_LENGTH = 8;

    // todo add parsing module.
    private boolean isLinearized;

    private double version;

    public ByteBuffer parseHeader(ByteBuffer byteBuffer) {
        byteBuffer.limit(Math.min(byteBuffer.limit(), 8 * 1024));
        ByteBuffer headerBuffer = byteBuffer.slice();
        byteBuffer.limit(byteBuffer.capacity());

        int matchPosition = 0;
        int matchLength = PDF_VERSION_MARKER.length - 1;
        while (headerBuffer.hasRemaining()) {
            if (headerBuffer.get() == PDF_VERSION_MARKER[matchPosition]) {
                if (matchPosition == matchLength) {
                    break;
                }
                matchPosition++;
            } else {
                matchPosition = 0;
            }
        }
        // check if we found the version flags.
        if (matchPosition == matchLength) {
            version = parseVersion(headerBuffer);
        } else {
            version = 0;
        }

        // check for some bad bytes
        if (headerBuffer.position() > PDF_VERSION_LENGTH) {
            int offset = headerBuffer.position() - PDF_VERSION_LENGTH;
            byteBuffer.position(offset);
            byteBuffer = byteBuffer.slice();
        } else {
            byteBuffer.position(0);
        }
        return byteBuffer;
    }


    public double getVersion() {
        return version;
    }

    private static double parseVersion(ByteBuffer buffer) {
        byte[] versionBytes = new byte[3];
        buffer.get(versionBytes);
        try {
            String value = new String(versionBytes);
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // quite for now.
        }
        return 0;
    }

}