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
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * The class <code>GoToAction</code> represents the Action type "GoTo".  It's
 * purpose when called to to navigate the a destination in the document
 * specified by this action.
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class GoToAction extends Action {

    public static final Name DESTINATION_KEY = new Name("D");

    // Destination to jump to, name, sting or array.
    private Destination destination;

    /**
     * Creates a new instance of a GoTo Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public GoToAction(Library l, Hashtable h) {
        super(l, h);
        // get the Destination for this action
        destination = new Destination(library, getObject(DESTINATION_KEY));
    }

    /**
     * Set the destination and adds the new data to the action's dictionary
     *
     * @param destination new destionat, replace old values.
     */
    public void setDestination(Destination destination) {
        entries.put(DESTINATION_KEY, destination.getObject());
        this.destination = destination;
    }

    /**
     * Gets the Destination object which the "GoTo" action should jump to.
     *
     * @return Destination object specified in the action.
     */
    public Destination getDestination() {
        return destination;
    }

}
