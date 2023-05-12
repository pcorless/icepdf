package org.icepdf.qa.viewer.commands;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.icepdf.qa.viewer.common.Mediator;


public class NextDiffFilterCommand implements EventHandler<ActionEvent>, Command {

    private final Mediator mediator;

    public NextDiffFilterCommand(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public void execute() {
        mediator.nextDiffFilter();
    }

    @Override
    public void handle(ActionEvent event) {
        execute();
    }
}
