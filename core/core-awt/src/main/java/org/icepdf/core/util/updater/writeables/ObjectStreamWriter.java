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
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.ObjectStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Packs objects into object streams (PDF 32000-1 7.5.7) instead of writing each one at the top level
 * of the file.
 * <p>
 * Most of a large document is small dictionaries - structure elements, annotations, outline items -
 * and written one by one they cost several times what they did in the object streams they were read
 * from. Packed, they are compressed together.
 * <p>
 * The object streams are written after everything else, just before the cross-reference stream: only
 * then is the highest object number in the file known, and an object stream needs a number of its own
 * above it. Until then each one is held compressed, which is small. A packed object's entry points at
 * its {@link Group}, and the group learns its number when it is written.
 *
 * @since 8.0.0
 */
class ObjectStreamWriter extends BaseWriter {

    /** Objects per object stream: enough to compress well, few enough to decode one object quickly. */
    static final int MAX_OBJECTS = 128;

    private final List<Group> sealed = new ArrayList<>();
    private Group current;

    /**
     * @param securityManager the save's security manager, null for an unencrypted document.  The
     *                        objects are written with writers of their own that leave strings plain
     *                        (deciphering any read encrypted from the file); the object stream is then
     *                        encrypted as a whole (PDF 32000-1 7.5.7, 7.6.1).
     */
    ObjectStreamWriter(SecurityManager securityManager) {
        this.securityManager = securityManager;
        this.plainStrings = true;
        initializeWriters();
    }

    /**
     * Whether an object may go in an object stream. Streams, objects with a generation other than
     * zero and the encryption dictionary may not (7.5.7). A signature dictionary may, but must not:
     * the signer fills in its /Contents and /ByteRange by patching the written bytes, which it cannot
     * do inside a compressed stream.
     */
    static boolean canPack(PObject pObject) {
        Reference reference = pObject.getReference();
        Object value = pObject.getObject();
        if (reference == null || reference.getGenerationNumber() != 0 || pObject.isDoNotEncrypt()
                || value == null || value instanceof Stream) {
            return false;
        }
        DictionaryEntries entries = value instanceof Dictionary ? ((Dictionary) value).getEntries()
                : value instanceof DictionaryEntries ? (DictionaryEntries) value : null;
        return entries == null || !entries.containsKey(SignatureDictionary.BYTE_RANGE_KEY);
    }

    /**
     * Adds an object to the current object stream.
     *
     * @return the object's cross-reference entry
     */
    Entry add(PObject pObject) throws IOException {
        if (current == null) {
            current = new Group();
        }
        Entry entry = current.add(pObject);
        if (current.count == MAX_OBJECTS) {
            current.seal();
            sealed.add(current);
            current = null;
        }
        return entry;
    }

    /**
     * Writes the object streams, numbering them from {@code firstObjectNumber}.
     *
     * @return their entries, in the order written
     */
    List<Entry> writeAll(int firstObjectNumber, long startingPosition, CountingOutputStream output)
            throws IOException {
        if (current != null) {
            current.seal();
            sealed.add(current);
            current = null;
        }
        List<Entry> entries = new ArrayList<>(sealed.size());
        int objectNumber = firstObjectNumber;
        for (Group group : sealed) {
            group.objectNumber = objectNumber++;
            Reference reference = new Reference(group.objectNumber, 0);
            entries.add(new Entry(reference, startingPosition + output.getCount()));
            DictionaryEntries dictionary = new DictionaryEntries();
            dictionary.put(Dictionary.TYPE_KEY, ObjectStream.TYPE);
            dictionary.put(ObjectStream.N_KEY, group.count);
            dictionary.put(ObjectStream.FIRST_KEY, group.first);
            dictionary.put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);
            Stream stream = new Stream(dictionary, new byte[0]);
            stream.setPObjectReference(reference);
            // the strings in it were written plain: in an encrypted document the stream is what is
            // encrypted, under its own object number.
            byte[] data = securityManager != null ? encryptStream(stream, group.compressed) : group.compressed;
            streamWriter.writeStreamObject(output, stream, data);
            group.compressed = null;
        }
        return entries;
    }

    /** One object stream: the objects in it, and once it is full, its compressed bytes. */
    final class Group {
        private int objectNumber = -1;
        private int count;
        private int first;
        private ByteArrayOutputStream offsets = new ByteArrayOutputStream();
        private ByteArrayOutputStream objects = new ByteArrayOutputStream();
        private CountingOutputStream objectsOutput = new CountingOutputStream(objects);
        private byte[] compressed;

        private Entry add(PObject pObject) throws IOException {
            if (count > 0) {
                objectsOutput.write(NEWLINE);
            }
            String offset = pObject.getReference().getObjectNumber() + " " + objectsOutput.getCount() + " ";
            offsets.write(offset.getBytes(StandardCharsets.ISO_8859_1));
            // the value alone: an object stream holds no "n g obj ... endobj" around it.
            writeValue(pObject, objectsOutput);
            return new Entry(pObject.getReference(), this, count++);
        }

        private void seal() throws IOException {
            offsets.write(NEWLINE);
            first = offsets.size();
            ByteArrayOutputStream out = new ByteArrayOutputStream(objects.size() / 3 + 64);
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(out, deflater)) {
                offsets.writeTo(deflaterOutputStream);
                objects.writeTo(deflaterOutputStream);
            } finally {
                deflater.end();
            }
            compressed = out.toByteArray();
            offsets = null;
            objects = null;
            objectsOutput = null;
        }

        /** The object stream's number; known once it has been written. */
        int getObjectNumber() {
            if (objectNumber < 0) {
                throw new IllegalStateException("object stream not written yet");
            }
            return objectNumber;
        }
    }
}
