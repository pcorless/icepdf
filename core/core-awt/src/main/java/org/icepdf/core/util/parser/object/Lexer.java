package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Lexer {

    private static final Logger logger =
            Logger.getLogger(Lexer.class.toString());

    private Library library;

    // stream reader pointers.
    private ByteBuffer streamBytes;

    // stream pointers
    private int pos, startTokenPos, endTokenPos;

    // lexer states
    private static final int
            TOKEN_NO_MORE = 0,
            TOKEN_NUMBER = 1,
            TOKEN_OPERAND = 2,
            TOKEN_LIT_STRING = 3,
            TOKEN_HEX_STRING = 4,
            TOKEN_NAME = 5,
            TOKEN_COMMENT = 6,
            TOKEN_DICTIONARY = 7,
            TOKEN_ARRAY = 8,
            TOKEN_BOOLEAN = 9,
            TOKEN_REFERENCE = 10,
            TOKEN_NULL = 11;

    public Lexer(Library library) {
        this.library = library;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) throws UnsupportedEncodingException {
        streamBytes = byteBuffer;
        pos = streamBytes.position();
    }

    public void skipWhiteSpace() {
        // a stream token's end of line marker can be either:
        // - a carriage return and a line feed
        // - just a line feed, and not by a carriage return alone.

        // check for carriage return and line feed, but reset if
        // just a carriage return as it is a valid stream byte

        byte streamByte = streamBytes.get(pos);
        if (streamByte == 13) {
            pos++;
            streamByte = streamBytes.get(pos);
            if (streamByte == 10) {
                pos++;
            }
        } else if (streamByte == 10) {
            // eat the character
            pos++;
        }
        streamBytes.position(pos);
//        while (pos < streamBytes.limit()) {
//            // find the next space
//            byte streamByte = streamBytes.get(pos);
//            if (!isDelimiter(streamByte)) {
//                streamBytes.position(pos);
//                break;
//            }
//            pos++;
//        }
    }

    public int skipUntilEndstream(int streamOffsetEnd) {
        pos = streamOffsetEnd;
        byte nextByte;
        while (pos < streamBytes.limit()) {
            nextByte = streamBytes.get(pos);
            System.out.print((int) nextByte + " ");
            pos++;
            if (nextByte == 'e' &&
                    streamBytes.get(pos) == 'n' &&
                    streamBytes.get(pos + 1) == 'd' &&
                    streamBytes.get(pos + 2) == 's' &&
                    streamBytes.get(pos + 3) == 't' &&
                    streamBytes.get(pos + 4) == 'r' &&
                    streamBytes.get(pos + 5) == 'e' &&
                    streamBytes.get(pos + 6) == 'a' &&
                    streamBytes.get(pos + 7) == 'm') {
                break;
            } else if (nextByte == 0x0A || nextByte == 0x0D || nextByte == 0x20) {
                streamOffsetEnd--;
                continue;
            } else {
                streamOffsetEnd++;
            }
        }
        return streamOffsetEnd;
    }

    public Object nextToken() throws IOException {
        return nextToken(null);
    }

    public Object nextToken(Reference reference) throws IOException {

        if (streamBytes == null) {
            throw new IOException("Object Stream, null input stream bytes.");
        }

        // get starting lexer state.
        int tokenType = parseNextState();

        switch (tokenType) {
            // we have a name
            case TOKEN_NUMBER:
                return startNumber();
            case TOKEN_OPERAND:
                return startOperand();
            case TOKEN_HEX_STRING:
                return startHexString(reference);
            case TOKEN_LIT_STRING:
                return startLiteralString(reference);
            case TOKEN_NAME:
                return startName();
            case TOKEN_ARRAY:
                return startArray(reference);
            case TOKEN_DICTIONARY:
                return startDictionary(reference);
            case TOKEN_BOOLEAN:
                return startBoolean();
            case TOKEN_REFERENCE:
                return startReference();
            case TOKEN_NULL:
                return startNull();
            case TOKEN_COMMENT:
                return startComment();
            default:
                return null;
        }
    }

    /**
     * Utility for parsing a hex strings.
     */
    private StringObject startHexString(Reference reference) throws IOException {
        // skip the starting (
        streamBytes.get();
        startTokenPos = streamBytes.position();
        while (streamBytes.hasRemaining()) {
            if (streamBytes.get() == '>') {
                // back out the ending )
                break;
            }
        }
        endTokenPos = streamBytes.position();
        // return the name object
        byte[] hexData = new byte[endTokenPos - startTokenPos - 1];
        streamBytes.position(startTokenPos);
        streamBytes.get(hexData);
        streamBytes.position(endTokenPos);
        pos = endTokenPos;

        HexStringObject hexStringObject = new HexStringObject(new String(hexData));
        hexStringObject.setReference(reference);
        return hexStringObject;
    }

    /**
     * Utility for parsing a lit strings.
     */
    private StringObject startLiteralString(Reference reference) throws IOException {
        // skip the starting (
        startTokenPos = streamBytes.position();

        // build out the string, NOTE: not checking for embedded Strings
        StringBuilder captured = new StringBuilder();
        int lookAhead;
        // skip past the starting (
        streamBytes.get();
        pos = streamBytes.position();
        // check to see if we have 2 byte encoding, if so we parse the string a little differently.
        if (pos + 2 < streamBytes.limit()) {
            if ((streamBytes.get(pos) & 0xff) == 255 && (streamBytes.get(pos + 1) & 0xff) == 254) {
                // skip the header bytes.
                pos += 2;
                while (!((streamBytes.get(pos) & 0xff) == ')' &&
                        (isDelimiter((byte) ((streamBytes.get(pos + 1) & 0xff))) ||
                                isTextDelimiter((byte) ((streamBytes.get(pos + 1) & 0xff)))))) {
                    int b1 = ((((int) streamBytes.get(pos)) & 0xFF) << 8) |
                            ((int) streamBytes.get(pos + 1)) & 0xFF;
                    captured.append((char) b1);
                    pos += 2;
                }
                pos++;
                streamBytes.position(pos);
                LiteralStringObject literalStringObject =  new LiteralStringObject(captured, true);
                literalStringObject.setReference(reference);
                return literalStringObject;
            }
        }

        int parenthesisCount = 1;
        int current;
        while (streamBytes.hasRemaining()) {
            current = streamBytes.get(pos) & 0xff;
            if (current != '\\' && current != ')' && current != '(') {
                captured.append((char) current);
                pos++;
            } else if (current != '\\' && current == ')' && parenthesisCount > 1) {
                captured.append((char) current);
                pos++;
                parenthesisCount--;
            } else if (current != '\\' && current == '(') {
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
                lookAhead = streamBytes.get(pos + 1) & 0xff;
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
                        lookAhead = streamBytes.get(pos + j + 1);
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
        streamBytes.position(pos);
        LiteralStringObject literalStringObject =  new LiteralStringObject(captured, true);
        literalStringObject.setReference(reference);
        return literalStringObject;
    }

    /**
     * Utility for parsing a name.
     */
    private Name startName() throws IOException {
        // skip first / of name
        streamBytes.get();
        startTokenPos = pos = streamBytes.position();
        byte charByte;
        while (pos < streamBytes.limit()) {
            // look for a natural break
            charByte = streamBytes.get(pos);
            if (isDelimiter(charByte) || isTextDelimiter(charByte)) {
                break;
            }
            pos++;
        }
        // return the name object
        byte[] nameBytes = new byte[pos - startTokenPos];
        streamBytes.position(startTokenPos);
        streamBytes.get(nameBytes);
        streamBytes.position(pos);
        return new Name(new String(nameBytes));
    }

    private Boolean startBoolean() throws IOException {
        startTokenPos = pos;
        while (streamBytes.hasRemaining()) {
            // look for a natural break
            if (isDelimiter(streamBytes.get(pos)) || isTextDelimiter(streamBytes.get(pos))) {
                break;
            }
            pos++;
        }
        if (pos <= streamBytes.limit()) {
            // return the name object
            byte[] booleanBytes = new byte[pos - startTokenPos];
            streamBytes.position(startTokenPos);
            streamBytes.get(booleanBytes);
            return Boolean.parseBoolean(new String(booleanBytes));
        } else {
            return null;
        }
    }

    private String startNull() throws IOException {
        startTokenPos = pos;
        while (streamBytes.hasRemaining()) {
            // look for a natural break
            if (isDelimiter(streamBytes.get(pos)) || isTextDelimiter(streamBytes.get(pos))) {
                break;
            }
            pos++;
        }
        if (pos <= streamBytes.limit()) {
            // return the name object
            byte[] booleanBytes = new byte[pos - startTokenPos];
            streamBytes.position(startTokenPos);
            streamBytes.get(booleanBytes);
            return "null";
        } else {
            return null;
        }
    }

    private Object startComment() throws IOException {
        do {
            pos++;
        }
        while (pos < streamBytes.limit() && streamBytes.get(pos) != 13 && streamBytes.get(pos) != 10);
        // we ignore comments
        streamBytes.position(pos);
        // skip passed it.
        return nextToken();
    }

    private Object startDictionary(Reference reference) throws IOException {
        startTokenPos = pos;

        DictionaryEntries entries = new DictionaryEntries();

        // skip past the starting <<
        pos += 2;

        Object key = null;
        Object value;
        int count = 1;
        while (!(streamBytes.get(pos) == '>' && streamBytes.get(pos + 1) == '>')) {
            if (count == 1) {
                key = nextToken(reference);
                count++;
            } else if (count == 2) {
                value = nextToken(reference);
                if (!(key instanceof Name)) {
                    break;
                }
                entries.put((Name) key, value);
                count = 1;
            }
            // check the buffer
            while (pos < streamBytes.limit()) {
                // look for a natural break
                if (!isDelimiter(streamBytes.get(pos))) {
                    break;
                }
                pos++;
            }
        }
        // skip the trailing >>
        pos += 2;
        streamBytes.position(pos);
        return ObjectFactory.getInstance(library, entries);
    }

    private List startArray(Reference reference) throws IOException {
        startTokenPos = pos;

        List<Object> array = new ArrayList<Object>();

        // skip past the starting [
        pos += 1;
        if (pos == streamBytes.limit()) {
            return array;
        }
        Object token;
        while (streamBytes.get(pos) != ']' && pos < streamBytes.limit()) {
            // add the tokens as we get them.
            token = nextToken(reference);
            if (token != null) {
                array.add(token);
            } else {
                break;
            }
            // push past any white space
            while (pos < streamBytes.limit()) {
                // look for a natural break
                if (!isDelimiter(streamBytes.get(pos))) {
                    break;
                }
                pos++;
            }
            pos = streamBytes.position();
        }
        // skip the trailing ]
        pos++;
        streamBytes.position(pos);
        return array;
    }

    private Object startReference() throws IOException {
        int objectNumber = (Integer) startNumber();
        parseNextState();
        int generationNumber = (Integer) startNumber();

        // parse base the R
        pos += 2;
        streamBytes.position(pos);

        return new Reference(objectNumber, generationNumber);
    }

    /**
     * Utility of processing a number state.
     */
    private Object startNumber() throws IOException {
        startTokenPos = pos;
        byte posByte;
        while (pos < streamBytes.limit()) {
            // check for white space or < or ( string start in an Array
            posByte = streamBytes.get(pos);
            if (posByte < '+' || posByte > '9' || posByte == '/') {
                break;
            }
            pos++;
        }
        // we can catch an exception and try again, moving the pos back until
        // we cn parse the number.
        streamBytes.position(pos);
        return parseNumber();
    }

    /**
     * Utility for processing the operand state.
     */
    private Object startOperand() throws IOException {
        startTokenPos = pos;
        byte posByte;
        while (pos < streamBytes.limit()) {
            // check for delimiters just encase the encoder didn't use spaces.
            posByte = streamBytes.get(pos);
            if (isDelimiter(posByte) ||
                    isTextDelimiter(posByte)) {
                break;
            }
            pos++;
        }
        streamBytes.position(pos);
        if (pos <= streamBytes.limit() && pos > startTokenPos) {
            int[] tmp = OperatorFactory.getOperator(streamBytes, startTokenPos, pos - startTokenPos);
            // check for 'null' token which maybe picked up as an operator.
            if (tmp == null) {
                return null;
            }
            // adjust for any potential parsing compensation.
            if (tmp[1] > 0) {
                pos -= tmp[1];
            }
            streamBytes.position(pos);
            return tmp[0];
        } else {
            // copy and fill the buffer so we cn continue parsing
            return null;
        }
    }

    /**
     * Utility to find the next token state.
     */
    private int parseNextState() throws IOException {
        int tokenType;
        // skip the white space
        while (pos < streamBytes.limit()) {
            // find the next space
            if (pos < streamBytes.limit() && streamBytes.get(pos) > 32) {//!isDelimiter(
                break;
            }
            pos++;
        }
        streamBytes.position(pos);

        // We found the end
        if (pos < streamBytes.limit()) {
            startTokenPos = pos;
            byte c = streamBytes.get(pos);
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
                    byte c2 = streamBytes.get(pos + 1);
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
                case '+':
                    tokenType = TOKEN_NUMBER;
                    break;
                case 't':
                    tokenType = TOKEN_BOOLEAN;
                    break;
                case 'f':
                    if (pos + 1 < streamBytes.limit()) {
                        c2 = streamBytes.get(pos + 1);
                        switch (c2) {
                            case 'a':
                                tokenType = TOKEN_BOOLEAN;
                                break;
                            default:
                                tokenType = TOKEN_OPERAND;
                                break;
                        }
                    } else {
                        tokenType = TOKEN_OPERAND;
                    }
                    break;
                case 'n':
                    if (pos + 1 < streamBytes.limit()) {
                        c2 = streamBytes.get(pos + 1);
                        switch (c2) {
                            case 'u':
                                tokenType = TOKEN_NULL;
                                break;
                            default:
                                tokenType = TOKEN_OPERAND;
                                break;
                        }
                    } else {
                        tokenType = TOKEN_OPERAND;
                    }
                    break;
                case '%':
                    tokenType = TOKEN_COMMENT;
                    break;
                default:
                    if (c <= '9' && c >= '-') {
                        int startTokenPos = pos;
                        tokenType = TOKEN_NUMBER;
                        // look a head two spaces to try and find R
                        int count = 0;
                        byte next;
                        while (pos + 1 < streamBytes.limit()) {
                            next = streamBytes.get(pos);
                            if (next <= 32) {
                                count++;
                            }
                            if (isTextDelimiter(next)) {
                                break;
                            }
                            pos++;
                            if (count == 2) {
                                next = streamBytes.get(pos);
                                if (next == 'R') {
                                    tokenType = TOKEN_REFERENCE;
                                    break;
                                } else {
                                    tokenType = TOKEN_NUMBER;
                                    break;
                                }
                            }
                        }
                        streamBytes.position(startTokenPos);
                        pos = startTokenPos;
                        break;
                    }
                    tokenType = TOKEN_OPERAND;
                    break;
            }

        } else {
            tokenType = TOKEN_NO_MORE;
        }
        return tokenType;
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
        return c >= 0 && c <= ' ';//space
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

    private Number parseNumber() {
        int digit = 0;
        float divisor = 10;
        float decimal = 0;
        boolean isDigit;
        boolean isDecimal = false;
        boolean signed = streamBytes.get(startTokenPos) == '-' ||
                streamBytes.get(startTokenPos) == '+';
        startTokenPos = signed ? startTokenPos + 1 : startTokenPos;
        // check for  double sign, thanks oracle forms!
        if (signed && streamBytes.get(startTokenPos) == '-') {
            startTokenPos++;
        }
        int current;
        for (int i = startTokenPos; i < pos; i++) {
            current = streamBytes.get(i) - 48;
            isDigit = streamBytes.get(i) >= 48 && streamBytes.get(i) <= 57;
            if (!isDecimal && isDigit) {
                digit = (digit * 10) + current;
            } else if (isDecimal && isDigit) {
                decimal += (current / divisor);
                divisor *= 10;
            } else if (streamBytes.get(i) == 46) {
                isDecimal = true;
            } else {
                // anything else we can assume malformed and should break.
                int offset = i - startTokenPos;
                offset = offset == 1 ? offset : offset - 1;
                pos -= offset;
                break;
            }
        }
        streamBytes.position(pos);
        if (signed) {
            if (isDecimal) {
                return -(digit + decimal);
            } else {
                return -digit;
            }
        } else {
            if (isDecimal) {
                return digit + decimal;
            } else {
                return digit;
            }
        }
    }
}
