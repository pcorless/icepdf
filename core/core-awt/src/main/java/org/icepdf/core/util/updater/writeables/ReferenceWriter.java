package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Reference;

import java.io.IOException;

public class ReferenceWriter extends BaseWriter {

    public void write(Reference writeable, CountingOutputStream output) throws IOException {
        writeInteger(writeable.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(writeable.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(REFERENCE);
    }
}
