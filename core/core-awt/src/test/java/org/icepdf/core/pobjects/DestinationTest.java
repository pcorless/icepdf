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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link Destination}: the array that says which page a link goes to and how the viewer
 * should fit it there.
 * <p>
 * The array is positional - {@code [page /XYZ left top zoom]} - so every coordinate is identified
 * only by where it sits, and a fit type whose coordinates are read from the wrong slots sends the
 * reader to a plausible but wrong place on the right page.  Each of the eight fit types is checked
 * for the coordinates the specification gives it, and for the ones it does not have.
 */
public class DestinationTest {

    private final Library library = new Library();
    private final Reference pageReference = new Reference(4, 0);

    /**
     * A destination built from an explicit array, as a link's /Dest holds it.
     *
     * @param values the destination array, after the page reference
     * @return the parsed destination
     */
    private Destination destination(Object... values) {
        List<Object> array = new ArrayList<>();
        array.add(pageReference);
        array.addAll(Arrays.asList(values));
        return new Destination(library, array);
    }

    // ------------------------------------------------------------------
    // the fit types
    // ------------------------------------------------------------------

    @DisplayName("XYZ carries a left, a top and a zoom")
    @Test
    public void xyz() {
        Destination destination = destination(Destination.TYPE_XYZ, 100f, 700f, 2f);
        assertEquals(pageReference, destination.getPageReference());
        assertEquals(Destination.TYPE_XYZ, destination.getType());
        assertEquals(100f, destination.getLeft(), 0.001f);
        assertEquals(700f, destination.getTop(), 0.001f);
        assertEquals(2f, destination.getZoom(), 0.001f);
    }

    @DisplayName("XYZ - a null coordinate means leave that axis alone")
    @Test
    public void xyzNullCoordinates() {
        // Writers use null for "keep the current value"; reading it as a number would jump the
        // view to the origin instead of staying put.
        Destination destination = destination(Destination.TYPE_XYZ, "null", "null", "null");
        assertNull(destination.getLeft());
        assertNull(destination.getTop());
        assertNull(destination.getZoom());
    }

    @DisplayName("XYZ - a zoom of zero is no zoom change, not a zoom of zero")
    @Test
    public void xyzZeroZoom() {
        // Zooming to zero would scale the page out of existence.
        assertNull(destination(Destination.TYPE_XYZ, 10f, 20f, "0").getZoom());
    }

    @DisplayName("Fit has no coordinates at all")
    @Test
    public void fit() {
        Destination destination = destination(Destination.TYPE_FIT);
        assertEquals(Destination.TYPE_FIT, destination.getType());
        assertEquals(pageReference, destination.getPageReference());
        assertNull(destination.getLeft());
        assertNull(destination.getTop());
    }

    @DisplayName("FitH carries a top")
    @Test
    public void fitH() {
        Destination destination = destination(Destination.TYPE_FITH, 700f);
        assertEquals(700f, destination.getTop(), 0.001f);
        assertNull(destination.getLeft());
    }

    @DisplayName("FitR carries a whole rectangle")
    @Test
    public void fitR() {
        // [page /FitR left bottom right top] - four numbers whose order is easy to transpose.
        Destination destination = destination(Destination.TYPE_FITR, 10f, 20f, 300f, 400f);
        assertEquals(10f, destination.getLeft(), 0.001f);
        assertEquals(20f, destination.getBottom(), 0.001f);
        assertEquals(300f, destination.getRight(), 0.001f);
        assertEquals(400f, destination.getTop(), 0.001f);
    }

    @DisplayName("FitB has no coordinates")
    @Test
    public void fitB() {
        Destination destination = destination(Destination.TYPE_FITB);
        assertEquals(Destination.TYPE_FITB, destination.getType());
        assertNull(destination.getTop());
    }

    @DisplayName("FitBH carries a top")
    @Test
    public void fitBH() {
        assertEquals(650f, destination(Destination.TYPE_FITBH, 650f).getTop(), 0.001f);
    }

    @DisplayName("FitBV carries a left")
    @Test
    public void fitBV() {
        assertEquals(50f, destination(Destination.TYPE_FITBV, 50f).getLeft(), 0.001f);
    }

