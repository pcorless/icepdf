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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A viewer reads a page's annotations on its UI thread (tab order, form values) while a render
 * thread initializes the same page.  The annotation list used to be published before it was filled,
 * so the reader could get a list still being built, or both threads built and published one.
 */
public class PageAnnotationsConcurrencyTest {

    private static final int ROUNDS = 40;
    private static final int THREADS = 4;

    @Test
    @DisplayName("getAnnotations racing init sees only the complete list")
    public void readerRacingInitSeesCompleteList() throws Exception {
        String file = Path.of("src/test/resources/acroform/all_fields.pdf").toAbsolutePath().toString();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Document document = new Document();
                document.setFile(file);
                try {
                    Page page = document.getPageTree().getPage(0);
                    CyclicBarrier start = new CyclicBarrier(THREADS);
                    List<Future<Integer>> sizes = new java.util.ArrayList<>();
                    for (int t = 0; t < THREADS; t++) {
                        boolean reader = t % 2 == 0;
                        sizes.add(pool.submit(() -> {
                            start.await();
                            if (!reader) page.init();
                            List<Annotation> annotations = page.getAnnotations();
                            return annotations == null ? -1 : annotations.size();
                        }));
                    }
                    List<Integer> seen = new java.util.ArrayList<>();
                    for (Future<Integer> size : sizes) seen.add(size.get(30, TimeUnit.SECONDS));
                    List<Annotation> settled = page.getAnnotations();
                    assertNotNull(settled);
                    for (int size : seen) assertEquals(settled.size(), size, "round " + round + ": " + seen);
                } finally {
                    document.dispose();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
