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

import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.List;

/**
 * A {@link Paint} that renders a Gouraud-shaded triangle mesh, the common
 * rasterisation target for PDF shading types 4-7 (free-form and lattice-form
 * Gouraud triangle meshes, and Coons/tensor patch meshes tessellated into
 * triangles).
 * <p>
 * Java2D has no native mesh paint, so each triangle is rasterised directly with
 * barycentric (Gouraud) colour interpolation.  The triangles are supplied in the
 * shading's own coordinate space; {@link #createContext} concatenates the live
 * device transform with the shading-to-user transform (the pattern
 * {@code Matrix}, identity for a bare {@code sh}) and rasterises every triangle
 * once into an ARGB buffer sized to the requested device bounds.  Pixels not
 * covered by any patch stay transparent so the surrounding content shows through.
 *
 * @since 7.5
 */
public class MeshShadingPaint implements Paint {

    /**
     * A single mesh triangle: three vertices in shading space, each with an
     * sRGB colour (packed ARGB).  Colours are interpolated across the triangle.
     */
    public static final class Triangle {
        final float x0, y0, x1, y1, x2, y2;
        final int c0, c1, c2;

        public Triangle(float x0, float y0, int c0,
                        float x1, float y1, int c1,
                        float x2, float y2, int c2) {
            this.x0 = x0;
            this.y0 = y0;
            this.c0 = c0;
            this.x1 = x1;
            this.y1 = y1;
            this.c1 = c1;
            this.x2 = x2;
            this.y2 = y2;
            this.c2 = c2;
        }
    }

    private final List<Triangle> triangles;
    private final AffineTransform shadingToUser;

    /**
     * @param triangles    mesh triangles in shading space (may be empty).
     * @param shadingToUser maps shading space to user space (the pattern
     *                      {@code Matrix}); may be {@code null} for a bare
     *                      {@code sh} shading, where shading space is user space.
     */
    public MeshShadingPaint(List<Triangle> triangles, AffineTransform shadingToUser) {
        this.triangles = triangles;
        this.shadingToUser = shadingToUser;
    }

    @Override
    public int getTransparency() {
        return TRANSLUCENT;
    }

    @Override
    public PaintContext createContext(ColorModel cm, Rectangle deviceBounds,
                                      Rectangle2D userBounds, AffineTransform xform,
                                      RenderingHints hints) {
        AffineTransform full = new AffineTransform(xform);
        if (shadingToUser != null) {
            full.concatenate(shadingToUser);
        }
        return new MeshPaintContext(triangles, full, deviceBounds);
    }

    /**
     * Rasterises the triangle mesh into an ARGB buffer covering the device
     * bounds once, then serves tiles from it.
     */
    private static final class MeshPaintContext implements PaintContext {

        private final int[] buffer;
        private final int imgW, imgH;
        private final int originX, originY;

        MeshPaintContext(List<Triangle> triangles, AffineTransform full, Rectangle deviceBounds) {
            originX = deviceBounds.x;
            originY = deviceBounds.y;
            imgW = Math.max(1, deviceBounds.width);
            imgH = Math.max(1, deviceBounds.height);
            buffer = new int[imgW * imgH];
            double[] pts = new double[6];
            for (Triangle t : triangles) {
                pts[0] = t.x0;
                pts[1] = t.y0;
                pts[2] = t.x1;
                pts[3] = t.y1;
                pts[4] = t.x2;
                pts[5] = t.y2;
                full.transform(pts, 0, pts, 0, 3);
                rasterize(pts, t.c0, t.c1, t.c2);
            }
        }

        /**
         * Scanline-fills one device-space triangle with barycentric colour
         * interpolation.  Shared triangle edges carry identical colours, so
         * overwriting on the seam is harmless.
         */
        private void rasterize(double[] p, int c0, int c1, int c2) {
            double x0 = p[0] - originX, y0 = p[1] - originY;
            double x1 = p[2] - originX, y1 = p[3] - originY;
            double x2 = p[4] - originX, y2 = p[5] - originY;

            double det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
            if (Math.abs(det) < 1e-9) {
                return; // degenerate sliver
            }
            int minX = (int) Math.floor(Math.min(x0, Math.min(x1, x2)));
            int maxX = (int) Math.ceil(Math.max(x0, Math.max(x1, x2)));
            int minY = (int) Math.floor(Math.min(y0, Math.min(y1, y2)));
            int maxY = (int) Math.ceil(Math.max(y0, Math.max(y1, y2)));
            if (minX < 0) minX = 0;
            if (minY < 0) minY = 0;
            if (maxX > imgW - 1) maxX = imgW - 1;
            if (maxY > imgH - 1) maxY = imgH - 1;

            int a0 = (c0 >>> 24) & 0xff, r0 = (c0 >> 16) & 0xff, g0 = (c0 >> 8) & 0xff, b0 = c0 & 0xff;
            int a1 = (c1 >>> 24) & 0xff, r1 = (c1 >> 16) & 0xff, g1 = (c1 >> 8) & 0xff, b1 = c1 & 0xff;
            int a2 = (c2 >>> 24) & 0xff, r2 = (c2 >> 16) & 0xff, g2 = (c2 >> 8) & 0xff, b2 = c2 & 0xff;

            double invDet = 1.0 / det;
            for (int y = minY; y <= maxY; y++) {
                double py = y + 0.5;
                int row = y * imgW;
                for (int x = minX; x <= maxX; x++) {
                    double px = x + 0.5;
                    double l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) * invDet;
                    double l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) * invDet;
                    double l2 = 1.0 - l0 - l1;
                    // small negative tolerance so shared edges don't leave gaps
                    if (l0 < -0.001 || l1 < -0.001 || l2 < -0.001) {
                        continue;
                    }
                    int a = (int) (l0 * a0 + l1 * a1 + l2 * a2 + 0.5);
                    int r = (int) (l0 * r0 + l1 * r1 + l2 * r2 + 0.5);
                    int g = (int) (l0 * g0 + l1 * g1 + l2 * g2 + 0.5);
                    int b = (int) (l0 * b0 + l1 * b1 + l2 * b2 + 0.5);
                    buffer[row + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public ColorModel getColorModel() {
            return ColorModel.getRGBdefault();
        }

        @Override
        public Raster getRaster(int x, int y, int w, int h) {
            WritableRaster raster = getColorModel().createCompatibleWritableRaster(w, h);
            int[] tile = ((DataBufferInt) raster.getDataBuffer()).getData();
            for (int j = 0; j < h; j++) {
                int sy = y + j - originY;
                if (sy < 0 || sy >= imgH) {
                    continue;
                }
                int srcRow = sy * imgW;
                int dstRow = j * w;
                for (int i = 0; i < w; i++) {
                    int sx = x + i - originX;
                    if (sx < 0 || sx >= imgW) {
                        continue;
                    }
                    tile[dstRow + i] = buffer[srcRow + sx];
                }
            }
            return raster;
        }

        @Override
        public void dispose() {
        }
    }
}
