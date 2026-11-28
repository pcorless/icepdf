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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Defs;

import java.awt.image.BufferedImage;

import static org.icepdf.core.pobjects.graphics.DeviceGray.DEVICEGRAY_KEY;
import static org.icepdf.core.pobjects.graphics.DeviceRGB.DEVICERGB_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.BITS_PER_COMPONENT_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.COLORSPACE_KEY;

/**
 * What the lossy re-encoders share (GH-697): which burned images they take, and how the dictionary is rewritten to
 * describe what they wrote.
 */
final class LossyEncoding {

    private LossyEncoding() {
    }

    /**
     * @return the components of an image a lossy encoder can write as it is: 1 for 8-bit grey, 3 for RGB with no
     * alpha; 0 for anything else, or for black-and-white content ({@link #isBilevel}), which goes to a lossless
     * encoder instead
     */
    static int components(BufferedImage image) {
        if (image == null || image.getColorModel().hasAlpha() || isBilevel(image)) {
            return 0;
        }
        switch (image.getType()) {
            case BufferedImage.TYPE_BYTE_GRAY:
                return 1;
            case BufferedImage.TYPE_INT_RGB:
            case BufferedImage.TYPE_INT_BGR:
            case BufferedImage.TYPE_3BYTE_BGR:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * Whether the image is, in effect, black and white: every sample (of a grid over it) is one of two values, as in
     * a scanned page of text. JPEG and JPEG 2000 both do badly with that, ringing around every edge; Flate does it
     * in a fraction of the size, so such an image is written losslessly whatever it arrived as.
     */
    static boolean isBilevel(BufferedImage image) {
        int first = -1;
        int second = -1;
        int stepX = Math.max(1, image.getWidth() / 512);
        int stepY = Math.max(1, image.getHeight() / 512);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                if (rgb == first || rgb == second) {
                    continue;
                }
                if (first < 0) {
                    first = rgb;
                } else if (second < 0) {
                    second = rgb;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A quality from a system property, 0 to 1, or {@code defaultValue} when it's unset or not a number.
     */
    static float quality(String property, float defaultValue) {
        String value = Defs.sysProperty(property);
        if (value != null) {
            try {
                float q = Float.parseFloat(value.trim());
                if (q >= 0f && q <= 1f) {
                    return q;
                }
            } catch (NumberFormatException e) {
                // the default
            }
        }
        return defaultValue;
    }

    /**
     * Points the stream at the bytes written: the filter, eight-bit samples in DeviceGray or DeviceRGB (what the
     * burned raster holds), and no decode parameters or in-data mask left over from the original encoding.
     */
    static ImageStream describe(ImageStream imageStream, byte[] encoded, String filter, int components) {
        imageStream.setRawBytes(encoded);
        imageStream.getEntries().put(Stream.FILTER_KEY, new Name(filter));
        imageStream.getEntries().put(COLORSPACE_KEY, components == 1 ? DEVICEGRAY_KEY : DEVICERGB_KEY);
        imageStream.getEntries().put(BITS_PER_COMPONENT_KEY, 8);
        imageStream.getEntries().remove(Stream.DECODEPARAM_KEY);
        imageStream.getEntries().remove(SMASK_IN_DATA_KEY);
        return imageStream;
    }

    private static final Name SMASK_IN_DATA_KEY =
            new Name("SMaskInData");
}
