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
package org.icepdf.core.pobjects;

import java.io.Serializable;

/**
 * <p>The <code>Reference</code> class represents a PDF object reference number.
 * A reference is made of two components:</p>
 * <ul>
 * <li>objectNumnber -  a unique number that identifies this object from other
 * objects in the PDF document.</li>
 * <li>bjectGeneration - identifies the generation number of the Reference.  By
 * default, the value is normally 0, but if the document
 * has been modifed by another user it will be incrementd
 * to keep track of changes.</li>
 * </ul>
 *
 * @since 1.0
 */
public class Reference implements Serializable {
    // object number
    int objf = 0;
    // object generation number.
    int genf = 0;

    /**
     * Creates a new instance of a Reference.
     *
     * @param o object number
     * @param g generation number
     */
    public Reference(Number o, Number g) {
        if (o != null) {
            objf = o.intValue();
        }
        if (g != null) {
            genf = g.intValue();
        }
    }

    /**
     * Creates a new instance of a Reference.
     *
     * @param o object number
     * @param g generation number
     */
    public Reference(int o, int g) {
        objf = o;
        genf = g;
    }

    /**
     * Creates a unique hash code for this reference object.
     *
     * @return hashcode.
     */
    public int hashCode() {
        return objf * 1000 + genf;
    }

    /**
     * Indicates whether some other reference object is "equal to" this one.
     *
     * @param obj reference object to compare to this reference.
     * @return tru,e if the two objects are equal; false, otherwise.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj != null && obj instanceof Reference) {
            Reference tmp = (Reference) obj;
            return (tmp.objf == objf) && (tmp.genf == genf);
        }
        return false;
    }

    /**
     * Gets the object number represented by this reference.
     *
     * @return object number
     */
    public int getObjectNumber() {
        return objf;
    }

    /**
     * Gets the generation number represented by this reference.
     *
     * @return generation number
     */
    public int getGenerationNumber() {
        return genf;
    }

    /**
     * Gets a string summary of the reference objects number and generation number.
     *
     * @return summary of reference object data.
     */
    public String toString() {
        return objf + " " + genf + "R";
    }
}
