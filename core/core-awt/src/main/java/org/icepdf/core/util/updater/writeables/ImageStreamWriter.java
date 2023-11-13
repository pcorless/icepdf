package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.security.SecurityManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageStreamWriter extends StreamWriter {
    public void write(ImageStream obj, SecurityManager securityManager, CountingOutputStream output) throws IOException {
        byte[] outputData;
        // decoded image is only set if the image was touch via a redaction burn.
        if (obj.getDecodedImage() != null) {
            BufferedImage bufferedImage = obj.getDecodedImage();
            // try and write the image as PNG.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(32 * 1024);
            ImageIO.write(bufferedImage, "jpeg", outputStream);
            outputData = outputStream.toByteArray();

            // update the dictionary filter /FlateDecode removing previous values.
            obj.getEntries().put(Stream.FILTER_KEY, Stream.FILTER_DCT_DECODE);

            // image params for PNG decode
            DictionaryEntries decodeParams = null;
            if (obj.getEntries().get(Stream.DECODEPARAM_KEY) != null) {
                decodeParams = obj.getLibrary().getDictionary(obj.getEntries(), Stream.DECODEPARAM_KEY);
            } else {
                decodeParams = new DictionaryEntries();
//                obj.getEntries().put(Stream.DECODEPARAM_KEY, decodeParams);
            }
//            decodeParams.put(PREDICTOR_VALUE, PredictorDecode.PREDICTOR_PNG_PAETH);

            // check if we need to encrypt the stream
            if (securityManager != null) {
                InputStream decryptedStream = securityManager.encryptInputStream(
                        obj.getPObjectReference(),
                        securityManager.getDecryptionKey(),
                        decodeParams,
                        new ByteArrayInputStream(outputData), true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = decryptedStream.read(data, 0, data.length)) != -1) {
                    out.write(data, 0, nRead);
                }
                outputData = out.toByteArray();
            }
        } else {
            // no modification, just write out the image unaltered.
            outputData = obj.getRawBytes();
        }
        writeStreamObject(output, obj, outputData);
    }
}
