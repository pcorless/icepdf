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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <p>The uniform resource identifier (URI) action represents destination
 * that is a hypertext link</p>
 * <p/>
 * <p>The URI can be extracted from this object so that the content can
 * be loaded in a web browser.  ICEpdf does not currently support image map
 * URI's.</p>
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class URIAction extends Action {

    public static final Name URI_KEY = new Name("URI");

    // uniform resource identifier to be resolved.
    private StringObject URI;

    // specifies whether to track the mouse position.
    private boolean isMap;

    /**
     * Creates a new instance of a Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public URIAction(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * Sets the URI string associated witht this action.
     * 
     * @param URI an string value except null.
     */
    public void setURI(String URI) {
        StringObject tmp = new LiteralStringObject(
                URI, getPObjectReference(), library.securityManager);
        // StringObject detection should allow writer to pick on encryption.
        entries.put(URIAction.URI_KEY, tmp);
        this.URI = tmp;
    }

    /**
     * Gets the Uniform resource identifier to resolve, encoded in 7-bit ASCII.
     *
     * @return uniform resouce.
     */
    public String getURI() {
        // URI should always be text, but there have been examples of
        // Hex encoded uri values.
        Object actionURI = getObject(URI_KEY);
        if (actionURI instanceof StringObject) {
            URI = (StringObject) actionURI;
        }
        return URI.getDecryptedLiteralString(library.securityManager);
    }

    /**
     * Gets a flag specifying whether to track thee mouse poisition when the
     * URI is resolved.  Default value is false.
     *
     * @return true if tmouse poiiin is to be called.
     */
    public boolean isMap() {
        return isMap;
    }

}
