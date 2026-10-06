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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests re-encoding an image that arrived as a JPEG.
 * <p>
 * This is the path a redaction takes over a photograph or a scan.  The burn draws over the decoded
 * pixels and the result has to be written back, and it cannot go back as a JPEG - the point of the
 * burn is that the covered pixels are gone, and re-compressing lossily would leave ghosts of them.
 * So the image comes back as Flate with a PNG predictor instead, and the dictionary has to be
 * rewritten to match or it stops describing its own samples.
 * <p>
 * The check that matters is a round trip: whatever is written has to decode back to the picture
 * that went in.  An encoder that produces bytes no decoder accepts fails nothing else - the save
 * succeeds, and the image is simply missing when the file is next opened.
 */
public class PredictorEncoderTest {

    private final Library library = new Library();

    private Resources resources() {
        return new Resources(library, new DictionaryEntries());
    }

    private static GraphicsState graphicsState() {
        return new GraphicsState(new Shapes());
    }

    /**
     * A two-tone test picture, which survives JPEG well enough to be recognised afterwards.
     */
    private static BufferedImage picture(int type) {
        BufferedImage image = new BufferedImage(16, 16, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 16);
        g.setColor(Color.BLUE);
        g.fillRect(8, 0, 8, 16);
        g.dispose();
        return image;
    }

