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

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The owner password lifts a document's restrictions; the empty password a document is first tried
 * with doesn't, even when the file has no permissions password; revision 2 permissions follow their
 * own bits; and a missing or wrong password is not logged as an error.  The encrypted documents are
 * built here with real /O and /U values (their objects needn't be encrypted: opening only has to
 * authenticate).
 */
public class OwnerPasswordAndPermissionsTest {

    private static final byte[] FILE_ID = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);
    // print, copy and annotate refused (bits 3, 5 and 6 clear); modify and the rest allowed.
    private static final int RESTRICTED = 0xFFFFFFC0 | 0x0008;

    @TempDir
    Path temp;

    private static List<Object> fileId() {
        List<Object> id = new ArrayList<>();
        id.add(new LiteralStringObject(Utils.convertByteArrayToByteString(FILE_ID)));
        id.add(new LiteralStringObject(Utils.convertByteArrayToByteString(FILE_ID)));
        return id;
    }

    /** /O and /U, as a writer computes them for these passwords, revision 3, 128-bit RC4. */
    private static byte[][] ownerAndUser(String owner, String user, int permissions) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.FILTER_KEY, new Name("Standard"));
        entries.put(EncryptionDictionary.R_KEY, 3);
        entries.put(EncryptionDictionary.V_KEY, 2);
        entries.put(EncryptionDictionary.LENGTH_KEY, 128);
        entries.put(EncryptionDictionary.P_KEY, permissions);
        entries.put(EncryptionDictionary.O_KEY, new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        entries.put(EncryptionDictionary.U_KEY, new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        EncryptionDictionary dictionary = new EncryptionDictionary(new Library(), entries, fileId());
        byte[] o = new StandardEncryption(dictionary).calculateOwnerPassword(owner, user, false);
        entries.put(EncryptionDictionary.O_KEY, new LiteralStringObject(Utils.convertByteArrayToByteString(o)));
        byte[] u = new StandardEncryption(new EncryptionDictionary(new Library(), entries, fileId()))
                .calculateUserPassword(user);
        return new byte[][]{o, u};
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder("<");
        for (byte b : bytes) out.append(String.format("%02X", b & 0xFF));
        return out.append('>').toString();
    }

    /** A one-page PDF with a standard security handler for these passwords. */
    private Path encrypted(String name, String owner, String user, int permissions) throws Exception {
        byte[][] ou = ownerAndUser(owner, user, permissions);
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>",
                "<< /Filter /Standard /V 2 /R 3 /Length 128 /P " + permissions + " /O " + hex(ou[0])
                        + " /U " + hex(ou[1]) + " >>"};
        StringBuilder out = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R /Encrypt 4 0 R /ID [")
                .append(hex(FILE_ID)).append(hex(FILE_ID)).append("] >>\nstartxref\n").append(xref)
                .append("\n%%EOF\n");
        Path file = temp.resolve(name + ".pdf");
        Files.write(file, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static Permissions open(Path file, String password) throws Exception {
        Document document = new Document();
        document.setSecurityCallback(d -> password);
        document.setFile(file.toString());
        try {
            return document.getSecurityManager().getPermissions();
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the user password keeps the document's restrictions; the owner password lifts them")
    @Test
    void ownerLiftsRestrictions() throws Exception {
        Path file = encrypted("owner", "owner", "user", RESTRICTED);
        Permissions asUser = open(file, "user");
        assertFalse(asUser.getPermissions(Permissions.PRINT_DOCUMENT));
        assertFalse(asUser.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertFalse(asUser.getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        Permissions asOwner = open(file, "owner");
        for (int i = Permissions.PRINT_DOCUMENT; i <= Permissions.DOCUMENT_ASSEMBLY; i++) {
            assertTrue(asOwner.getPermissions(i), "owner has permission " + i);
        }
    }

    @DisplayName("no permissions password: the empty password opens, restrictions kept (as poppler)")
    @Test
    void emptyOwnerKeepsRestrictions() throws Exception {
        Path file = encrypted("empty-owner", "", "", RESTRICTED);
        Permissions permissions = open(file, null);
        assertFalse(permissions.getPermissions(Permissions.PRINT_DOCUMENT));
        assertFalse(permissions.getPermissions(Permissions.CONTENT_EXTRACTION));
    }

    @DisplayName("a missing or cancelled password is not logged as SEVERE")
    @Test
    void cancelledPasswordLogsQuietly() throws Exception {
        Path file = encrypted("needs-password", "owner", "user", RESTRICTED);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) {
                records.add(record);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        Logger logger = Logger.getLogger(Document.class.getName());
        logger.addHandler(handler);
        try {
            Document document = new Document();
            document.setSecurityCallback(d -> null);
            assertThrows(PDFSecurityException.class, () -> document.setFile(file.toString()));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(records.stream().noneMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()),
                records.stream().map(r -> r.getLevel() + " " + r.getMessage())
                        .collect(java.util.stream.Collectors.toList()).toString());
    }

    private static Permissions revision2(int p) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.R_KEY, 2);
        entries.put(EncryptionDictionary.P_KEY, p);
        Permissions permissions = new Permissions(new EncryptionDictionary(new Library(), entries, fileId()));
        permissions.init();
        return permissions;
    }

    @DisplayName("revision 2: annotate and fill-in follow bit 6, not the copy bit; quality follows print")
    @Test
    void revision2Permissions() {
        // copy allowed (bit 5), annotate/fill refused (bit 6 clear), print allowed (bit 3)
        Permissions copyNoAnnotate = revision2(0xFFFFFFC0 | 0x04 | 0x10);
        assertTrue(copyNoAnnotate.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertFalse(copyNoAnnotate.getPermissions(Permissions.AUTHORING_FORM_FIELDS), "was the copy bit");
        assertFalse(copyNoAnnotate.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING), "was the copy bit");
        assertTrue(copyNoAnnotate.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY), "prints at full quality");
        // copy refused, annotate/fill allowed, print refused
        Permissions annotateNoCopy = revision2(0xFFFFFFC0 & ~0x04 & ~0x10 | 0x20);
        assertFalse(annotateNoCopy.getPermissions(Permissions.CONTENT_EXTRACTION));
        assertTrue(annotateNoCopy.getPermissions(Permissions.AUTHORING_FORM_FIELDS));
        assertTrue(annotateNoCopy.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING));
        assertFalse(annotateNoCopy.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY), "no print, no quality");
    }
}
