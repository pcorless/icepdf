package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceEntry;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;
import org.icepdf.core.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CompressedXrefTableWriter extends BaseTableWriter {

    private static final List<Integer> WIDTHS = Arrays.asList(4, 8, 4);

    public void writeIncrementalCompressedXrefTable(CrossReferenceRoot crossReferenceRoot,
                                                    SecurityManager securityManager, List<Entry> entries,
                                                    long startingPosition, CountingOutputStream output) throws IOException {
        PTrailer prevTrailer = crossReferenceRoot.getTrailerDictionary();
        DictionaryEntries newTrailer = (DictionaryEntries) prevTrailer.getDictionary().clone();
        this.setPreviousTrailer(newTrailer, crossReferenceRoot);
        int newTrailerSize = this.setTrailerSize(newTrailer, prevTrailer, entries);
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
        // one more for the xref object to be written below
        newTrailerSize += 1;
        newTrailer.put(PTrailer.SIZE_KEY, newTrailerSize);

        writeCompressedXrefTable(securityManager, newTrailer, entries, newTrailerSize, xrefPos, output);
    }

    private void writeCompressedXrefTable(SecurityManager securityManager, DictionaryEntries newTrailer,
                                          List<Entry> entries, int newTrailerSize, long xrefPos,
                                          CountingOutputStream output) throws IOException {
        this.closeTableEntries(entries);

        newTrailer.put(PTrailer.W_KEY, WIDTHS);

        ArrayList<Integer> index = new ArrayList<>();
        for (int i = 0; i < entries.size(); ) {
            i += createIndexArray(entries, i, index);
        }
        newTrailer.put(PTrailer.INDEX_KEY, index);

        newTrailer.put(Dictionary.TYPE_KEY, CrossReferenceStream.TYPE);

        Stream crossReferenceStream = new Stream(newTrailer, new byte[0]);
        crossReferenceStream.setPObjectReference(new Reference(newTrailerSize, 0));
        byte[] outputData = createXrefDataStream(entries);

        crossReferenceStream.setRawBytes(outputData);
        streamWriter.write(crossReferenceStream, securityManager, output);
        output.write(STARTXREF);
        this.writeLong(xrefPos, output);
        output.write(NEWLINE);
        output.write(COMMENT_EOF);
    }

    private byte[] createXrefDataStream(List<Entry> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            Utils.writeInteger(output, CrossReferenceEntry.TYPE_USED);
            Utils.writeLong(output, entry.getPosition());
            Utils.writeInteger(output, 0);
        }
        return output.toByteArray();
    }

    private int createIndexArray(List<Entry> entries, int beginIndex, ArrayList<Integer> index) {
        int beginObjNum = entries.get(beginIndex).getReference().getObjectNumber();
        int subSectionLength = subSectionCount(beginIndex, entries);
        index.add(beginObjNum);
        index.add(subSectionLength);
        return subSectionLength;
    }
}
