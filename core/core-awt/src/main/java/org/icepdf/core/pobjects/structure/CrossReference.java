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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.parser.object.ObjectLoader;

import java.io.IOException;
import java.util.HashMap;

public interface CrossReference {

    PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    int getObjectOffset(ObjectLoader objectLoader, Reference reference)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    CrossReferenceEntry getEntry(Reference reference)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    HashMap<Reference, CrossReferenceEntry> getEntries();

    int getXrefStartPos();

    DictionaryEntries getDictionaryEntries();
}