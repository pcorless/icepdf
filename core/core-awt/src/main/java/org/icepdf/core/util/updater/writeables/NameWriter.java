package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Name;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NameWriter extends BaseWriter {

    private static final byte[] NAME = "/".getBytes();

    private static final int POUND = 0x23;

    public void write(Name writeable, CountingOutputStream output) throws IOException {
        output.write(NAME);
        byte[] bytes = writeable.getName().getBytes(StandardCharsets.UTF_8);
        for (int b : bytes) {
            b &= 0xFF;
            if (b == POUND || b < 0x21 || b > 0x7E) {
                output.write(POUND);
                int hexVal = ((b >> 4) & 0x0F);
                int hexDigit = hexVal + ((hexVal >= 10) ? 'A' : '0');
                output.write(hexDigit);
                hexVal = (b & 0x0F);
                hexDigit = hexVal + ((hexVal >= 10) ? 'A' : '0');
                output.write(hexDigit);
            } else {
                output.write(b);
            }
        }
    }
}
