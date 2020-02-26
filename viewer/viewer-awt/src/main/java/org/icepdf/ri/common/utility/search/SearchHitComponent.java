package org.icepdf.ri.common.utility.search;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class SearchHitComponent extends JComponent {
    protected String text;

    protected SearchHitComponent(String text) {
        this.text = text;
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    doAction();
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    showMenu();
                }
            }
        });
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    protected abstract void doAction();

    protected abstract void showMenu();
}
