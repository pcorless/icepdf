/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.util.ResourceBundle;

/**
 * AbstractTask attempts to make it a little easier to great swing work user in the Viewer RI.
 */
public abstract class AbstractTask<T, V> extends SwingWorker<T, V> {

    // message displayed on progress bar
    protected String taskStatusMessage;
    protected int taskProgress;
    protected int lengthOfTask;

    // parent swing controller
    protected Controller controller;
    // message bundle for internationalization
    protected ResourceBundle messageBundle;
    // parent pane which will be updated for progress and messages.
    protected AbstractWorkerPanel workerPanel;

    public AbstractTask(Controller controller, AbstractWorkerPanel workerPanel, ResourceBundle messageBundle) {
        this.controller = controller;
        this.workerPanel = workerPanel;
        this.messageBundle = messageBundle;
    }

    /**
     * Returns the most recent dialog message, or null
     * if there is no current dialog message.
     *
     * @return current message dialog text.
     */
    public String getMessage() {
        return taskStatusMessage;
    }

}
