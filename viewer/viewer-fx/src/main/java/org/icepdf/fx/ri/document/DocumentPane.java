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
package org.icepdf.fx.ri.document;

import org.icepdf.fx.i18n.Messages;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.fx.panels.*;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.PdfViewEvent;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One open document and everything that belongs to it alone: its {@link PdfView}, its
 * {@link DocumentSession} and the panels that list its pages, bookmarks, comments and so on.  A
 * viewer holds one or more of these and shows the chrome (header, tool rail, side panel strip)
 * for the selected one.  A pane has no ties to any window, so it can move from one to another.
 */
public final class DocumentPane {

    private final PdfView view = new PdfView();
    private final DocumentSession session = new DocumentSession(view);
    private final SearchPanel search = new SearchPanel(view);
    private final ThumbnailPanel thumbnails = new ThumbnailPanel(view);
    private final AnnotationListPanel comments = new AnnotationListPanel(view);
    private final OutlinePanel outline = new OutlinePanel(view);
    private final AttachmentPanel attachments = new AttachmentPanel(view);
    private final LayersPanel layers = new LayersPanel(view);
    private final SignaturePanel signatures = new SignaturePanel(view);
    private final CollectionView collection = new CollectionView();
    private final HBox redactionBanner = new HBox(10);
    private final StackPane node = new StackPane(view, collection, redactionBanner);
    // the user closed the redaction reminder for this document.
    private boolean redactionBannerDismissed;
    private Runnable onExportRedacted;
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", "");
    private Consumer<String> onMessage = m -> { };
    private Consumer<String> onLink;

    public DocumentPane() {
        view.setOnAnnotationAction(event -> {
            if (event.getAction() instanceof URIAction uri && uri.getURI() != null) {
                if (onLink != null) onLink.accept(uri.getURI());
                else onMessage.accept(Messages.format("documentPane.link", uri.getURI()));
            }
        });
        collection.setVisible(false);
        collection.setOnOpen(attachments::open);
        view.documentProperty().addListener((o, a, b) -> {
            boolean portfolio = CollectionView.isCollection(b);
            collection.setDocument(portfolio ? b : null);
            collection.setVisible(portfolio);
            view.setVisible(!portfolio);
        });
        buildRedactionBanner();
        view.addEventHandler(PdfViewEvent.ANNOTATIONS_CHANGED, e -> updateRedactionBanner());
        view.documentProperty().addListener((o, a, b) -> {
            redactionBannerDismissed = false;
            updateRedactionBanner();
        });
        view.modifiedProperty().addListener((o, a, b) -> refreshTitle());
        session.fileProperty().addListener((o, a, b) -> refreshTitle());
        session.loadingFileProperty().addListener((o, a, b) -> refreshTitle());
        // the wait cursor while the file is read or a redacted copy written; the view keeps it until the first pages are painted.
        view.busyProperty().bind(session.loadingProperty().or(session.writingProperty()));
    }

    public PdfView getView() {
        return view;
    }

    public DocumentSession getSession() {
        return session;
    }

    public SearchPanel getSearchPanel() {
        return search;
    }

    public ThumbnailPanel getThumbnails() {
        return thumbnails;
    }

    public AnnotationListPanel getComments() {
        return comments;
    }

    public OutlinePanel getOutline() {
        return outline;
    }

    public AttachmentPanel getAttachments() {
        return attachments;
    }

    public LayersPanel getLayers() {
        return layers;
    }

    public SignaturePanel getSignatures() {
        return signatures;
    }

    /**
     * What shows in the document's place: the view, or for a PDF portfolio a tile per embedded file
     * instead of its cover sheet.
     */
    public Node getNode() {
        return node;
    }

    /** The file name, marked with a bullet while there are unsaved changes; empty with no file. */
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public String getTitle() {
        return title.get();
    }

