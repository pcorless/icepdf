package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;

import java.io.IOException;

public class PObjectWriter extends BaseWriter {

    public void write(PObject writeable, CountingOutputStream output) throws IOException {
        Reference ref = writeable.getReference();

        writeInteger(ref.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(BEGIN_OBJECT);
        writeValue(writeable, output);
        output.write(NEWLINE);
        output.write(END_OBJECT);

    }
}
