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

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RasterDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JpxDecoder.class.toString());

    public RasterDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {
        ImageParams imageParams = imageStream.getImageParams();
        byte[] decodedStreamData = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
        BufferedImage decodedImage = null;
        if (decodedStreamData != null) {
            try {
                decodedImage = ImageUtility.makeImageWithRasterFromBytes(decodedStreamData, graphicsState, imageParams);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error building image raster.", e);
            }
        }
        return decodedImage;
    }
}
