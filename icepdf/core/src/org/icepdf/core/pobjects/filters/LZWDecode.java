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
import org.icepdf.core.pobjects.Name;
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

    int currentRow = 0;

    public static final Name DECODEPARMS_KEY = new Name("DecodeParms");
    public static final Name EARLYCHANGE_KEY = new Name("EarlyChange");

    // code shall represent a single character of input data (0-255).

    // clear-table marking
    private static final int CLEAR_TABLE_MARKER = 256;

    // end of data marker
    private static final int END_OF_DATA_MARKER = 257;

    // table entry marker
    private static final int TABLE_ENTRY_MARKER = 258;

    private BitStream inb;
    private int earlyChange;
    private int code;
    private int old_code;
    private boolean firstTime;

    private int code_len;
    private int last_code;
    private Code[] codes;


    public LZWDecode(BitStream inb, Library library, HashMap entries) {
        super(library, entries);
        this.inb = inb;

        this.earlyChange = 1; // Default value
        HashMap decodeParmsDictionary = library.getDictionary(entries, DECODEPARMS_KEY);
        if (decodeParmsDictionary != null) {
            Number earlyChangeNumber = library.getNumber(decodeParmsDictionary, EARLYCHANGE_KEY);
            if (earlyChangeNumber != null) {
                this.earlyChange = earlyChangeNumber.intValue();
            }
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
    }

    protected int fillInternalBuffer() throws IOException {
        int numRead = 0;
        // start decompression,

        buffer = new byte[aboveBuffer.length + 1];

        if (firstTime) {
            firstTime = false;
            old_code = code = inb.getBits(code_len);
        } else if (inb.atEndOfFile()) {
            return -1;
        }

        do {
            // clear dictionary
            if (code == 256) {
                initCodeTable();
            }
            // stop, we're done
            else if (code == 257) {
                break;
            } else {
                // have a code already in dictionary
                if (codes[code] != null) {
                    Stack stack = new Stack();
                    codes[code].getString(stack);
                    Code c = (Code) stack.pop();
                    addToBuffer(c.c, numRead);
                    numRead++;

                    byte first = c.c;
                    while (!stack.empty() && numRead < buffer.length) {
                        c = (Code) stack.pop();
                        addToBuffer(c.c, numRead);
                        numRead++;
                    }
                    codes[last_code++] = new Code(codes[old_code], first);
                }
                // create the new code entry
                else {
                    if (code != last_code) {
                        throw new RuntimeException("LZWDecode failure");
                    }
                    Stack stack = new Stack();
                    codes[old_code].getString(stack);
                    Code c = (Code) stack.pop();
                    addToBuffer(c.c, numRead);
                    numRead++;
                    byte first = c.c;
                    while (!stack.empty() && numRead < buffer.length) {
                        c = (Code) stack.pop();
                        addToBuffer(c.c, numRead);
                        numRead++;
                    }
                    if (numRead < buffer.length) {
                        addToBuffer(first, numRead);
                        numRead++;
                    }
                    codes[code] = new Code(codes[old_code], first);
                    last_code++;
                }
            }
            if (code_len < 12 && last_code == (1 << code_len) - earlyChange) {
                code_len++;
            }
            old_code = code;
            code = inb.getBits(code_len);

            if (inb.atEndOfFile()) {
                break;
            }

        } while (numRead < (buffer.length - 512));
        currentRow++;
        /*
        if (predictor >= FLATE_PREDICTOR_PNG_NONE && predictor <= LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
            // grab the predictor type for this row.
            int currPredictor;
            int cp = (buffer[0] & 0xff);
            if (cp < 0) return -1;
            currPredictor = cp + FLATE_PREDICTOR_PNG_NONE;

            // we need to trim the buffer and remove the predictor value.
            byte[] tmp = new byte[aboveBuffer.length];
            System.arraycopy(buffer,1,tmp,0, tmp.length - 1 );
            buffer = tmp;
//
//            // apply predictor logic
            applyPredictor(numRead-1, currPredictor);

            // Swap buffers, so that aboveBuffer is what buffer just was
            aboveBuffer = buffer;

        }
        */
        return numRead - 1;
    }

    private void initCodeTable() {
        code_len = 9;
        last_code = 257;
        codes = new Code[4096];
        for (int i = 0; i < 256; i++) {
            codes[i] = new Code(null, (byte) i);
        }
    }

    private void addToBuffer(byte b, int offset) {
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
        Code prefix;
        byte c;

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
