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
package org.icepdf.core.pobjects.fonts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A TrueType/OpenType collection holds several faces in one file and has to contribute one font-list
 * entry each; registering only the first is how a Traditional Chinese document ended up drawn with a
 * Japanese face, since the CJK fonts shipped by most Linux distributions are collections.
 * <p>
 * Necessarily environment-dependent: it skips when the host has no collection installed.
 */
public class FontCollectionScanTest {

    /** True when at least one .ttc/.otc file exists under a directory the font scan walks. */
    private static boolean hasFontCollectionInstalled() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.addAll(FontManager.WINDOWS_FONT_PATHS);
        paths.addAll(FontManager.MAC_FONT_PATHS);
        paths.addAll(FontManager.LINUX_FONT_PATHS);
        for (String path : paths) {
            if (path != null && containsCollection(new java.io.File(path), 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCollection(java.io.File directory, int depth) {
        if (depth > 4 || !directory.isDirectory()) {
            return false;
        }
        java.io.File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        for (java.io.File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && (name.endsWith(".ttc") || name.endsWith(".otc"))) {
                return true;
            }
            if (file.isDirectory() && containsCollection(file, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    @DisplayName("every face of a font collection is registered, not just the first")
    @Test
    @SuppressWarnings("unchecked")
    public void collectionFacesAreAllRegistered() throws Exception {
        // Gate on the host actually having a collection to find, not on the scan having found one -
        // gating on the latter would turn "collections are ignored entirely" into a silent skip,
        // which is the very bug this guards.
        assumeTrue(hasFontCollectionInstalled(),
                "host has no .ttc/.otc font collections installed, nothing to check");

        FontManager fontManager = FontManager.getInstance();
        fontManager.readSystemFonts(null);

        Method snapshot = FontManager.class.getDeclaredMethod("snapshotFontList");
        snapshot.setAccessible(true);
        List<Object[]> fonts = (List<Object[]>) snapshot.invoke(fontManager);
        assertNotNull(fonts, "no fonts were discovered at all");

        // count how many faces each collection file contributed
        Map<String, Integer> facesPerCollection = new HashMap<>();
        for (Object[] font : fonts) {
            String path = String.valueOf(font[3]).toLowerCase();
            if (path.endsWith(".ttc") || path.endsWith(".otc")) {
                facesPerCollection.merge(path, 1, Integer::sum);
            }
        }
        assertTrue(!facesPerCollection.isEmpty(),
                "the host has font collections but none were registered: collections are being skipped");

        // a collection worth shipping holds more than one face; at least one must have contributed
        // several, or we are still registering only the first.
        int mostFaces = facesPerCollection.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(mostFaces > 1,
                "every collection contributed a single face, so only the first is being read: "
                        + facesPerCollection);

        // and the faces have to be distinguishable, or the collection can't be re-selected by name
        for (Object[] font : fonts) {
            String path = String.valueOf(font[3]).toLowerCase();
            if (facesPerCollection.getOrDefault(path, 0) > 1) {
                assertNotNull(font[0], "collection face registered without a name: " + path);
            }
        }
    }
}
