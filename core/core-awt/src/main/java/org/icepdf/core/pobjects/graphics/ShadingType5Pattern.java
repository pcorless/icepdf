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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.awt.Paint;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lattice-form Gouraud-shaded triangle mesh (PDF 32000-1 §8.7.4.5.6, shading
 * type 5).
 * <p>
 * Vertices carry no edge flags; they are read row by row, {@code VerticesPerRow}
 * per row.  Each cell between two adjacent rows is split into two triangles,
 * Gouraud-rasterised by a {@link MeshShadingPaint}.
 *
 * @since 6.2
 */
public class ShadingType5Pattern extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType5Pattern.class.getName());

    public static final Name VERTICES_PER_ROW_KEY = new Name("VerticesPerRow");

    private MeshShadingPaint meshPaint;

    public ShadingType5Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        if (inited) {
            return;
        }
        List<MeshShadingPaint.Triangle> triangles = new ArrayList<>();
        int perRow = library.getInt(shadingDictionary, VERTICES_PER_ROW_KEY);
        try {
            if (perRow >= 2) {
                List<Point2D.Float> coords = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                while (vertexBitStream.available() > 0) {
                    coords.add(readCoord());
                    colors.add(colorArgb(readColor()));
                }
                int rows = coords.size() / perRow;
                // Two triangles per lattice cell between adjacent rows/columns.
                for (int r = 0; r < rows - 1; r++) {
                    for (int col = 0; col < perRow - 1; col++) {
                        int i00 = r * perRow + col;
                        int i01 = i00 + 1;
                        int i10 = i00 + perRow;
                        int i11 = i10 + 1;
                        addTriangle(triangles,
                                coords.get(i00), colors.get(i00),
                                coords.get(i01), colors.get(i01),
                                coords.get(i10), colors.get(i10));
                        addTriangle(triangles,
                                coords.get(i01), colors.get(i01),
                                coords.get(i11), colors.get(i11),
                                coords.get(i10), colors.get(i10));
                    }
                }
            } else {
                logger.warning("Shading type 5: invalid VerticesPerRow " + perRow);
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 5 pattern vertices.");
        }
        meshPaint = buildMeshPaint(triangles, graphicsState);
        inited = true;
    }

    public Paint getPaint() {
        return meshPaint;
    }
}
