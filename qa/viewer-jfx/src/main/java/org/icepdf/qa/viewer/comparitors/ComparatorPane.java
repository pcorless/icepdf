package org.icepdf.qa.viewer.comparitors;

import javafx.scene.layout.BorderPane;
import org.icepdf.qa.viewer.common.Mediator;

/**
 * ComparatorPane base class for visual compare panes.
 */
public abstract class ComparatorPane extends BorderPane implements ComparatorPaneInterface {

    protected final Mediator mediator;

    public ComparatorPane(Mediator mediator) {
        this.mediator = mediator;
    }
}
