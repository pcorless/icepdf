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
import org.icepdf.core.util.Defs;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Writes a burned image that arrived as DCTDecode back as DCTDecode (GH-697), with the JDK's JPEG writer, rather
 * than as Flate: a scanned page re-encoded losslessly was several times its JPEG size. JPEG is lossy, so the
 * untouched part of the image loses a little more on the way through; the burned areas are flat colour, which
 * JPEG keeps well.
 * <p>
 * The original's own quantisation tables are reused, so the image comes back at the quality it was saved at
 * and about its size: a fixed quality made a coarsely compressed scan half as large again. When the original's
 * tables can't be read, {@value #QUALITY_PROPERTY} (0 to 1, {@value #DEFAULT_QUALITY} by default) is used;
 * setting the property always uses it.
 *
 * @since 8.1
 */
class DctEncoder implements ImageEncoder {

    static final String QUALITY_PROPERTY = "org.icepdf.core.redaction.jpegQuality";
    static final float DEFAULT_QUALITY = 0.9f;

    private final ImageStream imageStream;

    DctEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
    }

    /**
     * @return the stream, re-encoded; null for an image this encoder doesn't take (alpha, a sample layout it
     * doesn't read), which is then written losslessly
     */
    @Override
    public ImageStream encode() throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int components = LossyEncoding.components(image);
        if (components == 0) {
            return null;
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(image.getWidth() * image.getHeight() / 4 + 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            JPEGQTable[] tables = Defs.sysProperty(QUALITY_PROPERTY) == null
                    ? quantisationTables(imageStream.getRawBytes(), components) : null;
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param instanceof JPEGImageWriteParam) {
                ((JPEGImageWriteParam) param).setOptimizeHuffmanTables(true);
            }
            IIOMetadata metadata = null;
            if (tables != null) {
                metadata = withTables(writer, image, param, tables);
            }
            if (metadata == null) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(LossyEncoding.quality(QUALITY_PROPERTY, DEFAULT_QUALITY));
            }
            writer.write(null, new IIOImage(image, null, metadata), param);
        } finally {
            writer.dispose();
        }
        return LossyEncoding.describe(imageStream, bytes.toByteArray(), "DCTDecode", components);
    }

    private static final String JPEG_METADATA = "javax_imageio_jpeg_image_1.0";

    /**
     * The writer's default metadata for the image with its quantisation tables replaced by {@code tables}: the
     * tables go in the image's own DQT segment (the encode tables of the write param would make an abbreviated
     * stream with none).
     *
     * @return the metadata, or null when the writer's doesn't take tables this way
     */
    private static IIOMetadata withTables(ImageWriter writer, BufferedImage image, ImageWriteParam param,
                                          JPEGQTable[] tables) {
        try {
            IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
            Node tree = metadata.getAsTree(JPEG_METADATA);
            int replaced = 0;
            for (Node node : descendants(tree, "dqtable")) {
                int id = Integer.parseInt(((IIOMetadataNode) node).getAttribute("qtableId"));
                if (id < tables.length) {
                    ((IIOMetadataNode) node).setUserObject(tables[id]);
                    replaced++;
                }
            }
            if (replaced == 0) {
                return null;
            }
            metadata.setFromTree(JPEG_METADATA, tree);
            return metadata;
        } catch (IIOInvalidTreeException | RuntimeException e) {
            return null;
        }
    }

    private static List<Node> descendants(Node node, String name) {
        List<Node> found = new ArrayList<>();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equals(child.getNodeName())) {
                found.add(child);
            }
            found.addAll(descendants(child, name));
        }
        return found;
    }

    /** Zig-zag position of each coefficient in natural (row-major) order: DQT stores tables zig-zag. */
    private static final int[] ZIGZAG = {
            0, 1, 5, 6, 14, 15, 27, 28,
            2, 4, 7, 13, 16, 26, 29, 42,
            3, 8, 12, 17, 25, 30, 41, 43,
            9, 11, 18, 24, 31, 40, 44, 53,
            10, 19, 23, 32, 39, 45, 52, 54,
            20, 22, 33, 38, 46, 51, 55, 60,
            21, 34, 37, 47, 50, 56, 59, 61,
            35, 36, 48, 49, 57, 58, 62, 63};

    /**
     * The quantisation tables of the original JPEG, from its DQT segments, for the components written: one for
     * grey, luminance and chrominance for colour (the first table twice when it has only one).
     *
     * @return the tables in natural order, or null when there are none to read (not a JPEG, a table over 8 bits
     * that the baseline writer can't use, a damaged segment)
     */
    static JPEGQTable[] quantisationTables(byte[] jpeg, int components) {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return null;
        }
        int[][] byId = new int[4][];
        int at = 2;
        while (at + 4 <= jpeg.length) {
            if ((jpeg[at] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = jpeg[at + 1] & 0xFF;
            if (marker == 0xFF) {
                at++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                // start of scan: every table the image uses is defined by now
                break;
            }
            int length = ((jpeg[at + 2] & 0xFF) << 8) | (jpeg[at + 3] & 0xFF);
            int end = at + 2 + length;
            if (length < 2 || end > jpeg.length) {
                return null;
            }
            if (marker == 0xDB) {
                int p = at + 4;
                while (p < end) {
                    int precision = (jpeg[p] & 0xFF) >> 4;
                    int id = jpeg[p] & 0x0F;
                    if (precision != 0 || id > 3 || p + 65 > end) {
                        return null;
                    }
                    int[] natural = new int[64];
                    for (int i = 0; i < 64; i++) {
                        natural[i] = jpeg[p + 1 + ZIGZAG[i]] & 0xFF;
                    }
                    byId[id] = natural;
                    p += 65;
                }
            }
            at = end;
        }
        if (byId[0] == null) {
            return null;
        }
        if (components == 1) {
            return new JPEGQTable[]{new JPEGQTable(byId[0])};
        }
        int[] chroma = byId[1] != null ? byId[1] : byId[0];
        return new JPEGQTable[]{new JPEGQTable(byId[0]), new JPEGQTable(chroma)};
    }
}
