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
package org.icepdf.fx.ri;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Name;
import org.icepdf.fx.view.ViewMode;

/**
 * The view a document asks to be opened in: its catalog's {@code /PageLayout} (PDF 32000-1 table 29)
 * as a {@link ViewMode} and whether the first page stands alone.  The "Right" layouts put odd pages
 * on the right, which leaves page 1 on its own - the view's cover page.
 *
 * @param viewMode  the mode to show the document in
 * @param coverPage page 1 alone, the spreads after it
 */
public record PageLayout(ViewMode viewMode, boolean coverPage) {

    /** The layout the catalog names, or null when it names none (or one this viewer doesn't know). */
    public static PageLayout of(Catalog catalog) {
        Object value = catalog != null ? catalog.getObject(Catalog.PAGELAYOUT_KEY) : null;
        return value instanceof Name name ? of(name.getName()) : null;
    }

    /** The layout for a /PageLayout name, or null for an unknown one. */
    public static PageLayout of(String pageLayout) {
        if (pageLayout == null) return null;
        return switch (pageLayout) {
            case "SinglePage" -> new PageLayout(ViewMode.SINGLE_PAGE, false);
            case "OneColumn" -> new PageLayout(ViewMode.CONTINUOUS, false);
            case "TwoColumnLeft" -> new PageLayout(ViewMode.FACING_CONTINUOUS, false);
            case "TwoColumnRight" -> new PageLayout(ViewMode.FACING_CONTINUOUS, true);
            case "TwoPageLeft" -> new PageLayout(ViewMode.FACING, false);
            case "TwoPageRight" -> new PageLayout(ViewMode.FACING, true);
            default -> null;
        };
    }
}
