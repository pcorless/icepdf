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
}
