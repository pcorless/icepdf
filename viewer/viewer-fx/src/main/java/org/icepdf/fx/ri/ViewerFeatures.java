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

import org.icepdf.fx.ri.actions.ActionRegistry;
import org.icepdf.fx.ri.actions.tools.ToolModeAction;
import org.icepdf.fx.ri.actions.view.ViewModeAction;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.util.*;

/**
 * What a product lets its users have: which commands (action ids), side panels and page layouts
 * exist, which tools sit on the tool rail by default, and whether users may rearrange it.  This is
 * the first of two tiers; {@link UserLayout} - the user's own arrangement - only works within it.
 * <p>
 * Commands are matched by id or by prefix pattern ({@code "annotation.*"}).  A removal always beats
 * an addition, whatever the order.
 * <pre>{@code
 * ViewerFeatures features = ViewerFeatures.builder(ViewerFeatures.Preset.ANNOTATING)
 *         .add("signature.sign")
 *         .remove("document.save-as", "annotation.ink")
 *         .panels(SidePanel.THUMBNAILS, SidePanel.COMMENTS)
 *         .build();
 * }</pre>
 */
public final class ViewerFeatures {

    /** Starting points. */
    public enum Preset {
        /** Read, search, print, navigate; no editing. */
        READING,
        /** Reading plus the annotation tools, undo/redo and save. */
        ANNOTATING,
        /** Reading plus form filling, reset, signing and save. */
        FORMS,
        /** Everything. */
        FULL
    }

    private static final List<String> READING_ACTIONS = List.of("document.open", "document.print",
            "document.properties", "document.close", "app.*", "edit.copy", "edit.select-all", "search.*", "view.*",
            "navigation.*", "tool.*");
    private static final List<String> SAVING = List.of("document.save", "document.save-as", "edit.undo", "edit.redo");

    private final Set<String> includes;
    private final Set<String> excludes;
    private final EnumSet<SidePanel> panels;
    private final EnumSet<ViewMode> viewModes;
    private final List<String> defaultRail;
    private final List<String> railCandidates;
    private final boolean userCustomisable;

    private ViewerFeatures(Builder b) {
        includes = Collections.unmodifiableSet(new LinkedHashSet<>(b.includes));
        excludes = Collections.unmodifiableSet(new LinkedHashSet<>(b.excludes));
        panels = EnumSet.copyOf(b.panels.isEmpty() ? EnumSet.allOf(SidePanel.class) : b.panels);
        viewModes = EnumSet.copyOf(b.viewModes.isEmpty() ? EnumSet.allOf(ViewMode.class) : b.viewModes);
        defaultRail = List.copyOf(b.defaultRail);
        railCandidates = List.copyOf(b.railCandidates);
        userCustomisable = b.userCustomisable;
    }

    /** Everything, rearrangeable by the user. */
    public static ViewerFeatures full() {
        return builder(Preset.FULL).build();
    }

    public static Builder builder(Preset preset) {
        return new Builder(preset);
    }

    /** Whether a command is part of this product. */
    public boolean allows(String actionId) {
        if (actionId == null || matches(excludes, actionId) || !matches(includes, actionId)) return false;
        // a page layout that isn't offered, isn't offered as a command either.
        for (ViewMode mode : ViewMode.values()) {
            if (actionId.equals(ViewModeAction.id(mode))) return viewModes.contains(mode);
        }
        // the comments command needs the comments panel.
        if (actionId.equals("view.comments")) return panels.contains(SidePanel.COMMENTS);
        if (actionId.startsWith("search.panel")) return panels.contains(SidePanel.SEARCH);
        return true;
    }

    /** The allowed ids among a registry's actions, in registry order. */
    public List<String> allowedIds(ActionRegistry registry) {
        List<String> ids = new ArrayList<>();
        registry.all().forEach(a -> {
            if (allows(a.id())) ids.add(a.id());
        });
        return ids;
    }

    public boolean allows(SidePanel panel) {
        return panels.contains(panel);
    }

    public Set<SidePanel> panels() {
        return Collections.unmodifiableSet(panels);
    }

    public Set<ViewMode> viewModes() {
        return Collections.unmodifiableSet(viewModes);
    }

    /** Tools on the rail before the user changes anything (only allowed ones count). */
    public List<String> defaultRail() {
        return defaultRail;
    }

    /** Patterns of the commands that may go on the tool rail ("tool.*", "annotation.*"...). */
    public List<String> railCandidates() {
        return railCandidates;
    }

    /** Whether a command may sit on the tool rail. */
    public boolean railCandidate(String actionId) {
        return allows(actionId) && matches(railCandidates, actionId);
    }

    public boolean isUserCustomisable() {
        return userCustomisable;
    }

    public Set<String> includes() {
        return includes;
    }

    public Set<String> excludes() {
        return excludes;
    }

