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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.io.IOException;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class LZWDecode extends ChunkingInputStream {

    public static final Name DECODEPARMS_KEY = new Name("DecodeParms");
    public static final Name EARLYCHANGE_KEY = new Name("EarlyChange");


    private BitStream inb;
    private int earlyChange;
    private int code;
    private int old_code;
    private boolean firstTime;

    private int code_len;
    private int last_code;
    private Code[] codes;


    public LZWDecode(BitStream inb, Library library, DictionaryEntries entries) {
        this.inb = inb;

        this.earlyChange = 1; // Default value
        DictionaryEntries decodeParmsDictionary = library.getDictionary(entries, DECODEPARMS_KEY);
        if (decodeParmsDictionary != null) {
            Number earlyChangeNumber = library.getNumber(decodeParmsDictionary, EARLYCHANGE_KEY);
            if (earlyChangeNumber != null) {
                this.earlyChange = earlyChangeNumber.intValue();
            }
        }

        code = 0;
        old_code = 0;
        firstTime = true;
        initCodeTable();
        setBufferSize(32 * 1024);
    }

    protected int fillInternalBuffer() throws IOException {
        int numRead = 0;
        // start decompression,  haven't tried to optimized this one yet for
        // speed or for memory.

        if (firstTime) {
            firstTime = false;
            old_code = code = inb.getBits(code_len);
        } else if (inb.atEndOfFile())
            return -1;

        do {
            if (code == 256) {
                initCodeTable();
            } else if (code == 257) {
                break;
            } else {
                if (codes[code] != null) {
                    // code is in the table: emit its string and add a new entry
                    // (previous string + first byte of the current string).
                    Code entry = codes[code];
                    numRead += writeString(entry, numRead);
                    codes[last_code++] = new Code(codes[old_code], entry.first);
                } else {
                    // KwKwK case: code is not yet defined, it must be the next
                    // entry, whose string is the previous string plus its own
                    // first byte.
                    if (code != last_code)
                        throw new RuntimeException("LZWDecode failure");
                    Code prev = codes[old_code];
                    byte first = prev.first;
                    numRead += writeString(prev, numRead);
                    ensureCapacity(numRead + 1);
                    buffer[numRead++] = first;
                    codes[code] = new Code(codes[old_code], first);
                    last_code++;
                }
            }
            if (code_len < 12 && last_code == (1 << code_len) - earlyChange) {
                //System.err.println(last_code+" "+code_len);
                code_len++;
            }
            old_code = code;
            code = inb.getBits(code_len);

            if (inb.atEndOfFile())
                break;
        } while (numRead < buffer.length);

        return numRead;
    }

    private void initCodeTable() {
        code_len = 9;
        last_code = 257;
        codes = new Code[4096];
        for (int i = 0; i < 256; i++)
            codes[i] = new Code(null, (byte) i);
    }

    /**
     * Writes the byte string represented by {@code code} into the buffer at
     * {@code offset}, in forward order.  The string is reconstructed by walking
     * the prefix chain (which yields bytes back-to-front) and filling the buffer
     * from the end of the range towards {@code offset}, avoiding the per-code
     * stack/allocation the old reverse-then-emit approach used.
     *
     * @param code   code whose string to emit.
     * @param offset position in the buffer to write the first byte.
     * @return the number of bytes written (the code's string length).
     */
    private int writeString(Code code, int offset) {
        int len = code.length;
        ensureCapacity(offset + len);
        int p = offset + len;
        Code c = code;
        while (c != null) {
            buffer[--p] = c.c;
            c = c.prefix;
        }
        return len;
    }

    private void ensureCapacity(int needed) {
        if (needed > buffer.length) {
            int newLength = buffer.length * 2;
            while (newLength < needed) {
                newLength *= 2;
            }
            byte[] bufferNew = new byte[newLength];
            System.arraycopy(buffer, 0, bufferNew, 0, buffer.length);
            buffer = bufferNew;
        }
    }


    public void close() throws IOException {
        super.close();

        if (inb != null) {
            inb.close();
            inb = null;
        }
    }


    /**
     * Utility class for decode methods.  Each Code caches the length of its byte
     * string and its first byte so the decoder can emit and extend strings
     * without walking the prefix chain twice or allocating a stack per code.
     */
    private static class Code {
        final Code prefix;
        final byte c;
        final byte first;
        final int length;

        Code(Code p, byte cc) {
            prefix = p;
            c = cc;
            if (p == null) {
                first = cc;
                length = 1;
            } else {
                first = p.first;
                length = p.length + 1;
            }
        }
    }
}
