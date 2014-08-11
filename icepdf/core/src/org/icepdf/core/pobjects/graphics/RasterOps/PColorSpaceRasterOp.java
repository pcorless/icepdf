package org.icepdf.core.pobjects.graphics.RasterOps;

import org.icepdf.core.pobjects.graphics.DeviceRGB;
import org.icepdf.core.pobjects.graphics.PColorSpace;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;

/**
 * Convert a rgb encoded raster to the specified colour space.
 *
 * @since 5.1
 */
public class PColorSpaceRasterOp implements RasterOp {

    private RenderingHints hints = null;
    private PColorSpace colorSpace;

    public PColorSpaceRasterOp(PColorSpace colorSpace, RenderingHints hints) {
        this.hints = hints;
        this.colorSpace = colorSpace;
    }

    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        if (colorSpace instanceof DeviceRGB)
            return dest;

        float[] values = new float[3];
        int[] rgbValues = new int[3];
        int width = src.getWidth();
        int height = src.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src.getPixel(x, y, rgbValues);

                PColorSpace.reverseInPlace(rgbValues);
                colorSpace.normaliseComponentsToFloats(rgbValues, values, 255.0f);
                Color c = colorSpace.getColor(values);
                rgbValues[0] = c.getRed();
                rgbValues[1] = c.getGreen();
                rgbValues[2] = c.getBlue();

                dest.setPixel(x, y, rgbValues);
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
        if (dstPt == null) {
            dstPt = (Point2D) srcPt.clone();
        } else {
            dstPt.setLocation(srcPt);
        }
        return dstPt;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }
}
