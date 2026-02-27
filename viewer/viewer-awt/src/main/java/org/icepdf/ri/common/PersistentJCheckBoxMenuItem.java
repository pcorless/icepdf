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
package org.icepdf.ri.common;

import javax.swing.*;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import javax.swing.plaf.synth.SynthCheckBoxMenuItemUI;

/**
 * JCheckboxMenuItem that doesn't hide the menu when clicked
 */
public class PersistentJCheckBoxMenuItem extends JCheckBoxMenuItem {

    public PersistentJCheckBoxMenuItem(String title) {
        super(title);
    }

    public PersistentJCheckBoxMenuItem(String title, boolean b) {
        super(title, b);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (getUI() instanceof SynthCheckBoxMenuItemUI) {
            setUI(new SynthCheckBoxMenuItemUI() {
                @Override
                protected void doClick(MenuSelectionManager msm) {
                    menuItem.doClick(0);
                }
            });
        } else {
            setUI(new BasicCheckBoxMenuItemUI() {
                @Override
                protected void doClick(MenuSelectionManager msm) {
                    menuItem.doClick(0);
                }
            });
        }
    }
}
