package org.icepdf.core.util;

import java.nio.ByteBuffer;

/**
 *
 */
public class ByteBufferUtil {

    public static int findReverseString(ByteBuffer byteBuffer, int limit, final byte[] stingBytes) {
        return findReverseString(byteBuffer, limit, 0, stingBytes);
    }

    public static int findReverseNexNumber(ByteBuffer byteBuffer) {
        int end = byteBuffer.position();
        int start = 0;
        for (int i = end - 1; i >= 0; i--) {
            if (byteBuffer.get(i) <= 32) {
                start = i;
                break;
            }
        }
        byteBuffer.position(start + 1);
        byte[] number = new byte[end - start - 1];
        byteBuffer.get(number);
        byteBuffer.position(start); // skip white space.
        return Integer.parseInt(new String(number));
    }

    public static int findReverseString(ByteBuffer byteBuffer, int limit, int end, final byte[] stingBytes) {
        int matchLength = stingBytes.length - 1;
        int matchPosition = matchLength;
        byteBuffer.position(limit);
        for (int i = limit - 1; i >= end; i--) {
            if (byteBuffer.get(i) == stingBytes[matchPosition]) {
                if (matchPosition == 0) {
                    byteBuffer.position(i);
                    break;
                }
                matchPosition--;
            } else {
                matchPosition = matchLength;
            }
        }
        return byteBuffer.position();
    }

    public static boolean findString(ByteBuffer byteBuffer, final byte[] stingBytes) {
        int matchPosition = 0;
        int matchLength = stingBytes.length - 1;
        while (byteBuffer.hasRemaining()) {
            byte byteChar = byteBuffer.get();
            if (byteChar == stingBytes[matchPosition]) {
                if (matchPosition == matchLength) {
                    break;
                }
                matchPosition++;
            } else {
                matchPosition = 0;
            }
        }
        return matchPosition == matchLength;
    }
}

