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
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceEntry;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CompressedXrefTableWriter extends BaseTableWriter {

    public void writeIncrementalCompressedXrefTable(CrossReferenceRoot crossReferenceRoot,
                                                    SecurityManager securityManager, List<Entry> entries,
                                                    long startingPosition, CountingOutputStream output) throws IOException {
        PTrailer prevTrailer = crossReferenceRoot.getTrailerDictionary();
        DictionaryEntries newTrailer = (DictionaryEntries) prevTrailer.getDictionary().clone();
        this.setPreviousTrailer(newTrailer, crossReferenceRoot);
        int newTrailerSize = this.setTrailerSize(newTrailer, prevTrailer, entries);
        // the xref stream takes that number, so /Size is one more
        newTrailer.put(PTrailer.SIZE_KEY, newTrailerSize + 1);
        long xrefPos = startingPosition + output.getCount();
        newTrailer.remove(Stream.DECODEPARAM_KEY);
        newTrailer.put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);

        writeCompressedXrefTable(securityManager, newTrailer, entries, newTrailerSize, xrefPos, output);
    }

    public void writeFullCompressedXrefTable(CrossReferenceRoot crossReferenceRoot,
                                             SecurityManager securityManager, List<Entry> entries,
                                             long startingPosition, CountingOutputStream output) throws IOException {
        PTrailer prevTrailer = crossReferenceRoot.getTrailerDictionary();
        DictionaryEntries newTrailer = (DictionaryEntries) prevTrailer.getDictionary().clone();

        long xrefPos = startingPosition + output.getCount();
        // clear filter
        newTrailer.remove(Stream.DECODEPARAM_KEY);
        // previous key is no longer valid
        newTrailer.remove(PTrailer.PREV_KEY);
        // remove LibreOffice custom checksum value, should cause trouble but not the PDF specification
        newTrailer.remove(new Name("DocChecksum"));
        newTrailer.put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);
        // size, find max object reference in entries.
        int newTrailerSize = entries.stream().sorted(
                (r1, r2) -> r2.getReference().getObjectNumber() - r1.getReference().getObjectNumber()
        ).collect(Collectors.toList()).get(0).getReference().getObjectNumber();
        // the xref stream is the next object, and /Size is one more than the highest number
        newTrailerSize += 1;
        newTrailer.put(PTrailer.SIZE_KEY, newTrailerSize + 1);

        writeCompressedXrefTable(securityManager, newTrailer, entries, newTrailerSize, xrefPos, output);
    }

    private void writeCompressedXrefTable(SecurityManager securityManager, DictionaryEntries newTrailer,
                                          List<Entry> entries, int newTrailerSize, long xrefPos,
                                          CountingOutputStream output) throws IOException {
        this.closeTableEntries(entries);

        // As narrow as the values allow, as other writers do.  Ghostscript refuses an encrypted
        // document whose cross-reference stream has the fixed [4 8 4] this used to write.
        List<Integer> widths = widths(entries);
        newTrailer.put(PTrailer.W_KEY, widths);

        ArrayList<Integer> index = new ArrayList<>();
        for (int i = 0; i < entries.size(); ) {
            i += createIndexArray(entries, i, index);
        }
        newTrailer.put(PTrailer.INDEX_KEY, index);

        newTrailer.put(Dictionary.TYPE_KEY, CrossReferenceStream.TYPE);

        Stream crossReferenceStream = new Stream(newTrailer, new byte[0]);
        crossReferenceStream.setPObjectReference(new Reference(newTrailerSize, 0));
        byte[] outputData = createXrefDataStream(entries, widths);

        crossReferenceStream.setRawBytes(outputData);
        // never encrypted, even in an encrypted document: see writeCrossReferenceStream.
        streamWriter.writeCrossReferenceStream(crossReferenceStream, output);
        output.write(STARTXREF);
        this.writeLong(xrefPos, output);
        output.write(NEWLINE);
        output.write(COMMENT_EOF);
    }

    static byte[] createXrefDataStream(List<Entry> entries, List<Integer> widths) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            if (entry.isCompressed()) {
                writeField(output, CrossReferenceEntry.TYPE_COMPRESSED, widths.get(0));
                writeField(output, entry.getObjectStreamNumber(), widths.get(1));
                writeField(output, entry.getIndexInObjectStream(), widths.get(2));
            } else {
                writeField(output, CrossReferenceEntry.TYPE_USED, widths.get(0));
                writeField(output, entry.getPosition(), widths.get(1));
                // the generation: always 0 here once, which lost every object with another one
                writeField(output, entry.getReference().getGenerationNumber(), widths.get(2));
            }
        }
        return output.toByteArray();
    }

    /** The /W field widths: one byte for the type, and as few as hold the largest of the other two. */
    static List<Integer> widths(List<Entry> entries) {
        long second = 0;
        long third = 0;
        for (Entry entry : entries) {
            if (entry.isCompressed()) {
                second = Math.max(second, entry.getObjectStreamNumber());
                third = Math.max(third, entry.getIndexInObjectStream());
            } else {
                second = Math.max(second, entry.getPosition());
                third = Math.max(third, entry.getReference().getGenerationNumber());
            }
        }
        return Arrays.asList(1, bytesFor(second), bytesFor(third));
    }

    private static int bytesFor(long value) {
        int bytes = 1;
        while (bytes < 8 && (value >>> (8 * bytes)) != 0) {
            bytes++;
        }
        return bytes;
    }

    /** A big-endian field of the given width; a negative value (a deleted entry's -1) is written as 0. */
    private static void writeField(ByteArrayOutputStream output, long value, int width) {
        long field = Math.max(0, value);
        for (int shift = 8 * (width - 1); shift >= 0; shift -= 8) {
            output.write((int) (field >>> shift) & 0xFF);
        }
    }

    private int createIndexArray(List<Entry> entries, int beginIndex, ArrayList<Integer> index) {
        int beginObjNum = entries.get(beginIndex).getReference().getObjectNumber();
        int subSectionLength = subSectionCount(beginIndex, entries);
        index.add(beginObjNum);
        index.add(subSectionLength);
        return subSectionLength;
    }
}
