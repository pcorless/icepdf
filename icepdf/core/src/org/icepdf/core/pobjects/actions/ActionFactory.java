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
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * Factory for build actions
 *
 * @since 4.0
 */
public class ActionFactory {

    public static final int GOTO_ACTION = 1;
    public static final int URI_ACTION = 2;
    public static final int LAUNCH_ACTION = 3;
    public static final int GOTO_R_ACTION = 4;
    public static final int JAVA_SCRIPT_ACTION = 5;
    public static final int NAMED_ACTION = 6;
    public static final int SUBMIT_ACTION = 7;
    public static final int RESET_ACTION = 8;

    private ActionFactory() {
    }

    /**
     * Creates a new ACTION object of the type specified by the type constant.
     * Currently there are only two supporte action types; GoTo, Launch and URI.
     * <p/>
     * This call adds the new action object to the document library as well
     * as the document StateManager.
     *
     * @param library library to register action with
     * @param type    type of action to create
     * @return new action object of the specified type.
     */
    public static Action buildAction(Library library,
                                     int type) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        switch (type) {
            case GOTO_ACTION: {
                // set default goto action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_GOTO);
                // add a null destination entry
                entries.put(GoToAction.DESTINATION_KEY, new Destination(library, null));
                GoToAction action = new GoToAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case URI_ACTION: {
                // set default uri action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_URI);
                // add a null uri string entry
                Reference pObjectReference = stateManager.getNewReferencNumber();
                entries.put(URIAction.URI_KEY, new LiteralStringObject("", pObjectReference, library.getSecurityManager()));
                URIAction action = new URIAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case LAUNCH_ACTION: {
                // set default launch action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_LAUNCH);
                // add a null file string entry
                Reference pObjectReference = stateManager.getNewReferencNumber();
                entries.put(LaunchAction.FILE_KEY, new LiteralStringObject("", pObjectReference, library.getSecurityManager()));
                LaunchAction action = new LaunchAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case GOTO_R_ACTION: {
                // set default goto resource action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_GOTO_REMOTE);
                // The file in which the destination shall be located.
                Reference pObjectReference = stateManager.getNewReferencNumber();
                entries.put(GoToRAction.F_KEY, new LiteralStringObject("", pObjectReference, library.getSecurityManager()));
                GoToRAction action = new GoToRAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case JAVA_SCRIPT_ACTION: {
                // set default javascript action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_JAVA_SCRIPT);
                // The file in which the destination shall be located.
                entries.put(GoToRAction.F_KEY, "");
                JavaScriptAction action = new JavaScriptAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case NAMED_ACTION: {
                // set default named action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_NAMED);
                NamedAction action = new NamedAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case SUBMIT_ACTION: {
                // set default submit form action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_SUBMIT_SUBMIT);
                SubmitFormAction action = new SubmitFormAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
            case RESET_ACTION: {
                // set default reset form action values.
                entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
                entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_RESET_SUBMIT);
                ResetFormAction action = new ResetFormAction(library, entries);
                action.setPObjectReference(stateManager.getNewReferencNumber());
                return action;
            }
        }
        return null;
    }
}
