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
package org.icepdf.core.pobjects;

/**
 * The class represents a generic PDF object.  Each PDF object can be identified
 * by a unique reference object, which contains the objects object number and
 * generation number.
 *
 * @see org.icepdf.core.pobjects.Reference
 * @since 1.0
 */
public class PObject {
    private Object object;
    private Reference objectReference = null;

    /**
     * Create a new PObject.
     *
     * @param object           a PDF object that is associated by the objectNumber and
     *                         and objectGeneration data
     * @param objectNumber     the object number of the PDF object
     * @param objectGeneration the generation number of the PDF object
     */
    public PObject(Object object, Number objectNumber, Number objectGeneration) {
        this.object = object;
        objectReference = new Reference(objectNumber, objectGeneration);
    }

    /**
     * Create a new PObject.
     *
     * @param object          a PDF object that is associated by the objectNumber and
     *                        and objectGeneration data
     * @param objectReference Reference object which contains the PDF objects
     *                        number and generation data
     */
    public PObject(Object object, Reference objectReference) {
        this.object = object;
        this.objectReference = objectReference;
    }

    /**
     * Gets the reference information for this PDF object.
     *
     * @return Reference object which contains the PDF objects
     *         number and generation data
     */
    public Reference getReference() {
        return objectReference;
    }

    /**
     * Gets the generic PDF Object stored at this object number and generation.
     *
     * @return object refrenced byt he object number and generation
     */
    public Object getObject() {
        return object;
    }

    @Override
    public int hashCode() {
        int result = object != null ? object.hashCode() : 0;
        result = 31 * result + (objectReference != null ? objectReference.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PObject pObject = (PObject) o;

        if (object != null ? !object.equals(pObject.object) : pObject.object != null) {
            return false;
        }
        if (objectReference != null ? !objectReference.equals(pObject.objectReference) : pObject.objectReference != null) {
            return false;
        }

        return true;
    }

    /**
     * String representation of this object.  Used mainly for debugging.
     *
     * @return string representation of this object
     */
    public String toString() {
        return objectReference.toString() + "  " + object.toString();
    }
}
