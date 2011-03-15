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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <h2>Refer to: 8.4.3 Border Styles</h2>
 * <p/>
 * <table border=1>
 * <tr>
 * <td>Key</td>
 * <td>Type</td>
 * <td>Value</td>
 * </tr>
 * <tr>
 * <td><b>S</b></td>
 * <td>name</td>
 * <td><i>(Optional)</i> A name representing the border effect to apply. Possible values are:
 * <table border=0>
 * <tr>
 * <td>S</td>
 * <td>No effect: the border is as described by the annotation dictionary's <b>BS</b> entry.</td>
 * </tr>
 * <tr>
 * <td>C</td>
 * <td>The border should appear "cloudy". The width and dash array specified by <b>BS</b>
 * are honored.</td>
 * </tr>
 * </table>
 * Default value: S.</td>
 * </tr>
 * <tr>
 * <td><b>I</b></td>
 * <td>number</td>
 * <td><i>(Optional; valid only if the value of <b>S</b> is C)</i> A number describing the intensity of the effect.
 * Suggested values range from 0 to 2. Default value: 0.</td>
 * </tr>
 * </table>
 *
 * @author Mark Collette
 * @since 2.5
 */
public class BorderEffect extends Dictionary {
    /**
     * Creates a new instance of a BorderEffect.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public BorderEffect(Library l, Hashtable h) {
        super(l, h);
    }
}
