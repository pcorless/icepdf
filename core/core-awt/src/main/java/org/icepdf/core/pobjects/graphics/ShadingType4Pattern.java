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
 * Free-form Gouraud-shaded triangle mesh (PDF 32000-1 §8.7.4.5.5, shading
 * type 4).
 * <p>
 * Each vertex carries an edge flag, a coordinate, and a colour.  A flag of 0
 * begins a new triangle (completed by the next two flag-0 vertices); a flag of 1
 * reuses the previous triangle's second and third vertices (vb, vc) plus the new
 * vertex; a flag of 2 reuses the first and third (va, vc) plus the new vertex.
 * Triangles are Gouraud-rasterised by a {@link MeshShadingPaint}.
 *
 * @since 6.2
 */
public class ShadingType4Pattern extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType4Pattern.class.getName());

    private MeshShadingPaint meshPaint;

    public ShadingType4Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        if (inited) {
            return;
        }
        List<MeshShadingPaint.Triangle> triangles = new ArrayList<>();
        Point2D.Float va = null, vb = null, vc = null;
        int ca = 0, cb = 0, cc = 0;
        try {
            while (vertexBitStream.available() > 0) {
                int flag = readFlag() & 0x03;
                Point2D.Float p = readCoord();
                int c = colorArgb(readColor());
                if (flag == 0) {
                    // Start a new triangle: this vertex plus the next two (which
                    // shall also be flag 0) form it.
                    va = p;
                    ca = c;
                    if (vertexBitStream.available() <= 0) {
                        break;
                    }
                    readFlag();
                    vb = readCoord();
                    cb = colorArgb(readColor());
                    if (vertexBitStream.available() <= 0) {
                        break;
                    }
                    readFlag();
                    vc = readCoord();
                    cc = colorArgb(readColor());
                } else if (flag == 1) {
                    // Share edge vb-vc of the previous triangle.
                    va = vb;
                    ca = cb;
                    vb = vc;
                    cb = cc;
                    vc = p;
                    cc = c;
                } else {
                    // flag == 2: share edge va-vc of the previous triangle.
                    vb = vc;
                    cb = cc;
                    vc = p;
                    cc = c;
                }
                if (va != null && vb != null && vc != null) {
                    addTriangle(triangles, va, ca, vb, cb, vc, cc);
                }
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 4 pattern vertices.");
        }
        meshPaint = buildMeshPaint(triangles, graphicsState);
        inited = true;
    }

    public Paint getPaint() {
        return meshPaint;
    }
}
