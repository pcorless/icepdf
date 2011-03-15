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

import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <p>This class represents a PDF document's <i>Dictionary</i> object.  A
 * <i>Dictionary</i> object is an associative table containing pairs of objects,
 * known as the dictionary's entries.  The first element of each entry is the key
 * and the second element is the value.  Dictionary objects are the main building
 * blocks of a PDF document.  They are commonly used to collect and tie together
 * the attributes of complex objects such as fonts or pages within a
 * document. </p>
 * <p>Most of the Objects found in the package org.icepdf.core.pobject.* extend
 * this class.  Dictionary objects by convention have a "Type" entry which
 * identifies the type of object the dictionary describes. Classes that extend
 * Dictionary add functionality based on the specified Dictionary type.</p>
 *
 * @since 1.0
 */
public class Dictionary {

    public static final Name TYPE_KEY = new Name("Type");
    
    public static final Name SUBTYPE_KEY = new Name("Subtype");

    /**
     * Pointer to the documents <code>Library</code> object which
     * acts a central repository for the access of PDF object in the document.
     */
    protected Library library;

    /**
     * Table of associative pairs of objects.
     */
    protected Hashtable<Object, Object> entries;

    /**
     * Indicates if Dictionary has been initiated.
     */
    protected boolean inited;

    /**
     * Flag to indicate this object has been flaged for deletion.
     */
    protected boolean isDeleted;

    /**
     * Flags the object as new and not previously saved in the file
     */
    protected boolean isNew;

    // reference of stream, needed for encryption support
    private Reference pObjectReference;

    /**
     * Creates a new instance of a Dictionary.
     *
     * @param library document library.
     * @param entries dictionary entries.
     */
    public Dictionary(Library library, Hashtable entries) {
        this.library = library;
        this.entries = entries;
        if (this.entries == null) {
            this.entries = new Hashtable<Object, Object>();
        }
    }

    /**
     * <p>Sets the reference used to identify this Dictionary in the PDF document.
     * The reference number and generation number of this reference is needed by
     * the encryption algorithm to correctly decrypt this object.</p>
     * <p>This method should only be used by the PDF Parser.  Use of this method
     * outside the context of the PDF Parser may result in unpredictable
     * behavior. </p>
     *
     * @param reference Reference used to identify this Dictionary in the PDF
     *                  document.
     * @see #getPObjectReference()
     */
    public void setPObjectReference(Reference reference) {
        pObjectReference = reference;
    }

    /**
     * <p>Gets the reference used to identify this Dictionary in the PDF
     * document.  The reference number and generation number of this reference
     * is needed by the encryption algorithm to correctly decrypt this object.</p>
     *
     * @return Reference used to identify this Dictionary in a PDF document.
     * @see #setPObjectReference(org.icepdf.core.pobjects.Reference)
     */
    public Reference getPObjectReference() {
        return pObjectReference;
    }

    /**
     * Initiate the Dictionary. Retrieve any needed attributes.
     */
    public void init() {
    }

    /**
     * Gets a copy of the entries that make up the Dictionary.
     *
     * @return a copy of the Dictionary's entries.
     */
    public Hashtable<Object, Object> getEntries() {
        return entries;
    }

    public Object getObject(String key) {
        return library.getObject(entries, key);
    }

    public Object getObject(Name key) {
        return library.getObject(entries, key.getName());
    }

    /**
     * Gets a Number specified by the <code>key</code> in the dictionary
     * entries.  If the value is a reference, the Number object that the
     * reference points to is returned.  If the key cannot be found,
     * or the resulting object is not a Number, then null is returned.
     *
     * @param key key to find in entries Hashtable.
     * @return Number that the key refers to
     */
    protected Number getNumber(String key) {
        return library.getNumber(entries, key);
    }

    /**
     * Gets an int specified by the <code>key</code> in the dictionary
     * entries.  If the value is a reference, the int value that the
     * reference points to is returned.
     *
     * @param key key to find in entries Hashtable.
     * @return int value if a valid key,  else zero if the key does not point
     *         to an int or is invalid.
     */
    public int getInt(String key) {
        return library.getInt(entries, key);
    }

    /**
     * Gets a float specified by the <code>key</code> in the dictionary
     * entries.  If the value is a reference, the float value that the
     * reference points to is returned.
     *
     * @param key key to find in entries Hashtable.
     * @return float value if a valid key,  else zero if the key does not point
     *         to a float or is invalid.
     */
    public float getFloat(String key) {
        return library.getFloat(entries, key);
    }

    /**
     * Gets the PDF Documents Library.  A Library object is the central repository
     * of all objects that make up the PDF document hierarchy.
     *
     * @return documents library.
     */
    public Library getLibrary() {
        return library;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    /**
     * Returns a summary of the dictionary entries.
     *
     * @return dictionary values.
     */
    public String toString() {
        return getClass().getName() + "=" + entries.toString();
    }
}



