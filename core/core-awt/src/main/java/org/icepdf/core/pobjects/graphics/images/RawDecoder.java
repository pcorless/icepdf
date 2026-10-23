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

import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PColorSpace;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

/**
 * Decodes an image sample by sample, for what the raster path ({@link RasterDecoder}) leaves: DeviceCMYK,
 * Separation, DeviceN, Lab, CalGray and ICCBased images, and depths it has no raster for.  The samples come
 * from a {@link SampleReader} and each pixel's colour from a {@link PixelConverter} picked for the image.
 */
public class RawDecoder extends AbstractImageDecoder {

    public RawDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {
        ImageParams imageParams = imageStream.getImageParams();
        PColorSpace colorSpace = imageParams.getColourSpace();
        int bitsPerComponent = imageParams.getBitsPerComponent();
        int width = imageParams.getWidth();
        int height = imageParams.getHeight();

        byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
        if (data == null) {
            return null;
        }

        // the colour a stencil mask paints
        int fillRgb = graphicsState != null ? graphicsState.getFillColor().getRGB() : 1;
        // GH-501: when a CMYK group is being rasterised, keep the true CMYK samples alongside the sRGB image so
        // the group can blend in CMYK
        byte[] cmykCapture = !imageParams.isImageMask() && PixelConverter.capturesCmyk(colorSpace, bitsPerComponent)
                ? new byte[width * height * 4] : null;
        PixelConverter pixels = PixelConverter.forImage(colorSpace, bitsPerComponent, imageParams.getDecode(),
                imageParams.isImageMask(), fillRgb, width * height, cmykCapture);

        BufferedImage image = ImageUtility.createTranslucentCompatibleImage(width, height);
        // GH-617: a TYPE_INT_ARGB row is written with one copy; setRGB converts each pixel through the
        // colour model, which matters for other layouts (a premultiplied buffer) but not this one
        WritableRaster argbRaster = image.getType() == BufferedImage.TYPE_INT_ARGB ? image.getRaster() : null;
        SampleReader in = new SampleReader(data);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            in.startRow();
            for (int x = 0; x < width; x++) {
                row[x] = pixels.next(in);
            }
            if (argbRaster != null) {
                argbRaster.setDataElements(0, y, width, 1, row);
            } else {
                image.setRGB(0, y, width, 1, row, 0, width);
            }
        }

        if (cmykCapture != null) {
            ImageUtility.preserveCmykBytes(image, cmykCapture, width, height);
        }
        return image;
    }
}
