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
package org.icepdf.fx.signature;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** The sign dialog's keystore row, never shown: "Opening…" while it opens, a plain failure with the cause as a tooltip. */
class SignDialogKeystoreTest {

    @TempDir
    Path temp;
    private Document document;
    private SignDialog dialog;
    private Path keystore;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() throws Exception {
        keystore = Paths.get(Objects.requireNonNull(getClass().getResource("/signing/certificate.pfx")).toURI());
        Path source = Files.copy(Paths.get(Objects.requireNonNull(getClass().getResource("/forms/all_fields.pdf")).toURI()),
                temp.resolve("source.pdf"));
        document = new Document();
        document.setFile(source.toString());
        SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0, new Rectangle2D.Double(300, 40, 200, 60));
        dialog = Fx.call(() -> new SignDialog(null, document, field, new SigningSettings()));
    }

    @AfterEach
    void dispose() {
        if (document != null) document.dispose();
    }

    private void open(String password) {
        Fx.run(() -> {
            dialog.keystoreField().setText(keystore.toString());
            dialog.passwordField().setText(password);
            dialog.openKeystore();
            assertEquals(Messages.get("signDialog.openingKeystore"), dialog.keystoreStatus().getText(),
                    "shown while the keystore opens");
        });
        Fx.waitFor("the keystore to open", () -> !Messages.get("signDialog.openingKeystore").equals(dialog.keystoreStatus().getText()));
    }

    @DisplayName("a wrong password: generic text, the cause in the tooltip, no certificates")
    @Test
    void wrongPassword() {
        open("not-the-password");
        assertEquals(Messages.get("signDialog.keystoreFailed"), Fx.call(() -> dialog.keystoreStatus().getText()));
        String cause = Fx.call(() -> dialog.keystoreStatus().getTooltip() != null ? dialog.keystoreStatus().getTooltip().getText() : null);
        assertNotNull(cause, "the cause is in the tooltip");
        assertFalse(cause.isBlank() || "null".equals(cause), cause);
        assertTrue(Fx.call(() -> dialog.certificateList().getItems().isEmpty()));
    }

    @DisplayName("the right password lists the certificates and clears an earlier failure's tooltip")
    @Test
    void rightPasswordAfterWrong() {
        open("not-the-password");
        open("changeit");
        assertEquals("", Fx.call(() -> dialog.keystoreStatus().getText()));
        assertNull(Fx.call(() -> dialog.keystoreStatus().getTooltip()));
        assertFalse(Fx.call(() -> dialog.certificateList().getItems().isEmpty()));
    }

    @DisplayName("no keystore file: asks for one, nothing opened")
    @Test
    void noFile() {
        Fx.run(() -> {
            dialog.keystoreField().setText(temp.resolve("none.p12").toString());
            dialog.openKeystore();
        });
        assertEquals(Messages.get("signDialog.chooseKeystore"), Fx.call(() -> dialog.keystoreStatus().getText()));
    }
}
