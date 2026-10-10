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
package org.icepdf.fx.panels;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.CryptFilter;
import org.icepdf.core.pobjects.security.CryptFilterEntry;
import org.icepdf.core.pobjects.security.EncryptionDictionary;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a document properties dialog shows, read from a document: its description (information
 * dictionary, file, version, pages), its security (encryption and permissions) and its custom
 * information entries.  Each section is an ordered list of label / value rows.
 */
public final class DocumentProperties {

    /** One label / value line. */
    public record Row(String label, String value) {
    }

    private static final DateTimeFormatter DATES = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

    private DocumentProperties() {
    }

    /** File, information dictionary, PDF version, page count and first page size. */
    public static List<Row> description(Document document) {
        List<Row> rows = new ArrayList<>();
        String location = document.getDocumentLocation();
        File file = location != null ? new File(location) : null;
        add(rows, "File", file != null ? file.getName() : null);
        add(rows, "Location", file != null && file.getParent() != null ? file.getParent() : location);
        if (file != null && file.isFile()) {
            try {
                long bytes = Files.size(file.toPath());
                add(rows, "File size", AttachmentPanel.formatSize(bytes) + " (" + String.format("%,d", bytes) + " bytes)");
            } catch (java.io.IOException ignored) {
                // no size then
            }
        }
        PInfo info = document.getInfo();
        if (info != null) {
            add(rows, "Title", info.getTitle());
            add(rows, "Author", info.getAuthor());
            add(rows, "Subject", info.getSubject());
            add(rows, "Keywords", info.getKeywords());
            add(rows, "Created", date(info.getCreationDate()));
            add(rows, "Modified", date(info.getModDate()));
            add(rows, "Application", info.getCreator());
            add(rows, "PDF producer", info.getProducer());
        }
        Library library = document.getCatalog().getLibrary();
        if (library.getFileHeader() != null && library.getFileHeader().getVersion() > 0) {
            add(rows, "PDF version", String.valueOf(library.getFileHeader().getVersion()));
        }
        add(rows, "Pages", String.valueOf(document.getNumberOfPages()));
        if (document.getNumberOfPages() > 0) {
            PDimension size = document.getPageDimension(0, 0);
            add(rows, "Page size", pageSize(size.getWidth(), size.getHeight()));
        }
        add(rows, "Tagged PDF", tagged(document) ? "Yes" : "No");
        add(rows, "Encrypted", library.getSecurityManager() != null ? "Yes" : "No");
        return rows;
    }

    /** Encryption method and what the permissions allow; "No security" when it isn't encrypted. */
    public static List<Row> security(Document document) {
        List<Row> rows = new ArrayList<>();
        SecurityManager securityManager = document.getCatalog().getLibrary().getSecurityManager();
        if (securityManager == null) {
            rows.add(new Row("Security method", "No security"));
            return rows;
        }
        EncryptionDictionary encryption = securityManager.getEncryptionDictionary();
        Name filter = encryption != null ? encryption.getPreferredSecurityHandlerName() : null;
        rows.add(new Row("Security method", filter != null && "Standard".equals(filter.getName())
                ? "Password security" : filter != null ? filter.getName() : "Unknown"));
        if (encryption != null) rows.add(new Row("Encryption", encryptionMethod(encryption)));
        Permissions permissions = securityManager.getPermissions();
        if (permissions != null) {
            boolean print = permissions.getPermissions(Permissions.PRINT_DOCUMENT);
            boolean highQuality = permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY);
            rows.add(new Row("Printing", !print ? "Not allowed" : highQuality ? "Allowed" : "Low resolution only"));
            rows.add(new Row("Changing the document", allowed(permissions, Permissions.MODIFY_DOCUMENT)));
            rows.add(new Row("Document assembly", allowed(permissions, Permissions.DOCUMENT_ASSEMBLY)));
            rows.add(new Row("Content copying", allowed(permissions, Permissions.CONTENT_EXTRACTION)));
            rows.add(new Row("Content copying for accessibility", allowed(permissions, Permissions.CONTENT_ACCESSABILITY)));
            rows.add(new Row("Commenting", allowed(permissions, Permissions.AUTHORING_FORM_FIELDS)));
            rows.add(new Row("Filling of form fields", allowed(permissions, Permissions.FORM_FIELD_FILL_SIGNING)));
            rows.add(new Row("Signing", allowed(permissions, Permissions.FORM_FIELD_FILL_SIGNING)));
        }
        return rows;
    }

    /** The information dictionary's entries beyond the standard ones, by key. */
    public static Map<String, String> custom(Document document) {
        Map<String, String> custom = new LinkedHashMap<>();
        PInfo info = document.getInfo();
        if (info == null) return custom;
        Library library = document.getCatalog().getLibrary();
        for (Map.Entry<Object, Object> entry : info.getAllCustomExtensions().entrySet()) {
            Object value = library.getObject(entry.getValue());
            String text = value instanceof StringObject ? Utils.convertStringObject(library, (StringObject) value)
                    : value != null ? value.toString() : "";
            custom.put(String.valueOf(entry.getKey()), text);
        }
        return custom;
    }

    private static String allowed(Permissions permissions, int which) {
        return permissions.getPermissions(which) ? "Allowed" : "Not allowed";
    }

    /** RC4 or AES and the key length, from /V and the standard crypt filter's /CFM. */
    static String encryptionMethod(EncryptionDictionary encryption) {
        int version = encryption.getVersion();
        int bits = encryption.getKeyLength();
        if (version == 5) return "256-bit AES";
        if (version == 4) {
            CryptFilter filters = encryption.getCryptFilter();
            Name stream = encryption.getStmF();
            CryptFilterEntry entry = filters != null && stream != null ? filters.getCryptFilterByName(stream) : null;
            Name method = entry != null ? entry.getCryptFilterMethod() : null;
            if (method != null && method.getName().startsWith("AES")) return "128-bit AES";
            return "128-bit RC4";
        }
        return (bits > 0 ? bits : 40) + "-bit RC4";
    }

    private static boolean tagged(Document document) {
        Library library = document.getCatalog().getLibrary();
        Object markInfo = library.getObject(document.getCatalog().getEntries(), new Name("MarkInfo"));
        if (markInfo instanceof DictionaryEntries) {
            Object marked = library.getObject((DictionaryEntries) markInfo, new Name("Marked"));
            return Boolean.TRUE.equals(marked);
        }
        return false;
    }

    /** "8.50 × 11.00 in (215.9 × 279.4 mm)". */
    static String pageSize(double widthPt, double heightPt) {
        return String.format("%.2f × %.2f in (%.1f × %.1f mm)", widthPt / 72, heightPt / 72,
                widthPt / 72 * 25.4, heightPt / 72 * 25.4);
    }

    private static String date(PDate date) {
        if (date == null) return null;
        try {
            LocalDateTime time = date.asLocalDateTime();
            return time != null ? DATES.format(time) : date.toString();
        } catch (RuntimeException e) {
            return date.toString();
        }
    }

    private static void add(List<Row> rows, String label, String value) {
        if (value != null && !value.trim().isEmpty()) rows.add(new Row(label, value.trim()));
    }
}
