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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Device-pixel tiling of one page at one zoom, rotation and output scale.
 * <p>
 * Tiles are fixed-size squares anchored at the page's top-left device pixel; edge tiles are
 * clipped to the page.  Because the grid is keyed to the page rather than the viewport, panning
 * reuses every tile that stays visible and only renders the newly exposed ones, and memory scales
 * with the screen rather than the zoom - a 4000% page is just more tile indices, never a bigger
 * buffer.
 * <p>
 * Pure math with no toolkit types.
 */
final class TileGrid {

    public static final int DEFAULT_TILE_SIZE = 512;

    /** A tile's device-pixel rectangle within its page. */
    public record Tile(int column, int row, int x, int y, int width, int height) {
    }

    /** A device-pixel rectangle within a page. */
    public record Region(int x, int y, int width, int height) {
        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    private final int tileSize;
    private final long pageWidth;
    private final long pageHeight;
    private final int columns;
    private final int rows;

    /**
     * @param pageWidth  page width in device px (logical size × output scale, rounded up)
     * @param pageHeight page height in device px
     * @param tileSize   tile edge in device px
     */
    public TileGrid(long pageWidth, long pageHeight, int tileSize) {
        if (tileSize <= 0) throw new IllegalArgumentException("tileSize " + tileSize);
        this.tileSize = tileSize;
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.columns = (int) ((this.pageWidth + tileSize - 1) / tileSize);
        this.rows = (int) ((this.pageHeight + tileSize - 1) / tileSize);
    }

    /** Device size of a page whose logical size is {@code logical} at output scale {@code scale}. */
    public static long deviceSize(double logical, double scale) {
        return (long) Math.ceil(logical * scale - 1e-6);
    }

    public int getTileSize() {
        return tileSize;
    }

    public long getPageWidth() {
        return pageWidth;
    }

    public long getPageHeight() {
        return pageHeight;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public Tile tile(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            throw new IndexOutOfBoundsException(column + "," + row);
        }
        long x = (long) column * tileSize;
        long y = (long) row * tileSize;
        int w = (int) Math.min(tileSize, pageWidth - x);
        int h = (int) Math.min(tileSize, pageHeight - y);
        return new Tile(column, row, (int) x, (int) y, w, h);
    }

    /**
     * Tiles touching a device-pixel rectangle given in page coordinates (it may extend past the
     * page; it is clipped).  Row-major order.
     */
    public List<Tile> tilesIntersecting(double x, double y, double width, double height) {
        double x0 = Math.max(0, x);
        double y0 = Math.max(0, y);
        double x1 = Math.min(pageWidth, x + width);
        double y1 = Math.min(pageHeight, y + height);
        if (x1 <= x0 || y1 <= y0) return List.of();
        int c0 = (int) Math.floor(x0 / tileSize);
        int r0 = (int) Math.floor(y0 / tileSize);
        int c1 = Math.min(columns - 1, (int) Math.ceil(x1 / tileSize) - 1);
        int r1 = Math.min(rows - 1, (int) Math.ceil(y1 / tileSize) - 1);
        List<Tile> out = new ArrayList<>((c1 - c0 + 1) * (r1 - r0 + 1));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                out.add(tile(c, r));
            }
        }
        return out;
    }

    /**
     * The bounding rectangle of a set of tiles: one page.paint over this region, sliced into the
     * tiles afterwards, traverses the display list once instead of once per tile (the bench measured
     * per-tile painting at 3-20× the cost of one region paint).
     */
    public static Region union(Collection<Tile> tiles) {
        if (tiles.isEmpty()) return new Region(0, 0, 0, 0);
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (Tile t : tiles) {
            x0 = Math.min(x0, t.x());
            y0 = Math.min(y0, t.y());
            x1 = Math.max(x1, t.x() + t.width());
            y1 = Math.max(y1, t.y() + t.height());
        }
        return new Region(x0, y0, x1 - x0, y1 - y0);
    }
}
