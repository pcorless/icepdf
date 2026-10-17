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

import javafx.scene.control.Control;
import javafx.scene.control.Label;

/** Screen-reader helpers for the viewer's controls. */
public final class Accessibility {

    private static final Object NAME = new Object();

    private Accessibility() {
    }

    /**
     * Names a control for a screen reader without showing a label.  Text fields and combo boxes
     * are named only by their label (their accessible text is their value), so a field with just a
     * prompt or a neighbouring "of 12" reads as "edit, blank".  The label is never shown; it answers
     * through its accessible text since, outside a scene, it has no skin.
     */
    public static void name(Control control, String text) {
        Label label = (Label) control.getProperties().get(NAME);
        if (label == null) {
            label = new Label();
            label.setLabelFor(control);
            control.getProperties().put(NAME, label);
        }
        label.setText(text);
        label.setAccessibleText(text);
    }
}
