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
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A file embedded in a document (PDF 32000-1 7.11.4), as listed in its {@code /EmbeddedFiles} name
 * tree: its name, description, size and dates, and its bytes.
 *
 * @param name        the file name: the specification's Unicode {@code /UF}, else {@code /F}, else
 *                    the name-tree key
 * @param description the specification's {@code /Desc}, or empty
 * @param size        the uncompressed size from the file's {@code /Params}, or -1 when not given
 * @param modified    the file's modification date, or null
 * @param created     the file's creation date, or null
 * @param specification the file specification
 */
public record Attachment(String name, String description, long size, LocalDateTime modified, LocalDateTime created,
                         FileSpecification specification) {

    /** The document's embedded files in name-tree order; empty when it has none. */
    public static List<Attachment> of(Document document) {
        if (document == null) return Collections.emptyList();
        Catalog catalog = document.getCatalog();
        NameTree tree = catalog.getEmbeddedFilesNameTree();
        List<?> pairs = tree != null ? tree.getNamesAndValues() : null;
        if (pairs == null) return Collections.emptyList();
        Library library = catalog.getLibrary();
        List<Attachment> attachments = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            Object key = library.getObject(pairs.get(i));
            Object value = library.getObject(pairs.get(i + 1));
            if (!(value instanceof DictionaryEntries)) continue;
            FileSpecification specification = new FileSpecification(library, (DictionaryEntries) value);
            String keyName = key instanceof StringObject ? Utils.convertStringObject(library, (StringObject) key) : "";
            attachments.add(from(specification, keyName));
        }
        return attachments;
    }

    static Attachment from(FileSpecification specification, String fallbackName) {
        String name = specification.getUnicodeFileSpecification();
        if (name == null || name.isEmpty()) name = specification.getFileSpecification();
        if (name == null || name.isEmpty()) name = fallbackName;
        // a path-like name: only the file part is the name.
        if (name != null) name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        String description = specification.getDescription();
        EmbeddedFileStream stream = streamOf(specification);
        long size = stream != null ? stream.getParamUncompressedSize() : -1;
        LocalDateTime modified = dateTime(stream != null ? stream.getParamLastModifiedData() : null);
        LocalDateTime created = dateTime(stream != null ? stream.getParamCreationData() : null);
        return new Attachment(name != null ? name : "", description != null ? description : "",
                size > 0 ? size : -1, modified, created, specification);
    }

    /** The embedded stream, under {@code /EF /F} or, failing that, {@code /EF /UF}. */
    private static EmbeddedFileStream streamOf(FileSpecification specification) {
        EmbeddedFileStream stream = specification.getEmbeddedFileStream();
        if (stream != null) return stream;
        DictionaryEntries files = specification.getEmbeddedFileDictionary();
        if (files == null) return null;
        Object unicode = specification.getLibrary().getObject(files.get(FileSpecification.UF_KEY));
        return unicode instanceof Stream ? new EmbeddedFileStream(specification.getLibrary(), (Stream) unicode) : null;
    }

    private static LocalDateTime dateTime(PDate date) {
        try {
            return date != null ? date.asLocalDateTime() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True when the file has embedded bytes to save. */
    public boolean hasContent() {
        return streamOf(specification) != null;
    }

    /** True when the name says it's a PDF. */
    public boolean isPdf() {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    /** Writes the file's bytes. */
    public void writeTo(OutputStream out) throws IOException {
        EmbeddedFileStream stream = streamOf(specification);
        if (stream == null) throw new IOException(name + " has no embedded content");
        try (InputStream in = stream.getDecodedStreamData()) {
            if (in == null) throw new IOException(name + " could not be decoded");
            in.transferTo(out);
        }
    }

    /** Writes the file's bytes to {@code path}, replacing it. */
    public void saveTo(Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            writeTo(out);
        }
    }
}
