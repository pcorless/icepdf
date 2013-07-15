/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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

package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.tag.Tagger;
import org.icepdf.core.util.Defs;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for applying various colour models and masks to
 * image data.
 *
 * @since 5.0
 */
public class ImageUtility {

    private static final Logger logger =
            Logger.getLogger(ImageUtility.class.toString());

    protected static final int[] GRAY_1_BIT_INDEX_TO_RGB_REVERSED = new int[]{
            0xFFFFFFFF,
            0xFF000000
    };

    protected static final int[] GRAY_1_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFFFFFFFF
    };

    protected static final int[] GRAY_2_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF555555,
            0xFFAAAAAA,
            0xFFFFFFFF
    }; // 0. 1 2 3 4 5. 6 7 8 9 A. B C D E F.     0/3, 1/3, 2/3, 3/3

    protected static final int[] GRAY_4_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF111111,
            0xFF222222,
            0xFF333333,
            0xFF444444,
            0xFF555555,
            0xFF666666,
            0xFF777777,
            0xFF888888,
            0xFF999999,
            0xFFAAAAAA,
            0xFFBBBBBB,
            0xFFCCCCCC,
            0xFFDDDDDD,
            0xFFEEEEEE,
            0xFFFFFFFF
    };


    protected static final int JPEG_ENC_UNKNOWN_PROBABLY_YCbCr = 0;
    protected static final int JPEG_ENC_RGB = 1;
    protected static final int JPEG_ENC_CMYK = 2;
    protected static final int JPEG_ENC_YCbCr = 3;
    protected static final int JPEG_ENC_YCCK = 4;
    protected static final int JPEG_ENC_GRAY = 5;

    protected static String[] JPEG_ENC_NAMES = new String[]{
            "JPEG_ENC_UNKNOWN_PROBABLY_YCbCr",
            "JPEG_ENC_RGB",
            "JPEG_ENC_CMYK",
            "JPEG_ENC_YCbCr",
            "JPEG_ENC_YCCK",
            "JPEG_ENC_GRAY"
    };


    // default cmyk value,  > 255 will lighten the image.
    private static float blackRatio;

    // JDK 1.5 imaging order flag and b/r switch
    private static int redIndex = 0;
    private static int blueIndex = 2;

    static {
        // sniff out jdk 1.5 version
        String version = System.getProperty("java.version");
        if (version.contains("1.5")) {
            redIndex = 2;
            blueIndex = 0;
        }

        // black ratio
        blackRatio = Defs.intProperty("org.icepdf.core.cmyk.image.black", 255);
    }

    protected static BufferedImage alterBufferedImage(BufferedImage bi, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;

        int width = bi.getWidth();
        int height = bi.getHeight();

        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
            // scale the image to match the image mask.
            if (width < smaskWidth || height < smaskHeight) {
                // calculate scale factors.
                double scaleX = smaskWidth / (double) width;
                double scaleY = smaskHeight / (double) height;
                // scale the mask to match the base image.
                AffineTransform tx = new AffineTransform();
                tx.scale(scaleX, scaleY);
                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                BufferedImage bim = op.filter(bi, null);
                bi.flush();
                bi = bim;
            }
            width = bi.getWidth();
            height = bi.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
            // scale the image to match the image mask.
            if (width < maskWidth || height < maskHeight) {
                // calculate scale factors.
                double scaleX = maskWidth / (double) width;
                double scaleY = maskHeight / (double) height;
                // scale the mask to match the base image.
                AffineTransform tx = new AffineTransform();
                tx.scale(scaleX, scaleY);
                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                BufferedImage bim = op.filter(bi, null);
                bi.flush();
                bi = bim;
            }
            width = bi.getWidth();
            height = bi.getHeight();
        }
        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null &&
                (maskMinRGB == null || maskMaxRGB == null)) {
            return null;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean gotARBG = false;
                int argb = 0;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    // Alpha equals greyscale value of smask
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                } else {
                    gotARBG = true;
                    argb = bi.getRGB(x, y);
                    int red = ((argb >> 16) & 0xFF);
                    int green = ((argb >> 8) & 0xFF);
                    int blue = (argb & 0xFF);
                    if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                            green >= maskMinGreen && green <= maskMaxGreen &&
                            red >= maskMinRed && red <= maskMaxRed) {
                        alpha = 0x00;
                    }
                }
                if (alpha != 0xFF) {
                    if (!gotARBG)
                        argb = bi.getRGB(x, y);
                    argb &= 0x00FFFFFF;
                    argb |= ((alpha << 24) & 0xFF000000);
                    bi.setRGB(x, y, argb);
                }
            }
        }
        // apply the soft mask.
        if (smaskImage != null) {
            int[] srcBand = new int[width];
            int[] sMaskBand = new int[width];
            // iterate over each band to apply the mask
            for (int i = 0; i < height; i++) {
                bi.getRGB(0, i, width, 1, srcBand, 0, width);
                smaskImage.getRGB(0, i, width, 1, sMaskBand, 0, width);
                // apply the soft mask blending
                for (int j = 0; j < width; j++) {
                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                            | (srcBand[j] & ~0xff000000);
                }
                bi.setRGB(0, i, width, 1, sMaskBand, 0, width);
            }
        }
        return bi;
    }

    protected static BufferedImage alterRasterCMYK2BGRA(WritableRaster wr,
                                                        float[] decode) {
        int width = wr.getWidth();
        int height = wr.getHeight();

        // this convoluted cymk->rgba method is from DeviceCMYK class.
        float inCyan, inMagenta, inYellow, inBlack;
        float lastCyan = -1, lastMagenta = -1, lastYellow = -1, lastBlack = -1;
        double c, m, y2, aw, ac, am, ay, ar, ag, ab;
        float outRed, outGreen, outBlue;
        int rValue = 0, gValue = 0, bValue = 0, alpha = 0;
        int[] values = new int[wr.getNumBands()];
        byte[] dataValues = new byte[wr.getNumBands()];
        byte[] compColors;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                compColors = (byte[]) wr.getDataElements(x, y, dataValues);
                // apply decode param.
                ImageUtility.getNormalizedComponents(
                        compColors,
                        decode,
                        values);

                inCyan = values[0] / 255.0f;
                inMagenta = values[1] / 255.0f;
                inYellow = values[2] / 255.0f;
                // lessen the amount of black, standard 255 fraction is too dark
                // increasing the denominator has the same affect of lighting up
                // the image.
                inBlack = (values[3] / blackRatio);

                if (!(inCyan == lastCyan && inMagenta == lastMagenta &&
                        inYellow == lastYellow && inBlack == lastBlack)) {

                    c = clip(0, 1, inCyan + inBlack);
                    m = clip(0, 1, inMagenta + inBlack);
                    y2 = clip(0, 1, inYellow + inBlack);
                    aw = (1 - c) * (1 - m) * (1 - y2);
                    ac = c * (1 - m) * (1 - y2);
                    am = (1 - c) * m * (1 - y2);
                    ay = (1 - c) * (1 - m) * y2;
                    ar = (1 - c) * m * y2;
                    ag = c * (1 - m) * y2;
                    ab = c * m * (1 - y2);

                    outRed = (float) clip(0, 1, aw + 0.9137 * am + 0.9961 * ay + 0.9882 * ar);
                    outGreen = (float) clip(0, 1, aw + 0.6196 * ac + ay + 0.5176 * ag);
                    outBlue = (float) clip(0, 1, aw + 0.7804 * ac + 0.5412 * am + 0.0667 * ar + 0.2118 * ag + 0.4863 * ab);
                    rValue = (int) (outRed * 255);
                    gValue = (int) (outGreen * 255);
                    bValue = (int) (outBlue * 255);
                    alpha = 0xFF;
                }
                lastCyan = inCyan;
                lastMagenta = inMagenta;
                lastYellow = inYellow;
                lastBlack = inBlack;

                values[redIndex] = rValue;
                values[1] = gValue;
                values[blueIndex] = bValue;
                values[3] = alpha;
                wr.setPixel(x, y, values);
            }
        }
        // apply the soft mask, but first we need an rgba image,
        // this is pretty expensive, would like to find quicker method.
        BufferedImage tmpImage = makeRGBABufferedImage(wr, Transparency.TRANSLUCENT);
        return tmpImage;
    }

    protected static BufferedImage alterRasterCMYK2BGRA(WritableRaster wr) {
        int width = wr.getWidth();
        int height = wr.getHeight();

        // this convoluted cymk->rgba method is from DeviceCMYK class.
        float inCyan, inMagenta, inYellow, inBlack;
        float lastCyan = -1, lastMagenta = -1, lastYellow = -1, lastBlack = -1;
        double c, m, y2, aw, ac, am, ay, ar, ag, ab;
        float outRed, outGreen, outBlue;
        int rValue = 0, gValue = 0, bValue = 0, alpha = 0;
        int[] values = new int[4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);

                inCyan = values[0] / 255.0f;
                inMagenta = values[1] / 255.0f;
                inYellow = values[2] / 255.0f;
                // lessen the amount of black, standard 255 fraction is too dark
                // increasing the denominator has the same affect of lighting up
                // the image.
                inBlack = (values[3] / blackRatio);

                if (!(inCyan == lastCyan && inMagenta == lastMagenta &&
                        inYellow == lastYellow && inBlack == lastBlack)) {

                    c = clip(0, 1, inCyan + inBlack);
                    m = clip(0, 1, inMagenta + inBlack);
                    y2 = clip(0, 1, inYellow + inBlack);
                    aw = (1 - c) * (1 - m) * (1 - y2);
                    ac = c * (1 - m) * (1 - y2);
                    am = (1 - c) * m * (1 - y2);
                    ay = (1 - c) * (1 - m) * y2;
                    ar = (1 - c) * m * y2;
                    ag = c * (1 - m) * y2;
                    ab = c * m * (1 - y2);

                    outRed = (float) clip(0, 1, aw + 0.9137 * am + 0.9961 * ay + 0.9882 * ar);
                    outGreen = (float) clip(0, 1, aw + 0.6196 * ac + ay + 0.5176 * ag);
                    outBlue = (float) clip(0, 1, aw + 0.7804 * ac + 0.5412 * am + 0.0667 * ar + 0.2118 * ag + 0.4863 * ab);
                    rValue = (int) (outRed * 255);
                    gValue = (int) (outGreen * 255);
                    bValue = (int) (outBlue * 255);
                    alpha = 0xFF;
                }
                lastCyan = inCyan;
                lastMagenta = inMagenta;
                lastYellow = inYellow;
                lastBlack = inBlack;

                values[redIndex] = rValue;
                values[1] = gValue;
                values[blueIndex] = bValue;
                values[3] = alpha;
                wr.setPixel(x, y, values);
            }
        }
        // apply the soft mask, but first we need an rgba image,
        // this is pretty expensive, would like to find quicker method.
        BufferedImage tmpImage = makeRGBABufferedImage(wr, Transparency.TRANSLUCENT);
        return tmpImage;
    }

    /**
     * Clips the value according to the specified floor and ceiling.
     *
     * @param floor   floor value of clip
     * @param ceiling ceiling value of clip
     * @param value   value to clip.
     * @return clipped value.
     */
    private static double clip(double floor, double ceiling, double value) {
        if (value < floor) {
            value = floor;
        }
        if (value > ceiling) {
            value = ceiling;
        }
        return value;
    }

    public static void displayImage(final BufferedImage bufferedImage, final String title) {

        if (bufferedImage == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final BufferedImage bi = bufferedImage;
                final JFrame f = new JFrame("Image - " + title);
                f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

                JComponent image = new JComponent() {
                    @Override
                    public void paint(Graphics g_) {
                        super.paint(g_);
                        g_.drawImage(bi, 0, 0, f);
                    }
                };
                image.setPreferredSize(new Dimension(bi.getWidth(), bi.getHeight()));
                image.setSize(new Dimension(bi.getWidth(), bi.getHeight()));

                JPanel test = new JPanel();
                test.setPreferredSize(new Dimension(1200, 1200));
                JScrollPane tmp = new JScrollPane(image);
                tmp.revalidate();
                f.setSize(new Dimension(800, 800));
                f.getContentPane().add(tmp);
                f.validate();
                f.setVisible(true);
            }
        });


    }

    /**
     * Utility to build an RGBA buffered image using the specified raster and
     * a Transparency.OPAQUE transparency model.
     *
     * @param wr writable raster of image.
     * @return constructed image.
     */
    protected static BufferedImage makeRGBABufferedImage(WritableRaster wr) {
        return makeRGBABufferedImage(wr, Transparency.OPAQUE);
    }

    /**
     * Utility to build an RGBA buffered image using the specified raster and
     * transparency type.
     *
     * @param wr           writable raster of image.
     * @param transparency any valid Transparency interface type. Bitmask,
     *                     opaque and translucent.
     * @return constructed image.
     */
    protected static BufferedImage makeRGBABufferedImage(WritableRaster wr,
                                                         final int transparency) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[4];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = 8;
        }
        ColorModel cm = new ComponentColorModel(
                cs, bits, true, false,
                transparency,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    protected static BufferedImage makeRGBtoRGBABuffer(WritableRaster wr, int width, int height) {
        BufferedImage tmpImage = ImageUtility.makeRGBBufferedImage(wr);
        BufferedImage argbImage = new BufferedImage(width,
                height, BufferedImage.TYPE_INT_ARGB);
        int[] srcBand = new int[width];
        int[] argbBand = new int[width];
        // iterate over each band to apply the mask
        int r, g, b;
        for (int i = 0; i < height; i++) {
            tmpImage.getRGB(0, i, width, 1, srcBand, 0, width);
            // apply the soft mask blending
            for (int j = 0; j < width; j++) {
                r = (srcBand[j] >> 16) & 0xFF;
                g = (srcBand[j] >> 8) & 0xFF;
                b = (srcBand[j]) & 0xFF;
                argbBand[j] = 0xff000000 |
                        (r << 16) | (g << 8) | b;
            }
            argbImage.setRGB(0, i, width, 1, argbBand, 0, width);
        }
        tmpImage.flush();
        return argbImage;
    }


    protected static BufferedImage makeRGBBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[3];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    protected static BufferedImage makeGrayBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        int[] bits = new int[1];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    protected static BufferedImage makeRGBBufferedImage(WritableRaster wr,
                                                        float[] decode, PColorSpace colorSpace) {
        int width = wr.getWidth();
        int height = wr.getHeight();
        BufferedImage rgbImage = new BufferedImage(width,
                height, BufferedImage.TYPE_INT_RGB);
        WritableRaster rgbRaster = rgbImage.getRaster();
        float[] values = new float[colorSpace.getNumComponents()];
        int[] rgbValues = new int[4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                // apply decode param.
                getNormalizedComponents(
                        (byte[]) wr.getDataElements(x, y, null),
                        decode,
                        rgbValues);
                colorSpace.normaliseComponentsToFloats(rgbValues, values, 255.0f);
                Color c = colorSpace.getColor(values);
                rgbValues[0] = c.getRed();
                rgbValues[1] = c.getGreen();
                rgbValues[2] = c.getBlue();

                rgbRaster.setPixel(x, y, rgbValues);
            }
        }
        return rgbImage;
    }

    // This method returns a buffered image with the contents of an image from
    // java almanac
    protected static BufferedImage makeRGBABufferedImageFromImage(Image image) {

        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        // This code ensures that all the pixels in the image are loaded
        image = new ImageIcon(image).getImage();
        // Determine if the image has transparent pixels; for this method's
        // implementation, see Determining If an Image Has Transparent Pixels
        boolean hasAlpha = hasAlpha(image);
        // Create a buffered image with a format that's compatible with the screen
        BufferedImage bImage = null;
        try {
            // graphics environment calls can through headless exceptions so
            // proceed with caution.
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gs.getDefaultConfiguration();
            // Determine the type of transparency of the new buffered image
            int transparency = Transparency.OPAQUE;
            if (hasAlpha) {
                transparency = Transparency.BITMASK;
            }
            // Create the buffered image
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return null;
            }
            bImage = gc.createCompatibleImage(
                    image.getWidth(null), image.getHeight(null), transparency);
        } catch (HeadlessException e) {
            // The system does not have a screen
        }
        if (bImage == null) {
            // Create a buffered image using the default color model
            int type = BufferedImage.TYPE_INT_RGB;
            if (hasAlpha) {
                type = BufferedImage.TYPE_INT_ARGB;
            }
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return null;
            }
            bImage = new BufferedImage(width, height, type);
        }
        // Copy image to buffered image
        Graphics g = bImage.createGraphics();
        // Paint the image onto the buffered image
        g.drawImage(image, 0, 0, null);
        g.dispose();
        image.flush();
        return bImage;
    }

    // returns true if the specified image has transparent pixels, from
    // java almanac

    protected static boolean hasAlpha(Image image) {
        // If buffered image, the color model is readily available
        if (image instanceof BufferedImage) {
            BufferedImage bufferedImage = (BufferedImage) image;
            return bufferedImage.getColorModel().hasAlpha();
        }
        // Use a pixel grabber to retrieve the image's color model;
        // grabbing a single pixel is usually sufficient
        PixelGrabber pixelGrabber = new PixelGrabber(image, 0, 0, 1, 1, false);
        try {
            pixelGrabber.grabPixels();
        } catch (InterruptedException e) {
            // fail quietly
        }
        // Get the image's color model
        ColorModel cm = pixelGrabber.getColorModel();
        return cm == null || cm.hasAlpha();
    }

    /**
     * The basic idea is that we do a fuzzy colour conversion from YCCK to
     * CMYK.  The conversion is not perfect but when converted again from
     * CMYK to RGB the result is much better then going directly from YCCK to
     * RGB.
     * NOTE: no masking here, as it is done later in the call to
     * {@see alterRasterCMYK2BGRA}
     *
     * @param wr     writable raster to alter.
     * @param decode decode vector.
     */
    protected static void alterRasterYCCK2CMYK(WritableRaster wr,
                                               float[] decode) {

        float[] origValues = new float[wr.getNumBands()];
        double[] pixels = new double[4];
        double Y, Cb, Cr, K;
        double lastY = -1, lastCb = -1, lastCr = -1, lastK = -1;
        double c = 0, m = 0, y2 = 0, k = 0;

        int width = wr.getWidth();
        int height = wr.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // apply decode param.
                getNormalizedComponents(
                        (byte[]) wr.getDataElements(x, y, null),
                        decode,
                        origValues);

                Y = origValues[0] * 255;
                Cb = origValues[1] * 255;
                Cr = origValues[2] * 255;
                K = origValues[3] * 255;

                if (!(lastY == y && lastCb == Cb && lastCr == Cr && lastK == K)) {

                    // intel codecs, http://software.intel.com/sites/products/documentation/hpc/ipp/ippi/ippi_ch6/ch6_color_models.html
                    // Intel IPP conversion for JPEG codec.
                    c = 255 - (Y + (1.402 * Cr) - 179.456);
                    m = 255 - (Y - (0.34414 * Cb) - (0.71413636 * Cr) + 135.45984);
                    y2 = 255 - (Y + (1.7718 * Cb) - 226.816);
                    k = K;

                    c = clip(0, 255, c);
                    m = clip(0, 255, m);
                    y2 = clip(0, 255, y2);
                }

                lastY = Y;
                lastCb = Cb;
                lastCr = Cr;
                lastK = K;

                pixels[0] = c;
                pixels[1] = m;
                pixels[2] = y2;
                pixels[3] = k;

                wr.setPixel(x, y, pixels);
            }
        }
    }

    /**
     * Apply the Decode Array domain for each colour component.  Assumes output
     * range is 0-1f for each value in out.
     *
     * @param pixels colour to process by decode
     * @param decode decode array for colour space
     * @param out    return value
     *               always (2<sup>bitsPerComponent</sup> - 1).
     */
    protected static void getNormalizedComponents(
            byte[] pixels,
            float[] decode,
            float[] out) {
        // interpolate each colour component for the given decode domain.
        for (int i = 0; i < pixels.length; i++) {
            out[i] = decode[i * 2] + (pixels[i] & 0xff) * decode[(i * 2) + 1];
        }
    }

    /**
     * Apply the Decode Array domain for each colour component. Assumes output
     * range is 0-255 for each value in out.
     *
     * @param pixels colour to process by decode
     * @param decode decode array for colour space
     * @param out    return value
     *               always (2<sup>bitsPerComponent</sup> - 1).
     */
    protected static void getNormalizedComponents(
            byte[] pixels,
            float[] decode,
            int[] out) {
        // interpolate each colour component for the given decode domain.
        for (int i = 0; i < pixels.length; i++) {
            out[i] = (int) ((decode[i * 2] * 255) + (pixels[i] & 0xff) * (decode[(i * 2) + 1] * 255));
        }
    }

    /**
     * Convert a rgb encoded raster to the specified colour space.
     *
     * @param wr         writable rasters in rgb.
     * @param colorSpace colour space to convert colours too.
     */
    protected static void alterRasterRGB2PColorSpace(WritableRaster wr, PColorSpace colorSpace) {
        if (colorSpace instanceof DeviceRGB)
            return;
        float[] values = new float[3];
        int[] rgbValues = new int[3];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, rgbValues);

                PColorSpace.reverseInPlace(rgbValues);
                colorSpace.normaliseComponentsToFloats(rgbValues, values, 255.0f);
                Color c = colorSpace.getColor(values);
                rgbValues[0] = c.getRed();
                rgbValues[1] = c.getGreen();
                rgbValues[2] = c.getBlue();

                wr.setPixel(x, y, rgbValues);
            }
        }
    }

    protected static WritableRaster alterRasterRGBA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null && (maskMinRGB == null || maskMaxRGB == null))
            return null;

        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, rgbaValues);
                int red = rgbaValues[0];
                int green = rgbaValues[1];
                int blue = rgbaValues[2];

                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    // Alpha equals greyscale value of smask
                    alpha = (smaskImage.getRGB(x, y)) & 0xFF;//(smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                } else if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                        green >= maskMinGreen && green <= maskMaxGreen &&
                        red >= maskMinRed && red <= maskMaxRed) {
                    alpha = 0x00;
                }
                if (alpha != 0xFF) {
                    rgbaValues[3] = alpha;
                    wr.setPixel(x, y, rgbaValues);
                }
            }
        }
        return wr;
    }

    protected static void alterRasterY2Gray(WritableRaster wr,
                                            float[] decode) {
        int[] values = new int[1];
        int width = wr.getWidth();
        int height = wr.getHeight();
        boolean defaultDecode = 0.0f == decode[0];

        int Y;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);
                Y = values[0];
                Y = defaultDecode ? 255 - Y : Y;
                Y = (Y < 0) ? (byte) 0 : (Y > 255) ? (byte) 0xFF : (byte) Y;
                values[0] = Y;
                wr.setPixel(x, y, values);
            }
        }
    }

    protected static BufferedImage alterRasterYCbCr2RGBA(WritableRaster wr,
                                                         float[] decode) {
        byte[] dataValues = new byte[wr.getNumBands()];
        byte[] compColors;
        float[] values = new float[wr.getNumBands()];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {
                compColors = (byte[]) wr.getDataElements(x, y, dataValues);
                // apply decode param.
                ImageUtility.getNormalizedComponents(
                        compColors,
                        decode,
                        values);

                float Y = values[0] * 255;
                float Cb = values[1] * 255;
                float Cr = values[2] * 255;

                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                float rVal = Y + (1370705 * Cr_128 / 1000000);
                float gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                float bVal = Y + (1732446 * Cb_128 / 1000000);

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;

                // apply mask and smask values.

                values[0] = rByte;
                values[1] = gByte;
                values[2] = bByte;

                wr.setPixel(x, y, values);
            }

        }
        BufferedImage tmpImage = ImageUtility.makeRGBtoRGBABuffer(wr, width, height);
        return tmpImage;
    }

    /**
     * The basic idea is that we do a fuzzy colour conversion from YCCK to
     * BGRA.  The conversion is not perfect giving a bit of a greenish hue to the
     * image in question.  I've tweaked the core Adobe algorithm ot give slightly
     * "better" colour representation but it does seem to make red a little light.
     *
     * @param wr         image stream to convert colour space.
     * @param smaskImage smask used to apply alpha values.
     * @param maskImage  maks image for drop out.
     */
    protected static void alterRasterYCCK2BGRA(WritableRaster wr,
                                               BufferedImage smaskImage,
                                               BufferedImage maskImage,
                                               float[] decode,
                                               int bitsPerComponent) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        byte[] dataValues = new byte[wr.getNumBands()];
        float[] origValues = new float[wr.getNumBands()];
        double[] rgbaValues = new double[4];

        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // apply decode param.
                ImageUtility.getNormalizedComponents(
                        (byte[]) wr.getDataElements(x, y, dataValues),
                        decode,
                        origValues);

                float Y = origValues[0] * 255;
                float Cb = origValues[1] * 255;
                float Cr = origValues[2] * 255;
