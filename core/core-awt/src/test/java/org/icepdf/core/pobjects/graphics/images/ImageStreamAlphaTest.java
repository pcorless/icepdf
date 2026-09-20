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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.writeables.image.ImageEncoderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing an image that has transparency in it - the signature appearance case.
 * <p>
 * A signature image is a drawn or scanned mark on nothing, and what makes it look like a mark
 * rather than a sticker is that its edges fade out.  Those edges are partly transparent pixels, and
 * a PDF can only hold them one way: a soft mask, one grey sample of alpha per pixel.  The previous
 * approach keyed out pure white with a colour key {@code /Mask}, which can only say paint or do not
 * paint - so every antialiased edge came back either hard or fringed, and any white inside the mark
 * itself was punched out along with the background.
 * <p>
 * These check what actually lands in the dictionary and that the samples decode back to the alpha
 * that went in, because a mask that is written but wrong shows up as a picture, not an error.
 */
public class ImageStreamAlphaTest {

    private final Library library = new Library();

    {
        // Authoring an image registers it, and its mask, as changes to be written.
        library.setStateManager(new StateManager(new CrossReferenceRoot(library)));
    }

    private Resources resources() {
        return new Resources(library, new DictionaryEntries());
    }

    private static GraphicsState graphicsState() {
        return new GraphicsState(new Shapes());
    }

