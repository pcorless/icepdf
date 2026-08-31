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

import org.icepdf.core.pobjects.graphics.images.ImageStream;

import static org.icepdf.core.pobjects.graphics.images.ImageDecoderFactory.*;

/**
 * When a modified BufferedImage needs to be written out this factory checks the ImageStreams dictionary and will
 * create a new encoded byte[] that best matches the color data of the original data.  Rough guide
 * <ul>
 *     <li>CCITTFAX_DECODE_FILTERS, JBIG2_DECODE_FILTERS -> CCITTFAX encoder</li>
 *     <li>DCT_DECODE_FILTERS, JPX_DECODE_FILTERS -> png flate encoder</li>
 *     <li>fall back to raw raster encoding</li>
 * </ul>
 * The basic idea is that we are only writing images that have been altered by the redaction tooling. All images that
 * fall into this category have already to converted to rgb or grayscale colour space.  Because of the nature of
 * redacted output we have made some assumptions that some colour space information can be dropped and that image
 * quality should be maintained even if it results in a slightly larger output size.
 *
 * @since 7.2.0
 */
public class ImageEncoderFactory {

    public static ImageEncoder createEncodedImage(ImageStream imageStream) {
        // A stencil mask is one bit per pixel and has no colour space; it has to stay that way,
        // whatever it was filtered with, or the dictionary stops describing the samples.
        if (imageStream.getImageParams().isImageMask()) {
            return new StencilEncoder(imageStream);
        }
        if (containsFilter(imageStream, CCITTFAX_DECODE_FILTERS) ||
                containsFilter(imageStream, JBIG2_DECODE_FILTERS)) {
            return new FaxEncoder(imageStream);
        } else if (containsFilter(imageStream, DCT_DECODE_FILTERS) ||
                containsFilter(imageStream, JPX_DECODE_FILTERS)) {
            return new PredictorEncoder(imageStream);
        } else {
            return new RasterEncoder(imageStream);
        }
    }

    /**
     * Whether writing this image back will change the filter it arrived with.
     * <p>
     * A burn re-encodes whatever it touches, and the encoder is chosen from the filter the image came
     * in with rather than kept: a JPEG comes back as Flate RGB, and a JBIG2 as CCITT. The redacted
     * area is gone either way and the rest of the image is intact, so this is not a redaction
     * failure - but a photograph arriving as JPEG and leaving several times larger is a real change
     * to the document, and a caller comparing before and after deserves to know it was expected.
     * <p>
     * Predicted from the same routing {@link #createEncodedImage} uses, so the two cannot disagree
     * without this method being wrong.
     *
     * @param imageStream image about to be burned
     * @return true when the image will be written with a different filter than it arrived with
     */
    public static boolean changesFilter(ImageStream imageStream) {
        if (imageStream.getImageParams().isImageMask()) {
            // StencilEncoder writes Flate, whatever the stencil arrived as.
            return !containsFilter(imageStream, FLATE_DECODE_FILTERS);
        }
        if (containsFilter(imageStream, CCITTFAX_DECODE_FILTERS)) {
            // FaxEncoder writes CCITT, which is what it already is.
            return false;
        }
        if (containsFilter(imageStream, JBIG2_DECODE_FILTERS)) {
            // Also the fax encoder, so JBIG2 becomes CCITT.
            return true;
        }
        // Everything else - DCT and JPX through the predictor encoder, the rest through the raster
        // encoder - comes out Flate.
        return !containsFilter(imageStream, FLATE_DECODE_FILTERS);
    }

    private static final String[] FLATE_DECODE_FILTERS = new String[]{"FlateDecode", "/Fl", "Fl"};
}
