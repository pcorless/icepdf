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

    /**
     * Creates a new ACTION object of the type specified by the type constant.
     * Currently there are only two supporte action types; GoTo and URI.
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
        }
        return null;
    }
}
