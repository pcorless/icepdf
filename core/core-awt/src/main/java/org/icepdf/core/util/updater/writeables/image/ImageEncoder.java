package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.io.IOException;

/**
 * Image encoder base.
 *
 * @since 7.2.0
 */
public interface ImageEncoder {
    ImageStream encode() throws IOException;
}
