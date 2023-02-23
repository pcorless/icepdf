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
package org.icepdf.core.util;

import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.fonts.ofont.Encoding;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mark Collette
 * Date: 18-Feb-2005
 * Time: 3:53:40 PM
 */
public class Utils {

    private static final Logger logger =
            Logger.getLogger(Utils.class.toString());

    /**
     * Sets the value into the buffer, at the designated offset, using big-endian rules
     * Callers is responsible to ensure that value will fit into buffer, starting at offset
     *
     * @param value  to be set into buffer
     * @param buffer into which value is to be set
     * @param offset into buffer which value is to be set
     */
    public static void setIntIntoByteArrayBE(int value, byte[] buffer, int offset) {
        buffer[offset] = (byte) ((value >>> 24) & 0xff);
        buffer[offset + 1] = (byte) ((value >>> 16) & 0xff);
        buffer[offset + 2] = (byte) ((value >>> 8) & 0xff);
        buffer[offset + 3] = (byte) ((value) & 0xff);
    }

    /**
     * Sets the value into the buffer, at the designated offset, using big-endian rules
     * Callers is responsible to ensure that value will fit into buffer, starting at offset
     *
     * @param value  to be set into buffer
     * @param buffer into which value is to be set
     * @param offset into buffer which value is to be set
     */
    public static void setShortIntoByteArrayBE(short value, byte[] buffer, int offset) {
        buffer[offset] = (byte) ((value >>> 8) & 0xff);
        buffer[offset + 1] = (byte) ((value) & 0xff);
    }

    /**
     * Read long with varying bytes length
     *
     * @param byteBuffer byteBuffer to read from
     * @param numBytes   number of bytes to read to make integral value from [0, 8]
     * @return Integral value, which is composed of numBytes bytes, read using big-endian rules from in
     */
    public static int readIntWithVaryingBytesBE(ByteBuffer byteBuffer, int numBytes) {
        int val = 0;
        for (int i = 0; i < numBytes; i++) {
            int curr = byteBuffer.get();
            val <<= 8;
            val |= (curr & 0xFF);
        }
        return val;
    }

    /**
     * Read long with varying bytes length
     *
     * @param byteBuffer       byteBuffer to read from
     * @param numBytes number of bytes to read to make integral value from [0, 4]
     * @return Integral value, which is composed of numBytes bytes, read using big-endian rules from in
     */
    public static long readLongWithVaryingBytesBE(ByteBuffer byteBuffer, int numBytes) {
        long val = 0;
        for (int i = 0; i < numBytes; i++) {
            int curr = byteBuffer.get();
            val <<= 8;
            val |= (((long) curr) & ((long) 0xFF));
        }
        return val;
    }

