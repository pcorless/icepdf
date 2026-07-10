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

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Base class for Mesh shading types 4-7. Each subtype parses the shading vertex information slighly differently
 * but the decode and base parse for flag, coordinate and colour is the same.
 *
 * @since 6.2
 */
public abstract class ShadingMeshPattern extends ShadingPattern implements Pattern {

    private static final Logger logger =
            Logger.getLogger(ShadingMeshPattern.class.getName());

    public static final Name BITS_PER_FLAG_KEY = new Name("BitsPerFlag");
    public static final Name BITS_PER_COORDINATE_KEY = new Name("BitsPerCoordinate");

    protected static final int DECODE_X_MIN = 0;
    protected static final int DECODE_X_MAX = 1;
    protected static final int DECODE_Y_MIN = 2;
    protected static final int DECODE_Y_MAX = 3;

    // (Required) The number of bits used to represent the edge flag for each vertex (see below). The value of
    // BitsPerFlag shall be 2, 4, or 8, but only the least significant 2 bits in each flag value shall be used.
    // The value for the edge flag shall be 0, 1, or 2.
    protected final int bitsPerFlag;
    // (Required) The number of bits used to represent each vertex coordinate.
    // The value shall be 1, 2, 4, 8, 12, 16, 24, or 32.
    protected final int bitsPerCoordinate;
    // (Required) The number of bits used to represent each colour component.
    // The value shall be 1, 2, 4, 8, 12, or 16.
    protected final int bitsPerComponent;
    // colour space component count.
    protected final int colorSpaceCompCount;

    // vertex data
    protected final BitStream vertexBitStream;
    protected final Stream meshDataStream;

    // converted decode data to simply process later on, taken from our DecodeRasterOp class.
    protected final float[] decode;

    public ShadingMeshPattern(Library l, DictionaryEntries h, Stream meshDataStream) {
        super(l, h);
        this.meshDataStream = meshDataStream;
        shadingDictionary = meshDataStream.getEntries();
        bitsPerFlag = library.getInt(shadingDictionary, BITS_PER_FLAG_KEY);
        bitsPerCoordinate = library.getInt(shadingDictionary, BITS_PER_COORDINATE_KEY);
        bitsPerComponent = library.getInt(shadingDictionary, ImageParams.BITS_PER_COMPONENT_KEY);
        colorSpace = PColorSpace.getColorSpace(library, library.getObject(shadingDictionary, COLORSPACE_KEY));
        colorSpaceCompCount = colorSpace.getNumComponents();

        // Function is optional and cannot be used with indexed colour models.
        Object tmp = library.getObject(shadingDictionary, FUNCTION_KEY);
        if (tmp != null) {
            if (!(tmp instanceof java.util.List)) {
                function = new Function[]{Function.getFunction(library,
                        tmp)};
            } else {
                java.util.List functionTemp = (java.util.List) tmp;
                function = new Function[functionTemp.size()];
                for (int i = 0; i < functionTemp.size(); i++) {
                    function[i] = Function.getFunction(library, functionTemp.get(i));
                }
            }
        }
        decode = processDecode();
        vertexBitStream = new BitStream(meshDataStream.getDecodedByteArrayInputStream());
    }

    public abstract Paint getPaint();

    /**
     * An array of numbers specifying how to map vertex coordinates and colour components into the
     * appropriate ranges of values. The decoding method is similar to that used in image dictionaries
     * (see 8.9.5.2, "Decode Arrays"). The ranges shall be specified as follows:
     * [xmin xmax ymin ymax c1,min c1,max … cn,min cn,max]
     * Only one pair of c values shall be specified if a Function entry is present.
     * @return decode array of shadding mesh.
     */
    protected float[] processDecode() {
        float[] decode = new float[6];
        if (function == null) {
            decode = new float[4 + 2 * colorSpaceCompCount];
        }

        java.util.List<Number> decodeVec = (java.util.List<Number>) library.getObject(shadingDictionary, ImageParams.DECODE_KEY);

        // 2^bitsPerCoordinate - 1, computed with a long so the common 32-bit
        // case (4294967295) does not overflow an int.
        float maxValue = (float) ((1L << bitsPerCoordinate) - 1);
        for (int i = 0; i <= DECODE_Y_MAX; ) {
            float Dmin = decodeVec.get(i).floatValue();
            float Dmax = decodeVec.get(i + 1).floatValue();
            decode[i++] = Dmin;
            decode[i++] = (Dmax - Dmin) / maxValue;
        }
        maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;
        for (int i = 4; i < decode.length; ) {
            float Dmin = decodeVec.get(i).floatValue();
            float Dmax = decodeVec.get(i + 1).floatValue();
            decode[i++] = Dmin;
            decode[i++] = (Dmax - Dmin) / maxValue;
        }
        return decode;
    }

