/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static java.util.zip.Deflater.BEST_COMPRESSION;

public class StreamWriter extends BaseWriter {

    protected static final byte[] BEGIN_STREAM = "stream\n".getBytes();
    protected static final byte[] END_STREAM = "endstream\n".getBytes();

    public StreamWriter(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void write(Stream stream, SecurityManager securityManager, CountingOutputStream output) throws IOException {
        byte[] outputData;
        if (!stream.isRawBytesCompressed() &&
                stream.getEntries().containsKey(Stream.FILTER_KEY)) {

            // compress raw bytes
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
            deflater.setLevel(BEST_COMPRESSION);
            final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater);
            deflaterOutputStream.write(stream.getRawBytes());
            deflaterOutputStream.close();
            byteArrayOutputStream.close();
            outputData = byteArrayOutputStream.toByteArray();

            // update the dictionary filter /FlateDecode removing previous values.
            stream.getEntries().put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);

            // check if we need to encrypt the stream
            if (securityManager != null) {
                outputData = encryptStream(stream, outputData);
            }
        } else {
            outputData = stream.getRawBytes();
        }
        writeStreamObject(output, stream, outputData);
    }

    protected void writeStreamObject(CountingOutputStream output, Stream obj, byte[] outputData) throws IOException {
        Reference ref = obj.getPObjectReference();
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
