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

import org.icepdf.core.util.Library;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * <P>The trailer of a PDF file enables an application reading the file to quickly
 * find the cross-reference table and certain special objects. Applications
 * should read a PDF file from its end. The last line of the file contains only
 * the end-of-file marker, %%EOF.</p>
 * <p/>
 * <p>A document can have more then one trailer reference.  It is important to use
 * the addTrailer() method if a subsequent trailer is found, or the
 * addPreviousTrailer() method if a previous trailer is found, depending on if
 * the PDF file is being read linearly, or via random access seeking.</p>
 * <p/>
 * <p>If the Prev key entry is present then the document has more then one
 * cross-reference section.  There is a numerical value, which is typically
 * associated with the trailer, that comes after startxref, and before %%EOF.
 * It is byte offset from the beginning of the file to the beginning of the last
 * cross-reference section.</p>
 * <p/>
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

    // Position in the file. The LazyObjectLoader typically keeps this info
    // for all PDF objects, but the bootstrapping PTrialer is an exception,
    // and we need its location for writing incremental updates, so for
    // consistency we'll have all PTrailers maintain their position.
    private long position;

    // documents cross reference table
    private CrossReference m_CrossReferenceTable;

    // documents cross reference stream.
    private CrossReference m_CrossReferenceStream;

    /**
     * Create a new PTrailer object
     *
     * @param dictionary dictionary associated with the trailer
     */
    public PTrailer(Library library, Hashtable dictionary, CrossReference xrefTable, CrossReference xrefStream) {
        super(library, dictionary);

        m_CrossReferenceTable = xrefTable;
        m_CrossReferenceStream = xrefStream;
        if (m_CrossReferenceTable != null)
            m_CrossReferenceTable.setTrailer(this);
        if (m_CrossReferenceStream != null)
            m_CrossReferenceStream.setTrailer(this);
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
     * <p/>
     * <b>Required : </b> must not be an indirect reference
     *
     * @return total number of entries in the file's cross-reference table
     */
    public int getNumberOfObjects() {
        return library.getInt(entries, "Size");
    }

    /**
     * Gets the byte offset from the beginning of the file to the beginning of the
     * previous cross-reference section.
     * <p/>
     * (Present only if the file has more than one cross-reference section; must
     * not be an indirect reference)
     *
     * @return byte offset from beginning of the file to the beginning of the
     *         previous cross-reference section
     */
    public long getPrev() {
        return library.getLong(entries, "Prev");
    }

    /**
     * Depending on if the PDF file is version 1.4 or before, or 1.5 or after,
     * it may have a cross reference table, or cross reference stream, or both.
     * If there are both, then the cross reference table has precedence.
     *
     * @return the cross reference object with the highest precedence, for this trailer
     */
    protected CrossReference getPrimaryCrossReference() {
        if (m_CrossReferenceTable != null)
            return m_CrossReferenceTable;
        loadXRefStmIfApplicable();
        return m_CrossReferenceStream;
    }

    /**
     * Gets the cross reference table.
     *
     * @return cross reference table object; null, if one does not exist.
     */
    protected CrossReference getCrossReferenceTable() {
        return m_CrossReferenceTable;
    }

    /**
     * Gets the cross reference stream.
     *
     * @return cross reference stream object; null, if one does not exist.
     */
    protected CrossReference getCrossReferenceStream() {
        return m_CrossReferenceStream;
    }

    /**
     * Gets the catalog reference for the PDF document contained in the file.
     * <p/>
     * <b>Required : </b> must not be an indirect reference
     *
     * @return reference number of catalog reference.
     */
    public Reference getRootCatalogReference() {
        return library.getObjectReference(entries, "Root");
    }

    /**
     * Gets the Catalog entry for this PDF document.
     *
     * @return Catalog entry.
     */
    public Catalog getRootCatalog() {
        Object tmp = library.getObject(entries, "Root");
        // specification states the the root entry must be a indirect
        if (tmp instanceof Catalog) {
            return (Catalog) library.getObject(entries, "Root");
        }
        // there are however a few instances where the dictionary is specified
        // directly
        else if (tmp instanceof Hashtable) {
            return new Catalog(library, (Hashtable) tmp);
        }
        // if no root was found we return so that the use will be notified
        // of the problem which is the PDF can not be loaded.
        else {
            return null;
        }
    }

    /**
     * The document's encryption dictionary
     * <p/>
     * <b>Required : </b> if document is encrypted; PDF 1.1
     *
     * @return encryption dictionary
     */
    public Hashtable getEncrypt() {
        Object encryptParams = library.getObject(entries, "Encrypt");
        if (encryptParams instanceof Hashtable) {
            return (Hashtable) encryptParams;
        } else {
            return null;
        }
    }

    /**
     * The document's information dictionary
     * <p/>
     * <b>Optional : </b> must be an indirect reference.
     *
     * @return information dictionary
     */
    public PInfo getInfo() {
        Object info = library.getObject(entries, "Info");
        if (info instanceof Hashtable) {
            return new PInfo(library.getSecurityManager(), (Hashtable) info);
        } else {
            return null;
        }
    }

    /**
     * A vector of two strings constituting a file identifier
     * <p/>
     * <b>Optional : </b> PDF 1.1.
     *
     * @return vector containing constituting file identifier
     */
    public Vector getID() {
        return (Vector) library.getObject(entries, "ID");
    }

    /**
     * @return The position in te file where this trailer is located
     */
    public long getPosition() {
        return position;
    }

    /**
     * After this PTrailer is parsed, we store it's location within the PDF
     * here, for future use.
     */
    public void setPosition(long pos) {
        position = pos;
    }

    /**
     * Add the trailer dictionary to the current trailer object's dictionary.
     *
     * @param nextTrailer document trailer object
     */
    protected void addNextTrailer(PTrailer nextTrailer) {
        nextTrailer.getPrimaryCrossReference().addToEndOfChainOfPreviousXRefs(getPrimaryCrossReference());

        // Later key,value pairs take precedence over previous entries
        Hashtable nextDictionary = nextTrailer.getDictionary();
        Hashtable currDictionary = getDictionary();
        Enumeration currKeys = currDictionary.keys();
        while (currKeys.hasMoreElements()) {
            Object currKey = currKeys.nextElement();
            if (!nextDictionary.containsKey(currKey)) {
                Object currValue = currDictionary.get(currKey);
                nextDictionary.put(currKey, currValue);
            }
        }
    }

    protected void addPreviousTrailer(PTrailer previousTrailer) {
//System.out.println("PTrailer.addPreviousTrailer()");
        getPrimaryCrossReference().addToEndOfChainOfPreviousXRefs(previousTrailer.getPrimaryCrossReference());

        // Later key,value pairs take precedence over previous entries
        Hashtable currDictionary = getDictionary();
        Hashtable prevDictionary = previousTrailer.getDictionary();
        Enumeration prevKeys = prevDictionary.keys();
        while (prevKeys.hasMoreElements()) {
            Object prevKey = prevKeys.nextElement();
            if (!currDictionary.containsKey(prevKey)) {
                Object prevValue = prevDictionary.get(prevKey);
                currDictionary.put(prevKey, prevValue);
            }
        }
    }

    protected void onDemandLoadAndSetupPreviousTrailer() {
//System.out.println("PTrailer.onDemandLoadAndSetupPreviousTrailer() : " + this);
//try { throw new RuntimeException(); } catch(Exception e) { e.printStackTrace(); }
        long position = getPrev();
        if (position > 0L) {
            PTrailer prevTrailer = library.getTrailerByFilePosition(position);
            if (prevTrailer != null)
                addPreviousTrailer(prevTrailer);
        }
    }

    protected void loadXRefStmIfApplicable() {
        if (m_CrossReferenceStream == null) {
            long xrefStreamPosition = library.getLong(entries, "XRefStm");
            if (xrefStreamPosition > 0L) {
                // OK, this is a little weird, but basically, any XRef stream
                //  dictionary is also a Trailer dictionary, so our Parser
                //  makes both of the objects.
                // Now, we don't actually want to chain the trailer as our
                //  previous, but only want its CrossReferenceStream to make
                //  our own
                PTrailer trailer = library.getTrailerByFilePosition(xrefStreamPosition);
                if (trailer != null)
                    m_CrossReferenceStream = trailer.getCrossReferenceStream();
            }
        }
    }

    /**
     * Get the trailer object's dictionary.
     *
     * @return dictionary
     */
    public Hashtable getDictionary() {
        return entries;
    }

    /**
     * Returns a summary of the PTrailer dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "PTRAILER= " + entries.toString() + " xref table=" + m_CrossReferenceTable + "  xref stream=" + m_CrossReferenceStream;
    }
}
