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
package org.icepdf.core.pobjects;

import java.util.Objects;

/**
 * The class represents a generic PDF object.  Each PDF object can be identified
 * by a unique reference object, which contains the objects object number and
 * generation number.
 *
 * @see org.icepdf.core.pobjects.Reference
 * @since 1.0
 */
public class PObject {
    private final Object object;
    private final Reference objectReference;
    private int linearTraversalOffset;

    private boolean doNotEncrypt;

    /**
     * Create a new PObject.
     *
     * @param object           a PDF object that is associated by the objectNumber
     *                         and objectGeneration data
     * @param objectNumber     the object number of the PDF object
     * @param objectGeneration the generation number of the PDF object
     */
    public PObject(Object object, Number objectNumber, Number objectGeneration) {
        this.object = object;
        objectReference = new Reference(objectNumber, objectGeneration);
        if (object instanceof Dictionary) {
            Dictionary tmp = (Dictionary) object;
            tmp.setPObjectReference(objectReference);
        }
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

    public PObject(Object object, Reference objectReference, boolean doNotEncrypt) {
        this.object = object;
        this.objectReference = objectReference;
        this.doNotEncrypt = doNotEncrypt;
    }

    /**
     * Gets the reference information for this PDF object.
     *
     * @return Reference object which contains the PDF objects
     * number and generation data
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

        if (!Objects.equals(object, pObject.object)) {
            return false;
        }
        return Objects.equals(objectReference, pObject.objectReference);
    }

    public int getLinearTraversalOffset() {
        return linearTraversalOffset;
    }

    public void setLinearTraversalOffset(int linearTraversalOffset) {
        this.linearTraversalOffset = linearTraversalOffset;
    }

    /**
     * Flag dictionary as do not encrypt.  This is mainly used for the root xref dictionary, so we don't encrypt
     * StringObjects that are needed to open a document
     *
     * @return true if the dictionary StringObject values should not be encrypted on a document rewrite.
     */
    public boolean isDoNotEncrypt() {
        return doNotEncrypt;
    }

    public void setDoNotEncrypt(boolean doNotEncrypt) {
        this.doNotEncrypt = doNotEncrypt;
    }

    /**
     * String representation of this object.  Used mainly for debugging.
     *
     * @return string representation of this object
     */
    public String toString() {
        return objectReference.toString() + "  " + (object != null ? object.toString() : null);
    }
}
