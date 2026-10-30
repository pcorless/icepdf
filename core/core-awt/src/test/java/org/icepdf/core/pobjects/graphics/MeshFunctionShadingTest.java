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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mesh shadings (types 4-7) with a Function interpolate the parametric value t across a triangle
 * or patch and apply the function afterwards (PDF 32000-1 §8.7.4.5.5).  They used to convert each
 * vertex to a colour first and interpolate the colours, which skips whatever the function does in
 * between: a red -> green -> blue function across one cell rendered red -> purple -> blue.
 * <p>
 * The fixture ({@code shading/mesh_fixture.pdf}, built by {@code make_mesh_fixture.py}) paints one
 * shading per page inside x 20..380, y 40..280 of a 400x300 page; pages 2-4 run t from 0 at the
 * left to 1 at the right.  The expected colours are poppler's and Ghostscript's, which agree.
 */
public class MeshFunctionShadingTest {

    private static Document document;

    @BeforeAll
    static void open() throws Exception {
        document = new Document();
        document.setFile(Paths.get(Objects.requireNonNull(
                MeshFunctionShadingTest.class.getResource("/shading/mesh_fixture.pdf")).toURI()).toString());
    }

    @AfterAll
    static void close() {
        document.dispose();
    }

    private static BufferedImage render(int pageIndex) throws Exception {
        return (BufferedImage) document.getPageImage(pageIndex, GraphicsRenderingHints.PRINT,
                Page.BOUNDARY_CROPBOX, 0f, 1f);
    }

    /** The pixel at page-space (x, y), y up. */
    private static int[] rgb(BufferedImage image, int x, int y) {
        int c = image.getRGB(x, image.getHeight() - 1 - y);
        return new int[]{(c >> 16) & 255, (c >> 8) & 255, c & 255};
    }

    private static void assertNear(int[] expected, int[] actual, int tolerance, String where) {
        for (int i = 0; i < 3; i++) {
            assertTrue(Math.abs(expected[i] - actual[i]) <= tolerance,
                    where + ": expected ~(" + expected[0] + "," + expected[1] + "," + expected[2] + ") but was ("
                            + actual[0] + "," + actual[1] + "," + actual[2] + ")");
        }
    }

    @DisplayName("a Function mesh (types 4, 5 and 6) applies the function to the interpolated t")
    @ParameterizedTest(name = "page {0}")
    @ValueSource(ints = {2, 3, 4})
    void functionAppliedToInterpolatedT(int page) throws Exception {
        BufferedImage image = render(page - 1);
        // the middle of the function is green; interpolating the end colours gave (127, 0, 128).
        assertNear(new int[]{0, 255, 0}, rgb(image, 200, 160), 6, "middle");
        assertNear(new int[]{128, 128, 0}, rgb(image, 110, 160), 8, "quarter");
        assertNear(new int[]{0, 128, 128}, rgb(image, 290, 160), 8, "three quarters");
    }

    @DisplayName("across the whole width the mesh follows the function exactly")
    @Test
    void followsTheFunction() throws Exception {
        BufferedImage mesh = render(1);
        for (int x = 30; x <= 370; x += 20) {
            // pixel centre to t over the shading's 20..380, then the stitched red -> green -> blue.
            double t = (x + 0.5 - 20) / 360.0;
            double s = t < 0.5 ? t * 2 : (t - 0.5) * 2;
            int[] expected = t < 0.5
                    ? new int[]{(int) Math.round(255 * (1 - s)), (int) Math.round(255 * s), 0}
                    : new int[]{0, (int) Math.round(255 * (1 - s)), (int) Math.round(255 * s)};
            assertNear(expected, rgb(mesh, x, 160), 4, "x=" + x);
        }
    }

    @DisplayName("a type 5 lattice puts each vertex's colour at its vertex")
    @Test
    void type5Lattice() throws Exception {
        BufferedImage image = render(4);
        // inset a few pixels from the corner vertices: red, cyan, blue, dark green.
        assertNear(new int[]{255, 0, 0}, rgb(image, 22, 42), 20, "bottom left");
        assertNear(new int[]{0, 255, 255}, rgb(image, 377, 42), 20, "bottom right");
        assertNear(new int[]{0, 0, 255}, rgb(image, 22, 277), 20, "top left");
        assertNear(new int[]{0, 128, 0}, rgb(image, 377, 277), 20, "top right");
        // the middle row: grey under vertex (140, 160), black under (260, 160).
        assertNear(new int[]{128, 128, 128}, rgb(image, 140, 160), 6, "grey vertex");
        assertNear(new int[]{0, 0, 0}, rgb(image, 260, 160), 6, "black vertex");
    }
}
