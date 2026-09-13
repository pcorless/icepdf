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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the choice of image reference: which strategy is used to get an image onto the page.
 * <p>
 * A reference stands in for an image while it is being decoded, and the strategy decides what is
 * decoded and when - the stream at its own size, a copy scaled to the size it will be drawn at, a
 * pyramid of sizes, or a smoothed or blurred version.  The choice is a global setting, so it is
 * made once for the whole document and every image goes through whichever was picked; a strategy
 * that fails to produce an image produces a page with a hole in it.
 * <p>
 * The image here is four pixels square of plain RGB, which is enough to tell a decoded image from
 * nothing at all without making the test about image decoding.
 */
public class ImageReferenceFactoryTest {

    private final Library library = new Library();

    @AfterEach
    public void restoreDefaultStrategy() {
        // the strategy is a global, so a test that changed it has to put it back
        ImageReferenceFactory.imageReferenceType = ImageReferenceFactory.TYPE_DEFAULT;
    }

    /**
     * A four by four RGB image with no filter, small enough to be quick and real enough to decode.
     */
    private ImageStream imageStream() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, new Name("XObject"));
        entries.put(Dictionary.SUBTYPE_KEY, new Name("Image"));
        entries.put(new Name("Width"), 4);
        entries.put(new Name("Height"), 4);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), new Name("DeviceRGB"));
        byte[] samples = new byte[4 * 4 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (byte) (i * 5);
        }
        entries.put(Dictionary.LENGTH_KEY, samples.length);
        return new ImageStream(library, entries, ByteBuffer.wrap(samples));
    }

    private Resources resources() {
        return new Resources(library, new DictionaryEntries());
    }

    private static GraphicsState graphicsState() {
        return new GraphicsState(new Shapes());
    }

    private ImageReference reference(String type) {
        ImageReferenceFactory.imageReferenceType = type;
        return ImageReferenceFactory.getImageReference(
                imageStream(), new Name("Im1"), resources(), graphicsState(), 0, null);
    }

    // ------------------------------------------------------------------
    // the registry
    // ------------------------------------------------------------------

    @DisplayName("the five built-in strategies are registered, each with a name to show")
    @Test
    public void builtinsAreRegistered() {
        Map<String, ImageReferenceFactory.RegistryEntry> registered =
                ImageReferenceFactory.getRegisteredTypes();
        for (String key : new String[]{ImageReferenceFactory.TYPE_DEFAULT,
                ImageReferenceFactory.TYPE_SCALED, ImageReferenceFactory.TYPE_MIP_MAP,
                ImageReferenceFactory.TYPE_SMOOTH_SCALED, ImageReferenceFactory.TYPE_BLURRED}) {
            assertTrue(registered.containsKey(key), key + " should be registered");
            assertNotNull(registered.get(key).getDisplayName());
            assertNotNull(registered.get(key).getCreator());
        }
    }

    @DisplayName("the registry keeps the order the strategies were registered in")
    @Test
    public void registryIsOrdered() {
        // The viewer offers these in a menu, so the order is part of what the registry is for.
        Set<String> keys = ImageReferenceFactory.getRegisteredTypeKeys();
        assertEquals(ImageReferenceFactory.TYPE_DEFAULT, keys.iterator().next(),
                "the default should come first");
    }

    @DisplayName("the registry cannot be changed through the map it hands out")
    @Test
    public void registryIsNotModifiableFromOutside() {
        Map<String, ImageReferenceFactory.RegistryEntry> registered =
                ImageReferenceFactory.getRegisteredTypes();
        assertThrows(UnsupportedOperationException.class, () -> registered.remove("default"));
    }

    @DisplayName("a strategy of one's own can be registered and is then used")
    @Test
    public void registerACustomStrategy() {
        ImageReferenceFactory.register("test-only", "Test Only",
                (imageStream, name, graphicsState, resources, index, page) ->
                        new ImageStreamReference(imageStream, name, graphicsState, resources, index, page));
        assertTrue(ImageReferenceFactory.getRegisteredTypeKeys().contains("test-only"));
        assertInstanceOf(ImageStreamReference.class, reference("test-only"));
    }

    @DisplayName("a strategy must be registered under a usable key")
    @Test
    public void registerRejectsAnEmptyKey() {
        assertThrows(IllegalArgumentException.class, () ->
                ImageReferenceFactory.register(null, "No Key", null));
        assertThrows(IllegalArgumentException.class, () ->
                ImageReferenceFactory.register("", "No Key", null));
    }

    // ------------------------------------------------------------------
    // choosing a strategy
    // ------------------------------------------------------------------

    @DisplayName("each strategy builds the reference it names")
    @Test
    public void eachStrategyBuildsItsOwnReference() {
        assertInstanceOf(ImageStreamReference.class, reference(ImageReferenceFactory.TYPE_DEFAULT));
        assertInstanceOf(ScaledImageReference.class, reference(ImageReferenceFactory.TYPE_SCALED));
        assertInstanceOf(MipMappedImageReference.class, reference(ImageReferenceFactory.TYPE_MIP_MAP));
        assertInstanceOf(SmoothScaledImageReference.class,
                reference(ImageReferenceFactory.TYPE_SMOOTH_SCALED));
        assertInstanceOf(BlurredImageReference.class, reference(ImageReferenceFactory.TYPE_BLURRED));
    }

    @DisplayName("a strategy that is not registered falls back to the default")
    @Test
    public void unknownStrategyFallsBackToDefault() {
        // The setting comes from a system property, so it can name anything at all; an image that
        // could not be built would leave a hole in the page.
        assertInstanceOf(ImageStreamReference.class, reference("no-such-strategy"));
    }

    // ------------------------------------------------------------------
    // what each strategy produces
    // ------------------------------------------------------------------

    @DisplayName("every strategy reports the size of the image it stands for")
    @Test
    public void everyStrategyReportsASize() {
        for (String type : ImageReferenceFactory.getRegisteredTypeKeys()) {
            ImageReference imageReference = reference(type);
            assertEquals(4, imageReference.getWidth(), type + " should report the image width");
            assertEquals(4, imageReference.getHeight(), type + " should report the image height");
        }
    }

    @DisplayName("every strategy decodes to an image rather than to nothing")
    @Test
    public void everyStrategyProducesAnImage() throws Exception {
        // The point of the strategies is how the image is prepared, not whether there is one; a
        // strategy that returns null draws nothing, and the page simply lacks the image.
        for (String type : ImageReferenceFactory.getRegisteredTypeKeys()) {
            ImageReference imageReference = reference(type);
            BufferedImage image = imageReference.getImage();
            assertNotNull(image, type + " decoded to nothing");
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0,
                    type + " decoded to an empty image");
        }
    }

    @DisplayName("a reference knows the image stream and the name it came from")
    @Test
    public void referenceKeepsItsSource() {
        ImageReference imageReference = reference(ImageReferenceFactory.TYPE_DEFAULT);
        assertNotNull(imageReference.getImageStream());
        assertEquals(new Name("Im1"), imageReference.getXobjectName());
    }
}
