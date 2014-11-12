package org.icepdf.ri.common.views.listeners;

import org.icepdf.core.events.PageInitializingEvent;
import org.icepdf.core.events.PageLoadingEvent;
import org.icepdf.core.events.PagePaintingEvent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;

/**
 * DefaultPageViewLoadingListener takes advantage of the PageLoadingListener
 * interface to set the current page cursor to a wait symbol during  page load.
 *
 * @since 5.1.0
 */
public class DefaultPageViewLoadingListener extends PageViewLoadingListener {

    private JComponent pageComponent;
    private DocumentViewController documentViewController;

    public DefaultPageViewLoadingListener(JComponent pageComponent,
                                          DocumentViewController documentViewController) {
        this.pageComponent = pageComponent;
        this.documentViewController = documentViewController;
    }

    public void setDocumentViewController(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    public void pageLoadingStarted(PageLoadingEvent event) {
        pageComponent.setCursor(documentViewController.getViewCursor(
                DocumentViewController.CURSOR_WAIT));
    }

    public void pageInitializationStarted(PageInitializingEvent event) {
        pageComponent.setCursor(documentViewController.getViewCursor(
                DocumentViewController.CURSOR_WAIT));
    }

    public void pagePaintingStarted(PagePaintingEvent event) {
        pageComponent.setCursor(documentViewController.getViewCursor(
                DocumentViewController.CURSOR_WAIT));
    }

    public void pageLoadingEnded(PageLoadingEvent event) {
        // null will make the parent view icon be the default.
        pageComponent.setCursor(null);
    }
}
