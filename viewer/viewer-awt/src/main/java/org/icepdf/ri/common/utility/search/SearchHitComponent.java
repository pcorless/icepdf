/*
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
package org.icepdf.ri.common.utility.search;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class SearchHitComponent extends JComponent {
    protected final String text;

    protected SearchHitComponent(final String text) {
        this.text = text;
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
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

    @Override
    public int hashCode() {
        return getBounds().hashCode() + 3 * text.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof SearchHitComponent && ((SearchHitComponent) obj).getBounds().equals(getBounds()) && ((SearchHitComponent) obj).text.equals(text);
    }
}
