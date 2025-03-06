package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.updater.callbacks.ContentStreamCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Lexer {

    private static final Logger logger =
            Logger.getLogger(Lexer.class.toString());

    private static final int
            NO_MORE = 1,
            NUMBER = 2,
            OPERAND = 3,
            LIT_STRING = 4,
            HEX_STRING = 5,
            NAME = 6,
            COMMENT = 7,
            DICTIONARY = 8,
            ARRAY = 9,
            BOOLEAN = 10;

    private int streamCount;
    private Stream[] streams;

    private byte[] streamBytes;

    private int pos, numRead, startTokenPos;

    private int tokenType = 0;

    private ContentStreamCallback contentStreamCallbackCallback;

    public void setContentStream(Stream[] in, ContentStreamCallback contentStreamCallback) throws IOException {
        streams = in;
        streamCount = 0;
        streamBytes = streams[streamCount].getDecodedStreamBytes();
        if (streamBytes != null) {
            numRead = streamBytes.length;
        }
        contentStreamCallbackCallback = contentStreamCallback;
        if (contentStreamCallbackCallback != null) {
            contentStreamCallbackCallback.startContentStream(streams[streamCount]);
        }
    }

    public Object next() throws IOException {

        if (streamBytes == null) {
            throw new IOException("Content Stream, null input stream bytes.");
        }

        // get starting lexer state.
        parseNextState();

        switch (tokenType) {
            // we have a name
            case NUMBER:
                return startNumber();
            case OPERAND:
                return startOperand();
            case HEX_STRING:
                return startHexString();
            case LIT_STRING:
                return startLiteralString();
            case NAME:
                return startName();
            case ARRAY:
                return startArray();
            case DICTIONARY:
                return startDictionary();
            case BOOLEAN:
                return startBoolean();
            case COMMENT:
                return startComment();
            default:
                return null;
        }
    }

    public byte[] getImageBytes() {
        // skip past the D in ID and the first white space.
        pos += 1;
        // quick check for CR and LF after the ID.
        if (streamBytes[pos] == 10 || streamBytes[pos] == 13) {
            pos++;
            if (streamBytes[pos] == 10 || streamBytes[pos] == 13) {
                pos++;
            }
        }

        int startTokenPos = pos;
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
            else if (
                    (streamBytes[pos - 1] == 32 ||
                            streamBytes[pos - 1] == 10 ||
                            streamBytes[pos - 1] == 62 ||
                            streamBytes[pos - 1] == -1 ||
                            streamBytes[pos - 1] == 13 ||
                            streamBytes[pos - 1] == 0 || // null corner case
                            streamBytes[pos - 1] == 75) && // another corner case 'K', no whitespace between data and EI
                            streamBytes[pos] == 'E' &&
                            streamBytes[pos + 1] == 'I' &&
                            (streamBytes[pos + 2] == 32 ||
                                    streamBytes[pos + 2] == 10 ||
                                    streamBytes[pos + 2] == 13)) {
                int mark = pos;
                // avoid going to the next content stream, as inline images
                // aren't stretched across content streams, or at least we don't
                // think so.
                if (!(pos + 4 < streamBytes.length)) {
                    found = true;
                } else {
                    try {
                        pos += 2;
                        Object tmp = next();
                        // make sure we have an operand next as some streams can give
                        // us a false positive when EI and some white space is encountered.
                        if (tmp instanceof Integer && ((Integer) tmp) != Operands.OP &&
                                isDelimiter(streamBytes[pos])) {
                            found = true;
                        }
                        // rest the pos to before the EI.
                        pos = mark;
                    } catch (IOException e) {
                        logger.warning("Error parsing inline images.");
                    }
                }
            }
            if (found) {
                // remove the space before EI
                if (streamBytes[pos - 1] == 32) {
                    end = pos - 1;
                } else {
                    end = pos;
                }
                // skip passed the EI.
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

    public int getPos() {
        return pos;
    }

    private StringObject startHexString() {
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
        // position can be <= given the last pos++;
        if (pos <= numRead) {
            // return the name object
            return new HexStringObject(new String(
                    streamBytes, startTokenPos + 1, pos - startTokenPos - 2));
        } else {
            return null;
        }
    }

    private StringObject startLiteralString() {
        // skip the starting (
        startTokenPos = pos;

        // build out the string, NOTE: not checking for embedded Strings
        StringBuilder captured = new StringBuilder();
        int lookAhead;
        // skip past the starting (
        pos++;
        int parenthesisCount = 1;
        int current;
        while (pos < numRead) {
            current = streamBytes[pos] & 0xff;
            if (current != '\\' && current != ')' && current != '(') {
                captured.append((char) current);
                pos++;
            } else if (current == ')' && parenthesisCount > 1) {
                captured.append((char) current);
                pos++;
                parenthesisCount--;
            } else if (current == '(') {
                captured.append((char) current);
                pos++;
                parenthesisCount++;
            } else if (current == ')') {
                parenthesisCount--;
                if (parenthesisCount > 0) {
                    captured.append((char) current);
                    pos++;
                } else {
                    pos++;
                    break;
                }
            } else {
                /*
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
                lookAhead = (streamBytes[pos + 1] & 0xff);
                // capture the horizontal tab (HT), tab character is hard
                // to find, only appears in files with font substitution and
                // as a result we have better luck drawing a space character.
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
                    digit[0] = (byte) (lookAhead);
                    // octals have a max size of 3 digits, we already
                    // have one, so there can be up 2 more digits.
                    int offset = 1;
                    for (int j = 1; j <= 2; j++) {
                        lookAhead = streamBytes[pos + j + 1];
                        if (Character.isDigit(lookAhead)) {
                            digit[j] = (byte) lookAhead;
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
                // capture single quote(')
                else if (lookAhead == '\'') {
                    captured.append('\'');
                    pos += 2;
                }
                // capture double quote(")
                else if (lookAhead == '\"') {
                    captured.append('\"');
                    pos += 2;
                }
                // capture the form feed (FF)
                else if (lookAhead == '\\') {
                    captured.append('\\');
                    pos += 2;
                }
                // extra '/' tht we need to ignore
                else if (Character.isLetter(lookAhead)) {
                    captured.append((char) lookAhead);
                    pos += 2;
                }
                // avoid an infinite loop if any corner cases that don't need
                // to be escaped show up.
                else {
                    captured.append((char) lookAhead);
                    pos += 2;
                }
            }
        }
        return new LiteralStringObject(captured, true);
    }

    private Name startName() {
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

    private Boolean startBoolean() {
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

    private Integer startComment() {
        do {
            pos++;
        }
        while (pos < numRead && streamBytes[pos] != 13 && streamBytes[pos] != 10);
        return Operands.OP;
    }

    private DictionaryEntries startDictionary() throws IOException {
        startTokenPos = pos;

        DictionaryEntries dictionaryEntries = new DictionaryEntries();

        // skip past the starting <<
        pos += 2;
        // check for in very odd  corner cases. end
        checkLength();

        Object key = null;
        Object value;
        int count = 1;
        while (pos < streamBytes.length &&
                !(streamBytes[pos] == '>' && streamBytes[pos + 1] == '>')) {
            if (count == 1) {
                key = next();
                // double check we don't have an empty dictionary << >>
                if (key instanceof Integer &&
                        ((Integer) key) == Operands.OP) {
                    return dictionaryEntries;
                }
                count++;
            } else if (count == 2) {
                value = next();
                dictionaryEntries.put((Name)key, value);
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
        // skip the trailing >>
        pos += 2;
        return dictionaryEntries;
    }

    private void checkLength() throws IOException {
        if (pos == numRead) {
            if (streamCount < streams.length - 1) {
                nextContentStream();
            }
        }
    }

    private void nextContentStream() throws IOException {
        markContentStreamEnd();
        streamCount++;
        // assign next byte array, but skip over the corner
        // case of a zero length content stream.
        if (streams[streamCount].getDecompressedBytes() != null &&
                streams[streamCount].getDecompressedBytes().length == 0 &&
                streamCount + 1 < streams.length) {
            streamCount++;
        }
        streamBytes = streams[streamCount].getDecompressedBytes();
        markContentStreamStart();
        // reset the  pointers.
        pos = 0;
        numRead = streamBytes != null ? streamBytes.length : 0;
    }

    private void markContentStreamStart() throws IOException {
        if (contentStreamCallbackCallback != null) {
            contentStreamCallbackCallback.startContentStream(streams[streamCount]);
        }
    }

    private void markContentStreamEnd() throws IOException {
        if (contentStreamCallbackCallback != null) {
            contentStreamCallbackCallback.endContentStream();
        }
    }

    private List startArray() throws IOException {
        startTokenPos = pos;

        List<Object> array = new ArrayList<>();

        // skip past the starting [
        pos += 1;
        if (pos == numRead) {
            // check for in very odd  corner cases. end
            checkLength();
            if (pos == streamBytes.length) {
                return array;
            }
        }
        Object token;
        while (pos < numRead && streamBytes[pos] != ']') {
            // add the tokens as we get them.
            token = next();
            if (token instanceof Integer) {
                // we gone to var, likely empty array
                if (startTokenPos > pos) {
                    pos = startTokenPos;
                }
            } // only add non null values
            else if (token != null) {
                array.add(token);
            } else {
                break;
            }
            // push past any white space
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
        pos++;
        return array;
    }

    private Object startNumber() {
        startTokenPos = pos;
        while (pos < numRead) {
            if (streamBytes[pos] < '+' || streamBytes[pos] > '9' || streamBytes[pos] == '/') {
                break;
            }
            pos++;
        }
        return parseNumber();
    }

    /**
     * Utility for processing the operand state.
     */
    private Object startOperand() {
        startTokenPos = pos;
        while (pos < numRead) {
            // check for delimiters just encase the encoder didn't use spaces.
            if (isDelimiter(streamBytes[pos]) ||
                    isTextDelimiter(streamBytes[pos])) {
                break;
            }
            pos++;
        }
        if (pos <= numRead && pos > startTokenPos) {
            int[] tmp = Operands.parseOperand(streamBytes, startTokenPos, pos - startTokenPos);
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

    private void parseNextState() throws IOException {
        // skip the white space
        while (pos <= numRead) {
            if (pos == numRead) {
                if (streamCount < streams.length - 1) {
                    nextContentStream();
                    continue;
                } else {
                    break;
                }
            }
            // find the next space
            if (pos < streamBytes.length && streamBytes[pos] > 32) {//!isDelimiter(streamBytes[pos])) {
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
                    tokenType = NAME;
                    break;
                case '(':
                    tokenType = LIT_STRING;
                    break;
                case '[':
                    tokenType = ARRAY;
                    break;
                case '<':
                    byte c2 = streamBytes[pos + 1];
                    if (c2 == '<') {
                        tokenType = DICTIONARY;
                    } else {
                        tokenType = HEX_STRING;
                    }
                    break;
                case '-':
                case '+':
                    tokenType = NUMBER;
                    break;
                case 't':
                    tokenType = BOOLEAN;
                    break;
                case 'f':
                    if (pos + 1 < numRead) {
                        c2 = streamBytes[pos + 1];
                        if (c2 == 'a') {
                            tokenType = BOOLEAN;
                        } else {
                            tokenType = OPERAND;
                        }
                    } else {
                        tokenType = OPERAND;
                    }
                    break;
                case '%':
                    tokenType = COMMENT;
                    break;
                default:
                    if (c <= '9' && c >= '-') {
                        tokenType = NUMBER;
                        break;
                    }
                    tokenType = OPERAND;
                    break;
            }

        } else {
            tokenType = NO_MORE;
        }
    }

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
        boolean singed = streamBytes[startTokenPos] == '-' ||
                streamBytes[startTokenPos] == '+';
        startTokenPos = singed ? startTokenPos + 1 : startTokenPos;
        // check for  double neg sign
        if (singed && streamBytes[startTokenPos] == '-') {
            startTokenPos++;
        }
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
