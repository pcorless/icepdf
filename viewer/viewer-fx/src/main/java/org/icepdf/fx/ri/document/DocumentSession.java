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

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Worker;
import javafx.stage.Window;
import org.icepdf.core.SecurityCallback;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.redaction.RedactionReport;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.fx.view.PdfView;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The document shown in a {@link PdfView} and the file it came from: open, save, save as, close.
 * The session owns the {@link Document} - it disposes the old one whenever it replaces it - so an
 * application never has to.
 * <ul>
 *     <li><b>open</b> reads the file on a worker thread - {@link #loadingProperty()} is true
 *     meanwhile, for a busy indicator - and swaps it in on the FX thread; it asks for a password when
 *     the file needs one ({@link PasswordPrompt} by default), and first asks what to do with unsaved
 *     changes ({@link #setConfirmDiscard}).  A newer open, or a close, supersedes one in progress;</li>
 *     <li><b>save</b> writes the document (an incremental update) to a temporary file beside the
 *     target, closes it, moves the temporary file over the target in one step, and reopens it at
 *     the same page - the open file backs the document, so it can't be written in place, and a
 *     failed save never leaves half a file behind;</li>
 *     <li>while a background job uses the document - a redacted or signed copy being written, a
 *     print ({@link #hold}) - it is {@link #busyProperty() busy}: open, close, save and export
 *     refuse, saying so through onError, rather than dispose or write it under the job;</li>
 *     <li>what is being typed in a form field counts as a change, and is applied before a write
 *     ({@link PdfView#commitFieldEdit()});</li>
 *     <li>errors go to {@link #setOnError}; nothing here shows a dialog except the password prompt.</li>
 * </ul>
 * FX thread only.
 */
public final class DocumentSession {

    private static final Logger logger = Logger.getLogger(DocumentSession.class.getName());

    private final PdfView view;
    private final ReadOnlyObjectWrapper<Path> file = new ReadOnlyObjectWrapper<>(this, "file");
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(this, "loading");
    private final ReadOnlyBooleanWrapper writing = new ReadOnlyBooleanWrapper(this, "writing");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy");
    // background jobs using the document: writes (export, signing) and held ones (printing).
    private int writes;
    private final java.util.List<Worker<?>> heldJobs = new java.util.ArrayList<>();
    private final ReadOnlyObjectWrapper<Path> loadingFile = new ReadOnlyObjectWrapper<>(this, "loadingFile");
    // bumped by every open and close: a load that finishes under an older ticket is thrown away.
    private int ticket;
    private Document document;
    private Window owner;
    private BiFunction<Window, Path, SecurityCallback> security =
            (window, path) -> new PasswordPrompt(window, path.getFileName().toString());
    private BooleanSupplier confirmDiscard = () -> true;
    private Consumer<String> onError = message -> logger.warning(message);
    private Consumer<Path> onLoaded = path -> { };

    public DocumentSession(PdfView view) {
        this.view = view;
    }

    public PdfView getView() {
        return view;
    }

    /** The open document, or null. */
    public Document getDocument() {
        return document;
    }

    /** The open file (absolute), or null. */
    public ReadOnlyObjectProperty<Path> fileProperty() {
        return file.getReadOnlyProperty();
    }

    public Path getFile() {
        return file.get();
    }

    /** True while a file is being read (before it shows). */
    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }

    public boolean isLoading() {
        return loading.get();
    }

    /** True while a copy of the document is written in the background: {@link #exportRedacted}, or signing. */
    public ReadOnlyBooleanProperty writingProperty() {
        return writing.getReadOnlyProperty();
    }

    public boolean isWriting() {
        return writing.get();
    }

    /**
     * True while a background job uses the document: a copy being written ({@link #writingProperty()})
     * or a job {@link #hold held} open, such as a print.  Meanwhile open, close, save, save as and
     * export refuse (and say so through onError); {@link #dispose()} still closes it.
     */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public boolean isBusy() {
        return busy.get();
    }

    /**
     * Keeps the document from being closed, replaced or saved while {@code job} reads it (a print):
     * the session is busy until the job succeeds, fails or is cancelled.  Disposing the session
     * cancels it.
     */
    public void hold(Worker<?> job) {
        if (job == null || done(job.getState()) || heldJobs.contains(job)) return;
        heldJobs.add(job);
        updateBusy();
        job.stateProperty().addListener((o, was, now) -> {
            if (done(now) && heldJobs.remove(job)) updateBusy();
        });
    }

    private static boolean done(Worker.State state) {
        return state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED;
    }

    /** Marks a write as started (FX thread); the returned action ends it, once. */
    Runnable startWrite() {
        writes++;
        writing.set(true);
        updateBusy();
        boolean[] ended = new boolean[1];
        return () -> {
            if (ended[0]) return;
            ended[0] = true;
            writes--;
            writing.set(writes > 0);
            updateBusy();
        };
    }

    private void updateBusy() {
        busy.set(writes > 0 || !heldJobs.isEmpty());
    }

    /**
     * While {@link #isBusy() busy}, tells onError the document can't be changed yet.
     *
     * @return true if busy (the caller should stop)
     */
    public boolean reportBusy() {
        if (!isBusy()) return false;
        Path shown = getFile();
        onError.accept(shown != null && shown.getFileName() != null
                ? Messages.format("documentSession.busy", shown.getFileName()) : Messages.get("documentSession.busyUntitled"));
        return true;
    }

    /** The file being read (absolute), or null when nothing is loading. */
    public ReadOnlyObjectProperty<Path> loadingFileProperty() {
        return loadingFile.getReadOnlyProperty();
    }

    public Path getLoadingFile() {
        return loadingFile.get();
    }

    /** Window the password prompt belongs to. */
    public void setOwner(Window owner) {
        this.owner = owner;
    }

    /** Makes the security callback (password prompt) for a file; {@link PasswordPrompt} by default. */
    public void setSecurityCallback(BiFunction<Window, Path, SecurityCallback> factory) {
        this.security = factory;
    }

    /**
     * Asked before the open document is replaced or closed while it has unsaved changes; returns
     * false to stay.  By default the changes are dropped.
     */
    public void setConfirmDiscard(BooleanSupplier confirm) {
        this.confirmDiscard = confirm;
    }

    /** Receives what went wrong, for the user - the session's failures and the view's ({@code PdfViewErrorEvent}); by default it's logged. */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError != null ? onError : message -> logger.warning(message);
    }

    /** Hands a failure in the view (a {@code PdfViewErrorEvent}) to onError, with the session's own. */
    void reportError(String message) {
        onError.accept(message);
    }

    /** Called on the FX thread after a file is opened (or reopened after a save). */
    public void setOnLoaded(Consumer<Path> onLoaded) {
        this.onLoaded = onLoaded != null ? onLoaded : path -> { };
    }

    /**
     * True when the open document has changes not yet saved, or a form field holds typed text not yet
     * applied; read from the document, so it is current even before {@link PdfView#modifiedProperty()}
     * is refreshed.
     */
    public boolean isModified() {
        return document != null && (document.getStateManager().hasUnsavedUserChanges() || view.hasPendingFieldEdit());
    }

    /** Asks about unsaved changes (when there are any); true to go ahead. */
    public boolean confirmDiscard() {
        return !isModified() || confirmDiscard.getAsBoolean();
    }

    /**
     * Starts opening a file, after asking about unsaved changes to the current one.  The current
     * document stays until the new one is read; then {@code onLoaded}, or {@code onError}, hears.
     *
     * @return false if the user chose to keep the current document
     */
    public boolean open(Path path) {
        return open(path, ok -> { });
    }

    /** {@link #open(Path)}, then {@code done} with whether the file opened (not called when superseded). */
    public boolean open(Path path, Consumer<Boolean> done) {
        if (reportBusy() || !confirmDiscard()) return false;
        openAt(path, -1, done);
        return true;
    }

    /**
     * Starts opening a file without asking about unsaved changes - for a file that replaces the
     * open one (a signed copy, say) - at the zero-based page index {@code page} when that's >= 0.
     */
    public void openAt(Path path, int page) {
        if (reportBusy()) return;
        reopen(path, page);
    }

    /** {@link #openAt(Path, int)} for the session's own writes, which have ended (or never started) their job. */
    void reopen(Path path, int page) {
        openAt(path, page, ok -> { });
    }

    private void openAt(Path path, int page, Consumer<Boolean> done) {
        int mine = ++ticket;
        loadingFile.set(path.toAbsolutePath());
        loading.set(true);
        SecurityCallback callback = security.apply(owner, path);
        Thread worker = new Thread(() -> {
            Document next = new Document();
            next.setSecurityCallback(callback);
            Exception failure = null;
            try {
                next.setFile(path.toString());
            } catch (Exception e) {
                next.dispose();
                failure = e;
            }
            Exception failed = failure;
            Platform.runLater(() -> loaded(mine, path, page, failed == null ? next : null, failed, callback, done));
        }, "pdf-open");
        worker.setDaemon(true);
        worker.start();
    }

    private void loaded(int mine, Path path, int page, Document next, Exception failure, SecurityCallback callback,
                        Consumer<Boolean> done) {
        if (mine != ticket) {
            // a newer open or a close came first.
            if (next != null) next.dispose();
            return;
        }
        loading.set(false);
        loadingFile.set(null);
        if (failure instanceof PDFSecurityException) {
            boolean cancelled = callback instanceof PasswordPrompt prompt && prompt.isCancelled();
            if (!cancelled) onError.accept(Messages.format("documentSession.wrongPassword", path.getFileName()));
            done.accept(false);
            return;
        }
        if (failure != null) {
            logger.log(Level.FINE, "Open failed", failure);
            onError.accept(Messages.format("documentSession.openFailed", path, failure.getMessage()));
            done.accept(false);
            return;
        }
        Document old = document;
        document = next;
        file.set(path.toAbsolutePath());
        view.setDocument(next);
        if (old != null) old.dispose();
        if (page >= 0) view.setCurrentPageIndex(Math.min(page, view.getPageCount() - 1));
        view.refreshModified();
        onLoaded.accept(file.get());
        done.accept(true);
    }

    /** Closes the document, after asking about unsaved changes.  @return false if the user stayed */
    public boolean close() {
        if (reportBusy() || !confirmDiscard()) return false;
        dispose();
        return true;
    }

    /** Closes the document without asking (the window is going away). */
    public void dispose() {
        // a held job (a print) reads the document being disposed: stop it.
        for (Worker<?> job : new java.util.ArrayList<>(heldJobs)) job.cancel();
        ticket++;
        loading.set(false);
        loadingFile.set(null);
        Document old = document;
        document = null;
        file.set(null);
        view.setDocument(null);
        if (old != null) old.dispose();
        view.refreshModified();
    }

    /** Whether {@link #save()} can write to the open file (else it needs {@link #saveAs}). */
    public boolean canSaveInPlace() {
        return document != null && getFile() != null && Files.isWritable(getFile());
    }

    /**
     * Saves to the open file, which is then read back in (asynchronously).  @return true if saved; false if it failed (see onError) or can't be
     * saved in place ({@link #canSaveInPlace()}).
     */
    public boolean save() {
        if (!canSaveInPlace() || reportBusy()) return false;
        return writeAndReopen(getFile());
    }

    /** Saves to {@code target} (".pdf" added when missing), which becomes the open file. */
    public boolean saveAs(Path target) {
        if (document == null || target == null || reportBusy()) return false;
        return writeAndReopen(withPdfExtension(target));
    }

    /** {@code target}, with ".pdf" added when its name doesn't end with it (any case). */
    public static Path withPdfExtension(Path target) {
        Path name = target.getFileName();
        if (name == null || name.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) return target;
        return target.resolveSibling(name + ".pdf");
    }

    private boolean writeAndReopen(Path target) {
        // a value still being typed in a field goes out with the rest.
        view.commitFieldEdit();
        Path directory = target.toAbsolutePath().getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(directory, "." + target.getFileName(), ".tmp");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                document.saveToOutputStream(out);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Save failed", e);
            deleteQuietly(temp);
            onError.accept(Messages.format("documentSession.saveFailed", target.getFileName(), e.getMessage()));
            return false;
        }
        int page = view.getCurrentPageIndex();
        Path previous = getFile();
        Document old = document;
        document = null;
        view.setDocument(null);
        old.dispose();
        try {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Save failed", e);
            onError.accept(Messages.format("documentSession.replaceFailed", target.getFileName(), e.getMessage(), temp));
            reopen(previous != null ? previous : temp, page);
            return false;
        }
        reopen(target, page);
        return true;
    }

    /** True when the open document has redactions marked but not yet burned in. */
    public boolean hasRedactions() {
        return document != null && (document.hasRedactions() || termsRecorded(document));
    }

    /** Search terms recorded for redaction (search and redact): masked on export, with or without marks. */
    static boolean termsRecorded(Document document) {
        return document.getRedactionRequest() != null && document.getRedactionRequest().hasTerms();
    }

    /**
     * Writes a redacted copy to {@code target} (".pdf" added when missing): a full rewrite, which is
     * what burns the redactions - the marked content is removed, not covered - and drops the old
     * revisions an incremental save would keep.  Runs on a worker; the copy then replaces the open
     * document, and {@code done} gets the write's report (null if it failed - see onError).
     */
    public void exportRedacted(Path target, Consumer<RedactionReport> done) {
        if (document == null || target == null || reportBusy()) return;
        view.commitFieldEdit();
        Path out = withPdfExtension(target);
        Document writingDocument = document;
        int page = view.getCurrentPageIndex();
        Runnable endWrite = startWrite();
        Thread worker = new Thread(() -> {
            Path temp = null;
            try {
                temp = Files.createTempFile(out.toAbsolutePath().getParent(), "." + out.getFileName(), ".tmp");
                try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(temp))) {
                    writingDocument.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
                }
                try {
                    Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
                }
                RedactionReport report = writingDocument.getRedactionReport();
                Platform.runLater(() -> {
                    endWrite.run();
                    // the redacted copy is the document now; the marked original is left as it was.
                    writingDocument.getStateManager().setChangesSnapshot();
                    if (document == writingDocument) reopen(out, page);
                    done.accept(report);
                });
            } catch (Exception | Error e) {
                // an Error too: the session must not stay busy for good.
                logger.log(Level.WARNING, "Redacted export failed", e);
                deleteQuietly(temp);
                Platform.runLater(() -> {
                    endWrite.run();
                    onError.accept(Messages.format("documentSession.writeFailed", out.getFileName(), e.getMessage()));
                    done.accept(null);
                });
            }
        }, "pdf-redact");
        worker.setDaemon(true);
        worker.start();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // a stray temporary file
        }
    }
}
