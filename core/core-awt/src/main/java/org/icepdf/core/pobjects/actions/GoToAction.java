/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

/**
 * The class <code>GoToAction</code> represents the Action type "GoTo".  It's
 * purpose when called is to navigate to a destination in the document
 * specified by this action.
 *
 * @since 2.6
 */
public class GoToAction extends Action {

    public static final Name DESTINATION_KEY = new Name("D");

    /**
     * Creates a new instance of a GoTo Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public GoToAction(Library l, DictionaryEntries h) {
        super(l, h);
    }

    /**
     * Set the destination and adds the new data to the action's dictionary
     *
     * @param destination new destination, replace old values.
     */
    public void setDestination(Destination destination) {
        entries.put(DESTINATION_KEY, destination.getObject());
    }

    /**
     * Gets the Destination object which the "GoTo" action should jump to.
     *
     * @return Destination object specified in the action.
     */
    public Destination getDestination() {
        return new Destination(library, getObject(DESTINATION_KEY));
    }

}