    /**
     * A black square on transparency with a column of half-transparent pixels down the middle -
     * standing in for the soft edge of a signature stroke.
     */
    private static BufferedImage softEdged() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int alpha = x < 3 ? 0xFF : x == 3 ? 0x80 : 0x00;
                image.setRGB(x, y, alpha << 24);
            }
        }
        return image;
    }

    private static BufferedImage opaque() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 8, 8);
        g2d.dispose();
        return image;
    }

    /**
     * The shape {@code ImageIO} hands back for a small PNG or a GIF: an indexed palette, with one
     * entry of it transparent.
     */
    private static BufferedImage indexedWithTransparency() {
        byte[] reds = {(byte) 0xFF, 0};
        byte[] greens = {(byte) 0xFF, 0};
        byte[] blues = {(byte) 0xFF, 0};
        IndexColorModel colorModel = new IndexColorModel(1, 2, reds, greens, blues, 0);
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_BINARY, colorModel);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                image.getRaster().setSample(x, y, 0, x < 4 ? 0 : 1);
            }
        }
        return image;
    }

    /**
     * Writes the stream out and reads it back, which is the only way to tell whether what was
     * written describes itself.
     */
    private BufferedImage roundTrip(ImageStream stream) throws Exception {
        Reference maskReference = stream.getSoftMaskReference();
        if (maskReference != null) {
            // Both objects are written, so both have to be encoded before either can be read: the
            // image names its mask and decoding the image decodes the mask with it.
            encode(streamAt(maskReference));
        }
        ImageStream encoded = encode(stream);
        // A fresh stream over the written bytes: the one that was encoded still holds the decoded
        // image and would simply hand it back instead of reading what was written.
        ImageStream written = new ImageStream(library, encoded.getEntries(), encoded.getRawBytes());
        return written.getImage(graphicsState(), resources());
    }

    private ImageStream encode(ImageStream stream) throws Exception {
        ImageStream encoded = ImageEncoderFactory.createEncodedImage(stream).encode();
        assertNotNull(encoded, "an authored image has to encode to something");
        return encoded;
    }

    private ImageStream streamAt(Reference reference) {
        return (ImageStream) library.getStateManager().getChange(reference).getPObject().getObject();
    }

    private ImageStream softMaskOf(ImageStream stream) {
        Object reference = stream.getEntries().get(new Name("SMask"));
        assertNotNull(reference, "a partly transparent image has to be written with a soft mask");
        assertTrue(reference instanceof Reference, "the soft mask has to be its own object");
        return streamAt((Reference) reference);
    }

    // ------------------------------------------------------------------
    // what the dictionary says
    // ------------------------------------------------------------------

    @DisplayName("a partly transparent image is written with a soft mask, not a colour key mask")
    @Test
    public void transparencyBecomesASoftMask() {
        ImageStream stream = ImageStream.getInstance(library, null, softEdged(), true);

        assertNotNull(stream.getSoftMaskReference(), "expected an /SMask");
        // The colour key mask is what made the half transparent column come back either solid or
        // gone, and punched out any white inside the mark along with the background behind it.
        assertNull(stream.getEntries().get(new Name("Mask")),
                "the colour key mask should be gone, not written alongside");
    }

    @DisplayName("the soft mask describes itself as one grey sample per pixel")
    @Test
    public void softMaskIsEightBitGreyscale() {
        ImageStream softMask = softMaskOf(ImageStream.getInstance(library, null, softEdged(), true));

        DictionaryEntries entries = softMask.getEntries();
        assertEquals("DeviceGray", entries.get(new Name("ColorSpace")).toString());
        assertEquals(8, ((Number) entries.get(new Name("BitsPerComponent"))).intValue());
        assertEquals(8, ((Number) entries.get(new Name("Width"))).intValue());
        assertEquals(8, ((Number) entries.get(new Name("Height"))).intValue());
    }

    @DisplayName("a fully opaque image gets no soft mask")
    @Test
    public void opaqueImageNeedsNoMask() {
        // An alpha channel that is opaque everywhere describes nothing; a mask for it would be a
        // second image the size of the first, saying paint it all.
        ImageStream stream = ImageStream.getInstance(library, null, opaque(), true);
        assertNull(stream.getSoftMaskReference());
    }

    @DisplayName("transparency asked to be flattened is flattened onto white, not dropped")
    @Test
    public void flattenedTransparencyIsNotBlack() {
        // Dropping the alpha channel leaves the transparent pixels showing whatever colour they
        // carry, which for a PNG drawn on nothing is black - a signature on a black block.
        ImageStream stream = ImageStream.getInstance(library, null, softEdged(), false);

        assertNull(stream.getSoftMaskReference());
        assertEquals(0xFFFFFF, stream.getDecodedImage().getRGB(7, 4) & 0xFFFFFF,
                "a fully transparent pixel should have been flattened onto white");
    }

    // ------------------------------------------------------------------
    // the round trip
    // ------------------------------------------------------------------

    @DisplayName("the alpha that went in is the alpha that comes back")
    @Test
    public void alphaSurvivesTheRoundTrip() throws Exception {
        ImageStream stream = ImageStream.getInstance(library, null, softEdged(), true);
        BufferedImage mask = roundTrip(softMaskOf(stream));

        assertNotNull(mask, "the soft mask has to decode again");
        // Read as grey levels: 255 where the image was opaque, 128 down the soft column, 0 outside.
        assertEquals(0xFF, mask.getRGB(0, 4) & 0xFF, "the solid part of the mark stays solid");
        assertEquals(0x80, mask.getRGB(3, 4) & 0xFF, "the soft edge stays half transparent");
        assertEquals(0x00, mask.getRGB(7, 4) & 0xFF, "the background stays transparent");
    }

    @DisplayName("the image's own samples are written as colour, with the alpha left to the mask")
    @Test
    public void colourSamplesAreWrittenWithoutAlpha() throws Exception {
        ImageStream stream = ImageStream.getInstance(library, null, opaque(), true);
        BufferedImage back = roundTrip(stream);

        assertNotNull(back);
        assertEquals(Color.RED.getRGB() & 0xFFFFFF, back.getRGB(4, 4) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------
    // images that are not already in a shape the encoders read
    // ------------------------------------------------------------------

    @DisplayName("an indexed image with transparency is normalized rather than failing to encode")
    @Test
    public void indexedImageIsNormalized() throws Exception {
        // ImageIO hands back an indexed raster for most small PNGs and GIFs.  The predictor encoder
        // has no case for one and returns null, and the writer dereferenced what it returned - so
        // this used to throw on save rather than write a bigger image.
        ImageStream stream = ImageStream.getInstance(library, null, indexedWithTransparency(), true);

        assertFalse(stream.getDecodedImage().getRaster().getSampleModel().getClass()
                        .getSimpleName().contains("MultiPixelPacked"),
                "the indexed raster should have been converted to one the encoders can read");
        assertNotNull(roundTrip(stream), "and it has to encode and decode again");
    }

    @DisplayName("rebuilding an image rewrites its soft mask in place")
    @Test
    public void softMaskIsRewrittenNotOrphaned() {
        // The signature appearance is rebuilt on every settings change.  A mask left behind each
        // time is an object nothing refers to, growing the file on every keystroke.
        ImageStream first = ImageStream.getInstance(library, null, softEdged(), true);
        Reference imageReference = first.getPObjectReference();
        Reference maskReference = first.getSoftMaskReference();

        ImageStream second = ImageStream.getInstance(library, imageReference, maskReference,
                softEdged(), true);

        assertEquals(imageReference, second.getPObjectReference());
        assertEquals(maskReference, second.getSoftMaskReference());
    }

    @DisplayName("an image that stops being transparent releases its soft mask")
    @Test
    public void opaqueRebuildDropsTheSoftMask() {
        ImageStream first = ImageStream.getInstance(library, null, softEdged(), true);
        Reference maskReference = first.getSoftMaskReference();
        assertNotNull(maskReference);

        ImageStream second = ImageStream.getInstance(library, first.getPObjectReference(),
                maskReference, opaque(), true);

        assertNull(second.getSoftMaskReference());
        assertNull(library.getStateManager().getChange(maskReference),
                "the mask nothing refers to any more should not be written");
    }
}
