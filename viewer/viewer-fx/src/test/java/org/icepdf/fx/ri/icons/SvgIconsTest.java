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

import org.icepdf.fx.ri.SidePanel;
import org.icepdf.fx.ri.actions.StandardActions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The placeholder set covers everything the viewer shows; paths are plain SVG path data. */
class SvgIconsTest {

    private static final Pattern PATH = Pattern.compile("[MLHVCSQTAZmlhvcsqtaz0-9.\\s,-]+");

    @DisplayName("every built-in action, panel and UI piece has a placeholder icon")
    @Test
    void coverage() {
        SvgIcons icons = SvgIcons.placeholders();
        StandardActions.registry().all().forEach(a -> assertNotNull(icons.path(a.id()), "no icon for " + a.id()));
        for (SidePanel panel : SidePanel.values()) assertNotNull(icons.path("panel." + panel.id()), panel.id());
        for (String ui : List.of("ui.menu", "ui.more", "ui.settings", "ui.pin", "ui.close")) assertNotNull(icons.path(ui), ui);
        for (String id : icons.ids()) {
            String path = icons.path(id);
            assertTrue(path.startsWith("M") && PATH.matcher(path).matches(), "not path data: " + id);
        }
    }

    @DisplayName("a product's own set reads from properties")
    @Test
    void ownSet() throws Exception {
        SvgIcons own = SvgIcons.from(new ByteArrayInputStream("document.print=M1 1 L23 23\n".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("M1 1 L23 23", own.path("document.print"));
        assertNull(own.path("document.open"));
    }
}
