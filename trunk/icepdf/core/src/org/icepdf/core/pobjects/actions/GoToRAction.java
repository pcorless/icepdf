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
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <p>A remote go-to action is similar to an ordinary go-to action but jumps to
 * a destination in another PDF file instead of the current file. </p>
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class GoToRAction extends Action {

    // path to external file, see section 3.10.1 for more details on
    // resolving paths
    private String externalFile;
    private FileSpecification fileSpecification;

    // location in document that should be loaded.
    private Destination externalDestination;

    // new window?
    private Boolean isNewWindow;

    /**
     * Creates a new instance of a Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public GoToRAction(Library l, Hashtable h) {
        super(l, h);

        externalDestination =
                new Destination(library, library.getObject(entries, "D"));

        if (library.getObject(entries, "F") instanceof Hashtable) {
            fileSpecification =
                    new FileSpecification(library,
                            library.getDictionary(entries, "F"));
        } else if (library.getObject(entries, "F") instanceof StringObject) {
            externalFile =
                    ((StringObject) library.getObject(entries, "F"))
                            .getDecryptedLiteralString(
                                    library.getSecurityManager());
        }

        isNewWindow = library.getBoolean(entries, "NewWindow");

    }

    /**
     * Gets the destination associated with the external file path.
     *
     * @return destination object if any to be resolved.
     */
    public Destination getDestination() {
        return externalDestination;
    }

    /**
     * Gets the external file path
     *
     * @return file path of document to be opened.
     */
    public String getFile() {
        return externalFile;
    }

    /**
     * Gets the file specification of the destination file.  This objects should
     * be interigated to deside what should be done
     *
     * @return file specification, maybe nukll if external file was specified.
     */
    public FileSpecification getFileSpecification() {
        return fileSpecification;
    }

    /**
     * Indicates if the external document should be loaded in a new window or if
     * it should be loaded in the current.
     *
     * @return true indicates a new windows should be launched for the remote
     *         document; otherwise, false.
     */
    public Boolean isNewWindow() {
        return isNewWindow;
    }
}
