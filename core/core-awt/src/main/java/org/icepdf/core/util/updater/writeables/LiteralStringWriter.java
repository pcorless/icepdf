package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.LiteralStringObject;

import java.io.IOException;

public class LiteralStringWriter extends BaseWriter {

    protected static final byte[] BEGIN_LITERAL_STRING = "(".getBytes();
    protected static final byte[] END_LITERAL_STRING = ")".getBytes();

    protected static final String LITERAL_REGEX = "(?=[()\\\\])";
    protected static final String LITERAL_REPLACEMENT = "\\\\";

    public void write(LiteralStringObject writeable, CountingOutputStream output) throws IOException {
        write(writeable.getLiteralString(), output);
    }

    public void write(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        writeByteString(writeable.replaceAll(LITERAL_REGEX, LITERAL_REPLACEMENT), output);
        output.write(END_LITERAL_STRING);
    }
}
