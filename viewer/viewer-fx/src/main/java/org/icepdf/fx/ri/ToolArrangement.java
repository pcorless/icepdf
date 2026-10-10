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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An editable copy of how the tools are arranged - on the rail, in its "more" menu, or hidden -
 * for a customise dialog to work on.  Nothing changes until {@link #apply(UserLayout)}, which
 * writes it in one change.  No toolkit types, so it is tested directly.
 */
public final class ToolArrangement {

    /** Where a tool sits. */
    public enum Zone { RAIL, MORE, HIDDEN }

    private final List<String> rail = new ArrayList<>();
    private final List<String> more = new ArrayList<>();
    private final List<String> hidden = new ArrayList<>();

    private ToolArrangement() {
    }

    /**
     * What the user has now.
     *
     * @param available the ids of registered actions the host can run, in registry order
     */
    public static ToolArrangement of(ViewerFeatures features, UserLayout layout, Collection<String> available) {
        ToolArrangement a = new ToolArrangement();
        RailArrangement now = RailArrangement.of(features, layout, available);
        a.rail.addAll(now.rail());
        a.more.addAll(now.overflow());
        for (String id : available) {
            if (features.railCandidate(id) && !a.rail.contains(id) && !a.more.contains(id)) a.hidden.add(id);
        }
        return a;
    }

    /** The product's own arrangement: its default rail, the rest in "more", nothing hidden. */
    public static ToolArrangement defaults(ViewerFeatures features, Collection<String> available) {
        return of(features, UserLayout.inMemory(), available);
    }

    public List<String> rail() {
        return List.copyOf(rail);
    }

    public List<String> more() {
        return List.copyOf(more);
    }

    public List<String> hidden() {
        return List.copyOf(hidden);
    }

    public List<String> get(Zone zone) {
        return switch (zone) {
            case RAIL -> rail();
            case MORE -> more();
            case HIDDEN -> hidden();
        };
    }

    public Zone zoneOf(String id) {
        if (rail.contains(id)) return Zone.RAIL;
        if (more.contains(id)) return Zone.MORE;
        return hidden.contains(id) ? Zone.HIDDEN : null;
    }

    /** Moves a tool to {@code index} of a zone (clamped; the end if negative). */
    public void move(String id, Zone to, int index) {
        Zone from = zoneOf(id);
        if (from == null) return;
        List<String> source = list(from);
        int was = source.indexOf(id);
        source.remove(was);
        List<String> target = list(to);
        int at = index < 0 ? target.size() : Math.min(index, target.size());
        target.add(at, id);
    }

    /** One place up or down within its zone. */
    public void nudge(String id, int by) {
        Zone zone = zoneOf(id);
        if (zone == null) return;
        List<String> list = list(zone);
        int at = list.indexOf(id);
        int to = Math.max(0, Math.min(list.size() - 1, at + by));
        if (to != at) move(id, zone, to);
    }

    /** Writes this arrangement to the layout, in one change. */
    public void apply(UserLayout layout, ViewerFeatures features, Collection<String> available) {
        List<String> current = RailArrangement.of(features, layout, available).rail();
        layout.update(() -> {
            if (layout.isRailCustomised() || !rail.equals(current)) layout.setPinned(rail);
            for (String id : more) layout.setHidden(id, false);
            for (String id : hidden) layout.setHidden(id, true);
        });
    }

    private List<String> list(Zone zone) {
        return switch (zone) {
            case RAIL -> rail;
            case MORE -> more;
            case HIDDEN -> hidden;
        };
    }
}
