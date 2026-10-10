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
package org.icepdf.fx.view;

import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;

/**
 * One block of pixels seen by both toolkits: a Java2D {@link BufferedImage} and a JavaFX
 * {@link WritableImage} backed by the same {@code int[]} through a {@link PixelBuffer}.
 * <p>
 * Java2D paints it on a worker; JavaFX displays it with no copy.  TYPE_INT_ARGB_PRE is the format
 * on both sides (JavaFX's PixelBuffer only takes INT_ARGB_PRE / BYTE_BGRA_PRE), so pixels are
 * never converted either.  Once handed to the scene a buffer is treated as immutable: painting
 * into a displayed buffer would tear, so re-renders produce a new buffer instead.
 */
final class RasterBuffer {

    /** Stands for "nothing here" (an annotation tile with no annotation on it); never displayed. */
    public static final RasterBuffer EMPTY = new RasterBuffer(1, 1);

    private final BufferedImage image;
    private final int[] pixels;
    // created on first use, on the FX thread: a render worker never touches the toolkit, and the
    // cache and slicing work (and their tests) without one.
    private WritableImage fxImage;

    public RasterBuffer(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException(width + "x" + height);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /** Copies a rectangle out of a larger ARGB_PRE raster: slices a region render into tiles. */
    public static RasterBuffer slice(int[] source, int sourceStride, int x, int y, int width, int height) {
        RasterBuffer tile = new RasterBuffer(width, height);
        for (int row = 0; row < height; row++) {
            System.arraycopy(source, (y + row) * sourceStride + x, tile.pixels, row * width, width);
        }
        return tile;
    }

    /** Copies this buffer into a larger ARGB_PRE raster at (x, y): the inverse of {@link #slice}. */
    void copyInto(int[] target, int targetStride, int x, int y) {
        int width = getWidth();
        for (int row = 0; row < getHeight(); row++) {
            System.arraycopy(pixels, row * width, target, (y + row) * targetStride + x, width);
        }
    }

    /** Pixel array of a BufferedImage created as TYPE_INT_ARGB_PRE. */
    static int[] pixelsOf(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /** Java2D side; paint only before the buffer is handed to the scene. */
    public BufferedImage getBufferedImage() {
        return image;
    }

    /** JavaFX side; FX thread only (the image is created on first call). */
    public WritableImage getImage() {
        if (fxImage == null) {
            // zero-copy: the image shares this buffer's int[] through a PixelBuffer.
            PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(getWidth(), getHeight(), IntBuffer.wrap(pixels),
                    PixelFormat.getIntArgbPreInstance());
            fxImage = new WritableImage(pixelBuffer);
        }
        return fxImage;
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public long byteSize() {
        return (long) pixels.length * Integer.BYTES;
    }
}
