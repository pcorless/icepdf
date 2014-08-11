package org.icepdf.core.pobjects.graphics.RasterOps;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;

/**
 * Applies an algorithm to convert the Gray colour space to RGB.
 *
 * @since 5.1
 */
public class GrayRasterOp implements RasterOp {
    private RenderingHints hints = null;
    private float[] decode;

    public GrayRasterOp(float[] decode, RenderingHints hints) {
        this.hints = hints;
        this.decode = decode;
    }

    @Override
    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        int[] values = new int[1];
        int width = src.getWidth();
        int height = src.getHeight();
        boolean defaultDecode = 0.0f == decode[0];

        int Y;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src.getPixel(x, y, values);
                Y = values[0];
                Y = defaultDecode ? 255 - Y : Y;
                Y = (Y < 0) ? (byte) 0 : (Y > 255) ? (byte) 0xFF : (byte) Y;
                values[0] = Y;
                dest.setPixel(x, y, values);
            }
        }
        return dest;
    }

    @Override
    public Rectangle2D getBounds2D(Raster src) {
        return null;
    }

    @Override
    public WritableRaster createCompatibleDestRaster(Raster src) {
        return src.createCompatibleWritableRaster();
    }

    @Override
    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null)
            dstPt = (Point2D) srcPt.clone();
        else
            dstPt.setLocation(srcPt);
        return dstPt;
    }

    @Override
    public RenderingHints getRenderingHints() {
        return hints;
    }
}
