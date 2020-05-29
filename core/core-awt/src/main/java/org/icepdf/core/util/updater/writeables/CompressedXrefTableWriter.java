package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CompressedXrefTableWriter extends BaseTableWriter {

    private static final List<Integer> WIDTHS = Arrays.asList(4, 8, 4);

    public void writeCompressedXrefTable(PTrailer prevTrailer, SecurityManager securityManager, List<Entry> entries,
                                         long startingPosition, CountingOutputStream output) throws IOException {
        HashMap<Name, Object> newTrailer = (HashMap) prevTrailer.getDictionary().clone();
        this.setPreviousTrailer(newTrailer, prevTrailer);
        int newTrailerSize = this.setTrailerSize(newTrailer, prevTrailer, entries);
        long xrefPos = startingPosition + output.getCount();
        newTrailer.remove(Stream.DECODEPARAM_KEY);
        newTrailer.put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);

        this.closeTableEntries(entries);

        newTrailer.put(CrossReference.W_KEY, WIDTHS);

        ArrayList<Integer> index = new ArrayList<>();
        for (int i = 0; i < entries.size(); ) {
            i += createIndexArray(entries, i, index);
        }
        newTrailer.put(CrossReference.INDEX_KEY, index);

        Stream crossReferenceStream = new Stream(null, newTrailer, new byte[0]);
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
            Utils.writeInteger(output, CrossReference.Entry.TYPE_USED);
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