    /**
     * Reads the vertex descriptor flag, length of flag is defined by the bitsPerFlag dictionary entry.
     *
     * @return int value of the vertex flag.
     * @throws IOException bit stream issue.
     */
    protected int readFlag() throws IOException {
        return vertexBitStream.getBits(bitsPerFlag);
    }

    /**
     * Reads the vertex coordinate data, length of flag is defined by the bitsPerCoordinate dictionary entry.
     *
     * @return int value of the vertex coordinate.
     * @throws IOException bit stream issue.
     */
    protected Point2D.Float readCoord() throws IOException {
        // Coordinates can be up to 32 bits; getBits(32) returns a signed int, so
        // treat the raw sample as unsigned before decoding.
        long rawX = vertexBitStream.getBits(bitsPerCoordinate) & 0xFFFFFFFFL;
        long rawY = vertexBitStream.getBits(bitsPerCoordinate) & 0xFFFFFFFFL;
        // Map the raw sample into the Decode range.  processDecode stored the
        // interval minimum in decode[*_MIN] and the per-unit slope
        // (Dmax-Dmin)/(2^bits-1) in decode[*_MAX], so the decoded value is
        // Dmin + raw * slope.
        float x = decode[DECODE_X_MIN] + rawX * decode[DECODE_X_MAX];
        float y = decode[DECODE_Y_MIN] + rawY * decode[DECODE_Y_MAX];
        return new Point2D.Float(x, y);
    }

    /**
     * Reads the vertex colour data, length of flag is defined by the colorSpaceCompCount dictionary entry.
     * Color data is generate using the function if present as well as the defined colour space.
     *
     * @return int value of the vertex colour.
     * @throws IOException bit stream issue.
     */
    protected Color readColor() throws IOException {
        float[] primitives;
        if (function == null) {
            primitives = new float[colorSpaceCompCount];
            for (int i = 0, j = 4; i < colorSpaceCompCount; i++, j += 2) {
                float raw = vertexBitStream.getBits(bitsPerComponent);
                // normalize: Cmin + raw * slope (see readCoord)
                primitives[i] = decode[j] + raw * decode[j + 1];
            }
            return colorSpace.getColor(primitives, true);
        } else {
            float raw = vertexBitStream.getBits(bitsPerComponent);
            // normalize: Cmin + raw * slope
            float value = decode[4] + raw * decode[5];
            primitives = new float[]{value};
            float[] output = calculateValues(primitives);
            if (output != null) {
                return colorSpace.getColor(output, true);
            }
        }
        return null;
    }

    // ---- shared mesh rendering helpers (types 4-7) ----------------------------

    /** Number of subdivisions per patch edge when tessellating a Bézier patch. */
    protected static final int PATCH_SUBDIVISIONS = 10;

    /**
     * Wraps a tessellated triangle list in a {@link MeshShadingPaint}, anchoring
     * a shading <b>pattern</b> to its default coordinate system.  A shading
     * pattern's {@code Matrix} maps shading space to the page's default user
     * space and is independent of the fill-time CTM (PDF 32000-1 §8.7.3.1); the
     * paint runs under the live {@code base·CTM}, so the CTM is cancelled
     * ({@code base·CTM · CTM⁻¹·Matrix == base·Matrix}).
     * <p>
     * A mesh painted by the {@code sh} operator instead lives in the current
     * user space -- its coordinates are relative to the live CTM, which must
     * apply in full -- and carries no {@code /PatternType}.  Anchoring it would
     * cancel that CTM and push the mesh off the fill region, so the raw matrix
     * (identity for {@code sh}) is used unchanged.
     *
     * @param triangles     tessellated mesh triangles in shading space.
     * @param graphicsState graphics state at the fill (for the fill-time CTM).
     * @return a paint that Gouraud-rasterises the mesh.
     */
    protected MeshShadingPaint buildMeshPaint(List<MeshShadingPaint.Triangle> triangles,
                                              GraphicsState graphicsState) {
        AffineTransform shadingToUser = matrix;
        if (patternType == Pattern.PATTERN_TYPE_SHADING
                && graphicsState != null && graphicsState.getCTM() != null) {
            try {
                AffineTransform anchored = graphicsState.getCTM().createInverse();
                anchored.concatenate(matrix);
                shadingToUser = anchored;
            } catch (NoninvertibleTransformException e) {
                // degenerate CTM; fall back to the raw matrix.
            }
        }
        return new MeshShadingPaint(triangles, shadingToUser);
    }

