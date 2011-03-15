/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * Factory for build actions
 *
 * @since 4.0
 */
public class ActionFactory {

    public static final int GOTO_ACTION = 1;
    public static final int URI_ACTION = 2;
    public static final int LAUNCH_ACTION = 3;

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
        Hashtable<Name, Object> entries = new Hashtable<Name, Object>();
        if (GOTO_ACTION == type) {
            // set default link annotation values.
            entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
            entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_GOTO);
            // add a null destination entry
            entries.put(GoToAction.DESTINATION_KEY, new Destination(library, null));
            GoToAction action = new GoToAction(library, entries);
            action.setPObjectReference(stateManager.getNewReferencNumber());
            return action;
        } else if (URI_ACTION == type) {
            // set default link annotation values.
            entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
            entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_URI);
            // add a null uri string entry
            entries.put(URIAction.URI_KEY, "");
            URIAction action = new URIAction(library, entries);
            action.setPObjectReference(stateManager.getNewReferencNumber());
            return action;
        } else if (LAUNCH_ACTION == type) {
            // set default link annotation values.
            entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
            entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_LAUNCH);
            // add a null file string entry
            entries.put(LaunchAction.FILE_KEY, "");
            LaunchAction action = new LaunchAction(library, entries);
            action.setPObjectReference(stateManager.getNewReferencNumber());
            return action;
        }
        return null;
    }
}
