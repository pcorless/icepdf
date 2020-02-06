package org.icepdf.ri.common;

import javax.swing.*;
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
        setUI(new SynthCheckBoxMenuItemUI() {
            @Override
            protected void doClick(MenuSelectionManager msm) {
                menuItem.doClick(0);
            }
        });
    }
}
