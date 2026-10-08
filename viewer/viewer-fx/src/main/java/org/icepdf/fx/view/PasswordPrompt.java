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
package org.icepdf.fx.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.icepdf.core.SecurityCallback;
import org.icepdf.core.pobjects.Document;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Asks the user for the password of an encrypted document.  Set one on the {@link Document} before
 * opening it:
 * <pre>{@code
 * PasswordPrompt prompt = new PasswordPrompt(stage, file.getName());
 * document.setSecurityCallback(prompt);
 * try {
 *     document.setFile(path);
 * } catch (PDFSecurityException e) {
 *     if (!prompt.isCancelled()) showError("Wrong password");
 * }
 * }</pre>
 * Core asks again after a wrong password (three tries in all); the prompt says so.  Opening may run
 * on the FX thread or on a loader thread: off the FX thread the dialog is shown on it and the
 * caller waits for the answer.  Use one prompt per document.
 */
public class PasswordPrompt implements SecurityCallback {

    private final Window owner;
    private final String documentName;
    private int attempts;
    private volatile boolean cancelled;

    /**
     * @param owner        window the dialog is modal to; may be null
     * @param documentName shown in the prompt (a file name, say); may be null
     */
    public PasswordPrompt(Window owner, String documentName) {
        this.owner = owner;
        this.documentName = documentName;
    }

    @Override
    public String requestPassword(Document document) {
        attempts++;
        if (Platform.isFxApplicationThread()) return ask();
        CompletableFuture<String> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                answer.complete(ask());
            } catch (Throwable t) {
                answer.completeExceptionally(t);
            }
        });
        try {
            return answer.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelled = true;
            return null;
        } catch (ExecutionException e) {
            cancelled = true;
            return null;
        }
    }

    /** True once the user dismissed the prompt rather than giving a password. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Shows the dialog (FX thread); null when cancelled. */
    private String ask() {
        Dialog<String> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Password required");
        dialog.setHeaderText(attempts > 1 ? "Incorrect password.  Try again."
                : (documentName != null ? "“" + documentName + "”" : "This document")
                + " is protected by a password.");
        PasswordField field = new PasswordField();
        field.setPromptText("Password");
        VBox content = new VBox(8, new Label("Enter the password to open it:"), field);
        content.setPadding(new Insets(10, 10, 0, 10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? field.getText() : null);
        dialog.setOnShown(e -> field.requestFocus());
        String password = dialog.showAndWait().orElse(null);
        if (password == null) cancelled = true;
        return password;
    }
}
