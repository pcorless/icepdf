package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;

public class StreamWriter extends BaseWriter {

    private static final byte[] BEGIN_STREAM = "stream\r\n".getBytes();
    private static final byte[] END_STREAM = "endstream\r\n".getBytes();

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

            // update the dictionary filter /FlateDecode removing previous values.
            obj.getEntries().put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);

            // check if we need to encrypt the stream
            if (securityManager != null) {
                DictionaryEntries decodeParams = null;
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
        } else {
            outputData = obj.getRawBytes();
        }

        writeInteger(ref.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(BEGIN_OBJECT);

        obj.getEntries().put(Stream.LENGTH_KEY, outputData.length);
        writeDictionary(new PObject(obj, ref), output);
        output.write(NEWLINE);
        output.write(BEGIN_STREAM);
        output.write(outputData);
        output.write(NEWLINE);
        output.write(END_STREAM);
        output.write(END_OBJECT);
    }
}
