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
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Name;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NameWriter extends BaseWriter {

    private static final byte[] NAME = "/".getBytes();

    private static final int POUND = 0x23;

    private static final byte[] HEX_DIGITS = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    /**
     * Writes a name as {@code /} followed by its characters, hex escaping everything that is not a
     * regular character (7.3.5): whitespace, the delimiters, {@code #} itself, and anything outside
     * printable ASCII.  A delimiter written raw ends the name token early for the next reader, which
     * turns one name into a name plus whatever the rest of it parsed as.
     *
     * @param writeable name to write
     * @param output    stream to write to
     * @throws IOException if the stream cannot be written to
     */
    public void write(Name writeable, CountingOutputStream output) throws IOException {
        output.write(NAME);
        byte[] bytes = writeable.getName().getBytes(StandardCharsets.UTF_8);
        for (int b : bytes) {
            b &= 0xFF;
            if (isRegularCharacter(b)) {
                output.write(b);
            } else {
                output.write(POUND);
                output.write(HEX_DIGITS[(b >> 4) & 0x0F]);
                output.write(HEX_DIGITS[b & 0x0F]);
            }
        }
    }

    /**
     * @param b byte to test
     * @return true when {@code b} may appear in a name unescaped
     */
    private static boolean isRegularCharacter(int b) {
        if (b < 0x21 || b > 0x7E || b == POUND) {
            return false;
        }
        switch (b) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '[':
            case ']':
            case '{':
            case '}':
            case '/':
            case '%':
                return false;
            default:
                return true;
        }
    }
}
