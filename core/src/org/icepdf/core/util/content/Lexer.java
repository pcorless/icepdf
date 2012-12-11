/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util.content;

import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A state machine used to parse valid PDF content stream tokens in a input
 * stream.  As the tokens are parsed found operands are executed to manipulate
 * the stack.
 *
 * @author ICEsoft Technologies Inc.
 * @since 5.0
 */
public class Lexer {

    private static final Logger logger =
            Logger.getLogger(Lexer.class.toString());

    private int streamCount;
    private byte[][] streamsBytes;

    // stream reader pointers.
    private byte[] streamBytes;

    // stream pointers
    private int pos, numRead, startTokenPos;

    // lexer hinter
    private int tokenType = 0;

    // lexer states
    private static final int
            TOKEN_NO_MORE = 0,
            TOKEN_NUMBER = 1,
            TOKEN_OPERAND = 2,
            TOKEN_LIT_STRING = 3,
            TOKEN_HEX_STRING = 4,
            TOKEN_NAME = 5,
    //            TOKEN_COMMENT = 6,
    TOKEN_DICTIONARY = 7,
            TOKEN_ARRAY = 8,
            TOKEN_BOOLEAN = 9;

    /**
     * @param in content input stream.
     */
    public void contentStream(byte[][] in) throws UnsupportedEncodingException {
        streamsBytes = in;
        streamCount = 0;
        streamBytes = streamsBytes[streamCount];
        if (streamBytes != null) {
            numRead = streamBytes.length;
        }
    }


    /**
     * Phase 1: parse the stream one token at at time and use the existing
     * content parser logic to work though the operands
     * <p/>
     * Phase 2: cut in the command pattern where the stack self executes
     * as the operands are encountered.  Avoids having to iterate over all
     * the operands given that we already know what operand to execute.
     * <p/>
     * Phase 3: cut in the image proxy fo inline images and xform image
     * processing.
     */
    public Object nextToken() throws IOException {

        if (streamBytes == null) {
            throw new IOException("Content Stream, null input stream bytes.");
        }

        // get starting lexer state.
        parseNextState();

        switch (tokenType) {
            // we have a name
            case TOKEN_NUMBER:
                return startNumber();
            case TOKEN_OPERAND:
                return startOperand();
            case TOKEN_HEX_STRING:
                return startHexString();
            case TOKEN_LIT_STRING:
                return startLiteralString();
            case TOKEN_NAME:
                return startName();
            case TOKEN_ARRAY:
                return startArray();
            case TOKEN_DICTIONARY:
                return startDictionary();
            case TOKEN_BOOLEAN:
                return startBoolean();
            default:
                return null;
        }
    }

    public byte[] getImageBytes() {
        // skip past the D in ID and the first white space.
        pos += 2;
        startTokenPos = pos;
        int end = 0;
        // type3 fonts generally have the EI at the end of the stream with no
        // white space  and inline images all followed by a space.
        boolean found = false;
        while (pos < numRead) {
            // check if we have an EI at the end of the stream
            if (pos + 1 == streamBytes.length - 1 &&
                    streamBytes[pos] == 'E' &&
                    streamBytes[pos + 1] == 'I') {
                found = true;
            }
            // check for traditional EI and more content to come.
            else if (streamBytes[pos] == 'E' &&
                    streamBytes[pos + 1] == 'I' &&
                    (streamBytes[pos + 2] == 32 ||
                            streamBytes[pos + 2] == 10 ||
                            streamBytes[pos + 2] == 12 ||
                            streamBytes[pos + 2] == 13)) {
                found = true;
            }
            if (found) {
                // remove the space before EI
                end = pos - 1;
                pos += 2;
                break;
            }
            end = pos;
            pos++;
        }
        int length = end - startTokenPos;
        byte[] imageBytes = new byte[length];
        System.arraycopy(streamBytes, startTokenPos, imageBytes, 0, length);
        return imageBytes;
    }


    /**
     * Utility for parsing a hex strings.
     */
    private StringObject startHexString() throws IOException {
        // skip the starting (
        startTokenPos = pos++;
        while (pos < numRead) {
            if (streamBytes[pos] == '>') {
                // back out the ending )
                pos++;
                break;
            }
            pos++;
        }
        if (pos < numRead) {
            // return the name object
            return new HexStringObject(new String(
                    streamBytes, startTokenPos + 1, pos - startTokenPos - 2));
        } else {
            return null;
        }
    }

