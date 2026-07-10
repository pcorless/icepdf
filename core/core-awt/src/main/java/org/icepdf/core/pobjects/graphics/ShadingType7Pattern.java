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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.awt.Paint;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Tensor-Product Patch Mesh (PDF 32000-1 §8.7.4.5.8, shading type 7).
 * <p>
 * Each patch is a bicubic Bézier surface defined by 16 control points -- the 12
 * boundary points of a Coons patch (type 6) plus 4 interior points that give
 * finer control over the surface.  The mesh stream is a run of patches; the
 * first (edge flag 0) supplies all 16 points and 4 corner colours, while a patch
 * with flag 1-3 shares one edge (4 control points + 2 colours) with the previous
 * patch and reads the remaining 12 points + 2 colours.
 * <p>
 * The surface is tessellated into a grid of triangles (corner colours
 * interpolated bilinearly) and handed to a {@link MeshShadingPaint} for Gouraud
 * rasterisation.
 *
 * @since 6.2
 */
public class ShadingType7Pattern extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType7Pattern.class.getName());

    // Read order of the 16 tensor control points, as (row, col) into the 4x4
    // control grid p[row][col] (PDF 32000-1 Figure 46): the 12 boundary points
    // clockwise from the (0,0) corner, then the 4 interior points.
    private static final int[][] TENSOR_ORDER = {
            {0, 0}, {0, 1}, {0, 2}, {0, 3},
            {1, 3}, {2, 3}, {3, 3},
            {3, 2}, {3, 1}, {3, 0},
            {2, 0}, {1, 0},
            {1, 1}, {1, 2}, {2, 2}, {2, 1}
    };

    private MeshShadingPaint meshPaint;

    public ShadingType7Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        if (inited) {
            return;
        }
        List<MeshShadingPaint.Triangle> triangles = new ArrayList<>();
        // Previous patch kept in the linear read order so a shared edge can be
        // pulled by index (edges span points 0-3 / 3-6 / 6-9 / 9-0).
        Point2D.Float[] prevPoints = null;
        int[] prevColors = null;
        try {
            while (vertexBitStream.available() > 0) {
                int flag = readFlag() & 0x03;
                Point2D.Float[] points = new Point2D.Float[16];
                int[] colors = new int[4];
                if (flag == 0) {
                    for (int i = 0; i < 16; i++) {
                        points[i] = readCoord();
                    }
                    for (int i = 0; i < 4; i++) {
                        colors[i] = colorArgb(readColor());
                    }
                } else {
                    if (prevPoints == null) {
                        logger.warning("Shading type 7: shared patch with no predecessor.");
                        break;
                    }
                    // A shared patch reuses one edge of the previous patch as its
                    // first edge (points 0-3, colours 0-1) and reads the other
                    // 12 points and 2 colours.
                    int[] edge = sharedEdgeIndices(flag);
                    for (int i = 0; i < 4; i++) {
                        points[i] = prevPoints[edge[i]];
                    }
                    colors[0] = prevColors[edge[4]];
                    colors[1] = prevColors[edge[5]];
                    for (int i = 4; i < 16; i++) {
                        points[i] = readCoord();
                    }
                    colors[2] = colorArgb(readColor());
                    colors[3] = colorArgb(readColor());
                }
                Point2D.Float[][] grid = new Point2D.Float[4][4];
                for (int i = 0; i < 16; i++) {
                    grid[TENSOR_ORDER[i][0]][TENSOR_ORDER[i][1]] = points[i];
                }
                tessellatePatch(grid, colors, triangles);
                prevPoints = points;
                prevColors = colors;
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 7 pattern vertices.");
        }
        meshPaint = buildMeshPaint(triangles, graphicsState);
        inited = true;
    }

    /**
     * Maps an edge flag (1-3) to the previous patch's shared control points and
     * colours, in the linear read order.  Returns {@code [p0,p1,p2,p3,c0,c1]}
     * where the four point indices form the shared edge and the two colour
     * indices are its endpoint corners.
     * <ul>
     *   <li>flag 1: previous edge from corner 1 to corner 2 (points 3-6, colours 1-2)</li>
     *   <li>flag 2: previous edge from corner 2 to corner 3 (points 6-9, colours 2-3)</li>
     *   <li>flag 3: previous edge from corner 3 to corner 0 (points 9,10,11,0, colours 3-0)</li>
     * </ul>
     */
    private static int[] sharedEdgeIndices(int flag) {
        switch (flag) {
            case 1:
                return new int[]{3, 4, 5, 6, 1, 2};
            case 2:
                return new int[]{6, 7, 8, 9, 2, 3};
            default: // 3
                return new int[]{9, 10, 11, 0, 3, 0};
        }
    }

    public Paint getPaint() {
        return meshPaint;
    }
}
