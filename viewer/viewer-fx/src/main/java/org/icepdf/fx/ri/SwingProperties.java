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

import org.icepdf.fx.view.ViewMode;

import java.util.*;

/**
 * The Swing viewer's show/hide settings ({@code ViewerPropertiesManager}'s {@code application.*}
 * keys) mapped to this viewer's command ids and panels, for products moving over.  A key set to
 * {@code false} removes what it hid.  Swing settings with no counterpart here (status bar parts,
 * preference tabs, redaction, link and arrow tools, annotation flags) are ignored.
 */
public final class SwingProperties {

    /** Swing key -> command ids or patterns it hides. */
    static final Map<String, List<String>> ACTIONS = new LinkedHashMap<>();
    /** Swing key -> side panel it hides. */
    static final Map<String, SidePanel> PANELS = new LinkedHashMap<>();
    /** Swing key -> page layout it hides. */
    static final Map<String, ViewMode> VIEW_MODES = new LinkedHashMap<>();

    static {
        ACTIONS.put("application.toolbar.show.utility.open", List.of("document.open"));
        ACTIONS.put("application.toolbar.show.utility.save", List.of("document.save", "document.save-as"));
        ACTIONS.put("application.toolbar.show.utility.print", List.of("document.print"));
        ACTIONS.put("application.toolbar.show.utility.search", List.of("search.*"));
        ACTIONS.put("application.toolbar.show.utility.upane", List.of("view.side-panel"));
        ACTIONS.put("application.toolbar.show.pagenav", List.of("navigation.*"));
        ACTIONS.put("application.toolbar.show.zoom", List.of("view.zoom-in", "view.zoom-out", "view.actual-size"));
        ACTIONS.put("application.toolbar.show.fit", List.of("view.fit-page", "view.fit-width"));
        ACTIONS.put("application.toolbar.show.fullscreen", List.of("view.full-screen"));
        ACTIONS.put("application.toolbar.show.rotate", List.of("view.rotate-clockwise", "view.rotate-counterclockwise"));
        ACTIONS.put("application.toolbar.show.tool", List.of("tool.*"));
        ACTIONS.put("application.toolbar.show.annotation", List.of("annotation.*", "edit.delete-annotation"));
        ACTIONS.put("application.toolbar.show.forms", List.of("forms.*", "view.highlight-fields"));
        ACTIONS.put("application.toolbar.show.search", List.of("search.*"));
        ACTIONS.put("application.toolbar.annotation.delete.enabled", List.of("edit.delete-annotation"));
        ACTIONS.put("application.toolbar.annotation.highlight.enabled", List.of("annotation.highlight"));
        ACTIONS.put("application.toolbar.annotation.underline.enabled", List.of("annotation.underline"));
        ACTIONS.put("application.toolbar.annotation.strikeout.enabled", List.of("annotation.strike-out"));
        ACTIONS.put("application.toolbar.annotation.line.enabled", List.of("annotation.line"));
        ACTIONS.put("application.toolbar.annotation.rectangle.enabled", List.of("annotation.rectangle"));
        ACTIONS.put("application.toolbar.annotation.circle.enabled", List.of("annotation.ellipse"));
        ACTIONS.put("application.toolbar.annotation.ink.enabled", List.of("annotation.ink"));
        ACTIONS.put("application.toolbar.annotation.freetext.enabled", List.of("annotation.free-text"));
        ACTIONS.put("application.toolbar.annotation.text.enabled", List.of("annotation.note"));
        ACTIONS.put("application.toolbar.annotation.signature", List.of("signature.*"));
        ACTIONS.put("application.toolbar.show.resentfiles", List.of());   // recent files: the app's menu

        PANELS.put("application.utilitypane.show.bookmarks", SidePanel.BOOKMARKS);
        PANELS.put("application.utilitypane.show.attachments", SidePanel.ATTACHMENTS);
        PANELS.put("application.utilitypane.show.search", SidePanel.SEARCH);
        PANELS.put("application.utilitypane.show.thumbs", SidePanel.THUMBNAILS);
        PANELS.put("application.utilitypane.show.layers", SidePanel.LAYERS);
        PANELS.put("application.utilitypane.show.signatures", SidePanel.SIGNATURES);
        PANELS.put("application.utilitypane.show.annotation", SidePanel.COMMENTS);
        PANELS.put("application.utilitypane.show.annotation.markup", SidePanel.COMMENTS);

        VIEW_MODES.put("application.statusbar.show.viewmode.singlepage", ViewMode.SINGLE_PAGE);
        VIEW_MODES.put("application.statusbar.show.viewmode.single.page.continuous", ViewMode.CONTINUOUS);
        VIEW_MODES.put("application.statusbar.show.viewmode.double.page", ViewMode.FACING);
        VIEW_MODES.put("application.statusbar.show.viewmode.double.page.continuous", ViewMode.FACING_CONTINUOUS);
    }

    private SwingProperties() {
    }

    /** The Swing keys this viewer understands. */
    public static Set<String> keys() {
        Set<String> keys = new LinkedHashSet<>(ACTIONS.keySet());
        keys.addAll(PANELS.keySet());
        keys.addAll(VIEW_MODES.keySet());
        return keys;
    }

    static void apply(Properties properties, ViewerFeatures.Builder builder) {
        ACTIONS.forEach((key, ids) -> {
            if (hidden(properties, key)) builder.remove(ids.toArray(new String[0]));
        });
        EnumSet<SidePanel> panels = EnumSet.allOf(SidePanel.class);
        PANELS.forEach((key, panel) -> {
            if (hidden(properties, key)) panels.remove(panel);
        });
        if (panels.size() < SidePanel.values().length) builder.panels(panels.toArray(new SidePanel[0]));
        EnumSet<ViewMode> modes = EnumSet.allOf(ViewMode.class);
        VIEW_MODES.forEach((key, mode) -> {
            if (hidden(properties, key)) modes.remove(mode);
        });
        if (!modes.isEmpty() && modes.size() < ViewMode.values().length) builder.viewModes(modes.toArray(new ViewMode[0]));
    }

    private static boolean hidden(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value != null && !Boolean.parseBoolean(value.trim());
    }
}
