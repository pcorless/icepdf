package org.icepdf.core.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 *
 */
public class ByteBufferUtil {

    public static ByteBuffer sliceObjectStream(ByteBuffer objectByteBuffer, int objectOffsetStart, int objectOffsetEnd) {
        int streamLength = objectOffsetEnd - objectOffsetStart;
        int oldLimit = objectByteBuffer.limit();
        int boundLimit = Math.min(objectOffsetStart + streamLength, objectByteBuffer.capacity());

        objectByteBuffer.position(objectOffsetStart);
        objectByteBuffer.limit(boundLimit);
        ByteBuffer streamByteBuffer = objectByteBuffer.slice();
        objectByteBuffer.limit(oldLimit);
        return streamByteBuffer;
    }

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

    public static int findReverseString(ByteBuffer byteBuffer, int start, int end, final byte[] stingBytes) {
        int matchLength = stingBytes.length - 1;
        int matchPosition = matchLength;
        byteBuffer.position(start);
        for (int i = start - 1; i >= end; i--) {
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

    public static String stringFromBuffer(ByteBuffer buf, Charset charset) {
        CharBuffer cb = charset.decode(buf.duplicate());
        return (cb.toString());
    }
    public static String stringFromBuffer(ByteBuffer buf) {
        Charset charset = StandardCharsets.UTF_8;
        return (stringFromBuffer(buf, charset));
    }
}