    /**
     * Write the given int as a 4 byte integer to the given outputStream.
     *
     * @param in stream to write byte data to.
     * @param i  integer to convert to bytes.
     * @throws IOException write error.
     */
    public static void writeInteger(OutputStream in, int i) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        in.write(bb.array());
    }

    /**
     * Write the given int as a 8 byte long to the given outputStream.
     *
     * @param in stream to write byte data to.
     * @param i  long to convert to bytes.
     * @throws IOException write error.
     */
    public static void writeLong(OutputStream in, long i) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putLong(i);
        in.write(bb.array());
    }

    public static String convertByteArrayToHexString(byte[] buffer, boolean addSpaceSeparator) {
        return convertByteArrayToHexString(
                buffer, 0, buffer.length, addSpaceSeparator, -1, (char) 0);
    }

    public static String convertByteArrayToHexString(byte[] buffer, boolean addSpaceSeparator, int addDelimiterEverNBytes, char delimiter) {
        return convertByteArrayToHexString(
                buffer, 0, buffer.length, addSpaceSeparator, addDelimiterEverNBytes, delimiter);
    }

    public static String byteFormatter(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String convertByteArrayToHexString(
            byte[] buffer, int offset, int length,
            boolean addSpaceSeparator, int addDelimiterEverNBytes, char delimiter) {
        int presize = length * (addSpaceSeparator ? 3 : 2);
        if (addDelimiterEverNBytes > 0)
            presize += (length / addDelimiterEverNBytes);
        StringBuilder sb = new StringBuilder(presize);
        int delimiterCount = 0;
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            int currValue = 0;
            currValue |= (0xff & ((int) buffer[index]));
            String s = Integer.toHexString(currValue);
            sb.append("0".repeat(2 - s.length()));
            sb.append(s);
            if (addSpaceSeparator)
                sb.append(' ');
            delimiterCount++;
            if (addDelimiterEverNBytes > 0 && delimiterCount == addDelimiterEverNBytes) {
                delimiterCount = 0;
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    public static String getContentAndReplaceInputStream(InputStream[] inArray, boolean convertToHex) {
        String content = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            InputStream in = inArray[0];

            byte[] buf = new byte[1024];
            while (true) {
                int read = in.read(buf, 0, buf.length);
                if (read < 0)
                    break;
                out.write(buf, 0, read);
            }

//            while( true ) {
//                int read = in.read();
//                if( read < 0 )
//                    break;
//                out.write( read );
//            }

            if (!(in instanceof SeekableInput))
                in.close();
            out.flush();
            out.close();
            byte[] data = out.toByteArray();
            inArray[0] = new ByteArrayInputStream(data);
            if (convertToHex)
                content = Utils.convertByteArrayToHexString(data, true);
            else
                content = new String(data);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Problem extracting content stream");
        }
        return content;
    }

    public static String getContentFromSeekableInput(SeekableInput in, boolean convertToHex) {
        String content = null;
        try {
            in.beginThreadAccess();

            long position = in.getAbsolutePosition();

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            /*
            byte[] buf = new byte[1024];
            while( true ) {
                int read = in.read( buf, 0, buf.length );
                if( read < 0 )
                    break;
                out.write( buf, 0, read );
            }
            */

            while (true) {
                int read = in.getInputStream().read();
                if (read < 0)
                    break;
                out.write(read);
            }

            in.seekAbsolute(position);

            out.flush();
            out.close();
            byte[] data = out.toByteArray();
            if (convertToHex)
                content = Utils.convertByteArrayToHexString(data, true);
            else
                content = new String(data);
        } catch (IOException ioe) {
            logger.log(Level.FINE, "Problem getting debug string");
        } finally {
            in.endThreadAccess();
        }
        return content;
    }

    public static SeekableInput replaceInputStreamWithSeekableInput(InputStream in) {
        if (in instanceof SeekableInput)
            return (SeekableInput) in;

        SeekableInput sin = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);

            /*
            byte[] buf = new byte[1024];
            while( true ) {
                int read = in.read( buf, 0, buf.length );
                if( read < 0 )
                    break;
                out.write( buf, 0, read );
            }
            */

            while (true) {
                int read = in.read();
                if (read < 0)
                    break;
                out.write(read);
            }

            in.close();
            out.flush();
            out.close();
            byte[] data = out.toByteArray();
            sin = new SeekableByteArrayInputStream(data);
        } catch (IOException ioe) {
            logger.log(Level.FINE, "Problem getting debug string");
        }
        return sin;
    }

    public static void printMemory(String str) {
        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        long used = total - free;
        System.out.println("MEM  " + str + "    used: " + (used / 1024) + " KB    delta: " + ((used - lastMemUsed) / 1024) + " KB");
        lastMemUsed = used;
    }

    private static long lastMemUsed = 0;

    public static int numBytesToHoldBits(int numBits) {
        int numBytes = (numBits / 8);
        if ((numBits % 8) > 0)
            numBytes++;
        return numBytes;
    }

    /**
     * When converting between String chars and bytes, there's an implied
     * encoding to be used, dependent on the context and platform. If
     * none is specified, then String(byte[]) will use the platform's
     * default encoding. This method is for when encoding is not relevant,
     * when the String simply holds byte values in each char.
     * <p>
     * {@link org.icepdf.core.pobjects.LiteralStringObject}
     * {@link org.icepdf.core.pobjects.HexStringObject}
     *
     * @param string char sequence to convert to byte array.
     * @return byte array of input string, encoding untouched.
     */
    public static byte[] convertByteCharSequenceToByteArray(CharSequence string) {
        final int max = string.length();
        byte[] bytes = new byte[max];
        for (int i = 0; i < max; i++) {
            bytes[i] = (byte) string.charAt(i);
        }
        return bytes;
    }

    /**
     * When converting between String chars and bytes, there's an implied
     * encoding to be used, dependent on the context and platform. If
     * none is specified, then String(byte[]) will use the platform's
     * default encoding. This method is for when encoding is not relevant,
     * when the String simply holds byte values in each char.
     * <p>
     * {@link org.icepdf.core.pobjects.LiteralStringObject}
     * {@link org.icepdf.core.pobjects.HexStringObject}
     * @param  bytes to convert.
     * @return converted bytes
     */
    public static String convertByteArrayToByteString(byte[] bytes) {
        final int max = bytes.length;
        StringBuilder sb = new StringBuilder(max);
        for (byte aByte : bytes) {
            int b = ((int) aByte) & 0xFF;
            sb.append((char) b);
        }
        return sb.toString();
    }


    /**
     * Utility method for decrypting a String object found in a dictionary
     * as a plaing text.  The string can be encrypted as well as octal encoded,
     * which is handle by this method.
     *
     * @param library      document library used for encryption handling.
     * @param stringObject string object to convert to string
     * @return converted string.
     */
    public static String convertStringObject(Library library, StringObject stringObject) {
        String convertedStringObject = null;
        String titleText = stringObject.getDecryptedLiteralString(library.getSecurityManager());
        // If the title begins with 254 and 255 we are working with
        // Octal encoded strings. Check first to make sure that the
        // title string is not null, or is at least of length 2.
        if (titleText != null && titleText.length() >= 2 &&
                ((int) titleText.charAt(0)) == 254 &&
                ((int) titleText.charAt(1)) == 255) {

            StringBuilder sb1 = new StringBuilder();

            // convert teh unicode to characters.
            for (int i = 2; i < titleText.length(); i += 2) {
                try {
                    int b1 = ((((int) titleText.charAt(i)) & 0xFF) << 8) |
                            ((int) titleText.charAt(i + 1)) & 0xFF;
                    //System.err.println(b1 + " " + b2);
                    sb1.append((char) (b1));
                } catch (Exception ex) {
                    // intentionally left blank.
                }
            }
            convertedStringObject = sb1.toString();
        } else if (titleText != null) {
            StringBuilder sb = new StringBuilder();
            Encoding enc = Encoding.getPDFDoc();
            for (int i = 0; i < titleText.length(); i++) {
//                sb.append(titleText.charAt(i));
                // pdf encoding maps char < 24 to '?' or 63. so we'll skip this map.
                char character = titleText.charAt(i);
                if (character > 23) {
                    sb.append(enc.get(character));
                } else {
                    sb.append(titleText.charAt(i));
                }
            }
            convertedStringObject = sb.toString();
        }
        return convertedStringObject;
    }

    /**
     * Convert a utf-8 encoded string into into an octal enocded byte[] array.
     *
     * @param literalString string to convert.
     * @return converted string value.
     */
    public static String convertStringToOctal(String literalString) {
        // scan string ot see if we have any unicode.
        int length = literalString.length();
        boolean foundExtendedAscii = false;
        for (int i = 0; i < length; i++) {
            if (literalString.charAt(i) >= 255) {
                foundExtendedAscii = true;
                break;
            }
        }
        if (foundExtendedAscii) {
            char[] octalEncoded = new char[length * 2 + 2];
            octalEncoded[0] = 254;
            octalEncoded[1] = 255;
            for (int i = 0, j = 2; i < length; i++, j += 2) {
                octalEncoded[j] = (char) ((literalString.charAt(i) >> 8) & 0xFF);
                octalEncoded[j + 1] = (char) (literalString.charAt(i) & 0xFF);
            }
            return new String(octalEncoded);
        } else {
            return literalString;
        }
    }

    /**
     * White space characters defined by ' ', '\t', '\r', '\n', '\f'
     *
     * @param c true if character is white space
     * @return true if char is whitespace, false otherwise.
     */
    public static boolean isWhitespace(char c) {
        return ((c == ' ') || (c == '\t') || (c == '\r') ||
                (c == '\n') || (c == '\f') || (c == 0));
    }

    public static boolean isDelimiter(char c) {
        return ((c == '[') || (c == ']') ||
                (c == '(') || (c == ')') ||
                (c == '<') || (c == '>') ||
                (c == '{') || (c == '}') ||
                (c == '/') || (c == '%'));
    }
}