    /**
     * A reminder floating over the top of the document once redactions are marked: they are only
     * marks until the document is exported with them burned in, to a new file.  Closable; it comes
     * back for the next document.
     */
    private void buildRedactionBanner() {
        Label text = new Label(Messages.get("documentPane.redactionBanner"));
        text.setWrapText(true);
        Button export = new Button(Messages.get("documentPane.exportRedacted"));
        export.setOnAction(e -> {
            if (onExportRedacted != null) onExportRedacted.run();
        });
        Button close = new Button("\u2715");
        close.getStyleClass().add("close");
        close.setFocusTraversable(false);
        close.setAccessibleText(Messages.get("documentPane.close"));
        close.setOnAction(e -> {
            redactionBannerDismissed = true;
            redactionBanner.setVisible(false);
        });
        // the buttons keep their full width; the message wraps instead.
        export.setMinWidth(Region.USE_PREF_SIZE);
        close.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
        redactionBanner.getChildren().addAll(text, export, close);
        redactionBanner.getStyleClass().add("redaction-banner");
        redactionBanner.setAlignment(Pos.CENTER_LEFT);
        redactionBanner.setMaxWidth(640);
        redactionBanner.setMaxHeight(Region.USE_PREF_SIZE);
        redactionBanner.setPickOnBounds(false);
        redactionBanner.setVisible(false);
        StackPane.setAlignment(redactionBanner, Pos.TOP_CENTER);
        StackPane.setMargin(redactionBanner, new Insets(12, 24, 0, 24));
    }

    private void updateRedactionBanner() {
        org.icepdf.core.pobjects.Document document = session.getDocument();
        // the state manager knows the redactions marked since opening - cheap, unlike a page scan.
        boolean marked = document != null && (document.getStateManager().hasRedactions()
                || DocumentSession.termsRecorded(document));
        redactionBanner.setVisible(marked && !redactionBannerDismissed);
    }

    /** What the reminder's Export Redacted Copy button does (the viewer's export). */
    public void setOnExportRedacted(Runnable onExportRedacted) {
        this.onExportRedacted = onExportRedacted;
    }

    // the view's mode and cover page before a document's /PageLayout replaced them, and what the
    // document set; null when the view shows the viewer's own (or the user's) choice.
    private org.icepdf.fx.ri.PageLayout beforeDocumentLayout;
    private org.icepdf.fx.ri.PageLayout documentLayout;
    private boolean applyingPageLayout;

    /** True while {@link #applyPageLayout} changes the view: the change is the document's, not the user's. */
    public boolean isApplyingPageLayout() {
        return applyingPageLayout;
    }

    /**
     * Applies an opened document's /PageLayout (null when it names none), which overrides the viewer's
     * view mode as in the Swing viewer.  A document without one gets back the mode the view had before
     * an earlier document's layout replaced it - unless the user has changed the mode since, which
     * then stays.
     */
    public void applyPageLayout(org.icepdf.fx.ri.PageLayout layout) {
        applyingPageLayout = true;
        try {
            applyPageLayoutNow(layout);
        } finally {
            applyingPageLayout = false;
        }
    }

    private void applyPageLayoutNow(org.icepdf.fx.ri.PageLayout layout) {
        org.icepdf.fx.ri.PageLayout current = new org.icepdf.fx.ri.PageLayout(view.getViewMode(), view.isCoverPage());
        if (documentLayout != null && !documentLayout.equals(current)) beforeDocumentLayout = null; // the user's choice
        if (layout != null) {
            if (beforeDocumentLayout == null) beforeDocumentLayout = current;
            documentLayout = layout;
            view.setViewMode(layout.viewMode());
            view.setCoverPage(layout.coverPage());
        } else {
            if (beforeDocumentLayout != null && documentLayout != null && documentLayout.equals(current)) {
                view.setViewMode(beforeDocumentLayout.viewMode());
                view.setCoverPage(beforeDocumentLayout.coverPage());
            }
            beforeDocumentLayout = null;
            documentLayout = null;
        }
    }

    /** Works the title out again (after anything that changes the file or the modified state). */
    public void refreshTitle() {
        Path loading = session.getLoadingFile();
        Path file = session.getFile();
        // a file still loading names its tab already.
        if (loading != null && file == null) title.set(loading.getFileName().toString());
        else title.set(file == null ? "" : (view.isModified() ? "• " : "") + file.getFileName());
    }

    /** Receives the URI of a link the user follows; without one the link is shown as a message. */
    public void setOnLink(Consumer<String> onLink) {
        this.onLink = onLink;
    }

    /** Receives short messages for the user (a link was clicked). */
    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage != null ? onMessage : m -> { };
    }

    /** Closes the document without asking and stops background work. */
    public void dispose() {
        session.dispose();
        thumbnails.dispose();
        comments.dispose();
        collection.dispose();
        view.dispose();
    }
}
