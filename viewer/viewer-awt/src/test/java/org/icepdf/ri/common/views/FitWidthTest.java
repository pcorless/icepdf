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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Page;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fit width must not depend on the page having been laid out at the current zoom (GH-667).
 * <p>
 * Fit width divides the viewport by the page's unzoomed width. That width used to be read back from
 * the page's layout bounds, which lag a zoom or page change until the next validate, so a fit
 * applied in the same event-thread task as an open, a zoom or a jump to a search hit picked a wrong
 * zoom, up to the 6,400% maximum when the bounds were still empty.
 */
public class FitWidthTest {

    private static final String FIXTURE = "/redact/test_print.pdf";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("fit width straight after a zoom change fits the viewport, not the stale layout")
    @Test
    public void fitWidthIgnoresStaleLayout() throws Exception {
        SwingController controller = new SwingController();
        JFrame[] frame = new JFrame[1];
        AtomicReference<float[]> zooms = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new JFrame();
                frame[0].getContentPane().add(new SwingViewBuilder(controller).buildViewerPanel());
                controller.openDocument(FitWidthTest.class.getResource(FIXTURE).getFile());
                controller.setPageViewMode(DocumentViewControllerImpl.ONE_COLUMN_VIEW, false);
                // pack creates the peers, without which validate lays nothing out
                frame[0].pack();
                frame[0].setSize(1000, 800);
                frame[0].validate();
            });
            SwingUtilities.invokeAndWait(() -> {
                // the fit the settled layout gives
                controller.setPageFitMode(DocumentViewController.PAGE_FIT_WINDOW_WIDTH, true);
                float settled = controller.getDocumentViewController().getZoom();
                // zoom far away and fit again before anything is laid out at the new zoom
                controller.setZoom(4f);
                controller.setPageFitMode(DocumentViewController.PAGE_FIT_WINDOW_WIDTH, true);
                float refit = controller.getDocumentViewController().getZoom();
                zooms.set(new float[]{settled, refit, expectedFit(controller)});
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                controller.closeDocument();
                frame[0].dispose();
            });
        }
        float[] z = zooms.get();
        assertEquals(z[2], z[0], 0.02f, "settled fit width");
        assertEquals(z[0], z[1], 0.01f, "fit width applied before the layout caught up");
    }

    /**
     * Viewport width over the page's own width plus the view's padding, as fit width defines it.
     */
    private static float expectedFit(SwingController controller) {
        DocumentViewController dvc = controller.getDocumentViewController();
        JScrollPane scrollPane = (JScrollPane) dvc.getViewContainer();
        float viewportWidth = scrollPane.getViewport().getViewRect().width;
        if (!scrollPane.getVerticalScrollBar().isVisible()) {
            viewportWidth -= scrollPane.getVerticalScrollBar().getWidth();
        }
        Page page = controller.getDocument().getPageTree().getPage(dvc.getCurrentPageIndex());
        float pageWidth = (float) page.getSize(Page.BOUNDARY_CROPBOX, 0, 1f).getWidth();
        return viewportWidth / (pageWidth + BasePageViewLayout.PAGE_SPACING_HORIZONTAL
                + AbstractDocumentView.layoutInserts * 2);
    }
}
