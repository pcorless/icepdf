/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.updater.writeables.image;

import io.github.pcorless.jpx.Jpeg2000Encoder;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Writes a burned image that arrived as JPXDecode back as JPXDecode (GH-697), with jpx-imageio's JPEG 2000
 * encoder, as a raw codestream in DeviceGray or DeviceRGB, rather than as Flate: a JPEG 2000 scan re-encoded
 * losslessly was several times its size. Lossy (the 9/7 wavelet) at {@value #QUALITY_PROPERTY}, 0 to 1,
 * {@value #DEFAULT_QUALITY} by default; 1 writes losslessly (5/3).
 *
 * @since 8.1
 */
class JpxEncoder implements ImageEncoder {

    static final String QUALITY_PROPERTY = "org.icepdf.core.redaction.jpxQuality";
    static final float DEFAULT_QUALITY = 0.9f;

    private final ImageStream imageStream;

    JpxEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
    }

    /**
     * @return the stream, re-encoded; null for an image this encoder doesn't take, which is then written
     * losslessly
     */
    @Override
    public ImageStream encode() throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int components = LossyEncoding.components(image);
        if (components == 0) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        float quality = LossyEncoding.quality(QUALITY_PROPERTY, DEFAULT_QUALITY);
        Jpeg2000Encoder.Options options = new Jpeg2000Encoder.Options();
        if (quality >= 1f) {
            options.lossless();
        } else {
            options.quality(quality);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(width * height / 4 + 1024);
        Raster raster = image.getRaster();
        // one reader a thread: each keeps its own row buffer
        Jpeg2000Encoder.Source source = () -> {
            if (components == 1) {
                return (y, row) -> raster.getSamples(0, y, width, 1, 0, row);
            }
            int[] rgb = new int[width];
            return (y, row) -> {
                // interleaved R, G, B, whatever the raster's own layout
                image.getRGB(0, y, width, 1, rgb, 0, width);
                for (int x = 0, i = 0; x < width; x++) {
                    int pixel = rgb[x];
                    row[i++] = (pixel >> 16) & 0xff;
                    row[i++] = (pixel >> 8) & 0xff;
                    row[i++] = pixel & 0xff;
                }
            };
        };
        Jpeg2000Encoder.encode(source,
                width, height, components, options, bytes);
        return LossyEncoding.describe(imageStream, bytes.toByteArray(), "JPXDecode", components);
    }
}
