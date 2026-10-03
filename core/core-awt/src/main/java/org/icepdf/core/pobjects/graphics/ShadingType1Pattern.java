/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In Type 1 (function-based) shadings, the colour at every point in the domain
 * is defined by a specified mathematical function. The function need not be
 * smooth or continuous. This type is the most general of the available shading
 * types and is useful for shadings that cannot be adequately described with any
 * of the other types. Table 79 shows the shading dictionary entries specific
 * to this type of shading, in addition to those common to all shading
 * dictionaries
 *
 * @since 5.0
 */
public class ShadingType1Pattern extends ShadingPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType1Pattern.class.getName());

    /**
     * Grid cells per side over the domain.  The function is sampled at the grid vertices and the colour
     * interpolated across each cell, which follows smooth functions closely and keeps the cost bounded however
     * expensive the function is to evaluate (Type 4 functions are interpreted).
     */
    static final int GRID = 48;

    // [xmin xmax ymin ymax]
    private float[] domain;
    // the shading's own /Matrix: domain space -> shading (target) space
    private AffineTransform domainMatrix;
    private List<MeshShadingPaint.Triangle> triangles;

    public ShadingType1Pattern(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @SuppressWarnings("unchecked")
    public synchronized void init(GraphicsState graphicsState) {
        if (inited) {
            return;
        }
        inited = true;
        triangles = Collections.emptyList();

        if (shadingDictionary == null) {
            shadingDictionary = library.getDictionary(entries, SHADING_KEY);
        }
        if (shadingDictionary == null) {
            return;
        }
        colorSpace = PColorSpace.getColorSpace(library, library.getObject(shadingDictionary, COLORSPACE_KEY));

        domain = new float[]{0, 1, 0, 1};
        Object tmp = library.getObject(shadingDictionary, DOMAIN_KEY);
        if (tmp instanceof List && ((List<?>) tmp).size() >= 4) {
            List<?> values = (List<?>) tmp;
            for (int i = 0; i < 4; i++) {
                domain[i] = ((Number) values.get(i)).floatValue();
            }
        }

        domainMatrix = new AffineTransform();
        tmp = library.getObject(shadingDictionary, MATRIX_KEY);
        if (tmp instanceof List && ((List<?>) tmp).size() >= 6) {
            List<?> m = (List<?>) tmp;
            domainMatrix = new AffineTransform(
                    ((Number) m.get(0)).floatValue(), ((Number) m.get(1)).floatValue(),
                    ((Number) m.get(2)).floatValue(), ((Number) m.get(3)).floatValue(),
                    ((Number) m.get(4)).floatValue(), ((Number) m.get(5)).floatValue());
        }

        tmp = library.getObject(shadingDictionary, FUNCTION_KEY);
        if (tmp instanceof List) {
            List<?> functions = (List<?>) tmp;
            function = new Function[functions.size()];
            for (int i = 0; i < functions.size(); i++) {
                function[i] = Function.getFunction(library, functions.get(i));
            }
        } else if (tmp != null) {
            function = new Function[]{Function.getFunction(library, tmp)};
        }
        if (function == null || colorSpace == null) {
            return;
        }
        try {
            triangles = tessellate();
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Could not evaluate type 1 shading " + getPObjectReference()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Samples the 2-in function on a {@link #GRID}x{@link #GRID} grid over the domain and splits every cell into
     * two triangles carrying the sampled colours at their corners.
     */
    private List<MeshShadingPaint.Triangle> tessellate() {
        int n = GRID + 1;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = domain[0] + (domain[1] - domain[0]) * i / GRID;
            ys[i] = domain[2] + (domain[3] - domain[2]) * i / GRID;
        }
        int[][] argb = new int[n][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                argb[j][i] = colorAt(xs[i], ys[j]);
            }
        }
        List<MeshShadingPaint.Triangle> result = new ArrayList<>(GRID * GRID * 2);
        for (int j = 0; j < GRID; j++) {
            for (int i = 0; i < GRID; i++) {
                result.add(new MeshShadingPaint.Triangle(
                        xs[i], ys[j], argb[j][i],
                        xs[i + 1], ys[j], argb[j][i + 1],
                        xs[i + 1], ys[j + 1], argb[j + 1][i + 1]));
                result.add(new MeshShadingPaint.Triangle(
                        xs[i], ys[j], argb[j][i],
                        xs[i + 1], ys[j + 1], argb[j + 1][i + 1],
                        xs[i], ys[j + 1], argb[j + 1][i]));
            }
        }
        return result;
    }

    private int colorAt(float x, float y) {
        float[] values = calculateValues(new float[]{x, y});
        return 0xFF000000 | colorSpace.getColor(values, true).getRGB();
    }

    /**
     * Without a graphics state the shading is returned unanchored: domain space through the shading's /Matrix
     * (and the pattern matrix, for a pattern).
     */
    public Paint getPaint() {
        return getPaint(null);
    }

    @Override
    public Paint getPaint(GraphicsState graphicsState) {
        init(graphicsState);
        // a bare `sh` passes the shading dictionary itself as the entries, so its /Matrix is already the
        // domain matrix; a shading pattern adds its own matrix, anchored to the default space, on top
        AffineTransform domainToUser = shadingDictionary == entries || shadingDictionary == null
                // copied: anchorToDefaultSpace may hand back the pattern's own matrix, which must not be mutated
                ? new AffineTransform() : new AffineTransform(anchorToDefaultSpace(matrix, graphicsState));
        if (domainMatrix != null) {
            domainToUser.concatenate(domainMatrix);
        }
        // nothing is painted outside the domain (unlike a mesh, whose boundary colour is extended)
        return new MeshShadingPaint(triangles != null ? triangles : Collections.emptyList(), domainToUser, false);
    }

    public String toString() {
        return super.toString() +
                "\n                    domain: " + Arrays.toString(domain) +
                "\n                    matrix: " + domainMatrix +
                "\n                 function: " + Arrays.toString(function);
    }
}
