package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;

import java.io.IOException;
import java.util.List;

public class ArrayWriter extends BaseWriter {

    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();

    public void write(List<Object> writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_ARRAY);
        for (int i = 0, size = writeable.size(); i < size; i++) {
            writeValue(writeable.get(i), output);
            if (i < size - 1) {
                output.write(SPACE);
            }
        }
        output.write(END_ARRAY);
    }
}
