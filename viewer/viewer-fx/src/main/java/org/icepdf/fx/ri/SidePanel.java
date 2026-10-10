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

/** The side (utility) panels a viewer can offer; each is shown only when the document has content for it. */
public enum SidePanel {
    THUMBNAILS, BOOKMARKS, COMMENTS, ATTACHMENTS, LAYERS, SIGNATURES, SEARCH;

    /** Lower-case id, as used in settings files and {@code ViewerContext.showSidePanel} ("thumbnails"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SidePanel> of(String id) {
        for (SidePanel panel : values()) {
            if (panel.id().equalsIgnoreCase(id == null ? "" : id.trim())) return Optional.of(panel);
        }
        return Optional.empty();
    }
}
