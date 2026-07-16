/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.tools;

import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentTextSelection;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.Timer;
import java.util.List;

/**
 * Drives the text-selection caret blink.  A single {@link javax.swing.Timer} (EDT-safe) toggles the
 * caret's visible state and repaints the page that holds the document focus caret; the caret is
 * painted by {@code TextSelectionPageHandler.paintTool} only while {@link #isVisible()} is true.
 * <br>
 * The blink is started/stopped by the text-selection view handler as the tool is installed/removed,
 * and reset to solid whenever the caret moves so it is immediately visible on interaction.
 *
 * @since 7.5
 */
public final class CaretBlink {

    private static final int BLINK_RATE_MS = 500;

    private static Timer timer;
    private static boolean visible = true;
    private static DocumentViewController documentViewController;

    private CaretBlink() {
    }

    /**
     * @return true when the caret should currently be painted.
     */
    public static boolean isVisible() {
        return visible;
    }

    /**
     * Starts (or restarts) the blink for the given view.
     *
     * @param controller the active document view controller.
     */
    public static void start(DocumentViewController controller) {
        documentViewController = controller;
        visible = true;
        if (timer == null) {
            timer = new Timer(BLINK_RATE_MS, e -> tick());
            timer.setRepeats(true);
        }
        timer.restart();
    }

    /**
     * Stops the blink and leaves the caret in a steady (visible) state.
     */
    public static void stop() {
        if (timer != null) timer.stop();
        visible = true;
        repaintFocusPage();
        documentViewController = null;
    }

    /**
     * Resets the caret to solid and restarts the blink cycle; called when the caret moves so it is
     * immediately visible.  No-op when the blink is not running.
     */
    public static void reset() {
        if (timer == null || !timer.isRunning()) return;
        visible = true;
        timer.restart();
    }

    private static void tick() {
        visible = !visible;
        repaintFocusPage();
    }

    private static void repaintFocusPage() {
        if (documentViewController == null) return;
        DocumentViewModel model = documentViewController.getDocumentViewModel();
        if (model == null) return;
        DocumentTextSelection selection = model.getTextSelection();
        if (selection.isEmpty()) return;
        List<AbstractPageViewComponent> pages = model.getPageComponents();
        int focusPage = selection.getFocusPage();
        if (focusPage >= 0 && focusPage < pages.size()) {
            pages.get(focusPage).repaint();
        }
    }
}
