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
package org.icepdf.core.pobjects.security;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the document permission flags: what a reader is allowed to do with an encrypted document.
 * <p>
 * The flags live in the two's complement integer {@code /P}, counted from bit 1, with whole ranges
 * of bits reserved and required to be set.  This class reads each permission as a mask holding both
 * the bit in question and the reserved bits, so a permission is granted only when the reserved bits
 * are as the specification requires - which is worth pinning, because it means a file that writes
 * them differently is treated as granting nothing rather than as granting everything.
 */
public class PermissionsTest {

    /** Every reserved bit set, every permission bit clear: the most restrictive legal value. */
    private static final int NOTHING_ALLOWED = 0xFFFFF0C0;

    private static final int PRINT_BIT = 0x4;          // bit 3
    private static final int MODIFY_BIT = 0x8;         // bit 4
    private static final int EXTRACT_BIT = 0x10;       // bit 5
    private static final int MODIFY_TEXT_BIT = 0x20;   // bit 6
    private static final int MODIFY_FORMS_BIT = 0x100; // bit 9
    private static final int ACCESSIBILITY_BIT = 0x200;// bit 10
    private static final int ASSEMBLE_BIT = 0x400;     // bit 11
    private static final int PRINT_QUALITY_BIT = 0x800;// bit 12

