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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.icepdf.core.pobjects.graphics.images.ImageParams.BITS_PER_COMPONENT_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.COLORSPACE_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.DECODE_KEY;

/**
 * Writes a stencil mask back out as a stencil mask.
 * <p>
 * An {@code /ImageMask true} image is one bit per pixel and carries no colour of its own: each sample
 * says either "paint the colour that is currently set" or "leave this pixel alone" - PDF 32000-1
 * §8.9.6.2. It is how scanned text and logos are usually drawn, and it is the one image kind where
 * the thing a redaction has to change is not a colour but a decision.
 * <p>
 * The general raster encoder cannot express that. Sending a stencil through it produced an image
 * declared {@code /ImageMask true} alongside {@code /BitsPerComponent 8} and a {@code /ColorSpace},
 * which the specification does not allow together: a reader is told to read the samples as one-bit
 * stencil data and handed eight-bit RGB. This writes one bit per pixel and keeps the dictionary a
 * stencil's.
 * <p>
 * Samples are written with 0 meaning paint, which is the default {@code /Decode} for a stencil, so
 * {@code /Decode} is removed rather than preserved - whatever the original said, the samples written
 * here follow the default reading.
 *
 * @since 7.5.0
 */
public class StencilEncoder implements ImageEncoder {

    private final ImageStream imageStream;

    public StencilEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
    }

    @Override
    public ImageStream encode() throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int height = image.getHeight();
        int width = image.getWidth();

        ByteArrayOutputStream packed = new ByteArrayOutputStream(((width + 7) / 8) * height);
        try (MemoryCacheImageOutputStream bits = new MemoryCacheImageOutputStream(packed)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // A decoded stencil paints where it is opaque and leaves the page alone where it
                    // is transparent, so the alpha channel - not the colour, which is whatever fill
                    // colour was in force - is what says which sample this was.
                    boolean paints = (image.getRGB(x, y) >>> 24) != 0;
                    bits.writeBits(paints ? 0 : 1, 1);
                }
                int bitOffset = bits.getBitOffset();
                if (bitOffset != 0) {
                    // rows are byte aligned
                    bits.writeBits(1, 8 - bitOffset);
                }
            }
            bits.flush();
        }

        DictionaryEntries entries = imageStream.getEntries();
        entries.put(BITS_PER_COMPONENT_KEY, 1);
        // A stencil has no colour space, and saying otherwise makes the image invalid.
        entries.remove(COLORSPACE_KEY);
        entries.remove(DECODE_KEY);
        entries.remove(Stream.DECODEPARAM_KEY);
        entries.put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);
        imageStream.setRawBytes(deflate(packed.toByteArray()));
        return imageStream;
    }

    private static byte[] deflate(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 2);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream deflated = new DeflaterOutputStream(out, deflater)) {
                input.transferTo(deflated);
            }
            out.flush();
            deflater.end();
        }
        return out.toByteArray();
    }
}
