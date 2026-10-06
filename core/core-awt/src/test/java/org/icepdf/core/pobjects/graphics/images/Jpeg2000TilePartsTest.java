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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An encoder that writes TNsot one short (tile-parts 0..5 each claiming five) makes the JAI reader drop the last
 * tile-part, which turned a lossless palette logo into speckle (selection3.pdf).  These pin the repair on synthetic
 * codestreams: the count is corrected, a consistent stream is left alone, and anything unwalkable is untouched.
 */
public class Jpeg2000TilePartsTest {

    @DisplayName("under-counted TNsot is corrected for every tile-part, in a raw codestream")
    @Test
    public void underCountedTilePartsAreCorrected() throws IOException {
        byte[] stream = codestream(2, 6, 5);
        byte[] repaired = Jpeg2000TileParts.repairTilePartCounts(stream);
        assertNotSame(stream, repaired, "should repair on a copy");
        assertArrayEquals(new int[]{6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6}, declaredCounts(repaired));
        assertArrayEquals(new int[]{5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5}, declaredCounts(stream),
                "the original data must not be modified");
    }

    @DisplayName("the codestream inside a JP2 file is repaired too")
    @Test
    public void jp2WrappedCodestreamIsCorrected() throws IOException {
        byte[] codestream = codestream(1, 3, 2);
        byte[] jp2 = jp2(codestream);
        byte[] repaired = Jpeg2000TileParts.repairTilePartCounts(jp2);
        assertNotSame(jp2, repaired);
        int offset = jp2.length - codestream.length;
        byte[] inner = new byte[codestream.length];
        System.arraycopy(repaired, offset, inner, 0, inner.length);
        assertArrayEquals(new int[]{3, 3, 3}, declaredCounts(inner));
    }

    @DisplayName("a consistent stream, or one with TNsot 0 (unspecified), is returned as-is")
    @Test
    public void consistentStreamIsUntouched() throws IOException {
        byte[] consistent = codestream(2, 3, 3);
        assertSame(consistent, Jpeg2000TileParts.repairTilePartCounts(consistent));
        byte[] unspecified = codestream(2, 3, 0);
        assertSame(unspecified, Jpeg2000TileParts.repairTilePartCounts(unspecified));
    }

    @DisplayName("data that isn't a walkable codestream is returned as-is")
    @Test
    public void unrecognisedDataIsUntouched() throws IOException {
        byte[] garbage = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
        assertSame(garbage, Jpeg2000TileParts.repairTilePartCounts(garbage));
        assertNull(Jpeg2000TileParts.repairTilePartCounts(null));
        // a tile-part length that runs past the end of the data
        byte[] truncated = codestream(1, 3, 2);
        byte[] cut = new byte[truncated.length - 20];
        System.arraycopy(truncated, 0, cut, 0, cut.length);
        assertSame(cut, Jpeg2000TileParts.repairTilePartCounts(cut));
    }

    /**
     * SOC, a SIZ-like main-header segment, then {@code partsPerTile} tile-parts for each tile interleaved
     * tile-by-tile per part index (as progressive encoders emit them), each declaring {@code declaredTotal}, then EOC.
     */
    private static byte[] codestream(int tiles, int partsPerTile, int declaredTotal) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0xFF4F);                 // SOC
        out.writeShort(0xFF51);                 // SIZ (contents irrelevant to the repair)
        out.writeShort(10);
        out.write(new byte[8]);
        for (int part = 0; part < partsPerTile; part++) {
            for (int tile = 0; tile < tiles; tile++) {
                int payload = 5 + part;
                out.writeShort(0xFF90);         // SOT
                out.writeShort(10);             // Lsot
                out.writeShort(tile);           // Isot
                out.writeInt(12 + 2 + payload); // Psot: SOT segment + SOD + data
                out.writeByte(part);            // TPsot
                out.writeByte(declaredTotal);   // TNsot
                out.writeShort(0xFF93);         // SOD
                out.write(new byte[payload]);
            }
        }
        out.writeShort(0xFFD9);                 // EOC
        return bytes.toByteArray();
    }

    private static byte[] jp2(byte[] codestream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(12);
        out.writeBytes("jP  ");
        out.writeInt(0x0D0A870A);
        out.writeInt(20);
        out.writeBytes("ftyp");
        out.writeBytes("jp2 ");
        out.writeInt(0);
        out.writeBytes("jp2 ");
        out.writeInt(8 + codestream.length);
        out.writeBytes("jp2c");
        out.write(codestream);
        return bytes.toByteArray();
    }

    /** TNsot of each tile-part, in stream order. */
    private static int[] declaredCounts(byte[] codestream) {
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        for (int i = 0; i + 12 <= codestream.length; i++) {
            if ((codestream[i] & 0xFF) == 0xFF && (codestream[i + 1] & 0xFF) == 0x90) {
                counts.add(codestream[i + 11] & 0xFF);
            }
        }
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }
}
