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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class RunLengthDecode extends ChunkingInputStream {

    // set once the end-of-data marker is seen, so a later chunk does not resume past it
    private boolean eof = false;

    public RunLengthDecode(InputStream input) {
        super();

        setInputStream(input);
        setBufferSize(32 * 1024);
    }

    protected int fillInternalBuffer() throws IOException {
        if (eof) {
            return -1;
        }
        int numRead = 0;

        while (numRead < (buffer.length - 260)) { // && i != 128) {
            int i = in.read();
            if (i < 0)
                break;
            if (i == 128) {
                // 128 is the end-of-data marker (7.4.5).  Falling through to the run branch reads
                // it as a repeat of 129 copies, and the byte it then repeats is the -1 of a spent
                // stream, so every run-length stream ended with 129 bytes of 0xFF.
                eof = true;
                break;
            }
            if (i < 128) {
                numRead += fillBufferFromInputStream(numRead, i + 1);
            } else {
                int count = (257 - i);
                int j = in.read();
                byte jj = (byte) (j & 0xFF);
                for (int k = 0; k < count; k++) {
                    buffer[numRead++] = jj;
                }
            }
        }

        if (numRead == 0)
            return -1;
        return numRead;
    }
}
