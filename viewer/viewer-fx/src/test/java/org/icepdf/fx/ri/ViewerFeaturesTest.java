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

import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.view.ViewMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The two configuration tiers and the rail rules; no toolkit. */
class ViewerFeaturesTest {

    private static final List<String> ALL = StandardActions.registry().all().stream().map(a -> a.id()).toList();

    @DisplayName("presets: READING has no editing, ANNOTATING adds the tools, FORMS adds filling and signing")
    @Test
    void presets() {
        ViewerFeatures reading = ViewerFeatures.builder(ViewerFeatures.Preset.READING).build();
        assertTrue(reading.allows("document.print") && reading.allows("view.zoom-in") && reading.allows("tool.pan"));
        assertFalse(reading.allows("annotation.highlight") || reading.allows("document.save")
                || reading.allows("edit.undo") || reading.allows("forms.reset") || reading.allows("signature.sign"));

        ViewerFeatures annotating = ViewerFeatures.builder(ViewerFeatures.Preset.ANNOTATING).build();
        assertTrue(annotating.allows("annotation.ink") && annotating.allows("document.save") && annotating.allows("edit.undo"));
        assertFalse(annotating.allows("signature.sign") || annotating.allows("forms.reset"));

        ViewerFeatures forms = ViewerFeatures.builder(ViewerFeatures.Preset.FORMS).build();
        assertTrue(forms.allows("forms.reset") && forms.allows("signature.sign"));
        assertFalse(forms.allows("annotation.note"));

        assertEquals(ALL, ViewerFeatures.full().allowedIds(StandardActions.registry()), "FULL allows every built-in");
    }

    @DisplayName("add and remove take ids and patterns; a removal beats an addition")
    @Test
    void addRemove() {
        ViewerFeatures f = ViewerFeatures.builder(ViewerFeatures.Preset.READING)
                .add("annotation.*", "custom.upload")
                .remove("annotation.ink", "app.*")
                .build();
        assertTrue(f.allows("annotation.highlight") && f.allows("custom.upload"));
        assertFalse(f.allows("annotation.ink"));
        assertFalse(f.allows("app.exit") || f.allows("app.about"));
        ViewerFeatures readd = ViewerFeatures.builder(ViewerFeatures.Preset.FULL)
                .remove("annotation.*").add("annotation.note").build();
        assertFalse(readd.allows("annotation.note"), "a pattern removal still beats an exact addition");
        assertFalse(f.allows((String) null));
    }

    @DisplayName("view modes and panels gate their commands")
    @Test
    void modesAndPanels() {
        ViewerFeatures f = ViewerFeatures.builder(ViewerFeatures.Preset.FULL)
                .viewModes(ViewMode.CONTINUOUS)
                .panels(SidePanel.THUMBNAILS)
                .build();
        assertTrue(f.allows("view.mode.continuous"));
        assertFalse(f.allows("view.mode.facing"));
        assertFalse(f.allows("view.comments"), "no comments panel, no comments command");
        assertFalse(f.allows("search.panel"));
        assertTrue(f.allows("search.find"), "quick find doesn't need the panel");
        assertTrue(f.allows(SidePanel.THUMBNAILS));
        assertFalse(f.allows(SidePanel.LAYERS));
        assertEquals(Set.of(SidePanel.THUMBNAILS), f.panels());
    }

    @DisplayName("features from a product settings file")
    @Test
    void fromProperties() {
        Properties p = new Properties();
        p.setProperty("features.preset", "annotating");
        p.setProperty("features.add", "signature.sign");
        p.setProperty("features.remove", "document.save-as, annotation.ink");
        p.setProperty("features.panels", "thumbnails, comments, nonsense");
        p.setProperty("features.viewModes", "continuous, facing-continuous");
        p.setProperty("features.rail", "tool.select, annotation.highlight");
        p.setProperty("features.customisable", "false");
        ViewerFeatures f = ViewerFeatures.fromProperties(p);
        assertTrue(f.allows("signature.sign") && f.allows("annotation.note"));
        assertFalse(f.allows("document.save-as") || f.allows("annotation.ink"));
        assertEquals(Set.of(SidePanel.THUMBNAILS, SidePanel.COMMENTS), f.panels());
        assertEquals(Set.of(ViewMode.CONTINUOUS, ViewMode.FACING_CONTINUOUS), f.viewModes());
        assertEquals(List.of("tool.select", "annotation.highlight"), f.defaultRail());
        assertFalse(f.isUserCustomisable());
        assertEquals(ALL, ViewerFeatures.fromProperties(new Properties()).allowedIds(StandardActions.registry()),
                "an empty file is everything");
    }

