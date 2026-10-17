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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.document.DocumentSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file a save chooser's answer names once ".pdf" is added.  The chooser only asked about
 * replacing the name as typed ("report"), so replacing the file the extension leads to
 * ("report.pdf") is asked about here.
 */
public final class PdfTargets {

    private PdfTargets() {
    }

    /**
     * @return the chosen file with ".pdf" added when missing, or null if the user chose not to
     * replace the existing file that names
     */
    public static Path confirm(Window owner, File chosen) {
        if (chosen == null) return null;
        Path typed = chosen.toPath();
        Path target = DocumentSession.withPdfExtension(typed);
        if (target.equals(typed) || !Files.exists(target)) return target;
        ButtonType replace = new ButtonType(Messages.get("pdfTargets.replace"), ButtonBar.ButtonData.YES);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                Messages.format("pdfTargets.exists", target.getFileName()), replace, ButtonType.CANCEL);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        return alert.showAndWait().filter(b -> b == replace).isPresent() ? target : null;
    }
}
