/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.util;

import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInput;

import java.io.*;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author Mark Collette
 *         Date: 18-Feb-2005
 *         Time: 3:53:40 PM
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
        buffer[offset + 0] = (byte) ((value >>> 24) & 0xff);
        buffer[offset + 1] = (byte) ((value >>> 16) & 0xff);
        buffer[offset + 2] = (byte) ((value >>> 8) & 0xff);
        buffer[offset + 3] = (byte) ((value >>> 0) & 0xff);
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
        buffer[offset + 0] = (byte) ((value >>> 8) & 0xff);
        buffer[offset + 1] = (byte) ((value >>> 0) & 0xff);
    }

    /**
     * @param in       InputStream to read from
     * @param numBytes number of bytes to read to make integral value from [0, 8]
     * @return Integral value, which is composed of numBytes bytes, read using big-endian rules from in
     * @throws IOException
     */
    public static long readLongWithVaryingBytesBE(InputStream in, int numBytes) throws IOException {
//System.out.println("Utils.readLongWithVaryingBytesBE()  numBytes: " + numBytes);
        long val = 0;
        for (int i = 0; i < numBytes; i++) {
            int curr = in.read();
            if (curr < 0)
                throw new EOFException();
            val <<= 8;
            val |= (((long) curr) & ((long) 0xFF));
        }
        return val;
    }

    /**
     * @param in       InputStream to read from
     * @param numBytes number of bytes to read to make integral value from [0, 4]
     * @return Integral value, which is composed of numBytes bytes, read using big-endian rules from in
     * @throws IOException
     */
    public static int readIntWithVaryingBytesBE(InputStream in, int numBytes) throws IOException {
//System.out.println("Utils.readIntWithVaryingBytesBE()  numBytes: " + numBytes);
        int val = 0;
        for (int i = 0; i < numBytes; i++) {
            int curr = in.read();
            if (curr < 0)
                throw new EOFException();
            val <<= 8;
            val |= (curr & 0xFF);
        }
        return val;
    }

    public static String convertByteArrayToHexString(byte[] buffer, boolean addSpaceSeparator) {
        return convertByteArrayToHexString(
                buffer, 0, buffer.length, addSpaceSeparator, -1, (char) 0);
    }

    public static String convertByteArrayToHexString(byte[] buffer, boolean addSpaceSeparator, int addDelimiterEverNBytes, char delimiter) {
        return convertByteArrayToHexString(
                buffer, 0, buffer.length, addSpaceSeparator, addDelimiterEverNBytes, delimiter);
    }

    public static String convertByteArrayToHexString(
            byte[] buffer, int offset, int length,
            boolean addSpaceSeparator, int addDelimiterEverNBytes, char delimiter) {
        int presize = length * (addSpaceSeparator ? 3 : 2);
        if (addDelimiterEverNBytes > 0)
            presize += (length / addDelimiterEverNBytes);
        StringBuffer sb = new StringBuffer(presize);
        int delimiterCount = 0;
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            int currValue = 0;
            currValue |= (0xff & ((int) buffer[index]));
            String s = Integer.toHexString(currValue);
            for (int i = s.length(); i < 2; i++)
                sb.append('0');
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

    /**
     * boolean java.awt.GraphicsEnvironment.isHeadless() does not exist in Java 1.3,
     * since it was introduced in Java 1.4, so we use reflection to call it,
     * if it exists.
     * In the event of not being able to call graphicsEnvironment.isHeadless(),
     * instead of throwing an Exception, we simply return defaultReturnIfNoMethod
     *
     * @param graphicsEnvironment     java.awt.GraphicsEnvironment to call isHeadless() on
     * @param defaultReturnIfNoMethod Value to return if could not call graphicsEnvironment.isHeadless()
     */
    public static boolean reflectGraphicsEnvironmentISHeadlessInstance(Object graphicsEnvironment, boolean defaultReturnIfNoMethod) {
        try {
            Class clazz = graphicsEnvironment.getClass();
            Method isHeadlessInstanceMethod = clazz.getMethod("isHeadlessInstance", new Class[]{});
            if (isHeadlessInstanceMethod != null) {
                Object ret = isHeadlessInstanceMethod.invoke(
                        graphicsEnvironment, new Object[]{});
                if (ret instanceof Boolean)
                    return ((Boolean) ret).booleanValue();
            }
        }
        catch (Throwable t) {
            logger.log(Level.FINE,
                    "ImageCache: Java 1.4 Headless support not found.");
        }
        return defaultReturnIfNoMethod;
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
            out = null;
            inArray[0] = new ByteArrayInputStream(data);
            if (convertToHex)
                content = Utils.convertByteArrayToHexString(data, true);
            else
                content = new String(data);
        }
        catch (IOException ioe) {
            logger.log(Level.FINE, "Problem getting debug string");
        }
        catch (Throwable e) {
            logger.log(Level.FINE, "Problem getting content stream, skipping");
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
        }
        catch (IOException ioe) {
            logger.log(Level.FINE, "Problem getting debug string");
        }
        finally {
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
        }
        catch (IOException ioe) {
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
     * 
     * @see org.icepdf.core.pobjects.LiteralStringObject
     * @see org.icepdf.core.pobjects.HexStringObject
     * @see org.icepdf.core.pobjects.security.StandardEncryption
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
     * 
     * @see org.icepdf.core.pobjects.LiteralStringObject
     * @see org.icepdf.core.pobjects.HexStringObject
     * @see org.icepdf.core.pobjects.security.StandardEncryption
     */
    public static String convertByteArrayToByteString(byte[] bytes) {
        final int max = bytes.length;
        StringBuffer sb = new StringBuffer(max);
        for (int i = 0; i < max; i++) {
            int b = ((int) bytes[i]) & 0xFF;
            sb.append((char)b);
        }
        return sb.toString();
    }
}
