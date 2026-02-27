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
package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

/**
 *
 */
public class CrossReferenceTable extends CrossReferenceBase<Dictionary> {

    public CrossReferenceTable(Library library, DictionaryEntries dictionaryEntries, int xrefStartPos) {
        super(new Dictionary(library, dictionaryEntries), xrefStartPos);
    }

    public void addEntry(CrossReferenceEntry crossReferenceEntry) {
        int generation;
        if (crossReferenceEntry instanceof CrossReferenceUsedEntry) {
            generation = ((CrossReferenceUsedEntry) crossReferenceEntry).getGenerationNumber();
            indirectObjectReferences.put(new Reference(crossReferenceEntry.objectNumber, generation),
                    crossReferenceEntry);
        }
    }

}