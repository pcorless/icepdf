package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Reference;

import java.io.IOException;
import java.util.List;

public class XRefTableWriter extends BaseTableWriter {

    protected static final byte[] FREE = "f".getBytes();
    protected static final byte[] USED = "n".getBytes();

    public long writeXRefTable(List<Entry> entries, long startingPosition, CountingOutputStream output)
            throws IOException {
        int nextDeletedObjectNumber = this.closeTableEntries(entries);

        // setup start
        Entry zero = new Entry(new Reference(0, 65534));
        zero.setNextDeletedObjectNumber(nextDeletedObjectNumber);
        entries.add(0, zero);

        long xrefPosition = startingPosition + output.getCount();
        output.write(XREF);
        for (int i = 0; i < entries.size(); ) {
            i += writeXrefSubSection(entries, i, output);
        }
        return xrefPosition;
    }

    private int writeXrefSubSection(List<Entry> entries, int beginIndex, CountingOutputStream output) throws IOException {
        int startObjectNumber = entries.get(beginIndex).getReference().getObjectNumber();
        int subSectionLength = subSectionCount(beginIndex, entries);

        // sub section
        writeInteger(startObjectNumber, output);
        output.write(SPACE);
        writeInteger(subSectionLength, output);
        output.write(NEWLINE);

        Entry entry;
        for (int i = beginIndex; i < (beginIndex + subSectionLength); i++) {
            entry = entries.get(i);
            if (entry.isDeleted()) {
                writeZeroPaddedLong(entry.getNextDeletedObjectNumber(), 10, output);
                writeZeroPaddedLong(entry.getReference().getGenerationNumber() + 1, 5, output);
                output.write(FREE);
            } else {
                writeZeroPaddedLong(entry.getPosition(), 10, output);
                writeZeroPaddedLong(entry.getReference().getGenerationNumber(), 5, output);
                output.write(USED);
            }
            output.write(SPACE);
            output.write(NEWLINE);
        }
        return subSectionLength;
    }

    private void writeZeroPaddedLong(long val, int len, CountingOutputStream output) throws IOException {
        String str = Long.toString(val);
        if (str.length() > len) {
            str = str.substring(str.length() - len);
        }
        int padding = len - str.length();
        for (int i = 0; i < padding; i++) {
            output.write('0');
        }
        writeByteString(str, output);
        output.write(SPACE);
    }
}