    static boolean matches(Collection<String> patterns, String id) {
        for (String pattern : patterns) {
            if (pattern.equals("*") || pattern.equals(id)) return true;
            if (pattern.endsWith(".*") && id.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
        }
        return false;
    }

    // ---- properties ---------------------------------------------------------------------------

    /**
     * Reads features from properties - a product's settings file:
     * <pre>
     * features.preset=ANNOTATING
     * features.add=signature.sign
     * features.remove=document.save-as, annotation.ink
     * features.panels=thumbnails, bookmarks, comments
     * features.viewModes=CONTINUOUS, FACING
     * features.rail=tool.select, tool.pan, annotation.highlight
     * features.customisable=true
     * </pre>
     * Lists are comma separated; missing keys keep the preset's choice.  Unknown panel or layout
     * names are ignored.
     */
    public static ViewerFeatures fromProperties(Properties properties) {
        Preset preset = Preset.FULL;
        String presetName = properties.getProperty("features.preset");
        if (presetName != null) {
            try {
                preset = Preset.valueOf(presetName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // keep FULL
            }
        }
        Builder b = builder(preset);
        b.add(list(properties.getProperty("features.add")).toArray(new String[0]));
        b.remove(list(properties.getProperty("features.remove")).toArray(new String[0]));
        List<SidePanel> panels = new ArrayList<>();
        list(properties.getProperty("features.panels")).forEach(p -> SidePanel.of(p).ifPresent(panels::add));
        if (!panels.isEmpty()) b.panels(panels.toArray(new SidePanel[0]));
        List<ViewMode> modes = new ArrayList<>();
        for (String m : list(properties.getProperty("features.viewModes"))) {
            try {
                modes.add(ViewMode.valueOf(m.toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException ignored) {
                // not a layout
            }
        }
        if (!modes.isEmpty()) b.viewModes(modes.toArray(new ViewMode[0]));
        String rail = properties.getProperty("features.rail");
        if (rail != null) b.defaultRail(list(rail).toArray(new String[0]));
        String customisable = properties.getProperty("features.customisable");
        if (customisable != null) b.userCustomisable(Boolean.parseBoolean(customisable.trim()));
        return b.build();
    }

    /**
     * Reads a Swing viewer settings file ({@code ViewerPropertiesManager}'s {@code application.*}
     * show/hide keys) into features, so a product moving from the Swing RI keeps its choices.
     * Keys set to {@code false} remove what they hid; see {@link SwingProperties}.
     */
    public static ViewerFeatures fromSwingProperties(Properties properties) {
        Builder b = builder(Preset.FULL);
        SwingProperties.apply(properties, b);
        return b.build();
    }

    private static List<String> list(String value) {
        List<String> items = new ArrayList<>();
        if (value == null) return items;
        for (String part : value.split(",")) {
            if (!part.isBlank()) items.add(part.trim());
        }
        return items;
    }

    // ---- builder --------------------------------------------------------------------------------

    public static final class Builder {
        private final Set<String> includes = new LinkedHashSet<>();
        private final Set<String> excludes = new LinkedHashSet<>();
        private final EnumSet<SidePanel> panels = EnumSet.noneOf(SidePanel.class);
        private final EnumSet<ViewMode> viewModes = EnumSet.noneOf(ViewMode.class);
        private final List<String> defaultRail = new ArrayList<>();
        private final List<String> railCandidates = new ArrayList<>(List.of("tool.*", "annotation.*", "signature.*"));
        private boolean userCustomisable = true;

        private Builder(Preset preset) {
            String select = ToolModeAction.id(ToolMode.TEXT_SELECT), pan = ToolModeAction.id(ToolMode.PAN);
            switch (preset) {
                case READING -> {
                    includes.addAll(READING_ACTIONS);
                    defaultRail.addAll(List.of(select, pan));
                }
                case ANNOTATING -> {
                    includes.addAll(READING_ACTIONS);
                    includes.addAll(SAVING);
                    includes.addAll(List.of("edit.delete-annotation", "annotation.*"));
                    defaultRail.addAll(List.of(select, pan, "annotation.highlight", "annotation.note",
                            "annotation.free-text", "annotation.ink", "annotation.rectangle"));
                }
                case FORMS -> {
                    includes.addAll(READING_ACTIONS);
                    includes.addAll(SAVING);
                    includes.addAll(List.of("forms.*", "signature.*"));
                    defaultRail.addAll(List.of(select, pan, "signature.sign"));
                }
                case FULL -> {
                    includes.add("*");
                    defaultRail.addAll(List.of(select, pan, "annotation.highlight", "annotation.note",
                            "annotation.free-text", "annotation.ink", "annotation.rectangle", "signature.sign"));
                }
            }
        }

        /** Adds commands (ids or "prefix.*" patterns); an exact id also drops an earlier exact removal. */
        public Builder add(String... patterns) {
            for (String p : patterns) {
                includes.add(p);
                excludes.remove(p);
            }
            return this;
        }

        /** Removes commands (ids or patterns); a removal beats any addition. */
        public Builder remove(String... patterns) {
            excludes.addAll(Arrays.asList(patterns));
            return this;
        }

        /** The side panels offered (all, if never called). */
        public Builder panels(SidePanel... panels) {
            this.panels.clear();
            this.panels.addAll(Arrays.asList(panels));
            return this;
        }

        /** The page layouts offered (all, if never called). */
        public Builder viewModes(ViewMode... modes) {
            viewModes.clear();
            viewModes.addAll(Arrays.asList(modes));
            return this;
        }

        /** The tools on the rail before the user changes anything, in order. */
        public Builder defaultRail(String... ids) {
            defaultRail.clear();
            defaultRail.addAll(Arrays.asList(ids));
            return this;
        }

        /** Which commands may go on the tool rail (patterns); tools, annotation tools and signing by default. */
        public Builder railCandidates(String... patterns) {
            railCandidates.clear();
            railCandidates.addAll(Arrays.asList(patterns));
            return this;
        }

        /** Whether users may rearrange the rail (true by default). */
        public Builder userCustomisable(boolean customisable) {
            userCustomisable = customisable;
            return this;
        }

        public ViewerFeatures build() {
            return new ViewerFeatures(this);
        }
    }
}
