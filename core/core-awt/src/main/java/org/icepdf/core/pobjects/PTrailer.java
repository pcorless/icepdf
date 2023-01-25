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

import org.icepdf.core.util.Library;

import java.util.List;

/**
 * <P>The trailer of a PDF file enables an application reading the file to quickly
 * find the cross-reference table and certain special objects. Applications
 * should read a PDF file from its end. The last line of the file contains only
 * the end-of-file marker, %%EOF.</p>
 * <br>
 * <p>A document can have more then one trailer reference.  It is important to use
 * the addTrailer() method if a subsequent trailer is found, or the
 * addPreviousTrailer() method if a previous trailer is found, depending on if
 * the PDF file is being read linearly, or via random access seeking.</p>
 * <br>
 * <p>If the Prev key entry is present then the document has more then one
 * cross-reference section.  There is a numerical value, which is typically
 * associated with the trailer, that comes after startxref, and before %%EOF.
 * It is byte offset from the beginning of the file to the beginning of the last
 * cross-reference section.</p>
 * <br>
 * <p>In a regular PDF, it's the address of the current xref table.  In a linearized
 * PDF, it's the address of the xref table at the file beginning, or zero.
 * In an updated PDF, it's the address of the current xref table. In all cases,
 * the LastCrossReferenceSection field, at the end of the PDF file, points
 * to the byte offset from the beginning of the file, of the "last" xref section,
 * which means the xref section with the highest precedence. For each xref section,
 * its following trailer section has a Prev field, which points to the byte
 * offset from the beginning of the file, of the xref section with one less
 * degree of precedence.</p>
 *
 * @since 1.1
 */
public class PTrailer extends Dictionary {

    public static final Name SIZE_KEY = new Name("Size");
    public static final Name PREV_KEY = new Name("Prev");
    public static final Name ROOT_KEY = new Name("Root");
    public static final Name ENCRYPT_KEY = new Name("Encrypt");
    public static final Name INFO_KEY = new Name("Info");
    public static final Name ID_KEY = new Name("ID");
    public static final Name XREF_STRM_KEY = new Name("XRefStm");
    public static final Name TYPE_KEY = new Name("Type");

    // Stream specific
    public static final Name INDEX_KEY = new Name("Index");
    public static final Name W_KEY = new Name("W");

    /**
     * Create a new PTrailer object
     *
     * @param library           document library
     * @param dictionaryEntries trailer dictionary
     */
    public PTrailer(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
    }

    /**
     * Gets the total number of entries in the file's cross-reference table, as
     * defined by the combination of the original section and all updated sections.
     * Equivalently, this value is 1 greater than the highest object number
     * used in the file.
     * <ul>
     * <li>Note: Any object in a cross-reference section whose number is
     * greater than this value is ignored and considered missing.</li>
     * </ul>
     * <br>
     * <b>Required : </b> must not be an indirect reference
     *
     * @return total number of entries in the file's cross-reference table
     */
    public int getNumberOfObjects() {
        return library.getInt(entries, SIZE_KEY);
    }

    /**
     * Gets the byte offset from the beginning of the file to the beginning of the
     * previous cross-reference section.
     * <br>
     * (Present only if the file has more than one cross-reference section; must
     * not be an indirect reference)
     *
     * @return byte offset from beginning of the file to the beginning of the
     * previous cross-reference section
     */
    public long getPrev() {
        return library.getInt(entries, PREV_KEY);
    }

    /**
     * Gets the catalog reference for the PDF document contained in the file.
     * <br>
     * <b>Required : </b> must not be an indirect reference
     *
     * @return reference number of catalog reference.
     */
    public Reference getRootCatalogReference() {
        return library.getObjectReference(entries, ROOT_KEY);
    }

    /**
     * Gets the Catalog entry for this PDF document.
     *
     * @return Catalog entry.
     */
    public Catalog getRootCatalog() {
        Object tmp = library.getObject(entries, ROOT_KEY);
        // specification states the root entry must be a indirect
        if (tmp instanceof Catalog) {
            return (Catalog) tmp;
        }
        // there are however a few instances where the dictionary is specified
        // directly
        else if (tmp instanceof DictionaryEntries) {
            return new Catalog(library, (DictionaryEntries) tmp);
        }
        // if no root was found we return so that the use will be notified
        // of the problem which is the PDF can not be loaded.
        else {
            return null;
        }
    }

    /**
     * The document's encryption dictionary
     * <br>
     * <b>Required : </b> if document is encrypted; PDF 1.1
     *
     * @return encryption dictionary
     */
    public DictionaryEntries getEncrypt() {
        Object encryptParams = library.getObject(entries, ENCRYPT_KEY);
        if (encryptParams instanceof DictionaryEntries) {
            return (DictionaryEntries) encryptParams;
        } else {
            return null;
        }
    }

    /**
     * The document's information dictionary
     * <br>
     * <b>Optional : </b> must be an indirect reference.
     *
     * @return information dictionary
     */
    public PInfo getInfo() {
        final Object info = library.getObject(entries, INFO_KEY);
        if (info instanceof DictionaryEntries) {
            final PInfo pInfo = new PInfo(library, (DictionaryEntries) info);
            pInfo.setPObjectReference(library.getReference(entries, INFO_KEY));
            return pInfo;
        } else {
            return null;
        }
    }

    /**
     * A vector of two strings constituting a file identifier
     * <br>
     * <b>Optional : </b> PDF 1.1.
     *
     * @return vector containing constituting file identifier
     */
    public List getID() {
        return library.getArray(entries, ID_KEY);
    }

    /**
     * @return true if the trailer is a compressed trailer, false if it's compressed
     */
    public boolean isCompressedXref() {
        return entries.containsKey(TYPE_KEY);
    }

    /**
     * Get the trailer object's dictionary.
     *
     * @return dictionary
     */
    public DictionaryEntries getDictionary() {
        return entries;
    }

    /**
     * Returns a summary of the PTrailer dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "PTRAILER= " + entries.toString();
    }
}
