package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;

import java.util.HashMap;
import java.util.List;

public class BaseTableWriter extends BaseWriter {

    protected static final byte[] XREF = "xref\r\n".getBytes();
    protected static final byte[] TRAILER = "trailer\r\n".getBytes();
    protected static final byte[] STARTXREF = "\r\n\r\nstartxref\r\n".getBytes();
    protected static final byte[] COMMENT_EOF = "\r\n%%EOF\r\n".getBytes();

    protected int subSectionCount(int beginIndex, List<Entry> entries) {
        int beginObjNum = entries.get(beginIndex).getReference().getObjectNumber();
        int nextContiguous = beginObjNum + 1;
        for (int i = beginIndex + 1; i < entries.size(); i++) {
            if (entries.get(i).getReference().getObjectNumber() == nextContiguous) {
                nextContiguous++;
            } else {
                break;
            }
        }
        return nextContiguous - beginObjNum;
    }

    public int closeTableEntries(List<Entry> entries) {
        // close entries by referencing head
        int nextDeletedObjectNumber = 0;
        assert entries != null;
        Entry entry;
        for (int i = entries.size() - 1; i >= 0; i--) {
            entry = entries.get(i);
            if (entry.isDeleted()) {
                entry.setNextDeletedObjectNumber(nextDeletedObjectNumber);
                nextDeletedObjectNumber = entry.getReference().getObjectNumber();
            }
        }
        return nextDeletedObjectNumber;
    }

    public int setTrailerSize(HashMap<Name, Object> newTrailer, PTrailer prevTrailer, List<Entry> entries) {
        int oldSize = prevTrailer.getNumberOfObjects();
        int newEntries = entries != null ? entries.size() : 0;
        int newSize = oldSize + newEntries + 1;
        newTrailer.put(PTrailer.SIZE_KEY, newSize);
        return newSize;
    }

    public long setPreviousTrailer(DictionaryEntries newTrailer, CrossReferenceRoot crossReferenceRoot) {
        long xrefPrevPosition = crossReferenceRoot.getCrossReferences().get(0).getXrefStartPos();
        newTrailer.put(PTrailer.PREV_KEY, xrefPrevPosition);
        return xrefPrevPosition;
    }

}
