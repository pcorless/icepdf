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
package org.icepdf.fx.ri.actions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleViewerContextTest {

    @Test
    void offersOnlyWhatIsHandedOver() {
        ActionRegistry registry = StandardActions.registry();
        SimpleViewerContext context = ViewerContext.of(null);
        for (String id : List.of("search.find", "document.open", "document.save", "document.save-as",
                "document.export-redacted", "document.close", "search.panel", "view.side-panel", "app.exit")) {
            assertFalse(registry.get(id).isAvailable(context), id);
        }
        // the view's own commands need nothing from the host.
        assertTrue(registry.get("navigation.next").isAvailable(context));
        assertTrue(registry.get("edit.copy").isAvailable(context));

        List<String> ran = new ArrayList<>();
        context.onFind(() -> ran.add("find")).onSave(() -> ran.add("save"), null);
        assertTrue(registry.get("search.find").isAvailable(context));
        assertTrue(registry.get("document.save").isAvailable(context) && registry.get("document.save-as").isAvailable(context));
        // the search panel is a side panel, not Find.
        assertFalse(registry.get("search.panel").isAvailable(context));
        assertFalse(registry.get("document.export-redacted").isAvailable(context));

        registry.get("search.find").execute(context);
        registry.get("document.save-as").execute(context);
        assertEquals(List.of("find", "save"), ran);

        context.onFind(null);
        assertFalse(registry.get("search.find").isAvailable(context));
        assertThrows(UnsupportedOperationException.class, context::openDocument);
    }
}
