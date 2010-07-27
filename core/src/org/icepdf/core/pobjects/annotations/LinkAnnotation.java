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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <h2>Refer to: 8.4.5 Annotation Types</h2>
 * <p/>
 * <table border=1> <tr> <td>Key</td> <td>Type</td> <td>Value</td> </tr> <tr>
 * <td><b>Subtype</b></td> <td>name</td> <td><i>(Required)</i> The type of
 * annotation that this dictionary describes; must be <b>Link</b> for a link
 * annotation.</td> </tr> <tr> <td><b>Dest</b></td> <td>array, name, or
 * string</td> <td><i>(Optional; not permitted if an <b>A</b> entry is
 * present)</i> A destination to be displayed when the annotation is activated
 * (see Section 8.2.1, "Destinations"; see also implementation note 90 in
 * Appendix H).</td> </tr> <tr> <td><b>H</b></td> <td>name</td>
 * <td><i>(Optional; PDF 1.2)</i> The annotation's <i>highlighting mode</i>, the
 * visual effect to be used when the mouse button is pressed or held down inside
 * its active area: <table border=0> <tr> <td>N</td> <td>(None) No
 * highlighting.</td> </tr> <tr> <td>I</td> <td>(Invert) Invert the contents of
 * the annotation rectangle.</td> </tr> <tr> <td>O</td> <td>(Outline) Invert the
 * annotation's border.</td> </tr> <tr> <td>P</td> <td>(Push) Display the
 * annotation as if it were being pushed below the surface of the page; see
 * implementation note 91 in Appendix H.<br> Acrobat viewer displays the link
 * appearance with bevel border, ignoring any down appearance.</td> </tr>
 * </table>Default value: I.</td> </tr> <tr> <td><b>QuadPoints</b></td>
 * <td>array</td> <td><i>(Optional; PDF 1.6)</i> An array of 8 x n numbers
 * specifying the coordinates of n quadrilaterals in default user space that
 * comprise the region in which the link should be activated. The coordinates
 * for each quadrilateral are given in the order<br> x1 y1 x2 y2 x3 y3 x4 y4<br>
 * specifying the four vertices of the quadrilateral in counterclockwise order.
 * For orientation purposes, such as when applying an underline border style,
 * the bottom of a quadrilateral is the line formed by (x1, y1) and (x2, y2). If
 * this entry is not present or the viewer application does not recognize it,
 * the region specified by the <b>Rect</b> entry should be used.
 * <b>QuadPoints</b> should be ignored if any coordinate in the array lies
 * outside the region specified by <b>Rect</b>.</td> </tr> </table>
 *
 * @author Mark Collette
 * @since 2.5
 */
public class LinkAnnotation extends Annotation {

    /**
     * Key used to indcate highlight mode.
     */
    public static final Name DESTINATION_KEY = new Name("Dest");

    /**
     * Key used to indcate highlight mode.
     */
    public static final Name HIGHLIGHT_MODE_KEY = new Name("H");

    /**
     * Indicates that the annotation has no highlight effect.
     */
    public static final String HIGHLIGHT_NONE = "N";

    /**
     * Indicates that the annotation rectangle colours should be inverted for
     * its highlight effect.
     */
    public static final String HIGHLIGHT_INVERT = "I";

    /**
     * Indicates that the annotation rectangle border should be inverted for its
     * highlight effect.
     */
    public static final String HIGHLIGHT_OUTLINE = "O";

    /**
     * Indicates that the annotation rectangle border should be pushed below the
     * surface of th page.
     */
    public static final String HIGHLIGHT_PUSH = "P";

    /**
     * Creates a new instance of a LinkAnnotation.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public LinkAnnotation(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * <p>Gets the link annotations highlight mode (visual effect)taht should
     * be displayed when the mouse button is pressed or held down inside it's
     * active area.</p>
     *
     * @return one of the predefined highlight effects, HIGHLIGHT_NONE,
     *         HIGHLIGHT_OUTLINE or HIGHLIGHT_PUSH.
     */
    public String getHighlightMode() {
        Object possibleName = getObject(HIGHLIGHT_MODE_KEY);
        if (possibleName instanceof Name) {
            Name name = (Name) possibleName;
            if (name.getName().equalsIgnoreCase(HIGHLIGHT_NONE)) {
                return HIGHLIGHT_NONE;
            } else if (name.getName().equalsIgnoreCase(HIGHLIGHT_OUTLINE)) {
                return HIGHLIGHT_OUTLINE;
            } else if (name.getName().equalsIgnoreCase(HIGHLIGHT_PUSH)) {
                return HIGHLIGHT_PUSH;
            }
        }
        return HIGHLIGHT_INVERT;
    }

    /**
     * A destination to be displayed when the annotation is ativated.  Only
     * permitted if an A entry is not present.
     *
     * @return annotation target destination, null if not present in
     *         annotation.
     */
    public Destination getDestination() {
        Object obj = getObject(DESTINATION_KEY);
        if (obj != null) {
            return new Destination(library, obj);
        }
        return null;
    }
}
