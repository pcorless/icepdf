package org.icepdf.ri.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

public final class UriUtils {

    private static final Pattern URL_ESCAPE_PATTERN = Pattern.compile("%[0-9A-Fa-f]{2}");

    private UriUtils() {
    }

    public static String decode(final String source, final Charset charset) {
        final int length = source.length();
        if (length == 0) {
            return source;
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
        boolean changed = false;
        for (int i = 0; i < length; i++) {
            final int ch = source.charAt(i);
            if (ch == '%') {
                if (i + 2 < length) {
                    final char hex1 = source.charAt(i + 1);
                    final char hex2 = source.charAt(i + 2);
                    final int u = Character.digit(hex1, 16);
                    final int l = Character.digit(hex2, 16);
                    if (u == -1 || l == -1) {
                        throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                    }
                    baos.write((char) ((u << 4) + l));
                    i += 2;
                    changed = true;
                } else {
                    throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                }
            } else {
                baos.write(ch);
            }
        }
        return (changed ? new String(baos.toByteArray(), charset) : source);
    }

    public static String encodePath(final String source, final Charset charset) {
        //Avoid double encoding
        if (source == null || source.isEmpty() || URL_ESCAPE_PATTERN.matcher(source).find()) {
            return source;
        }

        final byte[] bytes = source.getBytes(charset);
        boolean original = true;
        for (final byte b : bytes) {
            if (!isAllowedPath(b)) {
                original = false;
                break;
            }
        }
        if (original) {
            return source;
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
        for (final byte b : bytes) {
            if (isAllowedPath(b)) {
                baos.write(b);
            } else {
                baos.write('%');
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                baos.write(hex1);
                baos.write(hex2);
            }
        }
        return new String(baos.toByteArray(), charset);
    }

    private static boolean isAllowedPath(final int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                c == '/' || c == '@' || c == ':' || c == '.' || c == '-' || c == '_' || c == '~' ||
                c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' || c == '*' ||
                c == '+' || c == ',' || c == ';' || c == '=';
    }

}
