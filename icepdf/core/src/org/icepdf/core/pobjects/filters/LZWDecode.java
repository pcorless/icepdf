/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Stack;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class LZWDecode extends LZWFlateBaseDecode {

    // code shall represent a single character of input data (0-255).

    // clear-table marking
    private static final int CLEAR_TABLE_MARKER = 256;

    // end of data marker
    private static final int END_OF_DATA_MARKER = 257;

    // table entry marker
    private static final int TABLE_ENTRY_MARKER = 258;

    private BitStream inb;
    /**
     * An indication of when to increase the code length. If the value of this entry is 0,
     * code length increases shall be postponed as long as possible. If the value is 1,
     * code length increases shall occur one code early. This parameter is included because
     * LZW sample code distributed by some vendors increases the code length one code earlier
     * than necessary. Default value: 1.
     */
    private int earlyChange;

    private int code;
    private int old_code;
    private boolean firstTime;

    // codes range from 9 to 12 bytes.
    private int code_len;
    private int last_code;
    private Code[] codes;


    public LZWDecode(BitStream inb, Library library, HashMap entries) {
        super(library, entries);

        this.inb = inb;
        this.earlyChange = 1; // Default value
        // look for earlyChange in teh decode params first.
        HashMap decodeParmsDictionary = library.getDictionary(entries, DECODE_PARMS_VALUE);
        if (decodeParmsDictionary != null) {
            Number earlyChangeNumber = library.getNumber(decodeParmsDictionary, EARLY_CHANGE_VALUE);
            if (earlyChangeNumber != null) {
                this.earlyChange = earlyChangeNumber.intValue();
            }
        }
        // double check early change isn't in the base dictionary.
        Number earlyChangeNumber = library.getNumber(decodeParmsDictionary, EARLY_CHANGE_VALUE);
        if (earlyChangeNumber != null) {
            this.earlyChange = earlyChangeNumber.intValue();
        }

        // Make buffer exactly large enough for one row of data (without predictor)
        int intermediateBufferSize = DEFAULT_BUFFER_SIZE;
        if (predictor != FLATE_PREDICTOR_NONE) {
            intermediateBufferSize =
                    Utils.numBytesToHoldBits(width * numComponents * bitsPerComponent);
        }
        aboveBuffer = new byte[intermediateBufferSize];
        setBufferSize(intermediateBufferSize);

        code = 0;
        old_code = 0;
        firstTime = true;
        initCodeTable();
        setBufferSize(4096);
    }

    protected int fillInternalBuffer() throws IOException {

        int numRead = 0;
        // start decompression,  haven't tried to optimized this one yet for
        // speed or for memory.
        if (firstTime) {
            firstTime = false;
            old_code = code = inb.getBits(code_len);
        } else if (inb.atEndOfFile()) {
            return -1;
        }

        // Swap buffers, so that aboveBuffer is what buffer just was
        byte[] temp = aboveBuffer;
        aboveBuffer = buffer;
        buffer = temp;

        Stack stack = new Stack();
        Code c;
        do {
            if (code == CLEAR_TABLE_MARKER) {
                initCodeTable();
            } else if (code == END_OF_DATA_MARKER) {
                break;
            } else {
                if (codes[code] != null) {
                    stack.clear();
                    codes[code].getString(stack);
                    c = (Code) stack.pop();
                    addToBuffer(c.c, numRead);
                    numRead++;
                    byte first = c.c;
                    while (!stack.empty()) {
                        c = (Code) stack.pop();
                        addToBuffer(c.c, numRead);
                        numRead++;
                    }
                    // while (codes[last_code]!=null) last_code++;
                    codes[last_code++] = new Code(codes[old_code], first);
                } else {
                    if (code != last_code)
                        throw new RuntimeException("LZWDecode failure");
                    stack.clear();
                    codes[old_code].getString(stack);
                    c = (Code) stack.pop();
                    addToBuffer(c.c, numRead);
                    numRead++;
                    byte first = c.c;
                    while (!stack.empty()) {
                        c = (Code) stack.pop();
                        addToBuffer(c.c, numRead);
                        numRead++;
                    }
                    addToBuffer(first, numRead);
                    numRead++;
                    codes[code] = new Code(codes[old_code], first);
                    last_code++;
                }
            }
            if (code_len < 12 && last_code == (1 << code_len) - earlyChange) {
                code_len++;
            }
            old_code = code;
            code = inb.getBits(code_len);

            if (inb.atEndOfFile())
                break;
        } while (numRead < (buffer.length - 512));

        // buffer is complete so we can no try to apply the predictor value.
        if (predictor >= FLATE_PREDICTOR_PNG_NONE && predictor <= LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
//            int currPredictor;
//            int cp = in.read();
//            if (cp < 0) return -1;
//            currPredictor = cp + FLATE_PREDICTOR_PNG_NONE;
            int currPredictor = predictor;

            // apply predictor logic
//            if (numRead < (buffer.length - 512))
//                applyPredictor(numRead, currPredictor);
        }

        return numRead;
    }

    private void initCodeTable() {
        code_len = 9;
        last_code = END_OF_DATA_MARKER;
        // The first output code that is 10 bits long shall be the one following
        // the creation of table entry 511, and similarly for 11 (1023) and 12 (2047) bits.
        // Codes shall never be longer than 12 bits; therefore, entry 4095 is
        // the last entry of the LZW table.
        codes = new Code[4096];
        for (int i = 0; i < CLEAR_TABLE_MARKER; i++) {
            codes[i] = new Code(null, (byte) i);
        }
    }

    private void addToBuffer(byte b, int offset) {
        if (offset >= buffer.length) { // Should never happen
            byte[] bufferNew = new byte[buffer.length * 2];
            System.arraycopy(buffer, 0, bufferNew, 0, buffer.length);
            buffer = bufferNew;
//            System.out.println();
        }
        buffer[offset] = b;
    }

    public void close() throws IOException {
        super.close();
        if (inb != null) {
            inb.close();
            inb = null;
        }
    }

    /**
     * Utility class for decode methods.
     */
    private static class Code {
        private Code prefix;
        private byte c;

        Code(Code p, byte cc) {
            prefix = p;
            c = cc;
        }

        void getString(Stack s) {
            s.push(this);
            if (prefix != null) {
                prefix.getString(s);
            }
        }
    }
}
