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
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
    public void normalImageIsNotScaled() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        BufferedImage image = decodeFixture("rev53-gray.j2k", 64, 64, "DeviceGray");
        assertNotNull(image);
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
    }

    @DisplayName("a really big JPEG 2000 image is subsampled while decoding, to about the preferred size")
    @Test
    public void reallyBigImageIsSubsampled() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // shrink the limits rather than encode a 10000-pixel image: 64 pixels is then really big, and the
        // preferred 16 asks for a subsampling of 4, which the reader takes as two discarded resolution levels
        int maxWidth = AbstractImageDecoder.maxImageWidth, maxHeight = AbstractImageDecoder.maxImageHeight;
        int preferred = AbstractImageDecoder.preferredSize;
        BufferedImage image;
        try {
            AbstractImageDecoder.maxImageWidth = 32;
            AbstractImageDecoder.maxImageHeight = 32;
            AbstractImageDecoder.preferredSize = 16;
            image = decodeFixture("rev53-gray.j2k", 64, 64, "DeviceGray");
        } finally {
            AbstractImageDecoder.maxImageWidth = maxWidth;
            AbstractImageDecoder.maxImageHeight = maxHeight;
            AbstractImageDecoder.preferredSize = preferred;
        }
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    // ------------------------------------------------------------------
    // ICEpdf's JPXDecode path over real streams (GH-613 fixtures): the PDF's colour space applied
    // to the reader's raster.
    // ------------------------------------------------------------------

    /** Decodes a fixture as a JPXDecode image XObject with the given /ColorSpace, as a page would. */
    static BufferedImage decodeFixture(String fixture, int width, int height, String colourSpace) {
        return decodeFixture(fixture, width, height, new Name(colourSpace));
    }

    /** As above, with /ColorSpace given as the PDF object (a name or an array). */
    static BufferedImage decodeFixture(String fixture, int width, int height, Object colourSpace) {
        return decodeImage(Jpeg2000Fixtures.bytes(fixture), width, height, colourSpace);
    }

    /** As above, for JPX data that is not a fixture as stored. */
    static BufferedImage decodeImage(byte[] data, int width, int height, Object colourSpace) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), height);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), colourSpace);
        entries.put(new Name("Filter"), new Name("JPXDecode"));
        Library library = new Library();
        ImageStream imageStream = new ImageStream(library, entries, data);
        // the render path: ImageParams needs Resources to resolve /ColorSpace (without them every
        // image is taken as DeviceGray), and the factory picks JpxDecoder from /Filter
        return imageStream.getImage(null, new Resources(library, new DictionaryEntries()));
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

    @DisplayName("a JP2 wrapper the reader rejects falls back to its codestream")
    @Test
    public void brokenWrapperFallsBackToCodestream() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // the pclr box renamed cmap: a cmap ahead of its pclr, and a second cmap, fail the JP2 header in
        // jpx-imageio (as in OpenJPEG). /Indexed never reads the wrapper, so take another colour space to
        // reach the retry with the codestream alone.
        byte[] jp2 = Jpeg2000Fixtures.bytes("jp2-pclr-cmyk.jp2").clone();
        int pclr = indexOf(jp2, "pclr".getBytes(StandardCharsets.US_ASCII));
        assertTrue(pclr > 0, "fixture has a pclr box");
        System.arraycopy("cmap".getBytes(StandardCharsets.US_ASCII), 0, jp2, pclr, 4);
        BufferedImage image = decodeImage(jp2, 64, 64, new Name("DeviceGray"));
        assertNotNull(image, "decoder gave up on the broken wrapper");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    @DisplayName("a JP2 palette image under a PDF /Indexed colour space takes the PDF's table (docGris.pdf, Chevron_2010.pdf)")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"jp2-pclr-rgb.jp2", "jp2-pclr-cmyk.jp2"})
    public void palettedFixtureUnderIndexed(String fixture) {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // the PDF's table replaces the JP2's own palette: jpx-imageio used to expand the pclr box into three
        // (docGris) or four (Chevron, CMYK) bands, and the IndexColorModel over them threw. A grey ramp
        // makes each pixel show the index it was looked up with.
        StringBuilder ramp = new StringBuilder();
        for (int i = 0; i < 256; i++) ramp.append(String.format("%02X%02X%02X", i, i, i));
        BufferedImage image = decodeFixture(fixture, 64, 64,
                Arrays.asList(new Name("Indexed"), new Name("DeviceRGB"), 255, new HexStringObject(ramp.toString())));
        assertNotNull(image, fixture);
        Raster indices = codestreamIndices(fixture);
        int bad = 0, distinct = 0;
        boolean[] seen = new boolean[256];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int i = indices.getSample(x, y, 0);
                if (!seen[i]) distinct++;
                seen[i] = true;
                if ((image.getRGB(x, y) & 0xFFFFFF) != (i << 16 | i << 8 | i)) bad++;
            }
        }
        assertTrue(distinct > 1, fixture + ": a fixture of one index proves nothing");
        assertEquals(0, bad, fixture + ": pixels not coloured by the PDF's table");
    }

    /** The palette indices: the fixture's codestream, read without its JP2 wrapper. */
    static Raster codestreamIndices(String fixture) {
        byte[] codestream = Jp2Codestream.unwrap(Jpeg2000Fixtures.bytes(fixture));
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(codestream))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            ImageReader r = it.next();
            r.setInput(in);
            Raster raster = r.read(0).getRaster();
            assertEquals(1, raster.getNumBands(), fixture + ": codestream components");
            return raster;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    @DisplayName("a stream with TNsot one short decodes the same as the intact one (GH-553)")
    @Test
    public void tnsotUndercountDecodes() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        // tnsot-undercount is tileparts-res with TNsot one short. JJ2000 trusted the count and dropped the last
        // tile-part, which ICEpdf repaired; jpx-imageio walks the tile-parts by their lengths (D-10), so no repair
        BufferedImage undercount = decodeFixture("tnsot-undercount.j2k", 64, 64, "DeviceRGB");
        BufferedImage intact = decodeFixture("tileparts-res.j2k", 64, 64, "DeviceRGB");
        assertNotNull(undercount);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) assertEquals(intact.getRGB(x, y), undercount.getRGB(x, y), "pixel " + x + "," + y);
        }
    }
}
