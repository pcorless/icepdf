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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Work the skin queued for later finds it disposed (the view's window closed) and does nothing. */
class SkinDisposeTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @DisplayName("a refresh queued before the skin is disposed doesn't throw when it runs")
    @Test
    void queuedRefreshAfterDispose() throws Exception {
        Document document = new Document();
        document.setFile(TinyPdf.onePage("", "").write(dir.resolve("one.pdf")).toString());
        List<Throwable> uncaught = new ArrayList<>();
        Thread.UncaughtExceptionHandler was = Fx.call(() -> Thread.currentThread().getUncaughtExceptionHandler());
        Fx.run(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> uncaught.add(e)));
        try {
            PdfView view = Fx.call(() -> {
                PdfView v = new PdfView();
                Fx.scene(v, 400, 300);
                v.setDocument(document);
                Fx.layout(v);
                return v;
            });
            Fx.run(() -> {
                ((PdfViewSkin) view.getSkin()).scheduleRefresh();
                view.setCurrentPageIndex(0);
                view.setSkin(null); // what closing the window does to the skin
            });
            Fx.settle();
            Fx.settle();
            assertEquals(List.of(), uncaught);
        } finally {
            Fx.run(() -> Thread.currentThread().setUncaughtExceptionHandler(was));
            document.dispose();
        }
    }
}