    @DisplayName("FitV carries a left")
    @Test
    public void fitV() {
        // [page /FitV left] - the vertical counterpart of FitH.
        assertEquals(50f, destination(Destination.TYPE_FITV, 50f).getLeft(), 0.001f);
    }

    // ------------------------------------------------------------------
    // malformed arrays
    // ------------------------------------------------------------------

    @DisplayName("a destination array shorter than its type needs does not throw")
    @Test
    public void truncatedArray() {
        // Damaged files run out of numbers part way through; the page reference is still usable.
        Destination destination = destination(Destination.TYPE_XYZ, 100f);
        assertEquals(pageReference, destination.getPageReference());
        assertEquals(100f, destination.getLeft(), 0.001f);
        assertNull(destination.getTop());
    }

    @DisplayName("an array holding only a page still names the page")
    @Test
    public void pageOnly() {
        Destination destination = destination();
        assertEquals(pageReference, destination.getPageReference());
        assertNull(destination.getType());
    }

    @DisplayName("a fit type written as a string rather than a name is still recognised")
    @Test
    public void typeAsString() {
        assertEquals(Destination.TYPE_FIT, destination("Fit").getType());
    }

    // ------------------------------------------------------------------
    // named destinations
    // ------------------------------------------------------------------

    @DisplayName("a destination given by name reports that name")
    @Test
    public void namedDestination() {
        Destination destination = new Destination(library, new LiteralStringObject("chapter.1"));
        assertEquals("chapter.1", destination.getNamedDestination());
    }

    @DisplayName("a named destination can be replaced by an explicit one")
    @Test
    public void clearNamedDestination() {
        Destination destination = new Destination(library, new LiteralStringObject("chapter.1"));
        assertNotNull(destination.getNamedDestination());
        destination.clearNamedDestination();
        assertNull(destination.getNamedDestination());
    }

    @DisplayName("setting a named destination replaces whatever was there")
    @Test
    public void setNamedDestination() {
        Destination destination = destination(Destination.TYPE_FIT);
        destination.setNamedDestination("chapter.2");
        assertEquals("chapter.2", destination.getNamedDestination());
    }

    // ------------------------------------------------------------------
    // building destination arrays
    // ------------------------------------------------------------------

    @DisplayName("the destination syntax helpers build the array each fit type expects")
    @Test
    public void destinationSyntax() {
        // These are what the library writes when a destination is created or edited, so the number
        // of elements and their order is what a reader will have to parse back.
        assertEquals(2, Destination.destinationSyntax(pageReference, Destination.TYPE_FIT).size());
        assertEquals(3, Destination.destinationSyntax(pageReference, Destination.TYPE_FITH, 700f).size());
        assertEquals(5, Destination.destinationSyntax(pageReference, Destination.TYPE_XYZ,
                100f, 700f, 1f).size());
        assertEquals(6, Destination.destinationSyntax(pageReference, Destination.TYPE_FITR,
                10f, 20f, 300f, 400f).size());
    }

    @DisplayName("a rebuilt destination array parses back to the same values")
    @Test
    public void resetDestArrayRoundTrip() {
        Destination destination = destination(Destination.TYPE_FIT);
        destination.resetDestArray(pageReference, Destination.TYPE_XYZ, 55f, 660f, 2f);

        Destination reparsed = new Destination(library, destination.getRawListDestination());
        assertEquals(pageReference, reparsed.getPageReference());
        assertEquals(Destination.TYPE_XYZ, reparsed.getType());
        assertEquals(55f, reparsed.getLeft(), 0.001f);
        assertEquals(660f, reparsed.getTop(), 0.001f);
    }

    @DisplayName("setLocation moves an existing destination")
    @Test
    public void setLocation() {
        Destination destination = destination(Destination.TYPE_XYZ, 100f, 700f, 1f);
        destination.setLocation(200f, 500f);
        assertEquals(200f, destination.getLeft(), 0.001f);
        assertEquals(500f, destination.getTop(), 0.001f);
    }
}
