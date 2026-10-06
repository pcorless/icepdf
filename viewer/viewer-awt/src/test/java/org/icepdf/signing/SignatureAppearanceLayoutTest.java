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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceLayout;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.ViewerPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the signature appearance puts things.
 * <p>
 * The image and the text were placed without reference to each other - the image from the left edge
 * at whatever size it asked for, the text right aligned at whatever width it needed - so a large
 * image and a long name were drawn over one another.  Nothing failed when they did; the appearance
 * simply came out unreadable.
 * <p>
 * Read off the generated content stream rather than off a rendering, because that is where the
 * placement decision actually lands, and a pixel comparison of overlapping text would need a
 * threshold to call it.
 */
public class SignatureAppearanceLayoutTest {

    /** The bounding box {@link SigningFixture} gives the signature widget. */
    private static final float BBOX_WIDTH = 375;

    /** Matches the image placement: {@code w 0 0 -h x y cm} in front of the image's {@code Do}. */
    private static final Pattern IMAGE_PLACEMENT = Pattern.compile(
            "q\\s+(-?[\\d.]+)\\s+[-\\d.]+\\s+[-\\d.]+\\s+(-?[\\d.]+)\\s+(-?[\\d.]+)\\s+(-?[\\d.]+)\\s+cm\\s*"
                    + "/\\S+\\s+Do");

    /** Matches the translation that positions the text block, which follows the image's {@code Q}. */
    private static final Pattern TEXT_PLACEMENT = Pattern.compile(
            "1\\.0 0\\.0 0\\.0 1\\.0 (-?[\\d.]+) (-?[\\d.]+) cm");

    /** Matches the size the text is actually set at. */
    private static final Pattern FONT_SIZE = Pattern.compile("/\\S+ ([\\d.]+) Tf");

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    /** A wide mark on transparency, which at its natural size would cover the whole appearance. */
    private static BufferedImage wideMark() {
        BufferedImage image = new BufferedImage(400, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(20, 30, 120));
        GeneralPath path = new GeneralPath();
        path.moveTo(20, 120);
        path.curveTo(60, 20, 110, 150, 150, 60);
        path.curveTo(190, 0, 210, 140, 250, 90);
        path.curveTo(290, 40, 330, 130, 380, 40);
        g2d.draw(path);
        g2d.dispose();
        return image;
    }

    /**
     * Signs with the given signer name and hands back the appearance stream's content.
     */
    private String appearanceStream(String tag, String signerName) throws Exception {
        return appearanceStream(tag, signerName, SignatureAppearanceLayout.SIDE_BY_SIDE);
    }

    private String appearanceStream(String tag, String signerName,
                                    SignatureAppearanceLayout layout) throws Exception {
        SigningFixture fixture = SigningFixture
                .of(new File("src/test/resources/annotation/hello_pdfa1.pdf"))
                .withAppearance(wideMark()).layout(layout);
        if (signerName != null) {
            fixture.signerName(signerName);
        }
        File signed = fixture.signTo(
                new File("./src/test/out/SignatureAppearanceLayoutTest_" + tag + ".pdf"));

        Document document = new Document();
        document.setFile(signed.getAbsolutePath());
        try {
            Library library = document.getCatalog().getLibrary();
            SignatureWidgetAnnotation widget = (SignatureWidgetAnnotation)
                    document.getPageTree().getPage(0).getAnnotations().stream()
                            .filter(a -> a instanceof SignatureWidgetAnnotation)
                            .findFirst().orElseThrow(() ->
                                    new IllegalStateException("no signature widget was written"));
            DictionaryEntries appearance = library.getDictionary(widget.getEntries(),
                    Annotation.APPEARANCE_STREAM_KEY);
            Form form = (Form) library.getObject(appearance,
                    Annotation.APPEARANCE_STREAM_NORMAL_KEY);
            return new String(form.getDecodedStreamBytes(0), StandardCharsets.ISO_8859_1);
        } finally {
            document.dispose();
        }
    }

