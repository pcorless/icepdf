package org.icepdf.core.pobjects.graphics.RasterOps;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;

/**
 * Raster operation that convers the YCbCr to a RGB colour space.
 *
 * @since 5.1
 */
public class YCbCrRasterOp implements RasterOp {

    private RenderingHints hints = null;

    public YCbCrRasterOp(RenderingHints hints) {
        this.hints = hints;
    }

    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        float[] values = new float[src.getNumBands()];
        int width = src.getWidth();
        int height = src.getHeight();
        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                src.getPixel(x, y, values);

                float Y = values[0];
                float Cb = values[1];
                float Cr = values[2];

                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                float rVal = Y + (1370705 * Cr_128 / 1000000);
                float gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                float bVal = Y + (1732446 * Cb_128 / 1000000);

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;

                values[0] = rByte;
                values[1] = gByte;
                values[2] = bByte;

                dest.setPixel(x, y, values);
            }
        }
        return dest;
    }

    public Rectangle2D getBounds2D(Raster src) {
        return null;
    }

    public WritableRaster createCompatibleDestRaster(Raster src) {
        return src.createCompatibleWritableRaster();
    }

    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null)
            dstPt = (Point2D) srcPt.clone();
        else
            dstPt.setLocation(srcPt);
        return dstPt;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }
}
