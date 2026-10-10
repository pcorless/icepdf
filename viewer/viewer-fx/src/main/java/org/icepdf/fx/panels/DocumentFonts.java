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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.util.Library;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The fonts a document uses, found page by page in the pages' resources (as the Swing viewer's
 * font dialog finds them): each font's name, type and encoding, whether it is embedded or a subset,
 * and - when it isn't embedded - the font the viewer substitutes for it.
 */
public final class DocumentFonts {

    /**
     * One font.
     *
     * @param name        the {@code /BaseFont}, subset prefix included
     * @param type        the font type ({@code /Subtype})
     * @param encoding    the encoding name, or empty
     * @param embedded    true when the document carries the font program
     * @param subset      true when the name has a subset prefix (ABCDEF+)
     * @param substitute  for a font that isn't embedded, the font used instead; else empty
     * @param firstPage   the first page (0-based) it was found on
     */
    public record FontInfo(String name, String type, String encoding, boolean embedded, boolean subset,
                           String substitute, int firstPage) {
    }

    private DocumentFonts() {
    }

    /**
     * Walks every page's font resources, reporting each font once; meant for a background thread.
     *
     * @param cancelled polled between pages
     * @param progress  receives the number of pages done
     * @param found     receives each font as it is found
     */
    public static void scan(Document document, BooleanSupplier cancelled, IntConsumer progress,
                            Consumer<FontInfo> found) throws InterruptedException {
        Library library = document.getCatalog().getLibrary();
        Set<Object> seen = new HashSet<>();
        for (int i = 0, pages = document.getNumberOfPages(); i < pages; i++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return;
            Page page = document.getPageTree().getPage(i);
            page.initPageResources();
            Resources resources = page.getResources();
            Map<Name, Object> fonts = resources != null ? resources.getFonts() : null;
            if (fonts != null) {
                for (Object value : fonts.values()) {
                    Object key = value instanceof Reference ? value : System.identityHashCode(value);
                    if (!seen.add(key)) continue;
                    Object resolved = library.getObject(value);
                    if (resolved instanceof Font) {
                        Font font = (Font) resolved;
                        try {
                            font.init();
                        } catch (RuntimeException e) {
                            // report what can be read without the program
                        }
                        found.accept(describe(font, i));
                    }
                }
            }
            progress.accept(i + 1);
        }
    }

    static FontInfo describe(Font font, int page) {
        String name = font.getBaseFont() != null ? font.getBaseFont() : "(unnamed)";
        boolean subset = name.length() > 7 && name.charAt(6) == '+' && name.substring(0, 6).chars().allMatch(Character::isUpperCase);
        FontDescriptor descriptor = font.getFontDescriptor();
        boolean embedded = descriptor != null && descriptor.getEmbeddedFont() != null;
        String substitute = "";
        if (!embedded) {
            FontFile file = font.getFont();
            if (file != null && file.getName() != null) substitute = file.getName();
        }
        Name type = font.getSubType();
        Name encoding = font.getEncoding();
        return new FontInfo(name, type != null ? type.getName() : "", encoding != null ? encoding.getName() : "",
                embedded, subset, substitute, page);
    }
}