    /**
     * Tessellates one bicubic Bézier patch (a 4x4 control grid) into triangles,
     * sampling the surface on a {@link #PATCH_SUBDIVISIONS} grid and blending the
     * four corner colours bilinearly.  Corner colours are given in ARGB at the
     * grid corners {@code (0,0),(0,3),(3,3),(3,0)}.  Shared by Coons (type 6, its
     * four interior points derived) and tensor (type 7) patches.
     */
    protected static void tessellatePatch(Point2D.Float[][] p, int[] corners,
                                          List<MeshShadingPaint.Triangle> out) {
        int n = PATCH_SUBDIVISIONS;
        int stride = n + 1;
        float[] gx = new float[stride * stride];
        float[] gy = new float[stride * stride];
        int[] gc = new int[stride * stride];
        for (int iu = 0; iu <= n; iu++) {
            double u = (double) iu / n;
            double[] bu = bernstein(u);
            for (int iv = 0; iv <= n; iv++) {
                double v = (double) iv / n;
                double[] bv = bernstein(v);
                double sx = 0, sy = 0;
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        double w = bu[i] * bv[j];
                        sx += w * p[i][j].x;
                        sy += w * p[i][j].y;
                    }
                }
                int idx = iu * stride + iv;
                gx[idx] = (float) sx;
                gy[idx] = (float) sy;
                gc[idx] = bilerpColor(corners, u, v);
            }
        }
        for (int iu = 0; iu < n; iu++) {
            for (int iv = 0; iv < n; iv++) {
                int a = iu * stride + iv;
                int b = a + 1;
                int c = a + stride;
                int d = c + 1;
                out.add(new MeshShadingPaint.Triangle(
                        gx[a], gy[a], gc[a], gx[b], gy[b], gc[b], gx[c], gy[c], gc[c]));
                out.add(new MeshShadingPaint.Triangle(
                        gx[b], gy[b], gc[b], gx[d], gy[d], gc[d], gx[c], gy[c], gc[c]));
            }
        }
    }

    /** Cubic Bernstein basis {@code [B0,B1,B2,B3]} at parameter t. */
    protected static double[] bernstein(double t) {
        double mt = 1 - t;
        return new double[]{mt * mt * mt, 3 * t * mt * mt, 3 * t * t * mt, t * t * t};
    }

    /**
     * Bilinear blend of four ARGB corner colours; corners map to
     * {@code (u,v)} in {@code {0,1}²}: c0 at (0,0), c1 at (0,1), c2 at (1,1),
     * c3 at (1,0).
     */
    protected static int bilerpColor(int[] c, double u, double v) {
        double w0 = (1 - u) * (1 - v);
        double w1 = (1 - u) * v;
        double w2 = u * v;
        double w3 = u * (1 - v);
        int a = blendChannel(c, 24, w0, w1, w2, w3);
        int r = blendChannel(c, 16, w0, w1, w2, w3);
        int g = blendChannel(c, 8, w0, w1, w2, w3);
        int b = blendChannel(c, 0, w0, w1, w2, w3);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int[] c, int shift, double w0, double w1, double w2, double w3) {
        double val = ((c[0] >>> shift) & 0xff) * w0 + ((c[1] >>> shift) & 0xff) * w1
                + ((c[2] >>> shift) & 0xff) * w2 + ((c[3] >>> shift) & 0xff) * w3;
        int v = (int) (val + 0.5);
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Packs a colour to ARGB, treating {@code null} as fully transparent. */
    protected static int colorArgb(Color color) {
        return color != null ? color.getRGB() : 0;
    }

    /**
     * Appends a single flat-shaded/Gouraud triangle (three vertices, each with
     * an ARGB colour) to the mesh.  Used by the Gouraud triangle mesh types
     * (4 and 5) which supply triangle vertices directly.
     */
    protected static void addTriangle(List<MeshShadingPaint.Triangle> out,
                                      Point2D.Float p0, int c0,
                                      Point2D.Float p1, int c1,
                                      Point2D.Float p2, int c2) {
        out.add(new MeshShadingPaint.Triangle(p0.x, p0.y, c0, p1.x, p1.y, c1, p2.x, p2.y, c2));
    }
}
