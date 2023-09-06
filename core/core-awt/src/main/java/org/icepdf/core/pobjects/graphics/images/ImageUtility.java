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

package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.pobjects.graphics.RasterOps.*;
import org.icepdf.core.util.Defs;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for applying various colour models and masks to
 * image data.
 *
 * @since 5.0
 */
public class ImageUtility {

    static final Logger logger =
            Logger.getLogger(ImageUtility.class.toString());

    static final int[] GRAY_1_BIT_INDEX_TO_RGB_REVERSED = new int[]{
            0xFFFFFFFF,
            0xFF000000
    };
    static final int[] GRAY_1_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFFFFFFFF
    };
    static final int[] GRAY_2_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF555555,
            0xFFAAAAAA,
            0xFFFFFFFF
    };
    // 0. 1 2 3 4 5. 6 7 8 9 A. B C D E F.     0/3, 1/3, 2/3, 3/3
    static final int[] GRAY_4_BIT_INDEX_TO_RGB = new int[]{
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

    private static final boolean scaleQuality;
    private static final int scaleWidth;
    private static final int scaleHeight;

    private static GraphicsConfiguration configuration;
    private static final int compatibleImageType;

    static {
        try {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
        } catch (Exception e) {
            // intentionally left blank
        }

        compatibleImageType = configuration != null ?
                configuration.createCompatibleImage(1, 1).getType() : -1;

        // decide if large images will be scaled
        scaleQuality = Defs.booleanProperty("org.icepdf.core.imageMaskScale.quality", true);

        // minimum size the image has to be before we apply restriction on size when scalling image mask and base image
        // to the same image size.
        scaleWidth = Defs.intProperty("org.icepdf.core.imageMaskScale.width", 7500);
        scaleHeight = Defs.intProperty("org.icepdf.core.imageMaskScale.height", 7500);
    }

    private ImageUtility() {

    }

    /**
     * Creates a new buffered image using a GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
     * instance.  If not available (headless) we full back to raw buffer creation.
     *
     * @param width  width of new image.
     * @param height height of new image.
     * @return returns an INT_RGB images.
     */
    public static BufferedImage createCompatibleImage(int width, int height) {
        if (configuration != null && compatibleImageType == BufferedImage.TYPE_INT_RGB) {
            return configuration.createCompatibleImage(width, height);
        } else {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
    }

    /**
     * Creates a new buffered image using a GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
     * instance.  If not available (headless) we full back to raw buffer creation.
     *
     * @param width  width of new image.
     * @param height height of new image.
     * @return created buffered image.
     */
    public static BufferedImage createTranslucentCompatibleImage(int width, int height) {
        if (configuration != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.finer("Creating translucent image buffer " + width + "x" + height);
            }
            return configuration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        } else {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private static BufferedImage alterBufferedImageAlpha(BufferedImage bi, int[] maskMinRGB, int[] maskMaxRGB) {

        // check for alpha, if not we need to create a copy
        if (!hasAlpha(bi)) {
            bi = createBufferedImage(bi);
        }

        int width = bi.getWidth();
        int height = bi.getHeight();

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

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0xFF;
                int argb = bi.getRGB(x, y);
                int red = ((argb >> 16) & 0xFF);
                int green = ((argb >> 8) & 0xFF);
                int blue = (argb & 0xFF);
                if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                        green >= maskMinGreen && green <= maskMaxGreen &&
                        red >= maskMinRed && red <= maskMaxRed) {
                    alpha = 0x00;
                }
                if (alpha != 0xFF) {
                    argb = bi.getRGB(x, y);
                    argb &= 0x00FFFFFF;
                    argb |= (0);
                    bi.setRGB(x, y, argb);
                }
            }
        }
        return bi;
    }

    public void writeImage(final BufferedImage bufferedImage, final String fileName, String baseOutputPath) {
        try {
            ImageIO.write(bufferedImage, "PNG",
                    new File(baseOutputPath + fileName + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void displayImage(final BufferedImage bufferedImage, final String title) {

        if (bufferedImage == null) {
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final JFrame f = new JFrame("Image - " + title);
                f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                final int width = (int) (bufferedImage.getWidth() * 1.2);
                final int height = (int) (bufferedImage.getHeight() * 1.2);

                JComponent image = new JComponent() {
                    @Override
                    public void paint(Graphics g_) {
                        super.paint(g_);
                        ((Graphics2D) g_).scale(1, 1);
                        g_.setColor(Color.green);
                        g_.fillRect(0, 0, 10000, 10000);
                        g_.drawImage(bufferedImage, 0, 0, f);
                        g_.setColor(Color.red);
                        g_.drawRect(0, 0, bufferedImage.getWidth() - 2, bufferedImage.getHeight() - 2);
                    }
                };
                image.setSize(new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight()));
                image.setPreferredSize(new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight()));

                JScrollPane tmp = new JScrollPane(image);
                tmp.revalidate();
                f.setSize(new Dimension(width, height));
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
    protected BufferedImage makeRGBABufferedImage(WritableRaster wr) {
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
    private BufferedImage makeRGBABufferedImage(WritableRaster wr, final int transparency) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[4];
        Arrays.fill(bits, 8);
        ColorModel cm = new ComponentColorModel(
                cs, bits, true, false,
                transparency,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    private static BufferedImage makeBufferedImage(Raster raster) {

        // create a generic colour model and reuse the wraster,  intent
        // is that this should save quite a bit of memory
        DirectColorModel colorModel = new DirectColorModel(24,
                0x00ff0000,    // Red
                0x0000ff00,    // Green
                0x000000ff,    // Blue
                0x0           // Alpha
        );
        raster = colorModel.createCompatibleWritableRaster(raster.getWidth(),
                raster.getHeight());
        return new BufferedImage(colorModel, (WritableRaster) raster, false, null);
    }


    static BufferedImage makeRGBBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[3];
        Arrays.fill(bits, 8);
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    static BufferedImage makeGrayBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        int[] bits = new int[1];
        Arrays.fill(bits, 8);
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    // This method returns a buffered image with the contents of an image from
    // java almanac
    protected BufferedImage makeRGBABufferedImageFromImage(Image image) {

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
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return null;
            }
            if (hasAlpha) {
                bImage = createTranslucentCompatibleImage(width, height);
            } else {
                bImage = createCompatibleImage(width, height);
            }
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

    public static boolean hasAlpha(Image image) {
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
     * Apply the Decode Array domain for each colour component.  Assumes output
     * range is 0-1f for each value in out.
     *
     * @param pixels colour to process by decode
     * @param decode decode array for colour space
     * @param out    return value
     *               always (2<sup>bitsPerComponent</sup> - 1).
     */
    protected void getNormalizedComponents(
            byte[] pixels,
            float[] decode,
            float[] out) {
        // interpolate each colour component for the given decode domain.
        for (int i = 0; i < pixels.length; i++) {
            out[i] = decode[i * 2] + (pixels[i] & 0xff) * decode[(i * 2) + 1];
        }
    }

    protected WritableRaster alterRasterRGBA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
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

    /**
     * (see 8.9.6.3, "Explicit Masking")
     * Explicit Masking algorithm, as of PDF 1.3.  The entry in an image dictionary
     * may be an image mask, as described under "Stencil Masking", which serves as
     * an explicit mask for the primary or base image.  The base image and the
     * image mask need not have the same resolution (width, height), but since
     * all images are defined on the unit square in user space, their boundaries on the
     * page will coincide; that is, they will overlay each other.
     * <p>
     * The image mask indicates indicates which places on the page are to be painted
     * and which are to be masked out (left unchanged).  Unmasked areas are painted
     * with the corresponding portions of the base image; masked areas are not.
     *
     * @param baseImage base image in which the mask weill be applied to
     * @param maskImage image mask to be applied to base image.
     */
    static BufferedImage applyExplicitMask(BufferedImage baseImage, BufferedImage maskImage) {
        // check to see if we need to scale the mask to match the size of the
        // base image.
        int baseWidth;
        int baseHeight;

        // check to make sure the mask and the image are the same size.
        BufferedImage[] images = scaleImagesToSameSize(baseImage, maskImage);
        baseImage = images[0];
        maskImage = images[1];

        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        baseWidth = baseImage.getWidth();
        baseHeight = baseImage.getHeight();
        int mask = 0xffffff;
        if (baseImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            mask = -1;
        }

        boolean hasAlpha = hasAlpha(baseImage);
        BufferedImage argbImage;
        if (hasAlpha) {
            argbImage = baseImage;
        } else {
            // aways create a new buffer as we need leave the pevioius image un change for some type of masks.
            argbImage = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }
        int[] srcBand = new int[baseWidth];
        int[] maskBnd = new int[baseWidth];
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            maskImage.getRGB(0, i, baseWidth, 1, maskBnd, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {
                if (maskBnd[j] == 0 || maskBnd[j] == mask || maskBnd[j] == 0xffffff) {
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
     * Blending mode colour transparency test.
     *
     * @param baseImage    image to apply blending mode to.
     * @param blendingMode type of blending mode.
     * @param blendColor   base blending colour
     * @return altered image.
     */
    public BufferedImage applyBlendingMode(BufferedImage baseImage, Name blendingMode, Color blendColor) {

        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        boolean hasAlpha = hasAlpha(baseImage);
        BufferedImage argbImage;
        if (hasAlpha) {
            argbImage = baseImage;
        } else {
            // aways create a new buffer as we need leave the pevioius image un change for some type of masks.
            argbImage = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }
        int[] srcBand = new int[baseWidth];
        int[] blendBand = new int[baseWidth];
        int blendColorValue = blendColor.getRGB();
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {

                if (srcBand[j] == blendColorValue || srcBand[j] == 0xffffff || srcBand[j] == 0xffff) {
                    //  set the pixel as transparent
                    blendBand[j] = 0xff;
                } else {
                    blendBand[j] = srcBand[j];
                }
            }
            argbImage.setRGB(0, i, baseWidth, 1, blendBand, 0, baseWidth);
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
     * <p>
     * If present, this entry shall override the current soft mask in the graphics
     * state, as well as the image’s Mask entry, if any. However, the other
     * transparency-related graphics state parameters—blend mode and alpha
     * constant—shall remain in effect. If SMask is absent, the image shall
     * have no associated soft mask (although the current soft mask in the
     * graphics state may still apply).
     *
     * @param baseImage  base image in which the mask weill be applied to
     * @param sMaskImage image mask to apply
     * @return resultant image.
     */
    public static BufferedImage applyExplicitSMask(BufferedImage baseImage, BufferedImage sMaskImage) {

        // check to make sure the mask and the image are the same size.
        BufferedImage[] images = scaleImagesToSameSize(baseImage, sMaskImage);
        baseImage = images[0];
        sMaskImage = images[1];
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        boolean hasAlpha = hasAlpha(baseImage);
        BufferedImage argbImage;
        if (hasAlpha) {
            argbImage = baseImage;
        } else {
            // always create a new buffer as we need leave the pevioius image un change for some type of masks.
            argbImage = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }
        int[] srcBand = new int[baseWidth];
        int[] sMaskBand = new int[baseWidth];
        int red, alpha, sa;
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            sMaskImage.getRGB(0, i, baseWidth, 1, sMaskBand, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {
                // take any one of the primaries and apply src image alpha.
                red = (sMaskBand[j] >> 16) & 0x000000FF;
                alpha = (srcBand[j] >> 24) & 0x000000FF;
                sa = ((int) (red * (alpha / 255.0f))) << 24;
                // apply the smask value as the alpha value
                srcBand[j] = sa | (srcBand[j] & ~0xff000000);
            }
            argbImage.setRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
        }
        baseImage.flush();
        baseImage = argbImage;

        return baseImage;
    }

    public BufferedImage applyExplicitLuminosity(BufferedImage baseImage, BufferedImage sMaskImage) {

        // check to make sure the mask and the image are the same size.
        BufferedImage[] images = scaleImagesToSameSize(baseImage, sMaskImage);
        baseImage = images[0];
        sMaskImage = images[1];
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        boolean hasAlpha = hasAlpha(baseImage);
        BufferedImage argbImage;
        if (hasAlpha) {
            argbImage = baseImage;
        } else {
            // aways create a new buffer as we need leave the pevioius image un change for some type of masks.
            argbImage = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }

        int[] srcBand = new int[baseWidth];
        int[] sMaskBand = new int[baseWidth];
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            sMaskImage.getRGB(0, i, baseWidth, 1, sMaskBand, 0, baseWidth);
            for (int j = 0; j < baseWidth; j++) {
                int red = (srcBand[j] >> 16) & 0x000000FF;
                int green = (srcBand[j] >> 8) & 0x000000FF;
                int blue = (srcBand[j]) & 0x000000FF;
                // colour is used as a degree of masking.
                int alpha = (sMaskBand[j] >> 16) & 0x000000FF;
                int trans = (sMaskBand[j] >> 24) & 0x000000FF;

                int redOut = Math.min(255, red - alpha);
                int greenOut = Math.min(255, green - alpha);
                int blueOut = Math.min(255, blue - alpha);
//                trans = trans - alpha;,

                // todo still broken,  needs some more examples/
                srcBand[j] = trans << 24
                        | Math.max(0, redOut) << 16
                        | Math.max(0, greenOut) << 8
                        | Math.max(0, blueOut);
//                if ((red >= 0 || green >= 0 || blue >= 0) && alpha != 0) {
//                    srcBand[j] =  (Math.min(mAlpha, alpha) )<< 24
////                    srcBand[j] = mAlpha << 24
//                            | (redOut & 0xff) << 16
//                            | (greenOut & 0xff) << 8
//                            | (blueOut & 0xff);
//                }
            }
            argbImage.setRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
        }
        baseImage.flush();
        baseImage = argbImage;

        return baseImage;
    }

    public static BufferedImage applyExplicitOutline(BufferedImage baseImage, BufferedImage sMaskImage) {

        // check to make sure the mask and the image are the same size.
        BufferedImage[] images = scaleImagesToSameSize(baseImage, sMaskImage);
        baseImage = images[0];
        sMaskImage = images[1];
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        boolean hasAlpha = hasAlpha(baseImage);
        BufferedImage argbImage;
        if (hasAlpha) {
            argbImage = baseImage;
        } else {
            // aways create a new buffer as we need leave the pevioius image un change for some type of masks.
            argbImage = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }

        int[] srcBand = new int[baseWidth];
        int[] sMaskBand = new int[baseWidth];
        // iterate over each band to apply the outline,  where the outline is any pixel with alpha.
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            sMaskImage.getRGB(0, i, baseWidth, 1, sMaskBand, 0, baseWidth);
            for (int j = 0; j < baseWidth; j++) {
                // take any one of the primaries and apply src image alpha.
                int alpha = (sMaskBand[j] >> 24) & 0x000000FF;
                int sa = alpha << 24;
                // apply the smask value as the alpha value
                if (sMaskBand[j] == 0)
                    srcBand[j] = sa
                            | (srcBand[j] & ~0xff000000);
            }
            argbImage.setRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
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
    static BufferedImage applyExplicitMask(BufferedImage baseImage, Color fill) {
        // create an
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();

        BufferedImage imageMask;
        if (hasAlpha(baseImage)) {
            imageMask = baseImage;
        } else {
            imageMask = createTranslucentCompatibleImage(baseWidth, baseHeight);
        }
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        int[] srcBand = new int[baseWidth];
        int[] maskBnd = new int[baseWidth];
        int fillRgb = fill.getRGB();
        // iterate over each band to apply the mask
        for (int i = 0; i < baseHeight; i++) {
            baseImage.getRGB(0, i, baseWidth, 1, srcBand, 0, baseWidth);
            imageMask.getRGB(0, i, baseWidth, 1, maskBnd, 0, baseWidth);
            // apply the soft mask blending
            for (int j = 0; j < baseWidth; j++) {
                if (!(srcBand[j] == -1 || srcBand[j] == 0xffffff)) {
                    maskBnd[j] = fillRgb;
                }
            }
            imageMask.setRGB(0, i, baseWidth, 1, maskBnd, 0, baseWidth);
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
    static BufferedImage applyIndexColourModel(WritableRaster wr, PColorSpace colourSpace, int bitsPerComponent) {
        BufferedImage img = null;
        try {
            colourSpace.init();
            // build out the colour table.
            Color[] colors = ((Indexed) colourSpace).accessColorTable();
            int colorsLength = (colors == null) ? 0 : colors.length;
            int[] cmap = new int[256];
            for (int i = 0; i < colorsLength; i++) {
                cmap[i] = colors[i].getRGB();
            }
            for (int i = colorsLength; i < cmap.length; i++) {
                cmap[i] = 0xFF000000;
            }
            // build a new buffer with indexed colour model.
            DataBuffer db = wr.getDataBuffer();
            //        SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
            //        WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
            ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, true, -1, db.getDataType());
            img = new BufferedImage(cm, wr, false, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Indexed colour model initialization interrupted.");
        }
        return img;
    }

    static BufferedImage applyGrayDecode(BufferedImage rgbImage, ImageParams imageParams) {
        WritableRaster wr = rgbImage.getRaster();
        int[] cmap = null;
        int bitsPerComponent = imageParams.getBitsPerComponent();
        float[] decode = imageParams.getDecode();
        if (bitsPerComponent == 1) {
            boolean defaultDecode = 0.0f == decode[0];
            cmap = defaultDecode ? GRAY_1_BIT_INDEX_TO_RGB : GRAY_1_BIT_INDEX_TO_RGB_REVERSED;
        } else if (bitsPerComponent == 2) {
            cmap = GRAY_2_BIT_INDEX_TO_RGB;
        } else if (bitsPerComponent == 4) {
            cmap = GRAY_4_BIT_INDEX_TO_RGB;
        }
        ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, false, -1, wr.getDataBuffer().getDataType());
        rgbImage = new BufferedImage(cm, wr, false, null);
        return rgbImage;
    }

    static BufferedImage convertSpaceToRgb(Raster colourRaster, PColorSpace colorSpace, float[] decode) {
        BufferedImage rgbImage = makeBufferedImage(colourRaster);
        WritableRaster rgbRaster = rgbImage.getRaster();
        // apply the decode filter
        DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
        decodeRasterOp.filter(colourRaster, (WritableRaster) colourRaster);
        // apply colour space
        PColorSpaceRasterOp pColorSpaceRasterOp = new PColorSpaceRasterOp(colorSpace, null);
        pColorSpaceRasterOp.filter(colourRaster, rgbRaster);
        return rgbImage;
    }

    static BufferedImage convertGrayToRgb(Raster grayRaster, float[] decode) {
        // apply the decode filter
        DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
        decodeRasterOp.filter(grayRaster, (WritableRaster) grayRaster);
        // convert from gray.
        GrayRasterOp grayRasterOp = new GrayRasterOp(decode, null);
        grayRasterOp.filter(grayRaster, (WritableRaster) grayRaster);
        return makeGrayBufferedImage((WritableRaster) grayRaster);
    }

    /**
     * Utility method to convert an CMYK based raster to RGB.  The can be
     * configured to use to different approaches.  The first and more accurate
     * method uses a ICC color profile specified by the DeviceCMYK.java class.
     * This method can be turned off using the system property
     * org.icepdf.core.cmyk.disableICCProfile=true at which point an less
     * precise method is used to calculate the resultan RGB color.
     *
     * @param cmykRaster CMYK base raster to convert to RGB.
     * @return Buffered image representation of raster.
     */
    static BufferedImage convertCmykToRgb(Raster cmykRaster, float[] decode) {
        BufferedImage rgbImage = makeBufferedImage(cmykRaster);

        if (!DeviceCMYK.isDisableICCCmykColorSpace()) {
            WritableRaster rgbRaster = rgbImage.getRaster();
//            ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
            // apply the decode filter
            DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
            decodeRasterOp.filter(cmykRaster, (WritableRaster) cmykRaster);
            // convert it to rgb
            IccCmykRasterOp cmykToRgb = new IccCmykRasterOp(null);
            cmykToRgb.filter(cmykRaster, rgbRaster);
        } else {
            WritableRaster rgbRaster = rgbImage.getRaster();

            // apply the decode filter
            DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
            decodeRasterOp.filter(cmykRaster, (WritableRaster) cmykRaster);
            // apply the Old non ICC color conversion code
            // convert it to rgb
            CMYKRasterOp cmykRasterOp = new CMYKRasterOp(null);
            cmykRasterOp.filter(cmykRaster, rgbRaster);
        }
        return rgbImage;
    }

    static BufferedImage convertYCbCrToRGB(Raster yCbCrRaster, float[] decode) {
        BufferedImage rgbImage = makeBufferedImage(yCbCrRaster);
        WritableRaster rgbRaster = rgbImage.getRaster();
        // apply the decode filter
        DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
        decodeRasterOp.filter(yCbCrRaster, (WritableRaster) yCbCrRaster);
        // convert to rgb
        RasterOp rasterOp;
        rasterOp = new YCbCrRasterOp(null);
        rasterOp.filter(yCbCrRaster, rgbRaster);
        return rgbImage;
    }

    static BufferedImage convertYCCKToRgb(Raster ycckRaster, float[] decode) {
        BufferedImage rgbImage = makeBufferedImage(ycckRaster);
        if (!DeviceCMYK.isDisableICCCmykColorSpace()) {
            WritableRaster rgbRaster = rgbImage.getRaster();
            //ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
            // apply the decode filter
            DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
            decodeRasterOp.filter(ycckRaster, (WritableRaster) ycckRaster);
            // apply the  YCCK to CMYK
            YCCKRasterOp ycckRasterOp = new YCCKRasterOp(null);
            ycckRasterOp.filter(ycckRaster, (WritableRaster) ycckRaster);
            // convert it to rgb
            IccCmykRasterOp cmykToRgb = new IccCmykRasterOp(null);
            cmykToRgb.filter(ycckRaster, rgbRaster);
        } else {
            WritableRaster rgbRaster = rgbImage.getRaster();
            // apply the decode filter
            DecodeRasterOp decodeRasterOp = new DecodeRasterOp(decode, null);
            decodeRasterOp.filter(ycckRaster, (WritableRaster) ycckRaster);
            // apply the  YCCK to CMYK
            YCCKRasterOp ycckRasterOp = new YCCKRasterOp(null);
            ycckRasterOp.filter(ycckRaster, (WritableRaster) ycckRaster);
            // apply the Old non ICC color conversion code
            // convert it to rgb
            CMYKRasterOp cmykRasterOp = new CMYKRasterOp(null);
            cmykRasterOp.filter(ycckRaster, rgbRaster);
        }
        return rgbImage;
    }

    static BufferedImage makeImageWithRasterFromBytes(byte[] data, GraphicsState graphicsState, ImageParams imageParams) {
        BufferedImage img = null;

        PColorSpace colourSpace = imageParams.getColourSpace();
        int width = imageParams.getWidth();
        int height = imageParams.getHeight();
        int colorSpaceCompCount = colourSpace.getNumComponents();
        int bitsPerComponent = imageParams.getBitsPerComponent();
        float[] decode = imageParams.getDecode();
        int dataLength = data.length;

        BufferedImage smaskImage = imageParams.getSMask(graphicsState) != null ?
                imageParams.getSMask(graphicsState).decode() : null;
        BufferedImage maskImage = imageParams.getMask(graphicsState) != null ?
                imageParams.getMask(graphicsState).decode() : null;

        boolean isImageMask = imageParams.isImageMask();
        ColorKeyMask colorKeyMask = null;
        int[] maskMinRGB = null;
        int[] maskMaxRGB = null;
        if (!isImageMask) {
            colorKeyMask = imageParams.getColorKeyMask();
            if (colorKeyMask != null) {
                maskMinRGB = colorKeyMask.getMaskMinRGB();
                maskMaxRGB = colorKeyMask.getMaskMaxRGB();
            }
        }

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
            if (isImageMask && bitsPerComponent == 1) {
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height,
                        bitsPerComponent, new Point(0, 0));
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
                Color fill = graphicsState.getFillColor();
                int[] cmap = new int[]{
                        (defaultDecode ? fill.getRGB() : a),
                        (defaultDecode ? a : fill.getRGB())
                };
                int transparentIndex = (defaultDecode ? 1 : 0);
                IndexColorModel icm = new IndexColorModel(
                        bitsPerComponent,       // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        colorSpaceCompCount == 4,                   // indicates whether alpha values are contained in the cmap array
                        transparentIndex,       // the index of the fully transparent pixel
                        db.getDataType());      // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(icm, wr, false, null);
            } else if (bitsPerComponent == 1 || bitsPerComponent == 2 || bitsPerComponent == 4) {
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitsPerComponent, new Point(0, 0));
                int[] cmap;
                if (bitsPerComponent == 1) {
                    boolean defaultDecode = 0.0f == decode[0];
                    cmap = defaultDecode ? GRAY_1_BIT_INDEX_TO_RGB : GRAY_1_BIT_INDEX_TO_RGB_REVERSED;
                } else if (bitsPerComponent == 2) {
                    cmap = GRAY_2_BIT_INDEX_TO_RGB;
                } else {
                    cmap = GRAY_4_BIT_INDEX_TO_RGB;
                }
                ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            } else if (bitsPerComponent == 8) {
                img = createCompatibleImage(width, height);
                // convert image data to rgb, seems to to give better colour tones. ?
                int[] dataToRGB = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                copyDecodedStreamBytesIntoGray(data, dataToRGB, decode);
            }
        } else if (colourSpace instanceof DeviceRGB || colourSpace instanceof CalRGB) {
            if (bitsPerComponent == 8) {
                boolean usingAlpha = smaskImage != null || maskImage != null ||
                        (maskMinRGB != null && maskMaxRGB != null);
                int type = usingAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
                img = new BufferedImage(width, height, type);
                // convert image data to rgb, a little out of order maybe?
                int[] dataToRGB = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                copyDecodedStreamBytesIntoRGB(data, dataToRGB);
                // apply alpha data.
                if (usingAlpha) {
                    img = alterBufferedImageAlpha(img, maskMinRGB, maskMaxRGB);
                }
            }
        } else if (colourSpace instanceof DeviceCMYK) {
            // this is slow and doesn't do decode properly,  push off parseImage()
            // as its quick, and we can do the generic decode and masking.
            if (false && bitsPerComponent == 8) {
                DataBuffer db = new DataBufferByte(data, dataLength);
                int[] bandOffsets = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++) {
                    bandOffsets[i] = i;
                }
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, colorSpaceCompCount, colorSpaceCompCount * width, bandOffsets);
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                //WritableRaster wr = Raster.createInterleavedRaster( db, width, height, colorSpaceCompCount*width, colorSpaceCompCount, bandOffsets, new Point(0,0) );
                ColorSpace cs = DeviceCMYK.getIccCmykColorSpace();
                int[] bits = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++) {
                    bits[i] = bitsPerComponent;
                }
                ColorModel cm = new ComponentColorModel(cs, bits, false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
        } else if (colourSpace instanceof Indexed) {
            if (bitsPerComponent == 1 || bitsPerComponent == 2 || bitsPerComponent == 4) {
                try {
                    colourSpace.init();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int[] cmap = new int[(colors == null) ? 0 : colors.length];
                for (int i = 0; i < cmap.length; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                int cmapMaxLength = 1 << bitsPerComponent;
                if (cmap.length > cmapMaxLength) {
                    int[] cmapTruncated = new int[cmapMaxLength];
                    System.arraycopy(cmap, 0, cmapTruncated, 0, cmapMaxLength);
                    cmap = cmapTruncated;
                }
//                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = (smaskImage != null || maskImage != null) ||
                        (maskMinRGB != null && maskMaxRGB != null);
                if (usingAlpha) {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitsPerComponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, true, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                    img = alterBufferedImageAlpha(img, maskMinRGB, maskMaxRGB);
                } else {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitsPerComponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            } else if (bitsPerComponent == 8) {
                try {
                    colourSpace.init();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int colorsLength = (colors == null) ? 0 : colors.length;
                int[] cmap = new int[256];
                for (int i = 0; i < colorsLength; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                for (int i = colorsLength; i < cmap.length; i++) {
                    cmap[i] = 0xFF000000;
                }
                boolean usingIndexedAlpha = colorKeyMask != null &&
                        colorKeyMask.getMaskMinIndex() >= 0 && colorKeyMask.getMaskMaxIndex() >= 0;
                boolean usingAlpha = (smaskImage != null || maskImage != null) ||
                        (maskMinRGB != null && maskMaxRGB != null);
                if (usingIndexedAlpha) {
                    for (int i = colorKeyMask.getMaskMinIndex(); i <= colorKeyMask.getMaskMaxIndex(); i++) {
                        cmap[i] = 0x00000000;
                    }
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, true, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else if (usingAlpha) {
                    int[] rgbaData = new int[width * height];
                    // use rgbaData length as an inline image may have a couple extra bytes at the end.
                    for (int index = 0, max = rgbaData.length; index < max; index++) {
                        int cmapIndex = (data[index] & 0xFF);
                        rgbaData[index] = cmap[cmapIndex];
                    }
                    DataBuffer db = new DataBufferInt(rgbaData, rgbaData.length);
                    int[] masks = new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};
                    //SampleModel sm = new SinglePixelPackedSampleModel(
                    //    db.getDataType(), width, height, masks );
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, width, masks, new Point(0, 0));
                    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    ColorModel cm = new DirectColorModel(cs, 32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000, false, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                } else {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            }
        } else if (colourSpace instanceof Separation || colourSpace instanceof CalGray) {
            if (colourSpace instanceof CalGray || ((Separation) colourSpace).isNamedColor()) {
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitsPerComponent, new Point(0, 0));
                int[] cmap = null;
                if (bitsPerComponent == 1) {
                    cmap = GRAY_1_BIT_INDEX_TO_RGB;
                } else if (bitsPerComponent == 2) {
                    cmap = GRAY_2_BIT_INDEX_TO_RGB;
                } else if (bitsPerComponent == 4) {
                    cmap = GRAY_4_BIT_INDEX_TO_RGB;
                } else if (bitsPerComponent == 8) {
                    return null;
                }
                ColorModel cm = new IndexColorModel(bitsPerComponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
        }
        // todo add further raw decode types to help speed up image decode
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

    private static void copyDecodedStreamBytesIntoGray(byte[] data, int[] pixels, float[] decode) {
        byte[] rgb = new byte[1];
        boolean defaultDecode = 0.0f == decode[0];
        int Y;
        try {
            InputStream input = new ByteArrayInputStream(data);
            for (int pixelIndex = 0; pixelIndex < pixels.length; pixelIndex++) {
                int argb = 0xFF000000;
                final int toRead = 1;
                int haveRead = 0;
                while (haveRead < toRead) {
                    int currRead = input.read(rgb, haveRead, toRead - haveRead);
                    if (currRead < 0)
                        break;
                    haveRead += currRead;
                }
                Y = (int) rgb[0] & 0xff;
                Y = defaultDecode ? Y : 255 - Y;
                argb |= (Y << 16) & 0x00FF0000;
                argb |= (Y << 8) & 0x0000FF00;
                argb |= (Y & 0x000000FF);
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

    private static BufferedImage createBufferedImage(Image imageIn, int imageType) {
        BufferedImage bufferedImageOut = new BufferedImage(imageIn
                .getWidth(null), imageIn.getHeight(null), imageType);
        Graphics g = bufferedImageOut.getGraphics();
        g.drawImage(imageIn, 0, 0, null);
        imageIn.flush();
        return bufferedImageOut;
    }

    /**
     * Utility method to scale the two provided images. There are two modes based
     * on the system property "".  The d
     *
     * @param baseImage base image that mask will be applied to
     * @param maskImage mask image that will be applied to base image.
     * @return array of altered baseImage and maskImage, should be same size on
     * return.
     */
    private static BufferedImage[] scaleImagesToSameSize(BufferedImage baseImage,
                                                         BufferedImage maskImage) {
        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        WritableRaster maskRaster = maskImage.getRaster();
        int maskWidth = maskRaster.getWidth();
        int maskHeight = maskRaster.getHeight();
        if (scaleQuality) {
            // scale the image to match the image mask.
            if (width < maskWidth || height < maskHeight) {
                // calculate scale factors.
                // MS Publisher ues a very strange masking technique where the base image is 2x2 and the mask
                // is a massive image that we can't work with with a normal amount of memory.  So we
                // we shrink anything that is really big.
                if (maskWidth > scaleWidth || maskHeight > scaleHeight) {
                    // hmm, lets shrink the image by a 10th.
                    maskImage = scale(maskWidth / 10, maskHeight / 10, maskWidth, maskHeight, maskImage);
                    maskWidth = maskImage.getRaster().getWidth();
                    maskHeight = maskImage.getRaster().getHeight();
                }
                baseImage = scale(maskWidth, maskHeight, width, height, baseImage);
            } else if (width > maskWidth || height > maskHeight) {
                // calculate scale factors.
                maskImage = scale(width, height, maskWidth, maskHeight, maskImage);
            }
        } else {
            // scale the mask to match the smaller image.
            if (width < maskWidth || height < maskHeight) {
                // calculate scale factors.
                maskImage = scale(width, height, maskWidth, maskHeight, maskImage);
            }
        }
        return new BufferedImage[]{baseImage, maskImage};
    }

    private static BufferedImage scale(int width, int height, int width2, int height2, BufferedImage image) {
        double scaleX = width / (double) width2;
        double scaleY = height / (double) height2;
        AffineTransform tx = new AffineTransform();
        tx.scale(scaleX, scaleY);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        BufferedImage bim = op.filter(image, null);
        image.flush();
        return bim;
    }

}
