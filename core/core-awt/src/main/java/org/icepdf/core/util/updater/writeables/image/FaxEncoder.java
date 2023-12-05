package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.icepdf.core.pobjects.graphics.images.FaxDecoder.K_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.BLACKIS1_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.DECODE_KEY;

/**
 * Encode the given image stream as a CCITTFax images.   The assumption is that the image stream is already
 * of type CCITTFax or JBig2.
 */
public class FaxEncoder implements ImageEncoder {

    private ImageStream imageStream;

    public FaxEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
    }

    @Override
    public ImageStream encode() throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int rows = image.getHeight();
        int cols = image.getWidth();

        /*
         * The following block avoids some CITTFax color shenanigans and flattens images data with more
         * than 1 color component.
         * Taken from commit 665021ad23690963313c6ce38c2e5e538e9fd85c of 3.0 from
         * org.apache.pdfbox.pdmodel.graphics.image.CCITTFaxFactory.java
         */
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream memoryCacheImageOutputStream =
                     new MemoryCacheImageOutputStream(byteArrayOutputStream)) {
            for (int y = 0; y < rows; ++y) {
                for (int x = 0; x < cols; ++x) {
                    memoryCacheImageOutputStream.writeBits(image.getRGB(x, y), 1);
                }
                int bitOffset = memoryCacheImageOutputStream.getBitOffset();
                if (bitOffset != 0) {
                    memoryCacheImageOutputStream.writeBits(0, 8 - bitOffset);
                }
            }
            memoryCacheImageOutputStream.flush();
        }

        byte[] byteArray = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream = new ByteArrayOutputStream(byteArray.length / 2);
        ByteArrayInputStream input = new ByteArrayInputStream(byteArray);

        CCITTFaxEncoderStream ccittFaxEncoderStream =
                new CCITTFaxEncoderStream(byteArrayOutputStream, cols, rows, TIFFExtension.FILL_LEFT_TO_RIGHT);
        input.transferTo(ccittFaxEncoderStream);
        input.close();
        byteArrayOutputStream.close();

        DictionaryEntries entries = imageStream.getEntries();
        entries.put(Stream.FILTER_KEY, Stream.FILTER_CCITT_FAX_DECODE);
        entries.remove(DECODE_KEY);
        if (imageStream.getEntries().get(Stream.DECODEPARAM_KEY) == null) {
            imageStream.getEntries().put(Stream.DECODEPARAM_KEY, new DictionaryEntries());
        }
        DictionaryEntries decodeParams = imageStream.getLibrary().getDictionary(imageStream.getEntries(),
                Stream.DECODEPARAM_KEY);
        // group 4 encoding
        decodeParams.put(K_KEY, -1);
        decodeParams.put(BLACKIS1_KEY, true);

        imageStream.setRawBytes(byteArrayOutputStream.toByteArray());

        return imageStream;
    }
}
