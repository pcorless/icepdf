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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Jp2CodestreamTest {

    @DisplayName("the codestream comes out of a JP2 file's jp2c box, whatever form its length takes")
    @Test
    public void codestreamIsUnwrapped() throws IOException {
        byte[] codestream = codestream();
        assertArrayEquals(codestream, Jp2Codestream.unwrap(jp2(codestream, 8 + codestream.length, false)));
        assertArrayEquals(codestream, Jp2Codestream.unwrap(jp2(codestream, 0, false)), "length 0: to the end");
        assertArrayEquals(codestream, Jp2Codestream.unwrap(jp2(codestream, 16 + codestream.length, true)),
                "XLBox length");
    }

    @DisplayName("a raw codestream, or data that isn't JPEG 2000, has nothing to unwrap")
    @Test
    public void nothingToUnwrap() throws IOException {
        assertNull(Jp2Codestream.unwrap(codestream()));
        assertNull(Jp2Codestream.unwrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}));
        assertNull(Jp2Codestream.unwrap(null));
        // a jp2c box that doesn't hold a codestream
        byte[] notCodestream = jp2(new byte[]{0, 0, 0, 0}, 0, false);
        assertNull(Jp2Codestream.unwrap(notCodestream));
    }

    /** SOC, a SIZ-like main-header segment, one tile-part and EOC: enough to be recognised. */
    private static byte[] codestream() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0xFF4F);                 // SOC
        out.writeShort(0xFF51);                 // SIZ (contents irrelevant here)
        out.writeShort(10);
        out.write(new byte[8]);
        out.writeShort(0xFF90);                 // SOT
        out.writeShort(10);
        out.writeShort(0);
        out.writeInt(12 + 2 + 5);
        out.writeByte(0);
        out.writeByte(1);
        out.writeShort(0xFF93);                 // SOD
        out.write(new byte[5]);
        out.writeShort(0xFFD9);                 // EOC
        return bytes.toByteArray();
    }

    /** Signature and ftyp boxes, then a jp2c box declaring {@code boxLength} (8-byte header, or 16 with XLBox). */
    private static byte[] jp2(byte[] codestream, long boxLength, boolean xl) throws IOException {
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
        out.writeInt(xl ? 1 : (int) boxLength);
        out.writeBytes("jp2c");
        if (xl) {
            out.writeLong(boxLength);
        }
        out.write(codestream);
        return bytes.toByteArray();
    }
}
