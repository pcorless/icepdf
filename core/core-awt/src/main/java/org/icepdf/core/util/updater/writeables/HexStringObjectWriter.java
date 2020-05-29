package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.HexStringObject;

import java.io.IOException;

public class HexStringObjectWriter extends BaseWriter {

    private static final byte[] BEGIN_HEX_STRING = "<".getBytes();
    private static final byte[] END_HEX_STRING = ">".getBytes();

    private static final String HEX_REGEX = "(?=[<>\\\\])";
    private static final String HEX_REPLACEMENT = "\\\\";


    public void write(HexStringObject writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        writeByteString(writeable.getHexString().replaceAll(HEX_REGEX, HEX_REPLACEMENT), output);
        output.write(END_HEX_STRING);
    }
}