    private static float group(Pattern pattern, String content, int group, String what) {
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find(), what + " should be in the appearance stream:\n" + content);
        return Float.parseFloat(matcher.group(group));
    }

    private static void assertImageIsLeftOfTheText(String content) {
        Matcher image = IMAGE_PLACEMENT.matcher(content);
        assertTrue(image.find(), "the appearance should draw the signature image:\n" + content);
        float imageLeft = Float.parseFloat(image.group(3));
        float imageRight = imageLeft + Float.parseFloat(image.group(1));

        // The text block's own translation comes after the image's, so search from there.
        Matcher text = TEXT_PLACEMENT.matcher(content);
        assertTrue(text.find(image.end()), "the appearance should draw the signature text:\n" + content);
        float textLeft = Float.parseFloat(text.group(1));

        assertTrue(imageLeft >= 0 && imageRight <= BBOX_WIDTH,
                "the image should stay inside the bounding box, was " + imageLeft + ".." + imageRight);
        assertTrue(textLeft >= imageRight,
                "the text should start after the image ends, but the image runs to " + imageRight
                        + " and the text starts at " + textLeft);
    }

    @DisplayName("the image and the text are placed in separate columns")
    @Test
    public void imageAndTextDoNotOverlap() throws Exception {
        // The image used to be drawn from the left edge at its own scale and the text right aligned
        // at its own width, neither knowing about the other, so a wide image ran under the text.
        assertImageIsLeftOfTheText(appearanceStream("columns", null));
    }

    @DisplayName("the font size the signer chose is the font size that is drawn")
    @Test
    public void chosenFontSizeIsHonoured() throws Exception {
        // This was laid out by shrinking the text until it fitted the column left over beside the
        // image, so every size past the point the text filled that column produced the same drawn
        // size as the one before it - and the dialog's font size control did nothing at all for a
        // signer whose name and contact details were long.
        String key = ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_SIZE;
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        String longSigner = "Alexandra Constantinopoulos-Wetherby";
        try {
            for (int chosen : new int[]{6, 12, 24}) {
                preferences.putInt(key, chosen);
                String content = appearanceStream("size" + chosen, longSigner);
                assertEquals(chosen, group(FONT_SIZE, content, 1, "the font size"), 0.01,
                        "the appearance should be drawn at the size that was chosen");
            }
        } finally {
            // A real stored setting of the signer's, not the test's.
            preferences.remove(key);
        }
    }

    @DisplayName("the image gives up width to the text rather than the text being made to fit")
    @Test
    public void imageYieldsWidthToLongerText() throws Exception {
        // What the image no longer taking a guaranteed share buys: the text keeps its size, and the
        // column beside it narrows instead.
        float besideShortText = imageWidth(appearanceStream("short", "Ann Lee"));
        float besideLongText = imageWidth(appearanceStream("longer",
                "Alexandra Constantinopoulos-Wetherby of Llanfairpwllgwyngyll"));

        assertTrue(besideLongText < besideShortText,
                "the image should have given up width, but " + besideLongText
                        + " was not less than " + besideShortText);
    }

    @DisplayName("the width the image is allowed is a setting, not a constant")
    @Test
    public void imageWidthFollowsItsPreference() throws Exception {
        // The layout limits used to be constants on the callback, reachable only by subclassing it.
        ViewerPropertiesManager properties = ViewerPropertiesManager.getInstance();
        String key = ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_WIDTH_MAX;
        float wide;
        float narrow;
        try {
            properties.getPreferences().putInt(key, 60);
            wide = imageWidth(appearanceStream("wide", null));
            properties.getPreferences().putInt(key, 30);
            narrow = imageWidth(appearanceStream("narrow", null));
        } finally {
            // A real stored setting of the signer's, not the test's.
            properties.getPreferences().remove(key);
        }
        assertTrue(narrow < wide, "a smaller maximum should draw a smaller image, but "
                + narrow + " was not less than " + wide);
    }

    private static float imageWidth(String content) {
        Matcher image = IMAGE_PLACEMENT.matcher(content);
        assertTrue(image.find(), "the appearance should draw the signature image:\n" + content);
        return Float.parseFloat(image.group(1));
    }

    @DisplayName("an overlay gives the image the whole field and draws the text over it")
    @Test
    public void overlayShareTheWidth() throws Exception {
        String content = appearanceStream("overlay", null, SignatureAppearanceLayout.OVERLAY);

        Matcher image = IMAGE_PLACEMENT.matcher(content);
        assertTrue(image.find(), "the appearance should draw the signature image:\n" + content);
        float imageLeft = Float.parseFloat(image.group(3));
        float imageRight = imageLeft + Float.parseFloat(image.group(1));
        Matcher text = TEXT_PLACEMENT.matcher(content);
        assertTrue(text.find(image.end()), "the appearance should draw the signature text:\n" + content);
        float textLeft = Float.parseFloat(text.group(1));

        // The point of this layout: the mark is not cut down to the share left over beside the text.
        assertTrue(imageRight > BBOX_WIDTH * 0.9f,
                "the image should span the field, but ran only to " + imageRight);
        assertTrue(textLeft < imageRight,
                "the text should be drawn over the image, not beside it");
        // Drawn before the text, so it ends up underneath it rather than over it.
        assertTrue(image.end() < text.start(), "the image should be painted under the text");
    }

    @DisplayName("the image is bigger under an overlay than beside the text")
    @Test
    public void overlayGivesTheMarkMoreRoom() throws Exception {
        float beside = imageWidth(appearanceStream("beside", null));
        float over = imageWidth(appearanceStream("over", null, SignatureAppearanceLayout.OVERLAY));

        assertTrue(over > beside, "an overlay should draw the mark larger, but "
                + over + " was not more than " + beside);
    }

    @DisplayName("a detail the certificate does not carry is left out, not written as the word null")
    @Test
    public void missingDetailsAreNotDrawnAsNull() throws Exception {
        // The lines were formatted straight through MessageFormat, so a certificate with no contact
        // address put "Contact: null" under the signature.
        String content = appearanceStream("nulls", null);

        Matcher text = Pattern.compile("\\((.*?)\\)] TJ").matcher(content);
        boolean drewSomething = false;
        while (text.find()) {
            drewSomething = true;
            assertFalse(text.group(1).contains("null"),
                    "a line with no value should not be drawn at all, but this was: " + text.group(1));
        }
        assertTrue(drewSomething, "the appearance should still draw its text:\n" + content);
    }
}
