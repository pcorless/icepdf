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

import org.icepdf.core.pobjects.annotations.Annotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updating an annotation read from the file (its first edit) keeps the page's annotation list as
 * it was: it used to be appended again, so it was listed, and painted, twice.
 */
public class PageUpdateAnnotationTest {

    private static final Path FILE = Paths.get("src/test/resources/updater/annotation_popup.pdf");

    @Test
    public void updatingAFileAnnotationDoesNotListItTwice() throws Exception {
        Document document = new Document();
        document.setFile(FILE.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            List<Annotation> before = page.getAnnotations();
            assertFalse(before.isEmpty(), "the fixture has annotations");
            int count = before.size();
            Annotation annotation = before.get(0);

            assertTrue(page.updateAnnotation(annotation));
            assertEquals(count, page.getAnnotations().size(), "first update of a file annotation");
            assertTrue(page.updateAnnotation(annotation));
            assertEquals(count, page.getAnnotations().size(), "a second update");
            assertEquals(1, page.getAnnotations().stream().filter(a -> a == annotation).count());
        } finally {
            document.dispose();
        }
    }
}
