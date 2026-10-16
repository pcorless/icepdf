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
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.ri.SigningStore;
import org.icepdf.fx.signature.DocumentSigning;
import org.icepdf.fx.signature.SignDialog;
import org.icepdf.fx.signature.SignaturePropertiesDialog;
import org.icepdf.fx.signature.SigningSettings;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.SignatureEvent;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Signing a document's empty signature field, start to finish: the sign dialog, then where to put
 * the signed copy, which is written off the FX thread and opened in the document's place.
 * Cancelling at any step leaves the field unsigned.  A signed field clicked shows its properties.
 * <pre>{@code
 * DocumentSession session = new DocumentSession(view);
 * SignatureFlow.install(session);      // clicking or drawing a signature field now signs it
 * }</pre>
 * The settings the dialog starts from (keystore, certificate, appearance, drawn signature) come
 * from a {@link SigningStore}, which is told what was chosen; they're kept in memory by default.
 * FX thread only.
 */
public final class SignatureFlow {

    private static final Logger logger = Logger.getLogger(SignatureFlow.class.getName());

    private final DocumentSession session;
    private SigningStore store = SigningStore.inMemory();
    private BiFunction<Window, Path, Path> chooseTarget = SignatureFlow::chooseWithFileChooser;
    private Consumer<String> onMessage = message -> { };
    private Consumer<String> onError = message -> logger.warning(message);

    public SignatureFlow(DocumentSession session) {
        this.session = session;
    }

    /**
     * A flow for the session's view, put in charge of its signature clicks
     * ({@link PdfView#setOnSignatureClicked}).
     */
    public static SignatureFlow install(DocumentSession session) {
        SignatureFlow flow = new SignatureFlow(session);
        session.getView().setOnSignatureClicked(flow::handle);
        return flow;
    }

    /** A click on a signature field (or one just drawn): sign an empty one, show a signed one's properties. */
    public void handle(SignatureEvent event) {
        if (event.getStatus().isSigned()) SignaturePropertiesDialog.show(session.getView(), event.getStatus());
        else sign(event.getStatus().widget());
    }

    /** Where the sign dialog's settings come from and go back to. */
    public SignatureFlow setSigningStore(SigningStore store) {
        this.store = store != null ? store : SigningStore.inMemory();
        return this;
    }

    /**
     * Asks where to write the signed copy, given the owner window and a suggested file (beside the
     * open one, "-signed" added); returns null to cancel.  A save {@link FileChooser} by default.
     */
    public SignatureFlow setTargetChooser(BiFunction<Window, Path, Path> chooseTarget) {
        this.chooseTarget = chooseTarget != null ? chooseTarget : SignatureFlow::chooseWithFileChooser;
        return this;
    }

    /** Receives progress for the user ("Signing…", "Signed x.pdf"); ignored by default. */
    public SignatureFlow setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage != null ? onMessage : message -> { };
        return this;
    }

    /** Receives what went wrong; logged by default. */
    public SignatureFlow setOnError(Consumer<String> onError) {
        this.onError = onError != null ? onError : message -> logger.warning(message);
        return this;
    }

    /** Signs an empty field of the session's document. */
    public void sign(SignatureWidgetAnnotation field) {
        Document document = session.getDocument();
        if (document == null || field == null) return;
        PdfView view = session.getView();
        Window owner = view.getScene() != null ? view.getScene().getWindow() : null;
        SigningSettings settings = store.settings();
        SignDialog.Result result = new SignDialog(owner, document, field, settings).showAndWait().orElse(null);
        // saved on cancel too: a signature drawn and the keystore opened are worth keeping either way.
        store.save(settings);
        if (result == null) return;
        try {
            DocumentSigning.prepare(field, result.signer(), result.request(), result.appearance());
        } catch (IllegalStateException e) {
            DocumentSigning.cancel(field);
            onError.accept(e.getMessage());
            return;
        }
        Path file = session.getFile();
        String base = file != null ? file.getFileName().toString().replaceFirst("(?i)\\.pdf$", "") : "document";
        Path suggested = file != null ? file.resolveSibling(base + "-signed.pdf") : Path.of(base + "-signed.pdf");
        Path target = chooseTarget.apply(owner, suggested);
        if (target == null) {
            DocumentSigning.cancel(field);
            return;
        }
        onMessage.accept(Messages.get("signatureFlow.signing"));
        int page = view.getCurrentPageIndex();
        // signing may call a timestamp authority: off the FX thread.
        Thread worker = new Thread(() -> {
            try {
                DocumentSigning.saveSigned(document, target);
                Platform.runLater(() -> {
                    // the signed copy is the document now; the original's pending signature is gone.
                    document.getStateManager().setChangesSnapshot();
                    if (session.getDocument() == document) session.openAt(target, page);
                    onMessage.accept(Messages.format("signatureFlow.signed", target.getFileName()));
                });
            } catch (Exception e) {
                logger.log(Level.FINE, "Signing failed", e);
                Platform.runLater(() -> onError.accept(Messages.format("signatureFlow.failed", e.getMessage())));
            }
        }, "pdf-sign");
        worker.setDaemon(true);
        worker.start();
    }

    private static Path chooseWithFileChooser(Window owner, Path suggested) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("signatureFlow.saveTitle"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("signatureFlow.pdfFilter"), "*.pdf"));
        chooser.setInitialFileName(suggested.getFileName().toString());
        Path folder = suggested.toAbsolutePath().getParent();
        if (folder != null && folder.toFile().isDirectory()) chooser.setInitialDirectory(folder.toFile());
        File chosen = chooser.showSaveDialog(owner);
        return chosen != null ? chosen.toPath() : null;
    }
}