    private static Permissions permissions(int revision, int flags) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.FILTER_KEY, new Name("Standard"));
        entries.put(EncryptionDictionary.R_KEY, revision);
        entries.put(EncryptionDictionary.P_KEY, flags);
        Permissions permissions = new Permissions(
                new EncryptionDictionary(new Library(), entries, new ArrayList<>()));
        permissions.init();
        return permissions;
    }

    // ------------------------------------------------------------------
    // revision 3 and later
    // ------------------------------------------------------------------

    @DisplayName("with no permission bits set, nothing is allowed")
    @Test
    public void nothingAllowed() {
        Permissions permissions = permissions(3, NOTHING_ALLOWED);
        assertFalse(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
        assertFalse(permissions.getPermissions(Permissions.MODIFY_DOCUMENT));
        assertFalse(permissions.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertFalse(permissions.getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        assertFalse(permissions.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING));
        assertFalse(permissions.getPermissions(Permissions.CONTENT_ACCESSABILITY));
        assertFalse(permissions.getPermissions(Permissions.DOCUMENT_ASSEMBLY));
        assertFalse(permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY));
    }

    @DisplayName("with every permission bit set, everything is allowed")
    @Test
    public void everythingAllowed() {
        // -1 is every bit set, which is what an unrestricted document carries.
        Permissions permissions = permissions(3, -1);
        assertTrue(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
        assertTrue(permissions.getPermissions(Permissions.MODIFY_DOCUMENT));
        assertTrue(permissions.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertTrue(permissions.getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        assertTrue(permissions.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING));
        assertTrue(permissions.getPermissions(Permissions.CONTENT_ACCESSABILITY));
        assertTrue(permissions.getPermissions(Permissions.DOCUMENT_ASSEMBLY));
        assertTrue(permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY));
    }

    @DisplayName("each permission bit grants its own permission and no other")
    @Test
    public void bitsAreIndependent() {
        // Confusing two of these grants something the author withheld, which is the whole point of
        // the flags; each is therefore checked on its own against a baseline of nothing allowed.
        assertTrue(permissions(3, NOTHING_ALLOWED | PRINT_BIT)
                .getPermissions(Permissions.PRINT_DOCUMENT));
        assertFalse(permissions(3, NOTHING_ALLOWED | PRINT_BIT)
                .getPermissions(Permissions.MODIFY_DOCUMENT));

        assertTrue(permissions(3, NOTHING_ALLOWED | MODIFY_BIT)
                .getPermissions(Permissions.MODIFY_DOCUMENT));
        assertFalse(permissions(3, NOTHING_ALLOWED | MODIFY_BIT)
                .getPermissions(Permissions.PRINT_DOCUMENT));

        assertTrue(permissions(3, NOTHING_ALLOWED | EXTRACT_BIT)
                .getPermissions(Permissions.CONTENT_EXTRACTION));
        assertTrue(permissions(3, NOTHING_ALLOWED | MODIFY_TEXT_BIT)
                .getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        assertTrue(permissions(3, NOTHING_ALLOWED | MODIFY_FORMS_BIT)
                .getPermissions(Permissions.FORM_FIELD_FILL_SIGNING));
        assertTrue(permissions(3, NOTHING_ALLOWED | ACCESSIBILITY_BIT)
                .getPermissions(Permissions.CONTENT_ACCESSABILITY));
        assertTrue(permissions(3, NOTHING_ALLOWED | ASSEMBLE_BIT)
                .getPermissions(Permissions.DOCUMENT_ASSEMBLY));
        assertTrue(permissions(3, NOTHING_ALLOWED | PRINT_QUALITY_BIT)
                .getPermissions(Permissions.PRINT_DOCUMENT_QUALITY));
    }

    @DisplayName("the usual print-only document allows printing and nothing else")
    @Test
    public void printOnly() {
        Permissions permissions = permissions(3, NOTHING_ALLOWED | PRINT_BIT | PRINT_QUALITY_BIT);
        assertTrue(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
        assertTrue(permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY));
        assertFalse(permissions.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertFalse(permissions.getPermissions(Permissions.MODIFY_DOCUMENT));
    }

    // ------------------------------------------------------------------
    // revision 2
    // ------------------------------------------------------------------

    @DisplayName("revision 2 has fewer bits, and derives the rest from the ones it has")
    @Test
    public void revisionTwo() {
        // Revision 2 has no separate bits for form filling, accessibility or assembly, so they
        // follow from extraction and modification respectively.
        Permissions extraction = permissions(2, NOTHING_ALLOWED | EXTRACT_BIT);
        assertTrue(extraction.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertTrue(extraction.getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        assertTrue(extraction.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING));
        assertTrue(extraction.getPermissions(Permissions.CONTENT_ACCESSABILITY));
        assertFalse(extraction.getPermissions(Permissions.DOCUMENT_ASSEMBLY));

        Permissions modify = permissions(2, NOTHING_ALLOWED | MODIFY_BIT);
        assertTrue(modify.getPermissions(Permissions.MODIFY_DOCUMENT));
        assertTrue(modify.getPermissions(Permissions.DOCUMENT_ASSEMBLY));
        assertFalse(modify.getPermissions(Permissions.CONTENT_EXTRACTION));
    }

    // ------------------------------------------------------------------
    // reserved bits and bounds
    // ------------------------------------------------------------------

    @DisplayName("a permission bit set while the reserved bits are not grants nothing")
    @Test
    public void reservedBitsAreRequired() {
        // Each permission is read as a mask holding the reserved bits as well as its own, so a /P
        // that does not set the reserved bits reads as granting nothing at all.  That is the safe
        // direction to be wrong in, but it does mean a producer that writes /P as a small positive
        // number has its permissions ignored rather than honoured.
        Permissions permissions = permissions(3, PRINT_BIT);
        assertFalse(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
    }

    @DisplayName("an index outside the permission set answers false rather than throwing")
    @Test
    public void indexOutOfBounds() {
        // The method takes a bare int, so a caller can hand it anything.
        Permissions permissions = permissions(3, -1);
        assertFalse(permissions.getPermissions(-1));
        assertFalse(permissions.getPermissions(8), "one past the last permission");
        assertFalse(permissions.getPermissions(Integer.MAX_VALUE));
    }

    @DisplayName("permissions are computed on first use without an explicit init")
    @Test
    public void lazyInit() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.R_KEY, 3);
        entries.put(EncryptionDictionary.P_KEY, -1);
        Permissions permissions = new Permissions(
                new EncryptionDictionary(new Library(), entries, new ArrayList<>()));
        assertTrue(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
    }
}
