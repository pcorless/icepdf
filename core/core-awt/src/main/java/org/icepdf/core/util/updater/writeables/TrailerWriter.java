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
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;

import java.io.IOException;
import java.util.List;

public class TrailerWriter extends BaseTableWriter {

    public void writeIncrementalUpdateTrailer(CrossReferenceRoot crossReferenceRoot, long xrefPosition, List<Entry> entries, CountingOutputStream output)
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
        this.writeDictionary(new PObject(new Dictionary(null, newTrailer), null, true), output);
        output.write(STARTXREF);
        this.writeLong(xrefPosition, output);
        output.write(COMMENT_EOF);
    }

    public void writeFullTrailer(CrossReferenceRoot crossReferenceRoot, long xrefPosition, List<Entry> entries, CountingOutputStream output)
            throws IOException {
        PTrailer prevTrailer = crossReferenceRoot.getTrailerDictionary();
        DictionaryEntries newTrailer = (DictionaryEntries) prevTrailer.getDictionary().clone();
        this.setTrailerSize(newTrailer, prevTrailer, entries);
        newTrailer.remove(PTrailer.XREF_STRM_KEY);
        newTrailer.remove(PTrailer.PREV_KEY);

        // todo update the ID with something fun.

        output.write(TRAILER);
        this.writeDictionary(new PObject(new Dictionary(null, newTrailer), null, true), output);
        output.write(STARTXREF);
        this.writeLong(xrefPosition, output);
        output.write(COMMENT_EOF);
    }
}
