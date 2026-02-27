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
package org.icepdf.qa.viewer.commands;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.icepdf.qa.viewer.common.Mediator;

/**
 *
 */
public class ToggleDiffFilterCommand implements EventHandler<ActionEvent>, Command {

    private final Mediator mediator;

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
