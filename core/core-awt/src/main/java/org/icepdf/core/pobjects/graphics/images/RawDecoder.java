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

import org.icepdf.core.io.BitStream;
import org.icepdf.core.pobjects.graphics.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RawDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(RawDecoder.class.getName());

    public RawDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {

        ImageParams imageParams = imageStream.getImageParams();
        PColorSpace colorSpace = imageParams.getColourSpace();
        int bitsPerComponent = imageParams.getBitsPerComponent();
        float[] decode = imageParams.getDecode();
        int width = imageParams.getWidth();
        int height = imageParams.getHeight();
        boolean isMask = imageParams.isImageMask();

        // store for manipulating bits in image
        int[] imageBits = new int[width];

        // RGB value for colour used as fill for image
        int fillRGB = 1;
        if (graphicsState != null) {
            fillRGB = graphicsState.getFillColor().getRGB();
        }

        // Number of colour components in image, should be 3 for RGB or 4
        // for ARGB.
        int colorSpaceCompCount = colorSpace.getNumComponents();
        boolean isDeviceRGB = colorSpace instanceof DeviceRGB;
        boolean isDeviceGray = colorSpace instanceof DeviceGray;

        // Max value used to represent a colour,  usually 255, min is 0
        int maxColourValue = ((1 << bitsPerComponent) - 1);

        int[] f = new int[colorSpaceCompCount];
        float[] ff = new float[colorSpaceCompCount];

        // last-sample cache for the colorSpace.getColor() paths below; getColor
        // (plus its Color allocation) is the per-pixel cost, and flat runs repeat
        // the same sample.  -1 is an impossible bit-stream value so the first
        // pixel always misses.
        int[] lastF = new int[colorSpaceCompCount];
        java.util.Arrays.fill(lastF, -1);
        int[] lastRgb = new int[1];

        // image mask from
        float imageMaskValue = decode[0];

        // Create the memory hole where where the buffered image will be written
        // too, bit by painful bit.
        BufferedImage bim = ImageUtility.createTranslucentCompatibleImage(width, height);

        // GH-501: when a CMYK group is being rasterised, capture the true CMYK
        // samples alongside the sRGB image so the group can blend in CMYK.  This
        // per-pixel decoder is the raw FlateDecode path (no intermediate CMYK
        // raster); cmykCapture is null (no cost) unless preservation is on for an
        // 8-bit DeviceCMYK image.
        byte[] cmykCapture = (ImageUtility.isPreserveCmyk() && bitsPerComponent == 8
                && colorSpaceCompCount == 4 && colorSpace instanceof DeviceCMYK)
                ? new byte[width * height * 4] : null;

        // create the buffer and get the first series of bytes from the cached
        // stream
        // get the full image data.
        byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
        BitStream in = new BitStream(new ByteArrayInputStream(data));


        try {
            // Start encoding bit stream into an image,  we work one pixel at
            // a time,  and grap the need bit information for the images
            // colour space and bits per colour
            for (int y = 0; y < height; y++) {

                for (int x = 0; x < width; x++) {

                    // if image has mask apply it
                    if (isMask) {
                        int bit = in.getBits(bitsPerComponent);
                        bit = (bit == imageMaskValue) ? fillRGB : 0x00000000;
                        imageBits[x] = bit;
                    }
                    // other wise start colour bit parsing
                    else {
                        // set some default values
                        int red = 255;
                        int blue = 255;
                        int green = 255;
                        int alpha = 255;

                        // indexed colour
                        if (colorSpaceCompCount == 1) {
                            // get value used for this bit
                            int bit = in.getBits(bitsPerComponent);
                            // check decode array if a colour inversion is needed
                            // if index 0 > index 1 then we have a need for ainversion
                            if (decode[0] > decode[1]) {
                                bit = (bit == maxColourValue) ? 0x00000000 : maxColourValue;
                            }

                            if (isDeviceGray) {
                                if (bitsPerComponent == 1)
                                    bit = ImageUtility.GRAY_1_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerComponent == 2)
                                    bit = ImageUtility.GRAY_2_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerComponent == 4)
                                    bit = ImageUtility.GRAY_4_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerComponent == 8) {
                                    bit = ((bit << 24) |
                                            (bit << 16) |
                                            (bit << 8) |
                                            bit);
                                }
                                imageBits[x] = bit;
                            } else {
                                f[0] = bit;
                                imageBits[x] = convertSample(colorSpace, f, ff, maxColourValue, lastF, lastRgb);
                            }
                        }
                        // normal RGB colour
                        else if (colorSpaceCompCount == 3) {
                            // We can have an ICCBased color space that has 3 components,
                            //  but where we can't assume it's RGB.
                            // But, when it is DeviceRGB, we don't want the performance hit
                            //  of converting the pixels via the PColorSpace, so we'll
                            //  break this into the two cases
                            if (isDeviceRGB && bitsPerComponent == 8) {
                                // binary values  so either 0 or 1, we must convert to 0-255
                                red = in.getBits(bitsPerComponent) * 255;
                                green = in.getBits(bitsPerComponent) * 255;
                                blue = in.getBits(bitsPerComponent) * 255;
                                // combine the colour together
                                imageBits[x] = (alpha << 24) | (red << 16) |
                                        (green << 8) | blue;
                            } else {
                                for (int i = 0; i < colorSpaceCompCount; i++) {
                                    f[i] = in.getBits(bitsPerComponent);
                                }
                                imageBits[x] = convertSample(colorSpace, f, ff, maxColourValue, lastF, lastRgb);
                            }
                        }
                        // normal aRGB colour,  this could use some more
                        // work for optimizing.
                        else if (colorSpaceCompCount == 4 || colorSpace instanceof DeviceN) {
                            for (int i = 0; i < colorSpaceCompCount; i++) {
                                f[i] = in.getBits(bitsPerComponent);
                                // apply decode
                                if (decode[0] > decode[1]) {
                                    f[i] = maxColourValue - f[i];
                                }
                            }
                            if (cmykCapture != null) {
                                // f holds the decoded C,M,Y,K samples (0..255) -- the
                                // true CMYK, before colorSpace.getColor() flattens to sRGB.
                                int idx = (y * width + x) * 4;
                                cmykCapture[idx] = (byte) f[0];
                                cmykCapture[idx + 1] = (byte) f[1];
                                cmykCapture[idx + 2] = (byte) f[2];
                                cmykCapture[idx + 3] = (byte) f[3];
                            }
                            imageBits[x] = convertSample(colorSpace, f, ff, maxColourValue, lastF, lastRgb);
                        }
                        // else just set pixel with the default values
                        else {
                            // combine the colour together
                            imageBits[x] = (alpha << 24) | (red << 16) |
                                    (green << 8) | blue;
                        }
                    }
                }
                // Assign the new bits for this pixel
                bim.setRGB(0, y, width, 1, imageBits, 0, 1);
            }
            // final clean up.
            in.close();
        } catch (IOException e) {
            logger.log(Level.FINE, "Error parsing image.", e);
        }

        if (cmykCapture != null) {
            ImageUtility.preserveCmykBytes(bim, cmykCapture, width, height);
        }

        return bim;

    }

    /**
     * Normalises and converts a colour sample to an ARGB int, reusing the last
     * result when the raw components are unchanged.  Avoids the per-pixel
     * normalise/getColor/Color-allocation cost across flat runs.
     *
     * @param colorSpace     colour space used to resolve the colour.
     * @param f              raw sample components for this pixel.
     * @param ff             scratch buffer for normalised components.
     * @param maxColourValue maximum raw component value.
     * @param lastF          previous sample components (mutated); seed with -1.
     * @param lastRgb        single-element holder for the previous ARGB result.
     * @return ARGB value for the sample.
     */
    private static int convertSample(PColorSpace colorSpace, int[] f, float[] ff, int maxColourValue,
                                     int[] lastF, int[] lastRgb) {
        boolean same = true;
        for (int i = 0; i < f.length; i++) {
            if (f[i] != lastF[i]) {
                same = false;
                lastF[i] = f[i];
            }
        }
        if (same) {
            return lastRgb[0];
        }
        colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);
        lastRgb[0] = colorSpace.getColor(ff).getRGB();
        return lastRgb[0];
    }
}
