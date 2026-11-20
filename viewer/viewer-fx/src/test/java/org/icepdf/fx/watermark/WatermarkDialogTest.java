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
package org.icepdf.fx.watermark;

import org.icepdf.core.util.watermark.Watermark;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WatermarkDialogTest {

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @DisplayName("starts from the document's watermark text, across the page and fitted")
    @Test
    void defaults() {
        Watermark watermark = Fx.call(() -> new WatermarkDialog(null, 612, 792, "CONFIDENTIAL").watermark());
        assertEquals("CONFIDENTIAL", watermark.getText());
        assertEquals(Watermark.Position.OVERLAY, watermark.getPosition());
        assertEquals(0f, watermark.getFontSize(), "fit to the page");
        assertEquals(Watermark.StandardFont.HELVETICA_BOLD, watermark.getFont());
    }

    @DisplayName("a header uses the chosen size; fit to page is only for the overlay")
    @Test
    void headerWithSize() {
        Watermark watermark = Fx.call(() -> {
            WatermarkDialog dialog = new WatermarkDialog(null, 612, 792, null);
            dialog.headerButton().setSelected(true);
            assertTrue(dialog.fitToPageBox().isDisabled(), "fit is for the overlay only");
            assertFalse(dialog.fontSizeSpinner().isDisabled(), "the size applies to a header");
            dialog.fontSizeSpinner().getValueFactory().setValue(20);
            dialog.fontBox().setValue(Watermark.StandardFont.TIMES_ROMAN);
            dialog.textField().setText("Internal");
            return dialog.watermark();
        });
        assertEquals(Watermark.Position.HEADER, watermark.getPosition());
        assertEquals(20f, watermark.getFontSize());
        assertEquals(Watermark.StandardFont.TIMES_ROMAN, watermark.getFont());
        assertEquals("Internal", watermark.getText());
    }

    @DisplayName("Apply is disabled while the text is blank")
    @Test
    void blankTextCantBeApplied() {
        Fx.run(() -> {
            WatermarkDialog dialog = new WatermarkDialog(null, 612, 792, null);
            javafx.scene.Node apply = dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
            assertFalse(apply.isDisabled(), "the default text can be applied");
            dialog.textField().setText("   ");
            assertTrue(apply.isDisabled(), "blank text can't");
        });
    }
}
