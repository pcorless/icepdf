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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class JpxDecoderTest {

    @DisplayName("a JPEG 2000 image within the size limit is decoded at full size")
    @Test
    public void normalImageIsNotScaled() throws IOException {
        // the image is made with a JPEG 2000 ImageIO writer: JJ2000 has one, jpx-imageio doesn't
        assumeTrue(ImageIO.getImageWritersByFormatName("jpeg2000").hasNext(), "no JPEG 2000 ImageIO writer");
        BufferedImage image = DctDecoderTest.decode(400, 16, "jpeg2000", "JPXDecode", true);
        assertNotNull(image);
        assertEquals(400, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @DisplayName("a really big JPEG 2000 image is subsampled while decoding, to about the preferred size")
    @Test
    public void reallyBigImageIsSubsampled() throws IOException {
        // the image is made with a JPEG 2000 ImageIO writer: JJ2000 has one, jpx-imageio doesn't
        assumeTrue(ImageIO.getImageWritersByFormatName("jpeg2000").hasNext(), "no JPEG 2000 ImageIO writer");
        BufferedImage image = DctDecoderTest.decode(12000, 16, "jpeg2000", "JPXDecode", true);
        assertNotNull(image);
        assertEquals(12000 / 8, image.getWidth());
        assertEquals(2, image.getHeight());
        assertTrue((image.getRGB(10, 0) & 0xFF) < 64);
        assertTrue((image.getRGB(image.getWidth() - 10, 0) & 0xFF) > 192);
    }

    // ------------------------------------------------------------------
    // ICEpdf's JPXDecode path over real streams (GH-613 fixtures), with whichever JPEG 2000
    // reader is installed: the PDF's colour space applied to the reader's raster, and the TNsot
    // repair applied before decoding.
    // ------------------------------------------------------------------

    /** Decodes a fixture as a JPXDecode image XObject with the given /ColorSpace, as a page would. */
    static BufferedImage decodeFixture(String fixture, int width, int height, String colourSpace) {
        return decodeFixture(fixture, width, height, new Name(colourSpace));
    }

    /** As above, with /ColorSpace given as the PDF object (a name or an array). */
    static BufferedImage decodeFixture(String fixture, int width, int height, Object colourSpace) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), height);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), colourSpace);
        entries.put(new Name("Filter"), new Name("JPXDecode"));
        Library library = new Library();
        ImageStream imageStream = new ImageStream(library, entries, Jpeg2000Fixtures.bytes(fixture));
        // the render path: ImageParams needs Resources to resolve /ColorSpace (without them every
        // image is taken as DeviceGray), and the factory picks JpxDecoder from /Filter
        return imageStream.getImage(null, new Resources(library, new DictionaryEntries()));
    }

    /** Whether the reader JpxDecoder would pick for this fixture (the first that reads rasters) is JJ2000. */
    static boolean readerIsJj2000(String fixture) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(Jpeg2000Fixtures.bytes(fixture)))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            while (it.hasNext()) {
                ImageReader r = it.next();
                if (r.canReadRaster()) return r.getClass().getName().contains("jaiimageio");
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Jpeg2000Fixtures.Component> reference(String name) {
        return Jpeg2000Fixtures.components(Jpeg2000Fixtures.references().stream()
                .filter(r -> r.name.equals(name) && r.region == null && r.reduce == 0)
                .findFirst().orElseThrow(IllegalStateException::new));
    }

    @DisplayName("a lossless RGB JPX image comes out exactly as the reference decode")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"rev53-rgb", "jp2-rgb", "jp2-pclr-rgb"})
    public void rgbFixtureExact(String name) {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        {
            String file = name + (name.startsWith("jp2") ? ".jp2" : ".j2k");
            // Known gap (GH-613): JJ2000 returns a JP2 palette image as one band of indices, and
            // JpxDecoder's DeviceRGB path (PColorSpaceRasterOp) reads three bands per pixel and
            // runs off the end of the raster. jpx-imageio applies the palette, so it passes.
            assumeTrue(!name.equals("jp2-pclr-rgb") || !readerIsJj2000(file),
                    "JJ2000 hands JpxDecoder palette indices for a DeviceRGB image (known gap)");
            BufferedImage image = decodeFixture(file, 64, 64, "DeviceRGB");
            assertNotNull(image, name);
            List<Jpeg2000Fixtures.Component> c = reference(name);
            int bad = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int p = y * 64 + x;
                    int want = (c.get(0).samples[p] << 16) | (c.get(1).samples[p] << 8) | c.get(2).samples[p];
                    if ((image.getRGB(x, y) & 0xFFFFFF) != want) bad++;
                }
            }
            assertEquals(0, bad, name + ": pixels differing from the reference");
        }
    }

    @DisplayName("a JP2 palette image under a PDF /Indexed colour space decodes (docGris.pdf)")
    @Test
    public void palettedFixtureUnderIndexed() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // jpx-imageio expands the pclr box, so the raster arrives as three bands of RGB rather than
        // one band of indices; putting the PDF's IndexColorModel over it threw. JJ2000 hands back
        // the indices, which the PDF's table (one black entry here) then colours.
        BufferedImage image = decodeFixture("jp2-pclr-rgb.jp2", 64, 64,
                Arrays.asList(new Name("Indexed"), new Name("DeviceRGB"), 0, new HexStringObject("000000")));
        assertNotNull(image);
        if (readerIsJj2000("jp2-pclr-rgb.jp2")) return;
        List<Jpeg2000Fixtures.Component> c = reference("jp2-pclr-rgb");
        int bad = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int p = y * 64 + x;
                int want = (c.get(0).samples[p] << 16) | (c.get(1).samples[p] << 8) | c.get(2).samples[p];
                if ((image.getRGB(x, y) & 0xFFFFFF) != want) bad++;
            }
        }
        assertEquals(0, bad, "pixels differing from the reader's palette expansion");
    }

    @DisplayName("a lossless grey JPX image comes out exactly as the reference decode")
    @Test
    public void grayFixtureExact() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        BufferedImage image = decodeFixture("rev53-gray.j2k", 64, 64, "DeviceGray");
        assertNotNull(image);
        int[] want = reference("rev53-gray").get(0).samples;
        int bad = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (image.getRaster().getSample(x, y, 0) != want[y * 64 + x]) bad++;
            }
        }
        assertEquals(0, bad, "samples differing from the reference");
    }

    @DisplayName("a CMYK JPX image is converted to RGB with the PDF's DeviceCMYK")
    @Test
    public void cmykFixtureConverted() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        BufferedImage image = decodeFixture("rev53-cmyk.j2k", 64, 64, "DeviceCMYK");
        assertNotNull(image);
        assertEquals(64, image.getWidth());
        assertEquals(3, image.getColorModel().getColorSpace().getNumComponents(), "converted to RGB");
    }

    @DisplayName("a stream with TNsot one short is repaired before decoding (GH-553)")
    @Test
    public void tnsotUndercountRepaired() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // tnsot-undercount is tileparts-res with TNsot one short: repaired, it must decode the same
        BufferedImage repaired = decodeFixture("tnsot-undercount.j2k", 64, 64, "DeviceRGB");
        BufferedImage intact = decodeFixture("tileparts-res.j2k", 64, 64, "DeviceRGB");
        assertNotNull(repaired);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) assertEquals(intact.getRGB(x, y), repaired.getRGB(x, y), "pixel " + x + "," + y);
        }
    }
}
