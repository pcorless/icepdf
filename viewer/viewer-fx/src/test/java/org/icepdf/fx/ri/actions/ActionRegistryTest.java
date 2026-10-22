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

import org.icepdf.fx.ri.actions.tools.ToolModeAction;
import org.icepdf.fx.ri.actions.view.ViewModeAction;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The built-in actions and the registry rules; no JavaFX toolkit needed. */
class ActionRegistryTest {

    @DisplayName("every built-in action has a unique, well-formed id and a label from the bundle")
    @Test
    void standardActions() {
        List<ViewerAction> all = StandardActions.registry().all();
        assertTrue(all.size() >= 50, "built-ins: " + all.size());
        Set<String> ids = new HashSet<>();
        for (ViewerAction action : all) {
            assertTrue(ids.add(action.id()), "duplicate " + action.id());
            assertTrue(action.id().matches("[a-z]+(\\.[a-z]+(-[a-z]+)*)+"), "id form: " + action.id());
            assertNotEquals(action.id(), action.label(), "no label in messages.properties for " + action.id());
        }
        // one per view mode and per tool, under their documented ids.
        for (ViewMode mode : ViewMode.values()) assertTrue(ids.contains(ViewModeAction.id(mode)), mode.name());
        for (ToolMode mode : ToolMode.values()) assertTrue(ids.contains(ToolModeAction.id(mode)), mode.name());
        assertTrue(ids.contains("annotation.free-text") && ids.contains("signature.sign") && ids.contains("tool.pan"));
    }

    @DisplayName("register refuses a taken id; replace swaps; remove drops; prefix lookup keeps order")
    @Test
    void registryRules() {
        ActionRegistry registry = StandardActions.registry();
        ViewerAction custom = ViewerAction.of("document.print", "My Print", c -> { });
        assertThrows(IllegalArgumentException.class, () -> registry.register(custom));
        registry.replace(custom);
        assertSame(custom, registry.get("document.print"));
        registry.register(ViewerAction.of("custom.upload", "Upload", c -> { }));
        assertEquals("Upload", registry.get("custom.upload").label());
        registry.remove("custom.upload");
        assertFalse(registry.contains("custom.upload"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("custom.upload"));
        List<String> annotations = registry.idsStartingWith("annotation.");
        assertEquals("annotation.highlight", annotations.get(0));
        assertTrue(annotations.size() >= 9);
        assertTrue(annotations.stream().allMatch(id -> id.startsWith("annotation.")));
    }

    @DisplayName("tool and view-mode actions are radio groups")
    @Test
    void groups() {
        ActionRegistry registry = StandardActions.registry();
        assertEquals(ToolModeAction.GROUP, ((ToggleAction) registry.get("tool.pan")).group());
        assertEquals(ViewModeAction.GROUP, ((ToggleAction) registry.get("view.mode.facing")).group());
        assertNull(((ToggleAction) registry.get("view.side-panel")).group());
    }
}
