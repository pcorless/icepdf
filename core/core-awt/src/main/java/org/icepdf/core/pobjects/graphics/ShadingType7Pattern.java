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
 * Tensor-Product Patch Meshes support.
 * <p>
 * Note: currently only parsing data and returning the first colour of the first vertex.
 *
 * @since 6.2
 */
public class ShadingType7Pattern extends ShadingMeshPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType7Pattern.class.toString());

    private ArrayList<Color> colorComponents = new ArrayList<>();

    public ShadingType7Pattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h, meshDataStream);
    }

    public synchronized void init(GraphicsState graphicsState) {
        ArrayList<Point2D.Float> coordinates = new ArrayList<>();
        colorComponents = new ArrayList<>();
        try {
            while (vertexBitStream.available() > 0) {
                int flag = readFlag();
                // read control points.
                for (int i = 0, ii = (flag != 0 ? 12 : 16); i < ii; i++) {
                    coordinates.add(readCoord());
                }
                for (int i = 0, ii = (flag != 0 ? 2 : 4); i < ii; i++) {
                    colorComponents.add(readColor());
                }
            }
        } catch (IOException e) {
            logger.warning("Error parsing Shading type 7 pattern vertices.");
        }
    }

    public Paint getPaint() {
        return colorComponents.get(1);
    }
}
