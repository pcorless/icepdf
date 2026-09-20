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
package org.icepdf.signing;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signature image with transparency in it, all the way through a real save and reopen.
 * <p>
 * The unit level covers what {@code ImageStream} builds; this covers what a reader actually gets.
 * The appearance stream, the image XObject, its soft mask and the resources naming them are four
 * separate objects written by three different pieces of code, and a signature image that looks
 * wrong in a viewer is usually one of them not being written rather than any of them being built
 * wrong.
 */
public class SignatureImageTransparencyTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    /**
     * A mark whose left half is solid, with a soft edge and then nothing - what a drawn or scanned
     * signature on transparency looks like at its edges.
     */
    private static BufferedImage softEdgedMark() {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = x < 24 ? 0xFF : x < 32 ? 0x80 : 0x00;
                image.setRGB(x, y, alpha << 24);
            }
        }
        return image;
    }

    private static BufferedImage opaqueMark() {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.dispose();
        return image;
    }

    /**
     * The image XObject the signed document's appearance stream draws.
     */
    private static ImageStream signatureImageOf(Document document) throws Exception {
        List<Annotation> annotations = document.getPageTree().getPage(0).getAnnotations();
        SignatureWidgetAnnotation widget = annotations.stream()
                .filter(a -> a instanceof SignatureWidgetAnnotation)
                .map(a -> (SignatureWidgetAnnotation) a)
                .findFirst().orElse(null);
        assertNotNull(widget, "the signed document should carry a signature widget");

        Library library = document.getCatalog().getLibrary();
        DictionaryEntries appearance = library.getDictionary(widget.getEntries(),
                Annotation.APPEARANCE_STREAM_KEY);
        assertNotNull(appearance, "the widget should have an appearance stream");
        Object normal = library.getObject(appearance, Annotation.APPEARANCE_STREAM_NORMAL_KEY);
        assertTrue(normal instanceof Form, "the normal appearance should be a form, was " + normal);

        Form form = (Form) normal;
        form.init();
        Resources resources = form.getResources();
        assertNotNull(resources, "the appearance stream has to name the image it draws");
        for (Name name : resources.getXObjects().keySet()) {
            ImageStream imageStream = resources.getImageStream(name);
            if (imageStream != null) {
                return imageStream;
            }
        }
        return null;
    }

    private static Document reopen(File file) throws Exception {
        Document document = new Document();
        document.setFile(file.getAbsolutePath());
        return document;
    }

    @DisplayName("a transparent signature image is written with a soft mask that decodes back")
    @Test
    public void transparencySurvivesSigning() throws Exception {
        File source = new File("src/test/resources/annotation/hello_pdfa1.pdf");
        File signed = SigningFixture.of(source).withAppearance(softEdgedMark())
                .signTo(new File("./src/test/out/SignatureImageTransparencyTest_soft.pdf"));

        Document document = reopen(signed);
        try {
            ImageStream imageStream = signatureImageOf(document);
            assertNotNull(imageStream, "the appearance stream should draw an image");
            // The colour key mask this used to be written with could only say paint or do not
            // paint, so the soft edge came back hard and any white inside the mark was punched out.
            assertNull(imageStream.getEntries().get(new Name("Mask")),
                    "no colour key mask should be written any more");

            ImageStream softMask = imageStream.getImageParams().getSMaskImageStream();
            assertNotNull(softMask, "the transparency should be carried as a soft mask");

            // Decoding the image applies its soft mask, so this is the alpha a reader ends up with.
            BufferedImage decoded = imageStream.getImage(new GraphicsState(new Shapes()),
                    new Resources(document.getCatalog().getLibrary(), new DictionaryEntries()));
            assertNotNull(decoded);
            assertEquals(0xFF, (decoded.getRGB(4, 16) >> 24) & 0xFF, "the solid part stays solid");
            assertEquals(0x00, (decoded.getRGB(60, 16) >> 24) & 0xFF, "the background stays clear");
            int softEdge = (decoded.getRGB(28, 16) >> 24) & 0xFF;
            assertTrue(softEdge > 0x60 && softEdge < 0xA0,
                    "the soft edge should still be about half transparent, was " + softEdge);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an opaque signature image is written without a soft mask")
    @Test
    public void opaqueImageCarriesNoMask() throws Exception {
        // A mask saying paint every pixel is a second image the size of the first, for nothing.
        File source = new File("src/test/resources/annotation/hello_pdfa1.pdf");
        File signed = SigningFixture.of(source).withAppearance(opaqueMark())
                .signTo(new File("./src/test/out/SignatureImageTransparencyTest_opaque.pdf"));

        Document document = reopen(signed);
        try {
            ImageStream imageStream = signatureImageOf(document);
            assertNotNull(imageStream);
            assertNull(imageStream.getImageParams().getSMaskImageStream());
        } finally {
            document.dispose();
        }
    }
}
