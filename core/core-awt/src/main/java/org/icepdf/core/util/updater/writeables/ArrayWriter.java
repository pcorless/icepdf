package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.PObject;

import java.io.IOException;
import java.util.List;

public class ArrayWriter extends BaseWriter {

    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        List<Object> writeable = (List<Object>) pObject.getObject();
        output.write(BEGIN_ARRAY);
        for (int i = 0, size = writeable.size(); i < size; i++) {
            writeValue(new PObject(writeable.get(i), pObject.getReference(), pObject.isDoNotEncrypt()), output);
            if (i < size - 1) {
                output.write(SPACE);
            }
        }
        output.write(END_ARRAY);
    }
}
