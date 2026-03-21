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
package org.icepdf.ri.common.views.listeners;

import org.icepdf.core.events.PageImageEvent;
import org.icepdf.core.events.PageInitializingEvent;
import org.icepdf.core.events.PageLoadingEvent;
import org.icepdf.core.events.PagePaintingEvent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;
import java.awt.*;

/**
 * DefaultPageViewLoadingListener takes advantage of the PageLoadingListener
 * interface to set the current page cursor to a wait symbol during  page load.
 *
 * @since 5.1.0
 */
public class DefaultPageViewLoadingListener extends PageViewLoadingListener {

    private final JComponent pageComponent;
    private DocumentViewController documentViewController;
    private final Cursor previousCursor;

    public DefaultPageViewLoadingListener(JComponent pageComponent,
                                          DocumentViewController documentViewController) {
        this.pageComponent = pageComponent;
        previousCursor = this.pageComponent.getCursor();
        this.documentViewController = documentViewController;
    }

    public void setDocumentViewController(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    public void pageLoadingStarted(PageLoadingEvent event) {
        SwingUtilities.invokeLater(() -> {
            if (documentViewController != null)
                pageComponent.setCursor(documentViewController.getViewCursor(
                        DocumentViewController.CURSOR_WAIT));
        });
    }

    public void pageInitializationStarted(PageInitializingEvent event) {
        SwingUtilities.invokeLater(() -> {
            if (documentViewController != null)
                pageComponent.setCursor(documentViewController.getViewCursor(
                        DocumentViewController.CURSOR_WAIT));
        });
    }

    public void pagePaintingStarted(PagePaintingEvent event) {
        SwingUtilities.invokeLater(() -> {
            if (documentViewController != null)
                pageComponent.setCursor(documentViewController.getViewCursor(
                        DocumentViewController.CURSOR_WAIT));
        });
    }

    @Override
    public void pageInitializationEnded(PageInitializingEvent event) {
        // null will make the parent view icon be the default.

        SwingUtilities.invokeLater(() -> pageComponent.setCursor(null));
    }

    @Override
    public void pageImageLoaded(PageImageEvent event) {
        super.pageImageLoaded(event);
    }

    @Override
    public void pagePaintingEnded(PagePaintingEvent event) {
        // null will make the parent view icon be the default.
        SwingUtilities.invokeLater(() -> pageComponent.setCursor(previousCursor));
    }

    public void pageLoadingEnded(PageLoadingEvent event) {
        // null will make the parent view icon be the default.
        SwingUtilities.invokeLater(() -> pageComponent.setCursor(null));
    }
}