//                float K = origValues[3] * 255;

                // removing alteration for now as some samples are too dark.
                // Y *= .95; // gives a darker image,  as y approaches zero,
                // the image becomes darke

                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                // adobe conversion for CCIR Rec. 601-1 standard.
                // http://partners.adobe.com/public/developer/en/ps/sdk/5116.DCT_Filter.pdf
//                double rVal = Y + (1.4020 * Cr_128);
//                double gVal = Y - (.3441363 * Cb_128) - (.71413636 * Cr_128);
//                double bVal = Y + (1.772 * Cb_128);

                // intel codecs, http://software.intel.com/sites/products/documentation/hpc/ipp/ippi/ippi_ch6/ch6_color_models.html
                // Intel IPP conversion for JPEG codec.
//                double rVal = Y + (1.402 * Cr) - 179.456;
//                double gVal = Y - (0.34414 * Cb) - (.71413636 * Cr) + 135.45984;
//                double bVal = Y + (1.772 * Cb) - 226.816;

                // ICEsoft custom algorithm, results may vary, res are a little
                // off but over all a better conversion/ then the stoke algorithms.
                double rVal = Y + (1.4020 * Cr_128);
                double gVal = Y + (.14414 * Cb_128) + (.11413636 * Cr_128);
                double bVal = Y + (1.772 * Cb_128);

                // Intel IPP conversion for ITU-R BT.601 for video
                // default 16, higher more green and darker blacks, lower less
                // green hue and lighter blacks.
