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
 * Coons Patch Mesh (PDF 32000-1 §8.7.4.5.7, shading type 6).
 * <p>
 * A Coons patch is bounded by four cubic Bézier curves (12 control points); the
 * surface interior is defined by a bilinear blend of the boundary curves.  This
 * is the special case of a tensor-product patch (type 7) whose four interior
 * control points are derived from the boundary, so a Coons patch is converted to
 * a 4x4 tensor control grid and tessellated with the shared
 * {@link #tessellatePatch} path.
 * <p>
 * Patches with edge flag 0 supply all 12 points and 4 corner colours; flags 1-3
 * share one edge (4 points + 2 colours) with the previous patch.
 *
 * @since 6.2
 */
public class ShadingType6Pattern extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType6Pattern.class.getName());

    // Read order of the 12 Coons boundary control points, as (row, col) into the
    // 4x4 grid p[row][col] (PDF 32000-1 Figure 45): the boundary clockwise from
    // the (0,0) corner.  The four interior points (1,1)(1,2)(2,2)(2,1) are
    // derived from the boundary in coonsInterior().
    private static final int[][] COONS_ORDER = {
            {0, 0}, {0, 1}, {0, 2}, {0, 3},
            {1, 3}, {2, 3}, {3, 3},
            {3, 2}, {3, 1}, {3, 0},
            {2, 0}, {1, 0}
    };

    private MeshShadingPaint meshPaint;

    public ShadingType6Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        if (inited) {
            return;
        }
        List<MeshShadingPaint.Triangle> triangles = new ArrayList<>();
        Point2D.Float[] prevPoints = null;
        int[] prevColors = null;
        try {
            while (vertexBitStream.available() > 0) {
                int flag = readFlag() & 0x03;
                Point2D.Float[] points = new Point2D.Float[12];
                int[] colors = new int[4];
                if (flag == 0) {
                    for (int i = 0; i < 12; i++) {
                        points[i] = readCoord();
                    }
                    for (int i = 0; i < 4; i++) {
                        colors[i] = colorArgb(readColor());
                    }
                } else {
                    if (prevPoints == null) {
                        logger.warning("Shading type 6: shared patch with no predecessor.");
                        break;
                    }
                    int[] edge = sharedEdgeIndices(flag);
                    for (int i = 0; i < 4; i++) {
                        points[i] = prevPoints[edge[i]];
                    }
                    colors[0] = prevColors[edge[4]];
                    colors[1] = prevColors[edge[5]];
                    for (int i = 4; i < 12; i++) {
                        points[i] = readCoord();
                    }
                    colors[2] = colorArgb(readColor());
                    colors[3] = colorArgb(readColor());
                }
                Point2D.Float[][] grid = new Point2D.Float[4][4];
                for (int i = 0; i < 12; i++) {
                    grid[COONS_ORDER[i][0]][COONS_ORDER[i][1]] = points[i];
                }
                coonsInterior(grid);
                tessellatePatch(grid, colors, triangles);
                prevPoints = points;
                prevColors = colors;
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 6 pattern vertices.");
        }
        meshPaint = buildMeshPaint(triangles, graphicsState);
        inited = true;
    }

    /**
     * Derives the four interior tensor control points of a Coons patch from its
     * boundary, so the bicubic evaluator reproduces the Coons surface (PDF
     * 32000-1 §8.7.4.5.7).  Each interior point is expressed relative to its
     * nearest corner.
     */
    private static void coonsInterior(Point2D.Float[][] p) {
        p[1][1] = combine(p[0][0], p[0][1], p[1][0], p[0][3], p[3][0], p[1][3], p[3][1], p[3][3]);
        p[1][2] = combine(p[0][3], p[0][2], p[1][3], p[0][0], p[3][3], p[1][0], p[3][2], p[3][0]);
        p[2][1] = combine(p[3][0], p[2][0], p[3][1], p[0][0], p[3][3], p[0][1], p[2][3], p[0][3]);
        p[2][2] = combine(p[3][3], p[3][2], p[2][3], p[3][0], p[0][3], p[2][0], p[0][2], p[0][0]);
    }

    /**
     * Interior control point = (-4·corner + 6·(e1+e2) - 2·(f1+f2) + 3·(g1+g2)
     * - opposite) / 9, the Coons-to-tensor conversion where {@code corner} is
     * the nearest corner, {@code e*} its two adjacent boundary points, {@code f*}
     * the far corners along each edge, {@code g*} the boundary points adjacent to
     * the opposite corner, and {@code opposite} the diagonally opposite corner.
     */
    private static Point2D.Float combine(Point2D.Float corner, Point2D.Float e1, Point2D.Float e2,
                                         Point2D.Float f1, Point2D.Float f2, Point2D.Float g1,
                                         Point2D.Float g2, Point2D.Float opposite) {
        float x = (-4 * corner.x + 6 * (e1.x + e2.x) - 2 * (f1.x + f2.x)
                + 3 * (g1.x + g2.x) - opposite.x) / 9f;
        float y = (-4 * corner.y + 6 * (e1.y + e2.y) - 2 * (f1.y + f2.y)
                + 3 * (g1.y + g2.y) - opposite.y) / 9f;
        return new Point2D.Float(x, y);
    }

    /** Shared-edge point/colour indices for edge flags 1-3 (see type 7). */
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
