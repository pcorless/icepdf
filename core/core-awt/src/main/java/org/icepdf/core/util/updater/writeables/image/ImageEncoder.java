package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.io.IOException;

public interface ImageEncoder {
    ImageStream encode() throws IOException;
}