    @DisplayName("a Swing viewer settings file carries over: hidden tools, panels and layouts")
    @Test
    void fromSwingProperties() {
        Properties p = new Properties();
        p.setProperty("application.toolbar.annotation.ink.enabled", "false");
        p.setProperty("application.toolbar.annotation.circle.enabled", "false");
        p.setProperty("application.toolbar.show.rotate", "false");
        p.setProperty("application.toolbar.show.utility.print", "true");
        p.setProperty("application.utilitypane.show.layers", "false");
        p.setProperty("application.statusbar.show.viewmode.double.page", "false");
        ViewerFeatures f = ViewerFeatures.fromSwingProperties(p);
        assertFalse(f.allows("annotation.ink") || f.allows("annotation.ellipse"));
        assertFalse(f.allows("view.rotate-clockwise") || f.allows("view.rotate-counterclockwise"));
        assertTrue(f.allows("document.print") && f.allows("annotation.highlight"));
        assertFalse(f.allows(SidePanel.LAYERS));
        assertTrue(f.allows(SidePanel.COMMENTS));
        assertFalse(f.allows("view.mode.facing"));
        assertTrue(f.allows("view.mode.continuous"));
        // every mapped key names real commands or patterns.
        SwingProperties.ACTIONS.values().forEach(ids -> ids.forEach(id -> assertTrue(
                ALL.stream().anyMatch(a -> ViewerFeatures.matches(List.of(id), a)), "no command for " + id)));
    }

    @DisplayName("rail: the product's default until the user arranges it; overflow is the rest, less hidden")
    @Test
    void rail() {
        ViewerFeatures f = ViewerFeatures.builder(ViewerFeatures.Preset.ANNOTATING).build();
        UserLayout layout = UserLayout.inMemory();
        RailArrangement r = RailArrangement.of(f, layout, ALL);
        assertEquals(f.defaultRail(), r.rail());
        assertTrue(r.overflow().contains("annotation.underline"));
        assertFalse(r.overflow().contains("signature.sign"), "not allowed by ANNOTATING");
        assertFalse(r.overflow().contains("view.zoom-in"), "not a rail candidate");

        layout.setPinned(List.of("annotation.ink", "tool.select", "signature.sign", "annotation.ink"));
        layout.setHidden("annotation.line", true);
        r = RailArrangement.of(f, layout, ALL);
        assertEquals(List.of("annotation.ink", "tool.select"), r.rail(), "user order, allowed only, no duplicates");
        assertFalse(r.overflow().contains("annotation.line"), "hidden");
        assertTrue(r.overflow().contains("tool.pan"), "unpinned default goes to overflow");

        // not customisable: the product's rail, the user's hiding ignored.
        ViewerFeatures fixed = ViewerFeatures.builder(ViewerFeatures.Preset.ANNOTATING).userCustomisable(false).build();
        r = RailArrangement.of(fixed, layout, ALL);
        assertEquals(fixed.defaultRail(), r.rail());
        assertTrue(r.overflow().contains("annotation.line"));

        // a host that can't run a tool doesn't show it.
        r = RailArrangement.of(f, UserLayout.inMemory(), List.of("tool.select", "annotation.highlight"));
        assertEquals(List.of("tool.select", "annotation.highlight"), r.rail());
        assertTrue(r.overflow().isEmpty());
    }

    @DisplayName("user layout: survives save and load; sides stay opposite; reset drops the arrangement")
    @Test
    void userLayout(@TempDir Path dir) {
        Path file = dir.resolve("sub/layout.properties");
        UserLayout layout = UserLayout.load(file);
        assertFalse(layout.isRailCustomised());
        assertEquals(UserLayout.Side.LEFT, layout.getRailSide());
        assertEquals(UserLayout.Side.RIGHT, layout.getPanelSide());
        layout.setPinned(List.of("tool.select", "annotation.highlight"));
        layout.setHidden("annotation.ink", true);
        layout.setRailSide(UserLayout.Side.RIGHT);
        layout.setRailAutoShow(true);
        layout.setPanelPinned(true);
        layout.setPanelHoverOpen(false);
        layout.setPanelWidth(312);
        layout.setLastPanel("comments");
        layout.save();

        UserLayout read = UserLayout.load(file);
        assertTrue(read.isRailCustomised());
        assertEquals(List.of("tool.select", "annotation.highlight"), read.getPinned());
        assertEquals(Set.of("annotation.ink"), read.getHidden());
        assertEquals(UserLayout.Side.RIGHT, read.getRailSide());
        assertEquals(UserLayout.Side.LEFT, read.getPanelSide());
        assertTrue(read.isRailAutoShow() && read.isPanelPinned() && !read.isPanelHoverOpen());
        assertEquals(312, read.getPanelWidth());
        assertEquals("comments", read.getLastPanel());

        read.setHidden("tool.select", true);
        assertEquals(List.of("annotation.highlight"), read.getPinned(), "hiding unpins");
        read.resetRail();
        assertFalse(read.isRailCustomised());
        assertTrue(read.getPinned().isEmpty() && read.getHidden().isEmpty());

        // an empty pinned list the user chose is kept as a choice.
        UserLayout empty = UserLayout.inMemory();
        empty.setPinned(List.of());
        assertTrue(empty.isRailCustomised());
        Properties written = empty.write();
        UserLayout back = UserLayout.inMemory();
        back.read(written);
        assertTrue(back.isRailCustomised() && back.getPinned().isEmpty());
    }
}
