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

import javafx.concurrent.Task;
import javafx.stage.Window;
import org.icepdf.fx.view.PdfView;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link ViewerContext#of}: the context for a bare {@link PdfView} in an application's window.  Each
 * host operation is offered once the application hands it a handler, so the commands that need it
 * (Find, Open, Save...) appear only then.  Nothing is put on the application's scene: the shortcuts
 * it wants, it installs ({@link ActionControls#installAccelerators}).
 */
public final class SimpleViewerContext implements ViewerContext {

    private final PdfView view;
    private final Map<Capability, Runnable> handlers = new EnumMap<>(Capability.class);
    private Runnable saveAs;
    private Consumer<Task<Void>> printStarted = task -> { };

    SimpleViewerContext(PdfView view) {
        this.view = view;
    }

    @Override
    public PdfView view() {
        return view;
    }

    @Override
    public Window window() {
        return view.getScene() != null ? view.getScene().getWindow() : null;
    }

    @Override
    public boolean has(Capability capability) {
        return handlers.containsKey(capability);
    }

    /** Find (Ctrl+F): typically puts the caret in the application's own search field. */
    public SimpleViewerContext onFind(Runnable focusFind) {
        return set(Capability.FIND, focusFind);
    }

    /** Go to page (Ctrl+G): typically puts the caret in the application's own page number field. */
    public SimpleViewerContext onPageNumber(Runnable focusPageNumber) {
        return set(Capability.PAGE_NUMBER, focusPageNumber);
    }

    /** Open: asks for a file and opens it. */
    public SimpleViewerContext onOpen(Runnable open) {
        return set(Capability.OPEN, open);
    }

    /** Save, and Save As (null: Save As runs {@code save} too, for an application that always asks where). */
    public SimpleViewerContext onSave(Runnable save, Runnable saveAs) {
        this.saveAs = saveAs;
        return set(Capability.SAVE, save);
    }

    /** Export Redacted Copy: writes a copy with the redactions burned in. */
    public SimpleViewerContext onExportRedacted(Runnable export) {
        return set(Capability.EXPORT_REDACTED, export);
    }

    /** Close: closes the document. */
    public SimpleViewerContext onClose(Runnable close) {
        return set(Capability.CLOSE, close);
    }

    /** A print has started (from the print command): to show its progress.  Nothing by default. */
    public SimpleViewerContext onPrintStarted(Consumer<Task<Void>> printStarted) {
        this.printStarted = printStarted != null ? printStarted : task -> { };
        return this;
    }

    private SimpleViewerContext set(Capability capability, Runnable handler) {
        if (handler != null) handlers.put(capability, handler);
        else handlers.remove(capability);
        return this;
    }

    private void run(Capability capability) {
        Runnable handler = handlers.get(capability);
        if (handler == null) throw new UnsupportedOperationException(capability.name());
        handler.run();
    }

    @Override
    public void focusFind() {
        run(Capability.FIND);
    }

    @Override
    public void focusPageNumber() {
        run(Capability.PAGE_NUMBER);
    }

    @Override
    public void openDocument() {
        run(Capability.OPEN);
    }

    @Override
    public void saveDocument() {
        run(Capability.SAVE);
    }

    @Override
    public void saveDocumentAs() {
        if (saveAs != null) saveAs.run();
        else saveDocument();
    }

    @Override
    public void exportRedacted() {
        run(Capability.EXPORT_REDACTED);
    }

    @Override
    public void closeDocument() {
        run(Capability.CLOSE);
    }

    @Override
    public void printStarted(Task<Void> task) {
        printStarted.accept(task);
    }
}