    /**
     * Utility for parsing a lit strings.
     */
    private StringObject startLiteralString() throws IOException {
        // skip the starting (
        startTokenPos = pos;

        // build out the string, NOTE: not checking for embedded Strings
        StringBuilder captured = new StringBuilder();
        byte lookAhead;
        // skip past the starting (
        pos++;
        int parenthesisCount = 1;
        int current;
        while (pos < numRead) {
            current = streamBytes[pos];
            if (current != '\\' && current != ')' && current != '(') {
                captured.append((char) (streamBytes[pos] & 0xff));
                pos++;
            } else if (current != '\\' && current == ')' && parenthesisCount > 1) {
                captured.append((char) (streamBytes[pos] & 0xff));
                pos++;
                parenthesisCount--;
            } else if (current != '\\' && current == '(') {
                captured.append((char) (streamBytes[pos] & 0xff));
                pos++;
                parenthesisCount++;
            } else if (current == ')') {
                parenthesisCount--;
                if (parenthesisCount > 0) {
                    captured.append((char) (streamBytes[pos] & 0xff));
                    pos++;
                } else {
                    pos++;
                    break;
                }
            } else {
                /**
                 * The escape sequences can be as follows:
                 *   \n  - line feed (LF)
                 *   \r  - Carriage return (CR)
                 *   \t  - Horizontal tab  (HT)
                 *   \b  - backspace (BS)
                 *   \f  - form feed (FF)
                 *   \(  - left parenthesis
                 *   \)  - right parenthesis
                 *   \\  - backslash
                 *   \ddd - character code ddd (octal)
                 *
                 * Note: (\0053) denotes a string containing two characters,
                 */
                lookAhead = streamBytes[pos + 1];
                // capture the horizontal tab (HT), tab character is hard
                // to find, only appears in files with font substitution and
                // as a result we ahve better luck drawing a space character.
                if (lookAhead == 't') {
                    captured.append('\t');
                    pos += 2;
                }
                // capture the line feed (LF)
                else if (lookAhead == 'n') {
                    captured.append('\n');
                    pos += 2;
                } else if (lookAhead == ')') {
                    captured.append(')');
                    pos += 2;
                } else if (lookAhead == '(') {
                    captured.append('(');
                    pos += 2;
                } else if (lookAhead == 13) {
                    pos += 2;
                } else if (lookAhead == 10) {
                    pos += 2;
                }
                // process the octal number.
                else if (Character.isDigit(lookAhead)) {
                    byte[] digit = new byte[3];
                    digit[0] = (lookAhead);
                    // octals have a max size of 3 digits, we already
                    // have one, so there can be up 2 more digits.
                    int offset = 1;
                    for (int j = 1; j <= 2; j++) {
                        lookAhead = streamBytes[pos + j + 1];
                        if (Character.isDigit(lookAhead)) {
                            digit[j] = lookAhead;
                            offset++;
                        }
                    }
                    // push i to match the octal offset
                    pos += offset + 1;

                    // get the octal number
                    int charNumber = 0;
                    try {
                        charNumber = Integer.parseInt(new String(digit, 0, offset), 8);
                    } catch (NumberFormatException e) {
                        logger.log(Level.FINE, "Integer parse error ", e);
                    }
                    // store the octacle number;
                    captured.append((char) charNumber);
                }
                // capture the carriage return (CR)
                else if (lookAhead == 'r') {
                    captured.append('\r');
                    pos += 2;
                }
                // capture the backspace (BS)
                else if (lookAhead == 'b') {
                    captured.append('\b');
                    pos += 2;
                }
                // capture the form feed (FF)
                else if (lookAhead == 'f') {
                    captured.append('\f');
                    pos += 2;
                }
                // capture the form feed (FF)
                else if (lookAhead == '\\') {
                    captured.append('\\');
                    pos += 2;
                }
            }
        }
        return new LiteralStringObject(captured, true);
    }