    /**
     * An image stream holding {@code image} as a JPEG, as a scanned page carries one.
     */
    private ImageStream jpegStream(BufferedImage image) throws Exception {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);
        byte[] encoded = jpeg.toByteArray();

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name("XObject"));
        entries.put(Dictionary.SUBTYPE_KEY, new Name("Image"));
        entries.put(new Name("Width"), image.getWidth());
        entries.put(new Name("Height"), image.getHeight());
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"),
                new Name(image.getType() == BufferedImage.TYPE_BYTE_GRAY ? "DeviceGray" : "DeviceRGB"));
        entries.put(new Name("Filter"), new Name("DCTDecode"));
        entries.put(Dictionary.LENGTH_KEY, encoded.length);
        return new ImageStream(library, entries, ByteBuffer.wrap(encoded));
    }

    /**
     * Decodes a stream, then hands the pixels back to it the way a burn does before writing.
     */
    private ImageStream decodedAndReady(BufferedImage source) throws Exception {
        ImageStream stream = jpegStream(source);
        BufferedImage decoded = stream.getImage(graphicsState(), resources());
        assertNotNull(decoded, "the fixture should decode before it is re-encoded");
        stream.setDecodedImage(decoded);
        return stream;
    }

    private static void assertRoughly(int expected, int actual, String what) {
        // JPEG is lossy, so the colours that come back are near rather than equal; near is enough
        // to tell red from blue, which is all this needs to show the picture survived.
        for (int shift : new int[]{16, 8, 0}) {
            int e = (expected >> shift) & 0xFF;
            int a = (actual >> shift) & 0xFF;
            assertTrue(Math.abs(e - a) < 24,
                    what + ": expected near " + Integer.toHexString(expected)
                            + " but was " + Integer.toHexString(actual));
        }
    }

    // ------------------------------------------------------------------
    // choosing the encoder
    // ------------------------------------------------------------------

    @DisplayName("a JPEG is re-encoded with the predictor encoder, not back to JPEG")
    @Test
    public void factoryPicksThePredictorEncoder() throws Exception {
        // Writing it back as a JPEG would re-compress lossily over the burn, leaving ghosts of
        // exactly the pixels the redaction was meant to remove.
        assertInstanceOf(PredictorEncoder.class,
                ImageEncoderFactory.createEncodedImage(decodedAndReady(
                        picture(BufferedImage.TYPE_INT_RGB))));
    }

    // ------------------------------------------------------------------
    // the round trip
    // ------------------------------------------------------------------

    @DisplayName("a colour image re-encodes to something that decodes back to it")
    @Test
    public void colourRoundTrip() throws Exception {
        ImageStream source = decodedAndReady(picture(BufferedImage.TYPE_INT_RGB));
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(source).encode();

        BufferedImage back = encoded.getImage(graphicsState(), resources());
        assertNotNull(back, "the re-encoded image has to decode again");
        assertEquals(16, back.getWidth());
        assertEquals(16, back.getHeight());
        assertRoughly(Color.RED.getRGB(), back.getRGB(2, 8), "the left half should still be red");
        assertRoughly(Color.BLUE.getRGB(), back.getRGB(13, 8), "the right half should still be blue");
    }

    @DisplayName("a greyscale image re-encodes and decodes back")
    @Test
    public void greyscaleRoundTrip() throws Exception {
        // A scanned page is usually a greyscale JPEG, which makes this the commonest thing a
        // redaction is ever burned over.  The encoder had no case for it and returned null, and
        // ImageStreamWriter dereferences what it returns - so saving the redaction threw.
        ImageStream source = decodedAndReady(picture(BufferedImage.TYPE_BYTE_GRAY));
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(source).encode();
        assertNotNull(encoded, "a greyscale image has to encode to something");

        BufferedImage back = encoded.getImage(graphicsState(), resources());
        assertNotNull(back);
        assertEquals(16, back.getWidth());
        assertEquals(16, back.getHeight());
    }

    @DisplayName("a greyscale image is described as greyscale, not as colour")
    @Test
    public void greyscaleKeepsItsColourSpace() throws Exception {
        // The colour space has to agree with the samples that were written.  Saying DeviceRGB over
        // one byte per pixel claims three components where the stream holds one, and /Colors in the
        // decode parameters says one - a dictionary contradicting itself.
        ImageStream encoded = ImageEncoderFactory
                .createEncodedImage(decodedAndReady(picture(BufferedImage.TYPE_BYTE_GRAY))).encode();

        assertEquals("DeviceGray", encoded.getEntries().get(new Name("ColorSpace")).toString());

        DictionaryEntries decodeParams = (DictionaryEntries)
                encoded.getEntries().get(new Name("DecodeParms"));
        assertEquals(1, ((Number) decodeParams.get(new Name("Colors"))).intValue(),
                "and the predictor has to be told the same component count");
    }

    @DisplayName("a colour image is still described as colour")
    @Test
    public void colourKeepsItsColourSpace() throws Exception {
        ImageStream encoded = ImageEncoderFactory
                .createEncodedImage(decodedAndReady(picture(BufferedImage.TYPE_INT_RGB))).encode();

        assertEquals("DeviceRGB", encoded.getEntries().get(new Name("ColorSpace")).toString());
        DictionaryEntries decodeParams = (DictionaryEntries)
                encoded.getEntries().get(new Name("DecodeParms"));
        assertEquals(3, ((Number) decodeParams.get(new Name("Colors"))).intValue());
    }

    // ------------------------------------------------------------------
    // the dictionary that describes what was written
    // ------------------------------------------------------------------

    @DisplayName("the filter is rewritten to say how the samples are now stored")
    @Test
    public void filterIsRewritten() throws Exception {
        // The dictionary still said DCTDecode while the bytes were Flate, nothing would decode it.
        ImageStream encoded = ImageEncoderFactory
                .createEncodedImage(decodedAndReady(picture(BufferedImage.TYPE_INT_RGB))).encode();

        Object filter = encoded.getEntries().get(new Name("Filter"));
        assertNotNull(filter, "the re-encoded image has to say what it was encoded with");
        assertTrue(filter.toString().contains("Flate"), "expected a Flate filter, was " + filter);
    }

    @DisplayName("the size and depth still describe the samples that were written")
    @Test
    public void dimensionsSurvive() throws Exception {
        ImageStream encoded = ImageEncoderFactory
                .createEncodedImage(decodedAndReady(picture(BufferedImage.TYPE_INT_RGB))).encode();

        DictionaryEntries entries = encoded.getEntries();
        assertEquals(16, ((Number) entries.get(new Name("Width"))).intValue());
        assertEquals(16, ((Number) entries.get(new Name("Height"))).intValue());
        assertEquals(8, ((Number) entries.get(new Name("BitsPerComponent"))).intValue());
        assertNotNull(entries.get(new Name("ColorSpace")));
    }

    @DisplayName("the encoded stream is not empty")
    @Test
    public void streamHasContent() throws Exception {
        // A zero length stream is a page with a blank where the picture was, and the save that
        // produced it reports success.
        ImageStream encoded = ImageEncoderFactory
                .createEncodedImage(decodedAndReady(picture(BufferedImage.TYPE_INT_RGB))).encode();
        assertTrue(encoded.getRawBytes().length > 0, "the encoded image should hold some bytes");
    }
}
