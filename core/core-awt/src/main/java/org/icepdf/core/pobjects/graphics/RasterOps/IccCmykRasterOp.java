package org.icepdf.core.pobjects.graphics.RasterOps;

import org.icepdf.core.pobjects.graphics.DeviceCMYK;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;

/**
 * Raster operation for converting a CMYK colour to RGB using an ICC colour profile.
 * <br>
 * CC Color Profile for colour conversion is very accurate but it's also very slow.  Calls
 * to ColorConvertOp can be very slow.  This class attempts to use a colour cache to speed
 * up decoding on larger images.
 *
 * @since 6.2.3
 */
public class IccCmykRasterOp implements RasterOp {
    private RenderingHints hints = null;
    private ColorSpace colorSpace;

    public IccCmykRasterOp(RenderingHints hints) {
        this.hints = hints;
        this.colorSpace = DeviceCMYK.getIccCmykColorSpace();
    }

    public WritableRaster filter(Raster src, WritableRaster dest) {

        if (dest == null) dest = src.createCompatibleWritableRaster();

        // may have to add some instance of checks
        byte[] srcPixels = ((DataBufferByte) src.getDataBuffer()).getData();
        int[] destPixels = ((DataBufferInt) dest.getDataBuffer()).getData();

        int bands = src.getNumBands();
        float[] colorValue = new float[bands];

        float[] rgbColorValue;

        for (int pixel = 0, intPixels = 0; pixel < srcPixels.length; pixel += bands, intPixels++) {

            colorValue[0] = (srcPixels[pixel] & 0xff) / 255.0f;
            colorValue[1] = (srcPixels[pixel + 1] & 0xff) / 255.0f;
            colorValue[2] = (srcPixels[pixel + 2] & 0xff) / 255.0f;
            colorValue[3] = (srcPixels[pixel + 3] & 0xff) / 255.0f;

            rgbColorValue = colorSpace.toRGB(colorValue); //new float[]{0.5f, 0.2f, 0.3f};//
            rgbColorValue[0] = rgbColorValue[0] * 255;
            rgbColorValue[1] = rgbColorValue[1] * 255;
            rgbColorValue[2] = rgbColorValue[2] * 255;

            destPixels[intPixels] = ((0xff) << 24) |
                    (((int) rgbColorValue[0] & 0xff) << 16) |
                    (((int) rgbColorValue[1] & 0xff) << 8) |
                    ((int) rgbColorValue[2] & 0xff);
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
