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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.stage.Window;
import org.icepdf.core.SecurityCallback;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.view.PasswordPrompt;
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
 *     <li><b>open</b> asks for a password when the file needs one ({@link PasswordPrompt} by
 *     default), and first asks what to do with unsaved changes ({@link #setConfirmDiscard});</li>
 *     <li><b>save</b> writes the document (an incremental update) to a temporary file beside the
 *     target, closes it, moves the temporary file over the target in one step, and reopens it at
 *     the same page - the open file backs the document, so it can't be written in place, and a
 *     failed save never leaves half a file behind;</li>
 *     <li>errors go to {@link #setOnError}; nothing here shows a dialog except the password prompt.</li>
 * </ul>
 * FX thread only.
 */
public final class DocumentSession {

    private static final Logger logger = Logger.getLogger(DocumentSession.class.getName());

    private final PdfView view;
    private final ReadOnlyObjectWrapper<Path> file = new ReadOnlyObjectWrapper<>(this, "file");
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

    /** Receives what went wrong, for the user; by default it's logged. */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    /** Called after a file is opened (or reopened after a save). */
    public void setOnLoaded(Consumer<Path> onLoaded) {
        this.onLoaded = onLoaded;
    }

    /** True when the open document has changes not yet saved. */
    public boolean hasUnsavedChanges() {
        return document != null && document.getStateManager().hasUnsavedUserChanges();
    }

    /** Asks about unsaved changes (when there are any); true to go ahead. */
    public boolean confirmDiscard() {
        return !hasUnsavedChanges() || confirmDiscard.getAsBoolean();
    }

    /**
     * Opens a file, after asking about unsaved changes to the current one.
     *
     * @return true if it opened
     */
    public boolean open(Path path) {
        if (!confirmDiscard()) return false;
        return openAt(path, -1);
    }

    /**
     * Opens a file without asking about unsaved changes - for a file that replaces the open one
     * (a signed copy, say) - at {@code page} when that's >= 0.
     */
    public boolean openAt(Path path, int page) {
        Document next = new Document();
        SecurityCallback callback = security.apply(owner, path);
        next.setSecurityCallback(callback);
        try {
            next.setFile(path.toString());
        } catch (PDFSecurityException e) {
            next.dispose();
            boolean cancelled = callback instanceof PasswordPrompt prompt && prompt.isCancelled();
            if (!cancelled) onError.accept("Could not open " + path.getFileName() + ": incorrect password.");
            return false;
        } catch (Exception e) {
            next.dispose();
            onError.accept("Could not open " + path + ":\n" + e.getMessage());
            return false;
        }
        Document old = document;
        document = next;
        file.set(path.toAbsolutePath());
        view.setDocument(next);
        if (old != null) old.dispose();
        if (page >= 0) view.setCurrentPageIndex(Math.min(page, view.getPageCount() - 1));
        view.refreshModified();
        onLoaded.accept(file.get());
        return true;
    }

    /** Closes the document, after asking about unsaved changes.  @return false if the user stayed */
    public boolean close() {
        if (!confirmDiscard()) return false;
        dispose();
        return true;
    }

    /** Closes the document without asking (the window is going away). */
    public void dispose() {
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
     * Saves to the open file.  @return true if saved; false if it failed (see onError) or can't be
     * saved in place ({@link #canSaveInPlace()}).
     */
    public boolean save() {
        if (!canSaveInPlace()) return false;
        return writeAndReopen(getFile());
    }

    /** Saves to {@code target} (".pdf" added when missing), which becomes the open file. */
    public boolean saveAs(Path target) {
        if (document == null || target == null) return false;
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            target = target.resolveSibling(target.getFileName() + ".pdf");
        }
        return writeAndReopen(target);
    }

    private boolean writeAndReopen(Path target) {
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
            onError.accept("Could not save " + target.getFileName() + ":\n" + e.getMessage());
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
            onError.accept("Could not replace " + target.getFileName() + ":\n" + e.getMessage()
                    + "\nThe document was saved as " + temp);
            openAt(previous != null ? previous : temp, page);
            return false;
        }
        return openAt(target, page);
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
