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

import java.util.HashMap;
import java.util.Map;

/**
 * Repairs a JPEG 2000 codestream whose tile-part headers under-count the tile's tile-parts.
 * <p>
 * Each tile-part header (SOT) carries {@code TNsot}, the total number of tile-parts its tile has (0 when the encoder
 * didn't say).  Some encoders write one less than they emit: every tile has tile-parts 0..5 but each header claims
 * five.  The JAI/JJ2000 reader trusts the count and stops a part early, so the last tile-part - which holds the
 * final refinement passes, often most of the data - is never read.  A lossless image then decodes approximately;
 * for a palette image the slightly-off indices land on unrelated palette entries and the result is speckle.
 * OpenJPEG in strict mode rejects such a stream; lenient decoders (Ghostscript, poppler) read every tile-part.
 * <p>
 * The repair walks the tile-parts by their own {@code Psot} lengths, counts them per tile and rewrites any non-zero
 * {@code TNsot} that disagrees.  Anything it can't walk cleanly is returned untouched.
 */
final class Jpeg2000TileParts {

    private static final int SOC = 0xFF4F;
    private static final int SOT = 0xFF90;
    private static final int EOC = 0xFFD9;

    private Jpeg2000TileParts() {
    }

    /**
     * @param data a JP2 file or a raw JPEG 2000 codestream
     * @return {@code data} itself when no repair is needed, otherwise a corrected copy
     */
    static byte[] repairTilePartCounts(byte[] data) {
        if (data == null) {
            return null;
        }
        int start = codestreamStart(data);
        if (start < 0) {
            return data;
        }
        // skip the main header: marker segments after SOC up to the first SOT
        int pos = start + 2;
        while (pos + 4 <= data.length && marker(data, pos) != SOT) {
            int marker = marker(data, pos);
            if ((marker & 0xFF00) != 0xFF00) {
                return data;
            }
            pos += 2 + u16(data, pos + 2);
        }
        // walk the tile-parts by their own lengths
        int[] sotPositions = new int[64];
        int sotCount = 0;
        Map<Integer, Integer> partsPerTile = new HashMap<>();
        while (pos + 12 <= data.length && marker(data, pos) == SOT) {
            int tile = u16(data, pos + 4);
            long psot = u32(data, pos + 6);
            if (sotCount == sotPositions.length) {
                int[] grown = new int[sotPositions.length * 2];
                System.arraycopy(sotPositions, 0, grown, 0, sotCount);
                sotPositions = grown;
            }
            sotPositions[sotCount++] = pos;
            partsPerTile.merge(tile, 1, Integer::sum);
            if (psot == 0) {
                break;  // last tile-part, runs to EOC
            }
            if (psot < 14 || pos + psot > data.length) {
                return data;
            }
            pos += (int) psot;
            if (pos + 2 <= data.length && marker(data, pos) == EOC) {
                break;
            }
        }
        if (sotCount == 0) {
            return data;
        }
        byte[] repaired = null;
        for (int i = 0; i < sotCount; i++) {
            int sot = sotPositions[i];
            int declared = data[sot + 11] & 0xFF;
            int actual = partsPerTile.get(u16(data, sot + 4));
            if (declared != 0 && declared != actual && actual <= 255) {
                if (repaired == null) {
                    repaired = data.clone();
                }
                repaired[sot + 11] = (byte) actual;
            }
        }
        return repaired != null ? repaired : data;
    }

    /**
     * @return offset of the SOC marker: 0 for a raw codestream, the payload of the {@code jp2c} box for a JP2 file,
     * or -1 if neither is recognised.
     */
    private static int codestreamStart(byte[] data) {
        if (data.length >= 2 && marker(data, 0) == SOC) {
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
                return codestream + 2 <= data.length && marker(data, codestream) == SOC ? codestream : -1;
            }
            if (length < header || pos + length > data.length) {
                return -1;
            }
            pos += (int) length;
        }
        return -1;
    }

    private static int marker(byte[] data, int pos) {
        return u16(data, pos);
    }

    private static int u16(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
    }

    private static long u32(byte[] data, int pos) {
        return ((long) (data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }
}
