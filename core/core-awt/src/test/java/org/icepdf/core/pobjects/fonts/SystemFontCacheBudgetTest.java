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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The system font cache keeps a byte budget, least recently used first out, on top of its soft
 * references: a font past the budget is read again; one inside it is the same instance.
 */
public class SystemFontCacheBudgetTest {

    private static final Path FONTS = Path.of("../core-fonts/src/main/resources/org/icepdf/core/fonts");

    @TempDir
    Path dir;

    @AfterEach
    public void restoreBudget() {
        FontFactory.setSystemFontCacheBudget(Long.getLong(FontFactory.CACHE_BUDGET_PROPERTY,
                Runtime.getRuntime().maxMemory() / 16 >> 20) << 20);
    }

    /** A copy of a bundled font under a path no other test has loaded, so the cache starts cold for it. */
    private File font(String name) throws Exception {
        return Files.copy(FONTS.resolve(name), dir.resolve(name)).toFile();
    }

    private static FontFile load(File file) {
        FontFile font = FontFactory.getInstance().createFontFile(file, FontFactory.FONT_TRUE_TYPE, null);
        assertNotNull(font, file.getName());
        return font;
    }

    @Test
    public void budgetDropsLeastRecentlyUsedFont() throws Exception {
        File a = font("Roboto-Regular.ttf");
        File b = font("Roboto-Bold.ttf");
        File c = font("Roboto-Italic.ttf");
        // each is charged twice its size: room for the two most recent, whatever else is cached.
        long charge = 2 * Math.max(a.length(), Math.max(b.length(), c.length()));
        FontFactory.setSystemFontCacheBudget(2 * charge + 1);

        FontFile fontA = load(a);
        FontFile fontB = load(b);
        assertSame(fontA, load(a), "a cached font is the same instance");
        // a was used last, so loading c drops b.
        FontFile fontC = load(c);

        assertSame(fontA, load(a));
        assertSame(fontC, load(c));
        assertNotSame(fontB, load(b), "the least recently used font is read again past the budget");
        assertTrue(FontFactory.getSystemFontCacheBytes() <= 2 * charge + 1);
    }

    @Test
    public void fontOverBudgetIsStillReturned() throws Exception {
        FontFactory.setSystemFontCacheBudget(1);
        File a = font("Roboto-Regular.ttf");
        FontFile fontA = load(a);
        assertSame(fontA, load(a), "the font just loaded stays, even alone over the budget");
    }
}
