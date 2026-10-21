/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.graphics.images;

import java.util.Arrays;

/**
 * Finds the JPEG 2000 codestream inside a JP2 file's {@code jp2c} box.
 * <p>
 * A PDF's image dictionary says how to colour a JPXDecode image, and its /ColorSpace replaces the colour boxes of
 * the JP2 wrapper (colr, pclr, cmap, cdef).  Decoding the bare codestream hands back the samples untouched: the
 * palette indices of an /Indexed image, and the image itself when a broken wrapper box fails the whole read.
 */
final class Jp2Codestream {

    private static final int SOC = 0xFF4F;

    private Jp2Codestream() {
    }

    /**
     * @param data a JP2 file or a raw JPEG 2000 codestream
     * @return the codestream inside a JP2 file's {@code jp2c} box, or null when {@code data} is not a JP2 file
     */
    static byte[] unwrap(byte[] data) {
        if (data == null) {
            return null;
        }
        int start = codestreamStart(data);
        if (start <= 0) {
            return null;
        }
        // the jp2c box header sits just before the codestream; length 0 runs to the end of the data
        int header = data[start - 4] == 'j' && data[start - 1] == 'c' ? 8 : 16;
        long length = header == 16
                ? (u32(data, start - 8) << 32) | u32(data, start - 4)
                : u32(data, start - 8);
        long end = length == 0 ? data.length : Math.min(data.length, (long) start - header + length);
        return Arrays.copyOfRange(data, start, (int) end);
    }

    /**
     * @return offset of the SOC marker: 0 for a raw codestream, the payload of the {@code jp2c} box for a JP2 file,
     * or -1 if neither is recognised.
     */
    private static int codestreamStart(byte[] data) {
        if (data.length >= 2 && u16(data, 0) == SOC) {
            return 0;
        }
        // JP2 signature box: length 12, type 'jP  '
        if (data.length < 12 || u32(data, 0) != 12 || data[4] != 'j' || data[5] != 'P') {
            return -1;
        }
        int pos = 0;
        while (pos + 8 <= data.length) {
            long length = u32(data, pos);
            int header = 8;
            if (length == 1) {
                if (pos + 16 > data.length) {
                    return -1;
                }
                length = (u32(data, pos + 8) << 32) | u32(data, pos + 12);
                header = 16;
            } else if (length == 0) {
                length = data.length - pos;
            }
            if (data[pos + 4] == 'j' && data[pos + 5] == 'p' && data[pos + 6] == '2' && data[pos + 7] == 'c') {
                int codestream = pos + header;
                return codestream + 2 <= data.length && u16(data, codestream) == SOC ? codestream : -1;
            }
            if (length < header || pos + length > data.length) {
                return -1;
            }
            pos += (int) length;
        }
        return -1;
    }

    private static int u16(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
    }

    private static long u32(byte[] data, int pos) {
        return ((long) (data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }
}
