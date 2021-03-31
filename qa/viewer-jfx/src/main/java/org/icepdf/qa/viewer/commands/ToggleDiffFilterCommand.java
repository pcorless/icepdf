package org.icepdf.qa.viewer.commands;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.icepdf.qa.viewer.common.Mediator;

/**
 *
 */
public class ToggleDiffFilterCommand implements EventHandler<ActionEvent>, Command {

    private Mediator mediator;

    public ToggleDiffFilterCommand(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public void execute() {
        mediator.toggleDiffFilter();
    }

    @Override
    public void handle(ActionEvent event) {
        execute();
    }
}
