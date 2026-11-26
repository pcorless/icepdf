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

    private boolean versionMarkerFound;

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
            versionMarkerFound = true;
            version = parseVersion(headerBuffer);
        } else {
            version = 1.0;
        }

        // check for some bad bytes.  Only when the marker was found: without one the scan ran to the end of
        // the window, and slicing there would throw away the start of a headerless file.
        if (versionMarkerFound && headerBuffer.position() > PDF_VERSION_LENGTH) {
            int offset = headerBuffer.position() - PDF_VERSION_LENGTH;
            byteBuffer.position(offset);
            byteBuffer = byteBuffer.slice();
        } else {
            byteBuffer.position(0);
        }
        return byteBuffer;
    }


    /**
     * Whether the %PDF- marker was found in the first 8KB of the file.  A file without one may still be a
     * damaged PDF that reindexing can recover, so its absence alone is not a reason to refuse the file.
     *
     * @return true if the version marker was found.
     */
    public boolean isVersionMarkerFound() {
        return versionMarkerFound;
    }

    public double getVersion() {
        return version;
    }

    public String getWriterVersion() {
        return "PDF-" + version;
    }

    private static double parseVersion(ByteBuffer buffer) {
        if (buffer.remaining() < 3) {
            return 1.0;
        }
        byte[] versionBytes = new byte[3];
        buffer.get(versionBytes);
        try {
            String value = new String(versionBytes);
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // quite for now.
        }
        return 1.0;
    }

}