//                double kLight = (1.164 * (Y -16 ));
//                double rVal = kLight + (1.596 * Cr_128);
//                double gVal = kLight - (0.392 * Cb_128) - (0.813 * Cr_128);
//                double bVal = kLight + (1.017 * Cb_128);
                // intel PhotoYCC Color Model [0.1],  not a likely candidate for jpegs.
//                double y1 = Y/255.0;
//                double c1 = Cb/255.0;
//                double c2 = Cr/255.0;
//                double rVal = ((0.981 * y1) + (1.315 * (c2 - 0.537))) *255.0;
//                double gVal = ((0.981 * y1) - (0.311 * (c1 - 0.612))- (0.669 * (c2 - 0.537))) *255.0;
//                double bVal = ((0.981 * y1) + (1.601 * (c1 - 0.612))) *255.0;

                // check the range an convert as needed.
                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }

                rgbaValues[0] = bByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = rByte;
                rgbaValues[3] = alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    protected static void alterRasterYCbCrA2RGBA(WritableRaster wr) {


        float[] origValues = new float[4];
        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, origValues);
                // apply decode param.
                // couldn't quite get this one right, doesn't decode
                // as I would have thought.
//                origValues = getNormalizedComponents(
//                        (byte[])wr.getDataElements(x,y,null),
//                        decode,
//                        maxValue);

                float Y = origValues[0];
                float Cb = origValues[1];
                float Cr = origValues[2];
                float K = origValues[3];
                Y = K - Y;
                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                float rVal = Y + (1370705 * Cr_128 / 1000000);
                float gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                float bVal = Y + (1732446 * Cb_128 / 1000000);

                /*
                // Formula used in JPEG standard. Gives pretty similar results
                //int rVal = Y + (1402000 * Cr_128/ 1000000);
                //int gVal = Y - (344140 * Cb_128 / 1000000) - (714140 * Cr_128 / 1000000);
                //int bVal = Y + (1772000 * Cb_128 / 1000000);
                */

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                float alpha = K;

                rgbaValues[0] = rByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = bByte;
                rgbaValues[3] = (int) alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    /**
     * (see 8.9.6.3, "Explicit Masking")
     * Explicit Masking algorithm, as of PDF 1.3.  The entry in an image dictionary
     * may be an image mask, as described under "Stencil Masking", which serves as
     * an explicit mask for the primary or base image.  The base image and the
     * image mask need not have the same resolution (width, height), but since
     * all images are defined on the unit square in user space, their boundaries on the
     * page will conincide; that is, they will overlay each other.
     * <p/>
     * The image mask indicates indicates which places on the page are to be painted
     * and which are to be masked out (left unchanged).  Unmasked areas are painted
     * with the corresponding portions of the base image; masked areas are not.
     *
     * @param baseImage base image in which the mask weill be applied to
     * @param maskImage image mask to be applied to base image.
     */
    protected static BufferedImage applyExplicitMask(BufferedImage baseImage, BufferedImage maskImage) {
        // check to see if we need to scale the mask to match the size of the
        // base image.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        final int maskWidth = maskImage.getWidth();
        final int maskHeight = maskImage.getHeight();

        // we're going for quality over memory foot print here, for most
        // masks its better to scale the base image up to the mask size.
        if (baseWidth != maskWidth || baseHeight != maskHeight) {
            // calculate scale factors.
            double scaleX = maskWidth / (double) baseWidth;
            double scaleY = maskHeight / (double) baseHeight;
            // scale the mask to match the base image.
            AffineTransform tx = new AffineTransform();
            tx.scale(scaleX, scaleY);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage sbim = op.filter(baseImage, null);
            baseImage.flush();
            baseImage = sbim;
        }

        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        baseWidth = baseImage.getWidth();
        baseHeight = baseImage.getHeight();

        BufferedImage argbImage = new BufferedImage(baseWidth,
                baseHeight, BufferedImage.TYPE_INT_ARGB);
        int[] srcBand = new int[baseWidth];
        int[] maskBnd = new int[baseWidth];
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            maskImage.getRGB(0, i, baseWidth, 1, maskBnd, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {
                if (maskBnd[j] == 0 || maskBnd[j] == 0xffffff) {
                    //  set the pixel as transparent
                    maskBnd[j] = 0xff;
                } else {
                    maskBnd[j] = srcBand[j];
                }
            }
            argbImage.setRGB(0, i, baseWidth, 1, maskBnd, 0, baseWidth);
        }
        baseImage.flush();
        baseImage = argbImage;

        return baseImage;
    }

    /**
     * (see 11.6.5.3, "Soft-Mask Images")
     * A subsidiary image XObject defining a soft-mask image that shall be used
     * as a source of mask shape or mask opacity values in the transparent imaging
     * model. The alpha source parameter in the graphics state determines whether
     * the mask values shall be interpreted as shape or opacity.
     * <p/>
     * If present, this entry shall override the current soft mask in the graphics
     * state, as well as the image’s Mask entry, if any. However, the other
     * transparency-related graphics state parameters—blend mode and alpha
     * constant—shall remain in effect. If SMask is absent, the image shall
     * have no associated soft mask (although the current soft mask in the
     * graphics state may still apply).
     *
     * @param baseImage base image in which the mask weill be applied to
     */
    protected static BufferedImage applyExplicitSMask(BufferedImage baseImage, BufferedImage sMaskImage) {
        // check to see if we need to scale the mask to match the size of the
        // base image.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        final int maskWidth = sMaskImage.getWidth();
        final int maskHeight = sMaskImage.getHeight();

        // we're going for quality over memory foot print here, for most
        // masks its better to scale the base image up to the mask size.
        if (baseWidth != maskWidth || baseHeight != maskHeight) {
            // calculate scale factors.
            double scaleX = maskWidth / (double) baseWidth;
            double scaleY = maskHeight / (double) baseHeight;
            // scale the mask to match the base image.
            AffineTransform tx = new AffineTransform();
            tx.scale(scaleX, scaleY);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage sbim = op.filter(baseImage, null);
            baseImage.flush();
            baseImage = sbim;
        }
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        baseWidth = baseImage.getWidth();
        baseHeight = baseImage.getHeight();

        BufferedImage argbImage = new BufferedImage(baseWidth,
                baseHeight, BufferedImage.TYPE_INT_ARGB);
        int[] srcBand = new int[baseWidth];
        int[] sMaskBand = new int[baseWidth];
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            sMaskImage.getRGB(0, i, baseWidth, 1, sMaskBand, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {
                if (sMaskBand[j] != -1 || sMaskBand[j] != 0xffffff || sMaskBand[j] != 0) {
                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                            | (srcBand[j] & ~0xff000000);
                }
            }
            argbImage.setRGB(0, i, baseWidth, 1, sMaskBand, 0, baseWidth);
        }
        baseImage.flush();
        baseImage = argbImage;

        return baseImage;
    }

    /**
     * Treats the base image as as mask data applying the specified fill colour
     * to the flagged bytes and a transparency value otherwise. This method
     * creates a new BufferedImage with a transparency model so it will cause
     * a memory spike.
     *
     * @param baseImage masking image.
     * @param fill      fill value to apply to mask.
     * @return masked image encoded with the fill colour and transparency.
     */
    protected static BufferedImage applyExplicitMask(BufferedImage baseImage, Color fill) {
        // create an
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        BufferedImage imageMask = new BufferedImage(baseWidth, baseHeight,
                BufferedImage.TYPE_INT_ARGB);

        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        for (int y = 0; y < baseHeight; y++) {
            for (int x = 0; x < baseWidth; x++) {
                int maskPixel = baseImage.getRGB(x, y);
                if (!(maskPixel == -1 || maskPixel == 0xffffff)) {
                    imageMask.setRGB(x, y, fill.getRGB());
                }
            }
        }
        // clean up the old image.
        baseImage.flush();
        // return the mask.
        return imageMask;
    }

    /**
     * Temporarily pulled out the index colur model application for images
     * from the raw image decode.  This method is only called from JPEG2000
     * code for now but will be consolidate as we move to to 5.0
     */
    protected static BufferedImage applyIndexColourModel(BufferedImage image,
                                                         int width, int height, PColorSpace colourSpace, int bitspercomponent) {
        BufferedImage img;
        colourSpace.init();
        // build out the colour table.
        Color[] colors = ((Indexed) colourSpace).accessColorTable();
        int colorsLength = (colors == null) ? 0 : colors.length;
        int[] cmap = new int[256];
        for (int i = 0; i < colorsLength; i++) {
            if (colors != null) {
                cmap[i] = colors[i].getRGB();
            }
        }
        for (int i = colorsLength; i < cmap.length; i++) {
            cmap[i] = 0xFF000000;
        }
        // build a new buffer with indexed colour model.
        DataBuffer db = image.getRaster().getDataBuffer();
        SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
        WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
        ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
        img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    protected static int getJPEGEncoding(byte[] data, int dataLength) {
        int jpegEncoding = JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;

        boolean foundAPP14 = false;
        byte compsTypeFromAPP14 = 0;
        boolean foundSOF = false;
        int numCompsFromSOF = 0;
        boolean foundSOS = false;
        int numCompsFromSOS = 0;

        int index = 0;
        while (true) {
            if (index >= dataLength)
                break;
            if (data[index] != ((byte) 0xFF))
                break;
            if (foundAPP14 && foundSOF)
                break;
            byte segmentType = data[index + 1];
            index += 2;
            if (segmentType == ((byte) 0xD8)) {
                //System.out.println("Found SOI (0xD8)");
                continue;
            }

            //System.out.println("Segment: " + Integer.toHexString( ((int)segmentType)&0xFF ));
            int length = (((data[index] << 8)) & 0xFF00) + (((int) data[index + 1]) & 0xFF);
            //System.out.println("   Length: " + length + "    Index: " + index);

            // APP14 (Might be Adobe file)
            if (segmentType == ((byte) 0xEE)) {
                //System.out.println("Found APP14 (0xEE)");
                if (length >= 14) {
                    foundAPP14 = true;
                    compsTypeFromAPP14 = data[index + 13];
                    //System.out.println("APP14 format: " + compsTypeFromAPP14);
                }
            } else if (segmentType == ((byte) 0xC0)) {
                foundSOF = true;
                //System.out.println("Found SOF (0xC0)  Start Of Frame");
                //int bitsPerSample = ( ((int)data[index+2]) & 0xFF );
                //int imageHeight = ( ((int)(data[index+3] << 8)) & 0xFF00 ) + ( ((int)data[index+4]) & 0xFF );
                //int imageWidth = ( ((int)(data[index+5] << 8)) & 0xFF00 ) + ( ((int)data[index+6]) & 0xFF );
                numCompsFromSOF = (((int) data[index + 7]) & 0xFF);
                //System.out.println("   bitsPerSample: " + bitsPerSample + ", imageWidth: " + imageWidth + ", imageHeight: " + imageHeight + ", numComps: " + numCompsFromSOF);
                //int[] compIds = new int[numCompsFromSOF];
                //for(int i = 0; i < numCompsFromSOF; i++) {
                //    compIds[i] = ( ((int)data[index+8+(i*3)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            } else if (segmentType == ((byte) 0xDA)) {
                foundSOS = true;
                //System.out.println("Found SOS (0xDA)  Start Of Scan");
                numCompsFromSOS = (((int) data[index + 2]) & 0xFF);
                //int[] compIds = new int[numCompsFromSOS];
                //for(int i = 0; i < numCompsFromSOS; i++) {
                //    compIds[i] = ( ((int)data[index+3+(i*2)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            }

            //System.out.println("   Data: " + org.icepdf.core.util.Utils.convertByteArrayToHexString( data, index+2, Math.min(length-2,dataLength-index-2), true, 20, '\n' ));
            index += length;
        }

        if (foundAPP14 && foundSOF) {
            if (compsTypeFromAPP14 == 0) {       // 0 seems to indicate no conversion
                if (numCompsFromSOF == 1)
                    jpegEncoding = JPEG_ENC_GRAY;
                if (numCompsFromSOF == 3)        // Most assume RGB. DesignJava_times_roman_substitution.PDF supports this.
                    jpegEncoding = JPEG_ENC_RGB;
                else if (numCompsFromSOF == 4)   // CMYK
                    jpegEncoding = JPEG_ENC_CMYK;
            } else if (compsTypeFromAPP14 == 1) {  // YCbCr
                jpegEncoding = JPEG_ENC_YCbCr;
            } else if (compsTypeFromAPP14 == 2) {  // YCCK
                jpegEncoding = JPEG_ENC_YCCK;
            }
        } else if (foundSOS) {
            if (numCompsFromSOS == 1)
                jpegEncoding = JPEG_ENC_GRAY; // Y
        }
        return jpegEncoding;
    }

    protected static BufferedImage makeImageWithRasterFromBytes(
            PColorSpace colourSpace,
            Color fill,
            int width, int height,
            int colorSpaceCompCount,
            int bitspercomponent,
            boolean imageMask,
            float[] decode,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB,
            int maskMinIndex, int maskMaxIndex, byte[] data, int dataLength) {
        BufferedImage img = null;

        // check if the ICCBased colour has an alternative that
        // we might support for decoding with a colorModel.
        if (colourSpace instanceof ICCBased) {
            ICCBased iccBased = (ICCBased) colourSpace;
            if (iccBased.getAlternate() != null) {
                // set the alternate as the current
                colourSpace = iccBased.getAlternate();
            }
        }

        if (colourSpace instanceof DeviceGray) {
            if (imageMask && bitspercomponent == 1) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_1_ImageMask");

                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height,
                        bitspercomponent, new Point(0, 0));

                // From PDF 1.6 spec, concerning ImageMask and Decode array:
                // it in different places different ways.
                // [0 1] (the default for an image mask), a sample value of 0 marks
                //       the page with the current color, and a 1 leaves the previous
                //       contents unchanged.
                // [1 0] Is the reverse
                // In case alpha transparency doesn't work, it'll paint white opaquely
                boolean defaultDecode = decode[0] == 0.0f;
                //int a = Color.white.getRGB();
                int a = 0x00FFFFFF; // Clear if alpha supported, else white
                int[] cmap = new int[]{
                        (defaultDecode ? fill.getRGB() : a),
                        (defaultDecode ? a : fill.getRGB())
                };
                int transparentIndex = (defaultDecode ? 1 : 0);
                IndexColorModel icm = new IndexColorModel(
                        bitspercomponent,       // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        true,                   // indicates whether alpha values are contained in the cmap array
                        transparentIndex,       // the index of the fully transparent pixel
                        db.getDataType());      // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(icm, wr, false, null);
            } else if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_124");
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                int[] cmap = null;
                if (bitspercomponent == 1) {
                    boolean defaultDecode = 0.0f == decode[0];
                    cmap = defaultDecode ? GRAY_1_BIT_INDEX_TO_RGB : GRAY_1_BIT_INDEX_TO_RGB_REVERSED;
                } else if (bitspercomponent == 2)
                    cmap = GRAY_2_BIT_INDEX_TO_RGB;
                else if (bitspercomponent == 4)
                    cmap = GRAY_4_BIT_INDEX_TO_RGB;
                ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_8");
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(),
                        width, height, 1, width, new int[]{0});
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                // apply decode array manually
                byte[] dataValues = new byte[sm.getNumBands()];
                float[] origValues = new float[sm.getNumBands()];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        ImageUtility.getNormalizedComponents(
                                (byte[]) wr.getDataElements(x, y, dataValues),
                                decode,
                                origValues
                        );
                        float gray = origValues[0] * 255;
                        byte rByte = (gray < 0) ? (byte) 0 : (gray > 255) ? (byte) 0xFF : (byte) gray;
                        origValues[0] = rByte;
                        wr.setPixel(x, y, origValues);
                    }
                }
                ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                ColorModel cm = new ComponentColorModel(cs, new int[]{bitspercomponent},
                        false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
            // apply explicit mask
            if (maskImage != null) {
                img = ImageUtility.applyExplicitMask(img, maskImage);
            }
            // apply soft mask
            if (smaskImage != null) {
                img = ImageUtility.applyExplicitSMask(img, smaskImage);
            }
        } else if (colourSpace instanceof DeviceRGB) {
            if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceRGB_8");
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_DeviceRGB_8_alpha=" + usingAlpha);
                int type = usingAlpha ? BufferedImage.TYPE_INT_ARGB :
                        BufferedImage.TYPE_INT_RGB;
                img = new BufferedImage(width, height, type);
                int[] dataToRGB = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                copyDecodedStreamBytesIntoRGB(data, dataToRGB);
                if (usingAlpha) {
                    img = ImageUtility.alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                }
            }
        } else if (colourSpace instanceof DeviceCMYK) {
            // TODO Look at doing CMYK properly, fallback code is very slow.
            if (false && bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceCMYK_8");
                DataBuffer db = new DataBufferByte(data, dataLength);
                int[] bandOffsets = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bandOffsets[i] = i;
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, colorSpaceCompCount, colorSpaceCompCount * width, bandOffsets);
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                //WritableRaster wr = Raster.createInterleavedRaster( db, width, height, colorSpaceCompCount*width, colorSpaceCompCount, bandOffsets, new Point(0,0) );
                ColorSpace cs = null;
//                try {
                //cs = new ColorSpaceCMYK(); //ColorSpace.getInstance( ColorSpace.CS_PYCC );//ColorSpace.TYPE_CMYK );
                ///cs = ColorSpaceWrapper.getICCColorSpaceInstance("C:\\Documents and Settings\\Mark Collette\\IdeaProjects\\TestJAI\\CMYK.pf");
//                }
//                catch (Exception csex) {
//                    if (logger.isLoggable(Level.FINE)) {
//                        logger.fine("Problem loading CMYK ColorSpace");
//                    }
//                }
                int[] bits = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bits[i] = bitspercomponent;
                ColorModel cm = new ComponentColorModel(cs, bits, false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
        } else if (colourSpace instanceof Indexed) {
            if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_124");
                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int[] cmap = new int[(colors == null) ? 0 : colors.length];
                for (int i = 0; i < cmap.length; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                int cmapMaxLength = 1 << bitspercomponent;
                if (cmap.length > cmapMaxLength) {
                    int[] cmapTruncated = new int[cmapMaxLength];
                    System.arraycopy(cmap, 0, cmapTruncated, 0, cmapMaxLength);
                    cmap = cmapTruncated;
                }
                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = smaskImage != null || maskImage != null ||
                        ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_Indexed_124_alpha=" +
                            (usingIndexedAlpha ? "indexed" : (usingAlpha ? "alpha" : "false")));
                if (usingAlpha) {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                    img = ImageUtility.alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                } else {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_8");
                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int colorsLength = (colors == null) ? 0 : colors.length;
                int[] cmap = new int[256];
                for (int i = 0; i < colorsLength; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                for (int i = colorsLength; i < cmap.length; i++)
                    cmap[i] = 0xFF000000;

                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_Indexed_8_alpha=" + (usingIndexedAlpha ? "indexed" : (usingAlpha ? "alpha" : "false")));
                if (usingIndexedAlpha) {
                    for (int i = maskMinIndex; i <= maskMaxIndex; i++)
                        cmap[i] = 0x00000000;
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, true, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else if (usingAlpha) {
                    int[] rgbaData = new int[width * height];
                    for (int index = 0; index < dataLength; index++) {
                        int cmapIndex = (data[index] & 0xFF);
                        rgbaData[index] = cmap[cmapIndex];
                    }
                    DataBuffer db = new DataBufferInt(rgbaData, rgbaData.length);
                    int[] masks = new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};
                    //SampleModel sm = new SinglePixelPackedSampleModel(
                    //    db.getDataType(), width, height, masks );
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, width, masks, new Point(0, 0));
                    ImageUtility.alterRasterRGBA(wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    ColorModel cm = new DirectColorModel(cs, 32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000, false, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            }
        }
        return img;
    }

    private static void copyDecodedStreamBytesIntoRGB(byte[] data, int[] pixels) {
        byte[] rgb = new byte[3];
        try {
            InputStream input = new ByteArrayInputStream(data);
            for (int pixelIndex = 0; pixelIndex < pixels.length; pixelIndex++) {
                int argb = 0xFF000000;
                final int toRead = 3;
                int haveRead = 0;
                while (haveRead < toRead) {
                    int currRead = input.read(rgb, haveRead, toRead - haveRead);
                    if (currRead < 0)
                        break;
                    haveRead += currRead;
                }
                if (haveRead >= 1)
                    argb |= ((((int) rgb[0]) << 16) & 0x00FF0000);
                if (haveRead >= 2)
                    argb |= ((((int) rgb[1]) << 8) & 0x0000FF00);
                if (haveRead >= 3)
                    argb |= (((int) rgb[2]) & 0x000000FF);
                pixels[pixelIndex] = argb;
            }
            input.close();
        } catch (IOException e) {
            logger.log(Level.FINE, "Problem copying decoding stream bytes: ", e);
        }
    }

    // default version of createBufferedImage
    public static BufferedImage createBufferedImage(Image imageIn) {
        return createBufferedImage(imageIn, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage createBufferedImage(Image imageIn,
                                                    int imageType) {
        BufferedImage bufferedImageOut = new BufferedImage(imageIn
                .getWidth(null), imageIn.getHeight(null), imageType);
        Graphics g = bufferedImageOut.getGraphics();
        g.drawImage(imageIn, 0, 0, null);

        return bufferedImageOut;
    }
}
