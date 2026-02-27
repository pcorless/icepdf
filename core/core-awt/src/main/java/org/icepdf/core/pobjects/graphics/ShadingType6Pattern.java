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

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Coons Patch Meshes support.
 * <p>
 * Note: currently only parsing data and returning the first colour of the first vertex.
 *
 * @since 6.2
 */
public class ShadingType6Pattern  extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType6Pattern.class.toString());

    private ArrayList<Color> colorComponents = new ArrayList<>();

    public ShadingType6Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        ArrayList<Point2D.Float> coordinates = new ArrayList<>();
        colorComponents = new ArrayList<>();
        try {
            while (vertexBitStream.available() > 0) {
                int flag = readFlag();
                // read control points.
                for (int i = 0, ii = (flag != 0 ? 8 : 12); i < ii; i++) {
                    coordinates.add(readCoord());
                }
                for (int i = 0, ii = (flag != 0 ? 2 : 4); i < ii; i++) {
                    colorComponents.add(readColor());
                }
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 6 pattern vertices.");
        }
    }

    public Paint getPaint() {
        return colorComponents.get(0);
    }
}
