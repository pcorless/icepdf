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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the signature reference dictionary: what a signature claims to cover, and how.
 * <p>
 * The transform method decides which kind of parameters the dictionary carries, and so which class
 * reads them.  DocMDP says the signature certifies the whole document and states what later
 * changes are permitted; FieldMDP says it covers named form fields; UR3 grants usage rights.
 * Reading one as another gives the wrong answer about what a signature actually promised.
 */
public class SignatureReferenceDictionaryTest {

    private final Library library = new Library();

    /**
     * A signature reference with the given transform method, and empty parameters for it.
     */
    private SignatureReferenceDictionary reference(String transformMethod) {
        DictionaryEntries entries = new DictionaryEntries();
        if (transformMethod != null) {
            entries.put(SignatureReferenceDictionary.TRANSFORM_METHOD_KEY, new Name(transformMethod));
        }
        entries.put(SignatureReferenceDictionary.TRANSFORM_PARAMS_KEY, new DictionaryEntries());
        return new SignatureReferenceDictionary(library, entries);
    }

    // ------------------------------------------------------------------
    // the transform method
    // ------------------------------------------------------------------

    @DisplayName("each transform method is recognised by name")
    @Test
    public void transformMethods() {
        assertEquals(SignatureReferenceDictionary.TransformMethods.DocMDP,
                reference("DocMDP").getTransformMethod());
        assertEquals(SignatureReferenceDictionary.TransformMethods.FieldMDP,
                reference("FieldMDP").getTransformMethod());
        assertEquals(SignatureReferenceDictionary.TransformMethods.UR3,
                reference("UR3").getTransformMethod());
    }

    @DisplayName("a method the library does not know is reported as none")
    @Test
    public void unknownTransformMethod() {
        // UR is the pre-1.6 spelling and there may be others; answering null lets the caller treat
        // the reference as one it cannot interpret rather than mistaking it for one it can.
        assertNull(reference("UR").getTransformMethod());
        assertNull(reference("SomethingElse").getTransformMethod());
    }

    @DisplayName("a reference with no transform method has none")
    @Test
    public void missingTransformMethod() {
        assertNull(reference(null).getTransformMethod());
    }

    // ------------------------------------------------------------------
    // the parameters that follow from it
    // ------------------------------------------------------------------

    @DisplayName("the transform method picks the class that reads its parameters")
    @Test
    public void transformParams() {
        assertInstanceOf(DocMDPTransferParam.class, reference("DocMDP").getTransformParams());
        assertInstanceOf(FieldMDPTransferParam.class, reference("FieldMDP").getTransformParams());
        assertInstanceOf(UR3TransferParam.class, reference("UR3").getTransformParams());
    }

    @DisplayName("a reference with no transform method has no parameters, and does not throw")
    @Test
    public void transformParamsWithoutAMethod() {
        // /TransformMethod is required, so a reference without one is malformed - but it arrives
        // from a file, and this used to read the name straight into an equals call.  A signature
        // carrying a damaged reference has to leave the document openable.
        assertNull(reference(null).getTransformParams());
    }

    @DisplayName("a method the library does not know yields no parameters")
    @Test
    public void transformParamsOfUnknownMethod() {
        assertNull(reference("SomethingElse").getTransformParams());
    }

    @DisplayName("the parameters read the entries they were given")
    @Test
    public void parametersAreRead() {
        DictionaryEntries params = new DictionaryEntries();
        params.put(new Name("P"), 2);
        params.put(TransformParams.VERSION_KEY, new Name("1.2"));

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(SignatureReferenceDictionary.TRANSFORM_METHOD_KEY, new Name("DocMDP"));
        entries.put(SignatureReferenceDictionary.TRANSFORM_PARAMS_KEY, params);

        DocMDPTransferParam docMdp = (DocMDPTransferParam)
                new SignatureReferenceDictionary(library, entries).getTransformParams();
        assertNotNull(docMdp);
        assertEquals(2, docMdp.getPermissions());
    }
}
