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
package org.icepdf.fx.view;

import org.icepdf.fx.view.TileGrid.Region;
import org.icepdf.fx.view.TileGrid.Tile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileGridTest {

    @Test
    void edgeTilesAreClippedToThePage() {
        TileGrid grid = new TileGrid(1000, 600, 512);
        assertEquals(2, grid.getColumns());
        assertEquals(2, grid.getRows());
        assertEquals(new Tile(1, 1, 512, 512, 488, 88), grid.tile(1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.tile(2, 0));
    }

    @Test
    void tilesIntersectingClipsToPageAndIsRowMajor() {
        TileGrid grid = new TileGrid(2048, 2048, 512);
        List<Tile> tiles = grid.tilesIntersecting(500, 1000, 30, 100);
        assertEquals(List.of(grid.tile(0, 1), grid.tile(1, 1), grid.tile(0, 2), grid.tile(1, 2)), tiles);
        assertEquals(16, grid.tilesIntersecting(-100, -100, 5000, 5000).size());
        assertTrue(grid.tilesIntersecting(3000, 0, 100, 100).isEmpty());
        assertTrue(grid.tilesIntersecting(0, 0, 0, 100).isEmpty());
    }

    @Test
    void exactTileBoundaryDoesNotPullInTheNeighbour() {
        TileGrid grid = new TileGrid(2048, 2048, 512);
        assertEquals(List.of(grid.tile(1, 0)), grid.tilesIntersecting(512, 0, 512, 1));
    }

    @Test
    void visibleTileCountIsBoundedByTheViewportNotTheZoom() {
        // letter page at 4000% on a 2x screen: ~49k x 63k device px.
        TileGrid grid = new TileGrid(TileGrid.deviceSize(612 * 40, 2), TileGrid.deviceSize(792 * 40, 2), 512);
        // a 1600x1000 logical viewport at 2x, anywhere on the page.
        List<Tile> tiles = grid.tilesIntersecting(20_000.5, 30_000.5, 3200, 2000);
        assertTrue(tiles.size() <= (3200 / 512 + 2) * (2000 / 512 + 2), "got " + tiles.size());
        Region region = TileGrid.union(tiles);
        assertTrue((long) region.width() * region.height() * 4 < 64L * 1024 * 1024,
                "region buffer stays viewport sized");
    }

    @Test
    void unionCoversAllTiles() {
        TileGrid grid = new TileGrid(1000, 1000, 512);
        Region r = TileGrid.union(List.of(grid.tile(1, 0), grid.tile(0, 1)));
        assertEquals(new Region(0, 0, 1000, 1000), r);
        assertTrue(TileGrid.union(List.of()).isEmpty());
    }

    @Test
    void deviceSizeRoundsUpButToleratesFloatNoise() {
        assertEquals(1224, TileGrid.deviceSize(612, 2));
        assertEquals(766, TileGrid.deviceSize(612.5, 1.25));
        assertEquals(1000, TileGrid.deviceSize(1000.0000000001, 1));
    }
}
