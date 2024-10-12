package com.icepdf.fx.util;

import javafx.scene.image.Image;

/**
 * Aid for loading image from image resources path.
 */
public class ImageLoader {

    public static Image loadImage(String filename) {
        return new Image(filename.getClass().getResourceAsStream(
                "/com/icepdf/fx/images/" + filename));
    }
}
