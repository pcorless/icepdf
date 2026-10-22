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

import java.util.Locale;
import java.util.Optional;

/**
 * The parts of {@link PdfViewer} around the document.  An application with its own tool bar leaves
 * them out ({@link ViewerFeatures.Builder#chrome}) and wires its controls to the viewer's actions
 * instead; the side panels themselves stay available on each document
 * ({@link org.icepdf.fx.ri.document.DocumentPane}) to place where it likes.
 */
public enum Chrome {
    /** The header bar: title or tabs, page and zoom fields, quick find, the main menu. */
    HEADER,
    /** The tool rail. */
    TOOL_RAIL,
    /** The side panel strip and its drawer (pages, bookmarks, comments...). */
    SIDE_PANELS;

    /** Lower-case id, as used in settings files ("header", "tool-rail", "side-panels"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<Chrome> of(String id) {
        for (Chrome part : values()) {
            if (part.id().equalsIgnoreCase(id == null ? "" : id.trim())) return Optional.of(part);
        }
        return Optional.empty();
    }
}
