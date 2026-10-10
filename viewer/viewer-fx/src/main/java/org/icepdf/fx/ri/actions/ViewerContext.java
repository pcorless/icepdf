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
package org.icepdf.fx.ri.actions;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.concurrent.Task;
import javafx.stage.Window;
import org.icepdf.fx.print.PdfPrintDialog;
import org.icepdf.fx.view.PdfView;

/**
 * What an action works on: the {@link PdfView}, the window it's in, and the host's application-level
 * operations - open, save, side panel, full screen - which only the host can do.  A host offers
 * what it supports ({@link #has}); actions needing something it lacks are left out of its menus
 * and bars.  The operations' defaults throw {@link UnsupportedOperationException}.
 */
public interface ViewerContext {

    /** Application-level operations a host may offer. */
    enum Capability {
        OPEN, SAVE, CLOSE, NEW_WINDOW, EXIT, PREFERENCES, SIDE_PANEL, FULL_SCREEN, FIND
    }

    PdfView view();

    /** The window the viewer is in (dialog owner); may be null before it's shown. */
    Window window();

    /** Whether the host offers an operation. */
    boolean has(Capability capability);

    /** Name for title bars and the About box. */
    default String applicationName() {
        return "ICEpdf";
    }

    // ---- host operations (see Capability) -------------------------------------------------

    /** Asks for a file and opens it. */
    default void openDocument() {
        throw new UnsupportedOperationException("open");
    }

    /** Saves the document in place. */
    default void saveDocument() {
        throw new UnsupportedOperationException("save");
    }

    /** Asks where, and saves a copy there. */
    default void saveDocumentAs() {
        throw new UnsupportedOperationException("save as");
    }

    /** Closes the document (asking about unsaved changes). */
    default void closeDocument() {
        throw new UnsupportedOperationException("close");
    }

    default void newWindow() {
        throw new UnsupportedOperationException("new window");
    }

    default void exit() {
        throw new UnsupportedOperationException("exit");
    }

    default void showPreferences() {
        throw new UnsupportedOperationException("preferences");
    }

    /** Side panel shown or hidden. */
    default BooleanProperty sidePanelVisibleProperty() {
        throw new UnsupportedOperationException("side panel");
    }

    /** Shows the side panel at a panel ("thumbnails", "bookmarks", "comments", "search"...). */
    default void showSidePanel(String panel) {
        throw new UnsupportedOperationException("side panel");
    }

    /** Whether a side panel has anything to show for the open document (comments, attachments...). */
    default ObservableBooleanValue sidePanelAvailable(String panel) {
        return new SimpleBooleanProperty(false);
    }

    default BooleanProperty fullScreenProperty() {
        throw new UnsupportedOperationException("full screen");
    }

    /** Puts the caret in the quick find field. */
    default void focusFind() {
        throw new UnsupportedOperationException("find");
    }

    /** Opens the search panel. */
    default void showSearch() {
        throw new UnsupportedOperationException("search");
    }

    // ---- hooks ------------------------------------------------------------------------------

    /** Sets up the print dialog before it shows (remembered settings, printers); does nothing by default. */
    default void customisePrintDialog(PdfPrintDialog dialog) {
    }

    /** A print has started; by default nothing.  A host might show its progress. */
    default void printStarted(Task<Void> task) {
    }
}
