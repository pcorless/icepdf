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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.util.Library;

import java.util.Hashtable;


/**
 * <p>The <code>Action</code> class represents an <i>Action Dictionary</i> which defines
 * characteristics and behavior of an action.  A PDF action can be a wide
 * variety of standard action types.  This class is designed to help users
 * get needed attributes from the Action Dictionary.  The Dictionary classes
 * getEntries method can be used to find other attributes associated with this action.</p>
 * <p/>
 * <p>ICEpdf currently only uses the "GoTo" action when working with document
 * outlines.  If your application is interpreting a page's Annotations then you
 * can query the Annotation object to get its Action.  </p>
 *
 * @since 1.0
 */
public class Action extends Dictionary {

    public static final String ACTION_TYPE = "S";

    public static final String ACTION_TYPE_GOTO = "GoTo";

    public static final String ACTION_TYPE_GOTO_REMOTE = "GoToR";

    public static final String ACTION_TYPE_LAUNCH = "Launch";

    public static final String ACTION_TYPE_URI = "URI";


    // type of annotation
    private String type;

    // todo implement next
    // private Object Next

    /**
     * Creates a new instance of a Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public Action(Library l, Hashtable h) {
        super(l, h);
        type = library.getObject(entries, "S").toString();
    }

    /**
     * <p>Gets the type of action that this dictionary describes.  The most
     * common actions can be found in the PDF Reference 1.6 in section
     * 8.5.3.  ICEpdf currently only takes advantage of the "GoTo" action
     * when a user clicks on a document outline. </p>
     *
     * @return The action type.
     */
    public String getType() {
        return type;
    }

}
