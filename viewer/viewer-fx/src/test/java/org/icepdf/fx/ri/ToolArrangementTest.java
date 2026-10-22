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

import org.icepdf.fx.ri.ToolArrangement.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolArrangementTest {

    private static final ViewerFeatures FEATURES = ViewerFeatures.full();
    private static final List<String> AVAILABLE = org.icepdf.fx.ri.actions.StandardActions.registry().all().stream()
            .map(org.icepdf.fx.ri.actions.ViewerAction::id).toList();

    private ToolArrangement start(UserLayout layout) {
        return ToolArrangement.of(FEATURES, layout, AVAILABLE);
    }

    @Test
    void startsFromTheProductDefaultAndPartitionsEveryCandidate() {
        ToolArrangement a = start(UserLayout.inMemory());
        assertEquals(FEATURES.defaultRail().stream().filter(AVAILABLE::contains).toList(), a.rail());
        assertTrue(a.hidden().isEmpty());
        assertEquals(AVAILABLE.stream().filter(FEATURES::railCandidate).count(), a.rail().size() + a.more().size());
    }

    @Test
    void moveAcrossZonesAndWithin() {
        ToolArrangement a = start(UserLayout.inMemory());
        String id = a.rail().get(0);
        a.move(id, Zone.HIDDEN, -1);
        assertEquals(Zone.HIDDEN, a.zoneOf(id));
        assertFalse(a.rail().contains(id));
        a.move(id, Zone.RAIL, 1);
        assertEquals(1, a.rail().indexOf(id));
        a.nudge(id, -1);
        assertEquals(0, a.rail().indexOf(id));
        a.nudge(id, -1);
        assertEquals(0, a.rail().indexOf(id), "stops at the top");
    }

    @Test
    void applyWritesOnceAndRoundTrips() {
        UserLayout layout = UserLayout.inMemory();
        AtomicInteger told = new AtomicInteger();
        layout.addListener(told::incrementAndGet);
        ToolArrangement a = start(layout);
        String first = a.rail().get(0);
        String moreTool = a.more().get(0);
        a.move(first, Zone.HIDDEN, -1);
        a.move(moreTool, Zone.RAIL, 0);
        a.apply(layout, FEATURES, AVAILABLE);
        assertEquals(1, told.get(), "one change for the whole arrangement");

        ToolArrangement again = start(layout);
        assertEquals(a.rail(), again.rail());
        assertEquals(List.of(first), again.hidden());
        assertEquals(a.more(), again.more());
    }

    @Test
    void applyingTheUntouchedDefaultKeepsTheRailFollowingTheProduct() {
        UserLayout layout = UserLayout.inMemory();
        start(layout).apply(layout, FEATURES, AVAILABLE);
        assertFalse(layout.isRailCustomised());
    }

    @Test
    void layoutListenersAreToldOfStructuralChangesAndCanLeave() {
        UserLayout layout = UserLayout.inMemory();
        AtomicInteger told = new AtomicInteger();
        Runnable l = told::incrementAndGet;
        layout.addListener(l);
        layout.setRailSide(UserLayout.Side.RIGHT);
        layout.setPanelWidth(300);
        layout.setLastPanel("search");
        assertEquals(1, told.get(), "width and last panel are the panel's own");
        layout.removeListener(l);
        layout.setRailAutoShow(true);
        assertEquals(1, told.get());
    }
}
