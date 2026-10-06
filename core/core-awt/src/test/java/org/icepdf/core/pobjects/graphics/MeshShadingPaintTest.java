/*
 * Copyright 2026 Patrick Corless
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

import org.junit.jupiter.api.Test;

import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Gouraud triangle rasteriser behind mesh shading types 4-7.  A single
 * triangle with three corner colours must interpolate barycentrically inside its
 * area and, for pixels of the fill region outside the mesh, edge-clamp to the
 * nearest vertex's colour (matching Ghostscript/Acrobat) so a mesh that does not
 * cover its whole fill path does not leave holes.
 */
public class MeshShadingPaintTest {

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int[] rasterAt(MeshShadingPaint paint, Rectangle bounds) {
        PaintContext ctx = paint.createContext(ColorModel.getRGBdefault(), bounds,
                bounds, new AffineTransform(), null);
        Raster raster = ctx.getRaster(bounds.x, bounds.y, bounds.width, bounds.height);
        int[] px = new int[bounds.width * bounds.height * 4];
        raster.getPixels(bounds.x, bounds.y, bounds.width, bounds.height, px);
        // pack to argb for convenience
        int[] out = new int[bounds.width * bounds.height];
        for (int i = 0; i < out.length; i++) {
            out[i] = argb(px[i * 4 + 3], px[i * 4], px[i * 4 + 1], px[i * 4 + 2]);
        }
        return out;
    }

    /**
     * A right triangle filling the lower-left half of a 10x10 tile: the corner
     * vertices carry their exact colours, and a pixel outside the triangle
     * edge-clamps to the nearest vertex's colour rather than staying transparent.
     */
    @Test
    public void interpolatesInsideAndClampsOutsideToNearestVertex() {
        int red = argb(255, 255, 0, 0);
        int green = argb(255, 0, 255, 0);
        int blue = argb(255, 0, 0, 255);
        MeshShadingPaint.Triangle t = new MeshShadingPaint.Triangle(
                0.5f, 0.5f, red, 9.5f, 0.5f, green, 0.5f, 9.5f, blue);
        MeshShadingPaint paint = new MeshShadingPaint(List.of(t), null);
        Rectangle bounds = new Rectangle(0, 0, 10, 10);

        int[] px = rasterAt(paint, bounds);

        // vertex pixels take (essentially) their own colour
        assertEquals(red, px[0 * 10 + 0], "vertex 0 must be its colour");
        // Pixel (8,9) lies past the hypotenuse (x+y>10), outside the lower-left
        // triangle, and is nearest the blue vertex (0.5,9.5): it edge-clamps to
        // blue and stays opaque instead of transparent.
        int outside = px[9 * 10 + 8];
        assertEquals(255, (outside >>> 24) & 0xff, "outside pixel must be opaque (edge-clamped)");
        assertEquals(blue, outside, "outside pixel clamps to the nearest vertex colour");
        // an interior pixel is an opaque blend of the three corners
        int mid = px[3 * 10 + 3];
        assertEquals(255, (mid >>> 24) & 0xff, "covered pixel must be opaque");
        assertTrue(((mid >> 16) & 0xff) > 0 && (mid & 0xff) > 0,
                "interior pixel must blend multiple corner colours");
    }

    /**
     * A uniform-colour triangle produces that flat colour everywhere it covers
     * (degenerate Gouraud case), confirming the barycentric weights sum to one.
     */
    @Test
    public void uniformColourFillsFlat() {
        int grey = argb(255, 128, 128, 128);
        MeshShadingPaint.Triangle t = new MeshShadingPaint.Triangle(
                0.5f, 0.5f, grey, 9.5f, 0.5f, grey, 0.5f, 9.5f, grey);
        MeshShadingPaint paint = new MeshShadingPaint(List.of(t), null);
        Rectangle bounds = new Rectangle(0, 0, 10, 10);

        int[] px = rasterAt(paint, bounds);
        int covered = Arrays.stream(px).filter(p -> ((p >>> 24) & 0xff) != 0).findFirst().orElse(0);
        assertEquals(grey, covered, "a uniform triangle must fill its flat colour");
    }
}
