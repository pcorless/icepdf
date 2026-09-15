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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a page's geometry: the boxes that say how big it is, and the rotation it is shown at.
 * <p>
 * Both are inheritable, so the answer for a page may live several levels up the page tree, and both
 * have defaults that apply when nothing in the tree says otherwise.  Everything that draws or
 * measures the page reads these, so a wrong answer is not a localised fault - it moves or resizes
 * the whole page, and the annotations and text positions that are computed against it.
 * <p>
 * Rotation is the subtler half.  The file states it clockwise and Java2D wants it counter
 * clockwise, so the value is normalised on the way out; a normalisation that is not idempotent
 * gives a different answer the second time it is asked, which is a whole page flipped.
 */
public class PageGeometryTest {

    private final Library library = new Library();
    private int nextObjectNumber = 1;

    private Reference register(Object object) {
        Reference reference = new Reference(nextObjectNumber++, 0);
        library.addObject(object, reference);
        return reference;
    }

    /**
     * A rectangle as a PDF array, lower left then upper right.
     */
    private static List<Object> box(float x1, float y1, float x2, float y2) {
        return new ArrayList<>(Arrays.asList(x1, y1, x2, y2));
    }

    /**
     * A page whose dictionary holds exactly the entries given, in key/value pairs.
     */
    private Page page(Object... keysAndValues) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, Page.TYPE);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entries.put((Name) keysAndValues[i], keysAndValues[i + 1]);
        }
        return new Page(library, entries);
    }

    /**
     * A page whose parent page tree holds the given entries, so inheritance has somewhere to look.
     *
     * @param parentEntries entries of the parent page tree, in key/value pairs
     * @param pageEntries   entries of the page itself, in key/value pairs
     * @return the page, with its parent registered and reachable
     */
    private Page pageUnder(Object[] parentEntries, Object... pageEntries) {
        DictionaryEntries parent = new DictionaryEntries();
        parent.put(Dictionary.TYPE_KEY, PageTree.TYPE);
        // a page tree reads its inheritable values during init, which wants a countable tree
        parent.put(PageTree.COUNT_KEY, 1);
        parent.put(PageTree.KIDS_KEY, new ArrayList<>());
        for (int i = 0; i < parentEntries.length; i += 2) {
            parent.put((Name) parentEntries[i], parentEntries[i + 1]);
        }
        PageTree pageTree = new PageTree(library, parent);
        pageTree.init();
        Reference parentReference = register(pageTree);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, Page.TYPE);
        entries.put(Page.PARENT_KEY, parentReference);
        for (int i = 0; i < pageEntries.length; i += 2) {
            entries.put((Name) pageEntries[i], pageEntries[i + 1]);
        }
        return new Page(library, entries);
    }

    // ------------------------------------------------------------------
    // the media box
    // ------------------------------------------------------------------

    @DisplayName("a page's own media box is used as given")
    @Test
    public void mediaBox() {
        Rectangle2D.Float mediaBox = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400)).getMediaBox();
        assertEquals(0f, mediaBox.x, 0.001);
        assertEquals(200f, mediaBox.width, 0.001);
        assertEquals(400f, mediaBox.height, 0.001);
    }

    @DisplayName("the box's y is its top edge, not its bottom")
    @Test
    public void boxYIsTheTopEdge() {
        // A PDF box is [lower-left upper-right] in a space where y increases upwards, and the
        // rectangle that comes back keeps that space: y is the larger of the two, not the smaller.
        // Reading it as a Java2D rectangle's upper-left y - which is what the type suggests, and
        // what the same field means once the page is mapped to the screen - flips everything
        // positioned against it.
        Rectangle2D.Float mediaBox = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400)).getMediaBox();
        assertEquals(400f, mediaBox.y, 0.001, "y is the top of the box");
        assertEquals(0f, mediaBox.y - mediaBox.height, 0.001, "and y minus the height is the bottom");
    }

    @DisplayName("a media box with a non-zero origin keeps its offset")
    @Test
    public void mediaBoxWithOffsetOrigin() {
        // A box need not start at the origin, and treating it as though it did shifts everything
        // drawn on the page by the offset.
        Rectangle2D.Float mediaBox = page(Page.MEDIABOX_KEY, box(10, 20, 210, 420)).getMediaBox();
        assertEquals(10f, mediaBox.x, 0.001);
        assertEquals(420f, mediaBox.y, 0.001);
        assertEquals(200f, mediaBox.width, 0.001);
        assertEquals(400f, mediaBox.height, 0.001);
    }

    @DisplayName("a media box is inherited from the page tree when the page has none")
    @Test
    public void mediaBoxIsInherited() {
        Page page = pageUnder(new Object[]{Page.MEDIABOX_KEY, box(0, 0, 300, 500)});
        assertEquals(300f, page.getMediaBox().width, 0.001);
        assertEquals(500f, page.getMediaBox().height, 0.001);
    }

    @DisplayName("a page's own media box wins over an inherited one")
    @Test
    public void ownMediaBoxWins() {
        Page page = pageUnder(new Object[]{Page.MEDIABOX_KEY, box(0, 0, 300, 500)},
                Page.MEDIABOX_KEY, box(0, 0, 200, 400));
        assertEquals(200f, page.getMediaBox().width, 0.001);
    }

    @DisplayName("a page with no media box anywhere falls back to US Letter")
    @Test
    public void mediaBoxDefault() {
        // /MediaBox is required, so reaching this means the file is wrong; a default page is a
        // better answer than no page.
        Rectangle2D.Float mediaBox = page().getMediaBox();
        assertEquals(612f, mediaBox.width, 0.001);
        assertEquals(792f, mediaBox.height, 0.001);
    }

    // ------------------------------------------------------------------
    // the crop box and the boxes that default to it
    // ------------------------------------------------------------------

    @DisplayName("a page with no crop box crops to its media box")
    @Test
    public void cropBoxDefaultsToMediaBox() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400));
        assertEquals(200f, page.getCropBox().width, 0.001);
        assertEquals(400f, page.getCropBox().height, 0.001);
    }

    @DisplayName("a crop box smaller than the media box is used as given")
    @Test
    public void cropBoxSmallerThanMedia() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(10, 10, 190, 390));
        assertEquals(180f, page.getCropBox().width, 0.001);
        assertEquals(380f, page.getCropBox().height, 0.001);
    }

    @DisplayName("a crop box larger than the media box is clipped to it")
    @Test
    public void cropBoxClippedToMediaBox() {
        // A crop box is defined to be the visible region, and nothing outside the media box is
        // visible, so a file claiming more gets the intersection.
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(-100, -100, 500, 900));
        assertTrue(page.getCropBox().width <= 200f,
                "the crop box should not be wider than the media box, was " + page.getCropBox().width);
        assertTrue(page.getCropBox().height <= 400f,
                "the crop box should not be taller than the media box, was " + page.getCropBox().height);
    }

    @DisplayName("the trim, bleed and art boxes default to the crop box")
    @Test
    public void optionalBoxesDefaultToCropBox() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(10, 10, 190, 390));
        for (Rectangle2D.Float optional :
                new Rectangle2D.Float[]{page.getTrimBox(), page.getBleedBox(), page.getArtBox()}) {
            assertEquals(180f, optional.width, 0.001);
            assertEquals(380f, optional.height, 0.001);
        }
    }

    @DisplayName("the trim, bleed and art boxes are used when the page gives them")
    @Test
    public void optionalBoxesAsGiven() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.TRIMBOX_KEY, box(5, 5, 105, 205),
                Page.BLEEDBOX_KEY, box(0, 0, 150, 250),
                Page.ARTBOX_KEY, box(20, 20, 120, 220));
        assertEquals(100f, page.getTrimBox().width, 0.001);
        assertEquals(150f, page.getBleedBox().width, 0.001);
        assertEquals(100f, page.getArtBox().width, 0.001);
        assertEquals(200f, page.getArtBox().height, 0.001);
    }

    @DisplayName("each box is computed once and answers the same every time")
    @Test
    public void boxesAreStable() {
        // The boxes are cached in fields on first use.  Asking twice has to give the same answer:
        // a page that reports one size and then another resizes under whatever is drawing it.
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(-100, -100, 500, 900));
        assertEquals(page.getMediaBox(), page.getMediaBox());
        assertEquals(page.getCropBox(), page.getCropBox());
        assertEquals(page.getTrimBox(), page.getTrimBox());
        assertEquals(page.getBleedBox(), page.getBleedBox());
        assertEquals(page.getArtBox(), page.getArtBox());
    }

    // ------------------------------------------------------------------
    // asking for a box by name
    // ------------------------------------------------------------------

    @DisplayName("getPageBoundary returns the box each constant names")
    @Test
    public void pageBoundaryByConstant() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(10, 10, 190, 390),
                Page.TRIMBOX_KEY, box(5, 5, 105, 205));
        assertEquals(200f, page.getPageBoundary(Page.BOUNDARY_MEDIABOX).width, 0.001);
        assertEquals(180f, page.getPageBoundary(Page.BOUNDARY_CROPBOX).width, 0.001);
        assertEquals(100f, page.getPageBoundary(Page.BOUNDARY_TRIMBOX).width, 0.001);
        assertEquals(180f, page.getPageBoundary(Page.BOUNDARY_BLEEDBOX).width, 0.001);
        assertEquals(180f, page.getPageBoundary(Page.BOUNDARY_ARTBOX).width, 0.001);
    }

    @DisplayName("getPageBoundary falls back to the crop box for a constant it does not know")
    @Test
    public void pageBoundaryUnknownConstant() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.CROPBOX_KEY, box(10, 10, 190, 390));
        assertEquals(180f, page.getPageBoundary(-99).width, 0.001);
    }

    // ------------------------------------------------------------------
    // rotation
    // ------------------------------------------------------------------

    @DisplayName("rotation is turned from the file's clockwise into Java2D's counter clockwise")
    @Test
    public void rotationIsNormalised() {
        // /Rotate is clockwise in the file and Java2D turns the other way, so the two are mirrored
        // about zero: 90 in the file is 270 here.  Reading it straight through would rotate every
        // landscape page the wrong way.
        assertEquals(0f, page(Page.ROTATE_KEY, 0).getPageRotation(), 0.001);
        assertEquals(270f, page(Page.ROTATE_KEY, 90).getPageRotation(), 0.001);
        assertEquals(180f, page(Page.ROTATE_KEY, 180).getPageRotation(), 0.001);
        assertEquals(90f, page(Page.ROTATE_KEY, 270).getPageRotation(), 0.001);
    }

    @DisplayName("a page with no rotation is not rotated")
    @Test
    public void noRotation() {
        assertEquals(0f, page().getPageRotation(), 0.001);
    }

    @DisplayName("asking for the rotation twice gives the same answer")
    @Test
    public void rotationIsIdempotent() {
        // The normalisation is a subtraction from 360, which is its own inverse: applying it twice
        // turns a /Rotate 90 page's 270 back into 90, a half turn out.  This is asked on every
        // paint, from several threads at once, so it has to be computable any number of times.
        Page page = page(Page.ROTATE_KEY, 90);
        float first = page.getPageRotation();
        for (int i = 0; i < 5; i++) {
            assertEquals(first, page.getPageRotation(), 0.001,
                    "the rotation changed when it was asked again");
        }
        assertEquals(270f, first, 0.001);
    }

    @DisplayName("rotation is inherited from the page tree")
    @Test
    public void rotationIsInherited() {
        Page page = pageUnder(new Object[]{Page.ROTATE_KEY, 90});
        assertEquals(270f, page.getPageRotation(), 0.001);
    }

    @DisplayName("a page's own rotation wins over an inherited one")
    @Test
    public void ownRotationWins() {
        Page page = pageUnder(new Object[]{Page.ROTATE_KEY, 90}, Page.ROTATE_KEY, 180);
        assertEquals(180f, page.getPageRotation(), 0.001);
    }

    // ------------------------------------------------------------------
    // rotation combined with the viewer's own
    // ------------------------------------------------------------------

    @DisplayName("with no user rotation the total is the page's own")
    @Test
    public void totalRotationWithoutUserRotation() {
        assertEquals(page(Page.ROTATE_KEY, 90).getPageRotation(),
                page(Page.ROTATE_KEY, 90).getTotalRotation(0), 0.001);
    }

    @DisplayName("the viewer's rotation is added to the page's")
    @Test
    public void totalRotationAddsUserRotation() {
        Page page = page(Page.ROTATE_KEY, 0);
        assertEquals(90f, page.getTotalRotation(90), 0.001);
        assertEquals(180f, page.getTotalRotation(180), 0.001);
        assertEquals(270f, page.getTotalRotation(270), 0.001);
    }

    @DisplayName("a total beyond a full turn wraps back into range")
    @Test
    public void totalRotationWraps() {
        Page page = page(Page.ROTATE_KEY, 0);
        assertEquals(0f, page.getTotalRotation(360), 0.001);
        assertEquals(90f, page.getTotalRotation(450), 0.001);
        assertEquals(0f, page.getTotalRotation(720), 0.001);
    }

    @DisplayName("a negative rotation wraps forwards rather than staying negative")
    @Test
    public void totalRotationOfNegativeUserRotation() {
        // The viewer turns a page anticlockwise by subtracting; a negative total would be compared
        // against 90, 180 and 270 and match none of them, taking the slow general path.
        Page page = page(Page.ROTATE_KEY, 0);
        assertEquals(270f, page.getTotalRotation(-90), 0.001);
        assertEquals(180f, page.getTotalRotation(-180), 0.001);
        assertEquals(90f, page.getTotalRotation(-270), 0.001);
    }

    @DisplayName("a rotation a hair off a right angle is snapped to it")
    @Test
    public void totalRotationSnapsToRightAngles() {
        // A rotation arrived at through radians lands fractionally off, and the fast paths for the
        // four right angles are chosen by equality.
        Page page = page(Page.ROTATE_KEY, 0);
        assertEquals(90f, page.getTotalRotation(89.9999f), 0.001);
        assertEquals(180f, page.getTotalRotation(180.0005f), 0.001);
        assertEquals(270f, page.getTotalRotation(269.9995f), 0.001);
        assertEquals(0f, page.getTotalRotation(0.0005f), 0.001);
    }

    // ------------------------------------------------------------------
    // the size that follows from the two
    // ------------------------------------------------------------------

    @DisplayName("an unrotated page is the size of its crop box")
    @Test
    public void sizeOfAnUnrotatedPage() {
        PDimension size = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400)).getSize(0);
        assertEquals(200, size.getWidth(), 0.001);
        assertEquals(400, size.getHeight(), 0.001);
    }

    @DisplayName("a quarter turn swaps the page's width and height")
    @Test
    public void sizeOfARotatedPage() {
        PDimension size = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400)).getSize(90);
        assertEquals(400, size.getWidth(), 0.001);
        assertEquals(200, size.getHeight(), 0.001);
    }

    @DisplayName("a half turn leaves the page the same size")
    @Test
    public void sizeOfAHalfTurnedPage() {
        PDimension size = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400)).getSize(180);
        assertEquals(200, size.getWidth(), 0.001);
        assertEquals(400, size.getHeight(), 0.001);
    }

    @DisplayName("zoom scales the page's size")
    @Test
    public void sizeAtZoom() {
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400));
        PDimension size = page.getSize(Page.BOUNDARY_CROPBOX, 0, 2f);
        assertEquals(400, size.getWidth(), 0.001);
        assertEquals(800, size.getHeight(), 0.001);
    }

    @DisplayName("a page rotated in the file is already turned before the viewer asks")
    @Test
    public void sizeOfAPageRotatedInTheFile() {
        // A landscape page is usually a portrait media box with /Rotate 90, so its size has to come
        // back turned even when the viewer has not asked for any rotation of its own.
        PDimension size = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400),
                Page.ROTATE_KEY, 90).getSize(0);
        assertEquals(400, size.getWidth(), 0.001);
        assertEquals(200, size.getHeight(), 0.001);
    }

    @DisplayName("the page transform is built for a page and is invertible")
    @Test
    public void pageTransform() {
        // Every coordinate shown to a user, and every one taken from a click, goes through this
        // both ways, so it has to be reversible.
        Page page = page(Page.MEDIABOX_KEY, box(0, 0, 200, 400));
        java.awt.geom.AffineTransform transform =
                page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1f);
        assertNotNull(transform);
        assertTrue(Math.abs(transform.getDeterminant()) > 0.0001,
                "a page transform that cannot be inverted loses every coordinate through it");
    }
}
