/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.graphics.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JpxDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JpxDecoder.class.toString());

    public JpxDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {

        BufferedImage tmpImage = null;
        try {
            // Verify that ImageIO can read JPEG2000
            Iterator<ImageReader> iterator = ImageIO.getImageReadersByFormatName("JPEG2000");
            if (!iterator.hasNext()) {
                logger.info(
                        "ImageIO missing required plug-in to read JPEG 2000 images. " +
                                "You can download the JAI ImageIO Tools from: " +
                                "http://www.oracle.com/technetwork/java/javasebusiness/" +
                                "downloads/java-archive-downloads-java-client-419417.html");
                return null;
            }
            ImageParams imageParams = imageStream.getImageParams();

            byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(data));


            // getting the raster for JPX seems to fail in most cases.
            Iterator<ImageReader> iter = ImageIO.getImageReaders(imageInputStream);
            ImageReader reader = null;
            while (iter.hasNext()) {
                reader = iter.next();
                if (reader.canReadRaster()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("JPXDecode Image reader: " + reader);
                    }
                    break;
                }
            }

            // read the raster data only, as we have our own logic to covert
            // the raster data to RGB colours.
            if (reader == null) {
                imageInputStream.close();
                return null;
            }
            ImageReadParam param = reader.getDefaultReadParam();
            reader.setInput(imageInputStream, true, true);
            try {
                tmpImage = reader.read(0, param);
            } finally {
                reader.dispose();
                imageInputStream.close();
            }
            WritableRaster wr = tmpImage.getRaster();

            PColorSpace colourSpace = imageParams.getColourSpace();
            int bitsPerComponent = imageParams.getBitsPerComponent();
            float[] decode = imageParams.getDecode();

            // special fallback scenario for ICCBased colours.
            if (colourSpace instanceof ICCBased) {
                ICCBased iccBased = (ICCBased) colourSpace;
                // first try and apply the color space
                try {
                    ColorSpace cs = iccBased.getColorSpace();
                    ColorConvertOp cco = new ColorConvertOp(cs, null);
                    tmpImage = ImageUtility.makeRGBBufferedImage(wr);
                    cco.filter(tmpImage, tmpImage);
                } catch (Throwable e) {
                    logger.warning("Error processing ICC Color profile, failing " +
                            "back to alternative.");
                    // set the alternate as the current and try and process
                    // using the below rules.
                    colourSpace = iccBased.getAlternate();
                }
            }
            // apply respective colour models to the JPEG2000 image.
            if (colourSpace instanceof DeviceRGB && bitsPerComponent == 8) {
                tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
            } else if (colourSpace instanceof DeviceCMYK && bitsPerComponent == 8) {
                tmpImage = ImageUtility.convertCmykToRgb(wr, decode);
            } else if ((colourSpace instanceof DeviceGray)
                    && bitsPerComponent == 8) {
                tmpImage = ImageUtility.makeGrayBufferedImage(wr);
            } else if (colourSpace instanceof Separation) {
                if (((Separation) colourSpace).isNamedColor()) {
                    tmpImage = ImageUtility.convertGrayToRgb(wr, decode);
//                    tmpImage = ImageUtility.makeGrayBufferedImage(wr);
                } else {
                    tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
                }
            } else if (colourSpace instanceof Indexed) {
                // still some issue here with Chevron.pdf
                tmpImage = ImageUtility.applyIndexColourModel(wr, colourSpace, bitsPerComponent);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JPEG2000 image: ", e);
        }

        return tmpImage;
    }
}
