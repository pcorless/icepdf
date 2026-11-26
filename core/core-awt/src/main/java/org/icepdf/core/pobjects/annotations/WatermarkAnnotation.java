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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;

/**
 * A watermark annotation (PDF 1.6, ISO 32000-1 section 12.5.6.22): content drawn on a page, such as
 * "Draft" or "Confidential", that a viewer shows and prints like page content.
 * <p>
 * The appearance stream is the watermark; this class doesn't regenerate it. Watermarks are created,
 * replaced and removed for a whole document with {@link org.icepdf.core.util.watermark.Watermarks},
 * which keeps one watermark annotation per page and shares one appearance stream between pages of
 * the same size and rotation. The watermark's text is kept in {@code /Contents} so it can be read
 * back and shown in a viewer.
 *
 * @since 8.1
 */
public class WatermarkAnnotation extends Annotation {

    /**
     * Optional fixed-print dictionary: how the watermark is placed when printed on a page of a
     * different size.
     */
    public static final Name FIXED_PRINT_KEY = new Name("FixedPrint");

    public WatermarkAnnotation(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    /**
     * The watermark's text, or an empty string when the annotation doesn't record one (a
     * watermark written by another tool may hold only an appearance).
     */
    public String getText() {
        String text = getContents();
        return text == null ? "" : text;
    }

    /**
     * Does nothing: the appearance is shared between pages and is rebuilt only through
     * {@link org.icepdf.core.util.watermark.Watermarks}, so moving or editing one page's watermark
     * can't change every other page's.
     */
    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {
    }
}
