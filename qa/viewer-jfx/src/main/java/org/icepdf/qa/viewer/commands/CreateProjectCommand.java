package org.icepdf.qa.viewer.commands;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.icepdf.qa.viewer.common.Mediator;

/**
 *
 */
public class CreateProjectCommand implements EventHandler<ActionEvent>, Command {

    private final Mediator mediator;

    public CreateProjectCommand(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public void handle(ActionEvent event) {
        execute();
    }

    public void execute() {
        mediator.showNewProjectDialog();
    }

}