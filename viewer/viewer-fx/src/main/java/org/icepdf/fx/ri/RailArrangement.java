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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the tool rail shows, worked out from the two tiers: the product's {@link ViewerFeatures} and
 * the user's {@link UserLayout}.
 * <ul>
 *     <li><b>rail</b>: the user's pinned tools in their order - or the product's default rail
 *     until the user arranges it, or always when the product doesn't allow arranging - keeping
 *     only tools the product allows on the rail and the host can run;</li>
 *     <li><b>overflow</b>: the other allowed rail tools ("more"), less the ones the user hid
 *     (hiding is ignored when the product doesn't allow arranging).</li>
 * </ul>
 *
 * @param rail     tools on the rail, in order
 * @param overflow tools reachable from the rail's overflow menu, in registry order
 */
public record RailArrangement(List<String> rail, List<String> overflow) {

    /**
     * @param available the ids of registered actions the host can run, in registry order
     */
    public static RailArrangement of(ViewerFeatures features, UserLayout layout, Collection<String> available) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String id : available) {
            if (features.railCandidate(id)) candidates.add(id);
        }
        boolean userOrder = features.isUserCustomisable() && layout.isRailCustomised();
        List<String> wanted = userOrder ? layout.getPinned() : features.defaultRail();
        Set<String> hidden = features.isUserCustomisable() ? layout.getHidden() : Set.of();
        List<String> rail = new ArrayList<>();
        for (String id : wanted) {
            if (candidates.contains(id) && !hidden.contains(id) && !rail.contains(id)) rail.add(id);
        }
        List<String> overflow = new ArrayList<>();
        for (String id : candidates) {
            if (!rail.contains(id) && !hidden.contains(id)) overflow.add(id);
        }
        return new RailArrangement(List.copyOf(rail), List.copyOf(overflow));
    }
}
