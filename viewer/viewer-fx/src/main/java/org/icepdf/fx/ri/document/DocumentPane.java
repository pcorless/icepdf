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

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.fx.panels.*;
import org.icepdf.fx.view.PdfView;

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
    private final StackPane node = new StackPane(view, collection);
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", "");
    private Consumer<String> onMessage = m -> { };
    private Consumer<String> onLink;

    public DocumentPane() {
        view.setOnAnnotationAction(event -> {
            if (event.getAction() instanceof URIAction uri && uri.getURI() != null) {
                if (onLink != null) onLink.accept(uri.getURI());
                else onMessage.accept("Link: " + uri.getURI());
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
        view.modifiedProperty().addListener((o, a, b) -> refreshTitle());
        session.fileProperty().addListener((o, a, b) -> refreshTitle());
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

    /** Works the title out again (after anything that changes the file or the modified state). */
    public void refreshTitle() {
        Path file = session.getFile();
        title.set(file == null ? "" : (view.isModified() ? "• " : "") + file.getFileName());
    }

    /** Receives short messages for the user (a link was clicked). */
    /** Receives the URI of a link the user follows; without one the link is shown as a message. */
    public void setOnLink(Consumer<String> onLink) {
        this.onLink = onLink;
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage != null ? onMessage : m -> { };
    }

    /** Closes the document without asking and stops background work. */
    public void dispose() {
        session.dispose();
        thumbnails.dispose();
        comments.dispose();
        collection.dispose();
    }
}
