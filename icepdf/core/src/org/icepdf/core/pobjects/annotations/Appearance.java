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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <h2>Refer to: 8.4.4 Appearance Streams</h2>
 * <p/>
 * <br>
 * An annotation can define as many as three separate appearances:
 * <ul>
 * <li> The normal appearance is used when the annotation is not interacting with the
 * user. This appearance is also used for printing the annotation.</li>
 * <li> The rollover appearance is used when the user moves the cursor into the annotation's
 * active area without pressing the mouse button.</li>
 * <li> The down appearance is used when the mouse button is pressed or held down
 * within the annotation's active area.</li>
 * </ul>
 * <p/>
 * <table border=1>
 * <tr>
 * <td>Key</td>
 * <td>Type</td>
 * <td>Value</td>
 * </tr>
 * <tr>
 * <td><b>N</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Required)</i> The annotation's normal appearance</td>
 * </tr>
 * <tr>
 * <td><b>R</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Optional)</i> The annotation's rollover appearance. Default value: the value of
 * the <b>N</b> entry.</td>
 * </tr>
 * <tr>
 * <td><b>D</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Optional)</i> The annotation's down appearance. Default value: the value of the
 * <b>N</b> entry.</td>
 * </tr>
 * </table>
 *
 * @author Mark Collette
 * @since 2.5
 */
public class Appearance extends Stream {
    /**
     * Create a new instance of an Appearance stream.
     *
     * @param l                  library containing a hash of all document objects
     * @param h                  hashtable of parameters specific to the Stream object.
     * @param streamInputWrapper Accessor to stream byte data
     */
    public Appearance(Library l, Hashtable h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h, streamInputWrapper);
    }
}
