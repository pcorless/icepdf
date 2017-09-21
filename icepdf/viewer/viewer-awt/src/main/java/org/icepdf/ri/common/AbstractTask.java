/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import java.util.ResourceBundle;

/**
 * AbstractTask attempts to make it a little easier to track a task with a 'current' represented the progress of
 * the "lengthOfTask".
 */
public abstract class AbstractTask<T extends AbstractTask> {

    // total number of signatures to process.
    protected int lengthOfTask;
    // current progress, used for the progress bar
    protected int current = 0;
    // message displayed on progress bar
    protected String taskStatusMessage;
    // flags for threading
    protected boolean done = false;
    protected boolean canceled = false;
    // parent swing controller
    protected SwingController controller;
    // message bundle for internationalization
    protected ResourceBundle messageBundle;

    protected boolean taskRunning;

    public AbstractTask(
            SwingController controller,
            ResourceBundle messageBundle,
            int lengthOfTask) {
        this.controller = controller;
        this.lengthOfTask = lengthOfTask;
        this.messageBundle = messageBundle;
    }

    public int getCurrentProgress() {
        return current;
    }

    /**
     * Stop the task.
     */
    public void stop() {
        canceled = true;
        taskStatusMessage = null;
    }

    /**
     * Find out if the task has completed.
     *
     * @return true if task is done, false otherwise.
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Status of task.
     *
     * @return true if the task is running otherwise, false.
     */
    public boolean isCurrentlyRunning() {
        return taskRunning;
    }

    /**
     * Gets the length of the task in a unit of divisible work.  Current/lengthOfTask will return progress of task.
     *
     * @return total length of task.
     */
    public int getLengthOfTask() {
        return lengthOfTask;
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

    /**
     * Get the concrete instance of the task.
     *
     * @return currently encapsulated abstract task implementation.
     */
    public abstract T getTask();

}
