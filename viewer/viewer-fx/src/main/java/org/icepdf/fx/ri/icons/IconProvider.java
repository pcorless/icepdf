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
package org.icepdf.fx.ri.icons;

import javafx.scene.Node;

/**
 * Icons for the viewer's commands and parts, by id: action ids ("document.print"), side panels
 * ("panel.comments") and UI pieces ("ui.menu", "ui.pin").  The default is {@link SvgIcons#placeholders()};
 * a product swaps the whole set or just some icons, chaining to the default for the rest:
 * <pre>{@code
 * IconProvider icons = SvgIcons.from(myIconsFile).orElse(SvgIcons.placeholders());
 * }</pre>
 */
@FunctionalInterface
public interface IconProvider {

    /**
     * A new icon node, {@code size} pixels square, or null when this provider has none for the id.
     * Icons carry the style class {@code icon} so a theme can colour them.
     */
    Node icon(String id, double size);

    /** This provider, then {@code fallback} for ids it has no icon for. */
    default IconProvider orElse(IconProvider fallback) {
        return (id, size) -> {
            Node icon = icon(id, size);
            return icon != null ? icon : fallback.icon(id, size);
        };
    }

    /** A provider with no icons (text-only controls). */
    static IconProvider none() {
        return (id, size) -> null;
    }
}