    /**
     * Utility for parsing a name.
     */
    private Name startName() throws IOException {
        // skip first / of name
        startTokenPos = pos++;
        while (pos < numRead) {
            // look for a natural break                          ``
            if (isDelimiter(streamBytes[pos]) || isTextDelimiter(streamBytes[pos])) {
                break;
            }
            pos++;
        }
        if (pos <= numRead) {
            // return the name object
            startTokenPos++;
            return new Name(new String(streamBytes, startTokenPos, pos - startTokenPos));
        } else {
            return null;
        }
    }

    private Boolean startBoolean() throws IOException {
        startTokenPos = pos;
        while (pos < numRead) {
            // look for a natural break
            if (isDelimiter(streamBytes[pos]) || isTextDelimiter(streamBytes[pos])) {
                break;
            }
            pos++;
        }
        if (pos <= numRead) {
            // return the name object
            return Boolean.parseBoolean(new String(streamBytes, startTokenPos, pos - startTokenPos));
        } else {
            return null;
        }
    }


    private HashMap startDictionary() throws IOException {
        startTokenPos = pos;

        HashMap<Object, Object> h = new HashMap<Object, Object>();

        // skip past the starting <<
        pos += 2;
        // check for in very odd  corner cases. end
        checkLength();

        Object key = null;
        Object value;
        int count = 1;
        while (!(streamBytes[pos] == '>' && streamBytes[pos + 1] == '>')) {
            if (count == 1) {
                key = nextToken();
                count++;
            } else if (count == 2) {
                value = nextToken();
                h.put(key, value);
                count = 1;
            }

            // check the buffer
            while (pos < numRead) {
                // look for a natural break
                if (!isDelimiter(streamBytes[pos])) {
                    break;
                }
                pos++;
            }
            // check for in very odd  corner cases. end
            checkLength();

        }
        // skip the trailing ]
        pos += 2;
        return h;
    }

    private void checkLength() {
        if (pos == numRead) {
            if (streamCount < streamsBytes.length - 1) {
                streamCount++;
                // assign next byte array, but skip over the corner
                // case of an zero length content stream.
                if (streamsBytes[streamCount].length == 0 &&
                        streamCount + 1 < streamsBytes.length) {
                    streamCount++;
                }
                streamBytes = streamsBytes[streamCount];
                // reset the  pointers.
                pos = 0;
                numRead = streamBytes.length;
            }
        }
    }

    private List startArray() throws IOException {
        startTokenPos = pos;

        List<Object> array = new ArrayList<Object>();

        // skip past the starting [
        pos += 1;
        Object token;
        // check for in very odd  corner cases. end
        checkLength();
        while (streamBytes[pos] != ']') {
            // add the tokens as we get them.
            token = nextToken();
            if (token instanceof Integer) {
                // we gone to var, likely empty array
                pos = startTokenPos;
            } else {
                array.add(token);
            }
            // push past any white space
            while (pos < numRead) {
                // look for a natural break
                if (!isDelimiter(streamBytes[pos])) {
                    break;
                }
                pos++;
            }
        }
        // skip the trailing ]
        pos++;
        return array;
    }

    /**
     * Utility of processing a number state.
     */
    private Object startNumber() throws IOException {
        startTokenPos = pos;
        while (pos < numRead) {
            // check for white space or < or ( string start in an Array
//            if (isDelimiter(streamBytes[pos]) || isTextDelimiter(streamBytes[pos])) { //buf[pos] == 40 ||buf[pos] == 60 || buf[pos] > 57) {
            if (streamBytes[pos] < '-' || streamBytes[pos] > '9') {
                break;
            }
            pos++;
        }
        // we can catch an exception and try again, moving the pos back until
        // we cn parse the number.
        return parseNumber();
    }

    /**
     * Utility for processing the operand state.
     */
    private Object startOperand() throws IOException {
        startTokenPos = pos;
        while (pos < numRead) {
            if (isDelimiter(streamBytes[pos])) {
                break;
            }
            pos++;
        }
        if (pos <= numRead && pos > startTokenPos) {
            int[] tmp = OperatorFactory.getOperator(streamBytes, startTokenPos, pos - startTokenPos);
            // adjust for any potential parsing compensation.
            if (tmp[1] > 0) {
                pos -= tmp[1];
            }
            return tmp[0];
        } else {
            // copy and fill the buffer so we cn continue parsing
            return null;
        }
    }

