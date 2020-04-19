package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;

import java.awt.geom.AffineTransform;
import java.io.IOException;

public class AffineTransformWriter extends BaseWriter {

    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();

    public void write(AffineTransform writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_ARRAY);
        writeLong((long) writeable.getScaleX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getShearX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getTranslateX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getScaleY(), output);
        output.write(SPACE);
        writeLong((long) writeable.getShearY(), output);
        output.write(SPACE);
        writeLong((long) writeable.getTranslateY(), output);
        output.write(END_ARRAY);
    }
}