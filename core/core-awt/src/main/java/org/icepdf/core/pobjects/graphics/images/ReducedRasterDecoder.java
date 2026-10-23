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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.graphics.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GH-625: decodes plain sample data (Flate, LZW and the like) at a reduced size, box filtering
 * each {@code factor x factor} block of source pixels into one as the rows stream in.  Neither the
 * inflated bytes nor the full raster are ever held: memory is the reduced image plus a row.
 * <p>
 * Each source pixel is first turned into colour exactly as the full decode would turn it (the
 * {@link ImageUtility} raster paths, else {@link RawDecoder}'s per-pixel conversion), and the
 * colours are averaged, so a reduced image is the full image box filtered, the same filter the
 * GH-608 downsampler applies to a full decode when it is drawn small.
 */
final class ReducedRasterDecoder extends AbstractImageDecoder {

    private static final Logger logger = Logger.getLogger(ReducedRasterDecoder.class.getName());

    private final int factor;

    /**
     * @param factor the reduction, a power of two
     */
    ReducedRasterDecoder(ImageStream imageStream, GraphicsState graphicsState, int factor) {
        super(imageStream, graphicsState);
        this.factor = factor;
    }

    /** One source pixel's colour. */
    private interface PixelColour {
        int argb(int[] samples);
    }

    @Override
    public BufferedImage decode() {
        ImageParams params = imageStream.getImageParams();
        PColorSpace colourSpace = params.getColourSpace();
        if (colourSpace == null) {
            return null;
        }
        int width = params.getWidth();
        int height = params.getHeight();
        int bpc = params.getBitsPerComponent();
        int components = colourSpace.getNumComponents();
        if (width <= 0 || height <= 0 || components <= 0) {
            return null;
        }
        PixelColour colour = colourFor(colourSpace, bpc, params.getDecode());
        int samplesPerPixel = colour instanceof Generic ? ((Generic) colour).samplesPerPixel : components;

        BoxFilter filter = new BoxFilter(width, height, factor);
        int rowBytes = (int) (((long) width * components * bpc + 7) / 8);
        byte[] row = new byte[rowBytes];
        int[] argb = new int[width];
        int[] samples = new int[components];
        try (InputStream in = imageStream.openDecodedInputStream()) {
            if (in == null) {
                return null;
            }
            for (int y = 0; y < height; y++) {
                if (!readRow(in, row)) {
                    break;
                }
                for (int x = 0; x < width; x++) {
                    for (int c = 0; c < samplesPerPixel; c++) {
                        samples[c] = sample(row, x * components + c, bpc);
                    }
                    argb[x] = colour.argb(samples);
                }
                filter.addRow(argb);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Problem reading image samples: ", e);
        }
        return filter.finish();
    }

    /**
     * {@code image} box filtered by {@code factor}: what a codec that can't average while it decodes
     * (JPEG, whose reader subsamples by dropping pixels) hands over, reduced after.
     */
    static BufferedImage boxFilter(BufferedImage image, int factor) {
        if (image == null || factor < 2) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        BoxFilter filter = new BoxFilter(width, height, factor);
        int[] argb = new int[width];
        int type = image.getType();
        if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB) {
            for (int y = 0; y < height; y++) {
                image.getRGB(0, y, width, 1, argb, 0, width);
                filter.addRow(argb);
            }
            return filter.finish();
        }
        // Anything else is turned into colour by drawing it, a strip at a time, as the page draws it.
        // getRGB isn't the same: on a gray image it applies the gray colour space's gamma, which
        // drawing doesn't, and comes out a good deal lighter.
        int stripHeight = Math.min(height, factor * 16);
        BufferedImage strip = new BufferedImage(width, stripHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = strip.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            for (int y0 = 0; y0 < height; y0 += stripHeight) {
                g.drawImage(image, 0, -y0, null);
                for (int y = 0; y < Math.min(stripHeight, height - y0); y++) {
                    strip.getRGB(0, y, width, 1, argb, 0, width);
                    filter.addRow(argb);
                }
            }
        } finally {
            g.dispose();
        }
        return filter.finish();
    }

    /**
     * Averages each {@code factor x factor} block of ARGB rows, added one at a time, into one pixel;
     * the blocks at the right and bottom edges may be partial.
     */
    static final class BoxFilter {
        private final int width;
        private final int factor;
        private final int shift;
        private final int outWidth;
        private final BufferedImage image;
        private final int[] out;
        // per output column: alpha, red, green, blue sums over the current block of rows
        private final long[] sums;
        private int rowsInBlock;
        private int outY;

        BoxFilter(int width, int height, int factor) {
            this.width = width;
            this.factor = factor;
            shift = Integer.numberOfTrailingZeros(factor);
            outWidth = ReducedDecode.reduced(width, factor);
            image = new BufferedImage(outWidth, ReducedDecode.reduced(height, factor), BufferedImage.TYPE_INT_ARGB);
            out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            sums = new long[outWidth * 4];
        }

        void addRow(int[] argb) {
            for (int x = 0; x < width; x++) {
                int pixel = argb[x];
                int i = (x >> shift) << 2;
                sums[i] += pixel >>> 24;
                sums[i + 1] += (pixel >> 16) & 0xFF;
                sums[i + 2] += (pixel >> 8) & 0xFF;
                sums[i + 3] += pixel & 0xFF;
            }
            if (++rowsInBlock == factor) {
                emit();
            }
        }

        /** The reduced image; rows never added (data that ran short) stay transparent. */
        BufferedImage finish() {
            if (rowsInBlock > 0) {
                emit();
            }
            return image;
        }

        /** Writes one row of block averages and clears the sums. */
        private void emit() {
            int offset = outY++ * outWidth;
            for (int ox = 0; ox < outWidth; ox++) {
                int columns = Math.min(factor, width - (ox << shift));
                long count = (long) rowsInBlock * columns;
                long half = count / 2;
                int i = ox << 2;
                int a = (int) ((sums[i] + half) / count);
                int r = (int) ((sums[i + 1] + half) / count);
                int g = (int) ((sums[i + 2] + half) / count);
                int b = (int) ((sums[i + 3] + half) / count);
                out[offset + ox] = (a << 24) | (r << 16) | (g << 8) | b;
                sums[i] = sums[i + 1] = sums[i + 2] = sums[i + 3] = 0;
            }
            rowsInBlock = 0;
        }
    }

    /** Fills {@code row}; false at the end of the data, the last short row included. */
    private static boolean readRow(InputStream in, byte[] row) throws IOException {
        int read = 0;
        while (read < row.length) {
            int n = in.read(row, read, row.length - read);
            if (n <= 0) {
                return false;
            }
            read += n;
        }
        return true;
    }

    /** Sample {@code index} of a byte aligned row of {@code bpc} bit samples. */
    static int sample(byte[] row, int index, int bpc) {
        switch (bpc) {
            case 8:
                return row[index] & 0xFF;
            case 16:
                return ((row[index * 2] & 0xFF) << 8) | (row[index * 2 + 1] & 0xFF);
            default:
                int bit = index * bpc;
                int b = row[bit >> 3] & 0xFF;
                return (b >> (8 - bpc - (bit & 7))) & ((1 << bpc) - 1);
        }
    }

    /**
     * How the full decode colours a pixel of this image: the {@link ImageUtility} raster path it
     * takes for an unmasked image, else {@link RawDecoder}, which it falls back to.
     */
    private static PixelColour colourFor(PColorSpace colourSpace, int bpc, float[] decode) {
        PColorSpace raster = colourSpace;
        if (raster instanceof ICCBased && ((ICCBased) raster).getAlternate() != null) {
            raster = ((ICCBased) raster).getAlternate();
        }
        boolean lowBits = bpc == 1 || bpc == 2 || bpc == 4;
        if (raster instanceof DeviceGray) {
            if (lowBits) {
                int[] table = bpc == 1
                        ? (decode[0] == 0.0f ? ImageUtility.GRAY_1_BIT_INDEX_TO_RGB
                        : ImageUtility.GRAY_1_BIT_INDEX_TO_RGB_REVERSED)
                        : grayTable(bpc);
                return s -> table[s[0]];
            } else if (bpc == 8) {
                boolean defaultDecode = decode[0] == 0.0f;
                return s -> {
                    int v = defaultDecode ? s[0] : 255 - s[0];
                    return 0xFF000000 | (v << 16) | (v << 8) | v;
                };
            }
        } else if (raster instanceof DeviceRGB || raster instanceof CalRGB) {
            if (bpc == 8) {
                return s -> 0xFF000000 | (s[0] << 16) | (s[1] << 8) | s[2];
            }
        } else if (raster instanceof Indexed) {
            if (lowBits || bpc == 8) {
                int[] table = indexTable((Indexed) raster, bpc);
                return s -> s[0] < table.length ? table[s[0]] : 0xFF000000;
            }
        } else if (raster instanceof CalGray
                || (raster instanceof Separation && ((Separation) raster).isNamedColor())) {
            if (lowBits) {
                int[] table = grayTable(bpc);
                return s -> table[s[0]];
            }
        }
        return new Generic(colourSpace, bpc, decode);
    }

    private static int[] grayTable(int bpc) {
        return bpc == 1 ? ImageUtility.GRAY_1_BIT_INDEX_TO_RGB
                : bpc == 2 ? ImageUtility.GRAY_2_BIT_INDEX_TO_RGB
                : ImageUtility.GRAY_4_BIT_INDEX_TO_RGB;
    }

    /** The palette as the raster path builds it: cut to the bit depth, or padded with black to 256. */
    private static int[] indexTable(Indexed indexed, int bpc) {
        indexed.init();
        Color[] colors = indexed.accessColorTable();
        int length = colors == null ? 0 : colors.length;
        int[] table = new int[bpc == 8 ? 256 : Math.min(length, 1 << bpc)];
        java.util.Arrays.fill(table, 0xFF000000);
        for (int i = 0; i < Math.min(length, table.length); i++) {
            table[i] = colors[i].getRGB();
        }
        return table;
    }

    /** {@link RawDecoder}'s conversion, for every colour space the raster paths leave to it. */
    private static final class Generic implements PixelColour {
        private final RawDecoder.SampleConverter converter;
        private final int samplesPerPixel;

        Generic(PColorSpace colourSpace, int bpc, float[] decode) {
            converter = new RawDecoder.SampleConverter(colourSpace, bpc, decode);
            samplesPerPixel = converter.samplesPerPixel();
        }

        @Override
        public int argb(int[] samples) {
            return converter.toArgb(samples);
        }
    }
}
