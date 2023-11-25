package org.icepdf.core.util.redaction;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.updater.writeables.BaseWriter;
import org.icepdf.core.util.updater.writeables.DictionaryWriter;
import org.icepdf.core.util.updater.writeables.image.ImageEncoder;
import org.icepdf.core.util.updater.writeables.image.ImageEncoderFactory;

import java.io.IOException;

/**
 *  Burn the redactionPath into the given inline image content.
 */
public class InlineImageWriter {

    public static final DictionaryWriter dictionaryWriter = new DictionaryWriter();

    public static void write(CountingOutputStream contentOutputStream, ImageStream imageStream) throws IOException {
        ImageEncoder imageEncoder = ImageEncoderFactory.createEncodedImage(imageStream);
        imageStream = imageEncoder.encode();
        byte[] outputData = imageStream.getRawBytes();

        // BI is already in the stream, don't need to write it.

        // write image dictionary
        contentOutputStream.write(BaseWriter.NEWLINE);
        dictionaryWriter.writeInline(imageStream.getEntries(), contentOutputStream);
        // write image data.
        contentOutputStream.write("ID".getBytes());
        contentOutputStream.write(BaseWriter.SPACE);
        contentOutputStream.write(outputData);

        // and we're done
        contentOutputStream.write(BaseWriter.NEWLINE);
        contentOutputStream.write("EI".getBytes());
        contentOutputStream.write(BaseWriter.NEWLINE);
    }
}