    /**
     * Utility to find the next token state.
     */
    private void parseNextState() throws IOException {
        // skip the white space
        while (pos <= numRead) {
            if (pos == numRead) {
                if (streamCount < streamsBytes.length - 1) {
                    streamCount++;
                    // skip zero length stream
                    if (streamsBytes[streamCount].length == 0 &&
                            streamCount + 1 < streamsBytes.length) {
                        streamCount++;
                    }
                    // assign next byte array
                    streamBytes = streamsBytes[streamCount];
                    // reset the  pointers.
                    pos = 0;
                    numRead = streamBytes.length;
                } else {
                    tokenType = TOKEN_NO_MORE;
                    break;
                }
            }
            // find the next space
            if (streamBytes[pos] > 32) {//!isDelimiter(streamBytes[pos])) {
                break;
            }
            pos++;
        }

        // We found the end
        if (pos < numRead) {
            startTokenPos = pos;
            byte c = streamBytes[pos];
            switch (c) {
                case '/':
                    tokenType = TOKEN_NAME;
                    break;
                case '(':
                    tokenType = TOKEN_LIT_STRING;
                    break;
                case '[':
                    tokenType = TOKEN_ARRAY;
                    break;
                case '<':
                    byte c2 = streamBytes[pos + 1];
                    switch (c2) {
                        case '<':
                            tokenType = TOKEN_DICTIONARY;
                            break;
                        default:
                            tokenType = TOKEN_HEX_STRING;
                            break;
                    }
                    break;
                case '-':
                    tokenType = TOKEN_NUMBER;
                    break;
                case 't':
                    tokenType = TOKEN_BOOLEAN;
                    break;
                case 'f':
                    if (pos + 1 < numRead) {
                        c2 = streamBytes[pos + 1];
                        switch (c2) {
                            case 'a':
                                tokenType = TOKEN_BOOLEAN;
                                break;
                            default:
                                tokenType = TOKEN_OPERAND;
                                break;
                        }
                    }
                    tokenType = TOKEN_OPERAND;
                    break;
                case '%':
                    // ignore all the characters after a comment token until
                    // we get to the end of the line
                    do {
                        pos++;
                    }
                    while (pos < numRead && streamBytes[pos] != 13 && streamBytes[pos] != 10);
                    parseNextState();
                    break;
                default:
                    if (c <= '9' && c >= '-') {
                        tokenType = TOKEN_NUMBER;
                        break;
                    }
                    tokenType = TOKEN_OPERAND;
                    break;
            }

        } else {
            tokenType = TOKEN_NO_MORE;
        }
    }


    /**
     * Utility for finding token delimiter in a type 4 function stream.
     *
     * @param c character to compare against known delimiters.
     * @return true if c is a delimiter otherwise, false.
     */
    private static boolean isDelimiter(byte c) {
//        return c == 32 || // space
//                c == 0 ||  // null
//                c == 9 ||  // horizontal tab
//                c == 10 || // line feed
//                c == 12 || // form feed
//                c == 13;   // carriage return
        // experimental
        return c <= ' ';//space
    }

    /**
     * Utility for finding token delimiter based on specific tokens.
     *
     * @param c character to compare against known delimiters.
     * @return true if c is a delimiter otherwise, false.
     */
    private static boolean isTextDelimiter(byte c) {
        return c == '(' ||
                c == '<' ||
                c == '[' ||
                c == '/' ||
                c == ']' ||
                c == ')' ||
                c == '>';
    }

    private float parseNumber() {
        float digit = 0;
        float divisor = 10;
        boolean isDigit;
        boolean isDecimal = false;
        boolean singed = streamBytes[startTokenPos] == '-';
        startTokenPos = singed ? startTokenPos + 1 : startTokenPos;
        int current;
        for (int i = startTokenPos; i < pos; i++) {
            current = streamBytes[i] - 48;
            isDigit = streamBytes[i] >= 48 && streamBytes[i] <= 57;
            if (!isDecimal && isDigit) {
                digit = (digit * 10) + current;
            } else if (isDecimal && isDigit) {
                digit += (current / divisor);
                divisor *= 10;
            } else if (streamBytes[i] == 46) {
                isDecimal = true;
            } else {
                // anything else we can assume malformed and should break.
                int offset = i - startTokenPos;
                offset = offset == 1 ? offset : offset - 1;
                pos -= offset;
                break;
            }
        }
        if (singed) {
            return -digit;
        } else {
            return digit;
        }
    }
}
