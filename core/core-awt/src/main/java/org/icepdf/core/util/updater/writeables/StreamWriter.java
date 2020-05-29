package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.Deflater;

public class StreamWriter extends BaseWriter {

    private static final byte[] BEGIN_STREAM = "stream\r\n".getBytes();
    private static final byte[] END_STREAM = "\r\nendstream\r\n".getBytes();

    public void write(Stream obj, SecurityManager securityManager, CountingOutputStream output) throws IOException {
        Reference ref = obj.getPObjectReference();

        byte[] outputData;
        if (!obj.isRawBytesCompressed() &&
                obj.getEntries().containsKey(Stream.FILTER_KEY)) {
            byte[] rawBytes = obj.getRawBytes();
            byte[] decompressedOutput = new byte[rawBytes.length];
            Deflater compressor = new Deflater();
            compressor.setInput(rawBytes);
            compressor.finish();
            int compressedDataLength = compressor.deflate(decompressedOutput);
            outputData = new byte[compressedDataLength];
            System.arraycopy(decompressedOutput, 0, outputData, 0, compressedDataLength);
        } else {
            outputData = obj.getRawBytes();
        }
        if (securityManager != null) {
            HashMap<Name, Object> decodeParams = null;
            if (obj.getEntries().get(Stream.DECODEPARAM_KEY) != null) {
                decodeParams = obj.getLibrary().getDictionary(obj.getEntries(), Stream.DECODEPARAM_KEY);
            } else {
                // default crypt filter
            }
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

        writeInteger(ref.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(BEGIN_OBJECT);

        obj.getEntries().put(Stream.LENGTH_KEY, outputData.length);
        obj.getEntries().put(Stream.FORM_TYPE_KEY, 1);
        writeDictionary(obj, output);

        output.write(BEGIN_STREAM);
        output.write(outputData);
        output.write(END_STREAM);
        output.write(END_OBJECT);
    }
}
