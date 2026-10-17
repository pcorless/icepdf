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
package org.icepdf.fx.ri.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The error card: stays until dismissed by ×, Esc or a newer error; Copy; messages still show beside it. */
class ErrorCardTest {

    private Toast toast;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() {
        toast = Fx.call(() -> {
            Toast t = new Toast();
            Fx.scene(new StackPane(t), 800, 600);
            return t;
        });
    }

    private Button button(String text) {
        return Fx.call(() -> toast.lookupAll(".button").stream()
                .map(Button.class::cast)
                .filter(b -> text.equals(b.getText()) || text.equals(b.getAccessibleText()))
                .findFirst().orElseThrow(() -> new AssertionError("no button " + text)));
    }

    @DisplayName("an error stays until × is pressed")
    @Test
    void closeButton() {
        Fx.run(() -> toast.showError("Could not save a.pdf: disk full"));
        assertEquals("Could not save a.pdf: disk full", Fx.call(toast::getError));
        Fx.run(() -> button(Messages.get("toast.dismiss")).fire());
        assertEquals("", Fx.call(toast::getError));
    }

    @DisplayName("Esc on the card dismisses it")
    @Test
    void escape() {
        Fx.run(() -> toast.showError("Could not open b.pdf"));
        Fx.run(() -> Fx.press(button(Messages.get("toast.copy")), KeyCode.ESCAPE, false));
        assertEquals("", Fx.call(toast::getError));
    }

    @DisplayName("Copy puts the message on the clipboard")
    @Test
    void copy() {
        Fx.run(() -> toast.showError("Could not sign c.pdf: bad password"));
        Fx.run(() -> button(Messages.get("toast.copy")).fire());
        assertEquals("Could not sign c.pdf: bad password", Fx.call(() -> Clipboard.getSystemClipboard().getString()));
        assertEquals("Could not sign c.pdf: bad password", Fx.call(toast::getError), "copying leaves the card up");
    }

    @DisplayName("a newer error replaces the old; a blank one is ignored")
    @Test
    void replace() {
        Fx.run(() -> toast.showError("first"));
        Fx.run(() -> toast.showError("second"));
        Fx.run(() -> toast.showError("  "));
        assertEquals("second", Fx.call(toast::getError));
    }

    @DisplayName("a message shows below the error card, which stays")
    @Test
    void messageBelowError() {
        Fx.run(() -> {
            toast.showError("Could not save");
            toast.show("Saved a copy");
        });
        Fx.waitFor("the message to fade in", () -> !toast.getMessage().isEmpty());
        assertEquals("Saved a copy", Fx.call(toast::getMessage));
        assertEquals("Could not save", Fx.call(toast::getError));
        Fx.run(() -> {
            Fx.layout(toast);
            Node card = toast.lookup(".toast-error");
            Node message = toast.lookup(".toast");
            double cardBottom = card.localToScene(card.getBoundsInLocal()).getMaxY();
            double messageTop = message.localToScene(message.getBoundsInLocal()).getMinY();
            assertTrue(cardBottom <= messageTop, "card " + cardBottom + " above message " + messageTop);
        });
    }
}
