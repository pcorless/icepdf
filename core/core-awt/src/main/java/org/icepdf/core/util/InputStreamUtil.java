package org.icepdf.core.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class InputStreamUtil {
    public static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }
}