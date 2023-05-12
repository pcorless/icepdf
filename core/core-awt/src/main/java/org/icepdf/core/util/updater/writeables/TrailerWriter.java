package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;

import java.io.IOException;
import java.util.List;

public class TrailerWriter extends BaseTableWriter {

    public void writeTrailer(CrossReferenceRoot crossReferenceRoot, long xrefPosition, List<Entry> entries, CountingOutputStream output)
            throws IOException {
        PTrailer prevTrailer = crossReferenceRoot.getTrailerDictionary();
        DictionaryEntries newTrailer = (DictionaryEntries) prevTrailer.getDictionary().clone();
        long previousTrailerPosition = this.setPreviousTrailer(newTrailer, crossReferenceRoot);
        this.setTrailerSize(newTrailer, prevTrailer, entries);
        newTrailer.remove(PTrailer.XREF_STRM_KEY);

        if (previousTrailerPosition == 0) {
            throw new IllegalStateException("Cannot write trailer to an PDF with an invalid object offset");
        }

        output.write(TRAILER);
        this.writeDictionary(new Dictionary(null, newTrailer), output);
        output.write(STARTXREF);
        this.writeLong(xrefPosition, output);
        output.write(COMMENT_EOF);
    }
}
