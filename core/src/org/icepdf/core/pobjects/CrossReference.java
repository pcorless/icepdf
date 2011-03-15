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
import org.icepdf.core.util.Parser;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author Mark Collette
 * @since 2.0
 */

public class CrossReference {

    private static final Logger logger =
            Logger.getLogger(CrossReference.class.toString());

//    private Vector<Entry> m_vXRefEntries;
    private Hashtable<Number, Entry> m_hObjectNumber2Entry;

    /**
     * In a Linearized PDF, we don't want to load all Trailers and their XRefs
     * upfront, but would rather load the first upfront, and then lazily load
     * the rest.
     * If m_xrefPrevious != null, Then just use it
     * If m_xrefPrevious == null And m_PTrailer == null,
     * Then we can't do anything
     * If m_xrefPrevious == null And m_PTrailer != null,
     * Then use m_PTrailer to setup m_xrefPrevious
     */
    private PTrailer m_PTrailer;
    private CrossReference m_xrefPrevious;
    private CrossReference m_xrefPeer;
    private boolean m_bIsCrossReferenceTable;
    private boolean m_bHaveTriedLoadingPrevious;
    private boolean m_bHaveTriedLoadingPeer;

    public CrossReference() {
//        m_vXRefEntries = new Vector<Entry>(4096);
        m_hObjectNumber2Entry = new Hashtable<Number, Entry>(4096);
    }

    public void setTrailer(PTrailer trailer) {
        m_PTrailer = trailer;
    }

    public void addXRefTableEntries(Parser parser) {
        m_bIsCrossReferenceTable = true;
        try {
//System.out.println("Starting to read xref table     : " + (new java.util.Date()));
//int countOfAllEntries = 0;
            while (true) {
                Object startingObjectNumberOrTrailer = parser.getNumberOrStringWithMark(16);
                if (!(startingObjectNumberOrTrailer instanceof Number)) {
                    parser.ungetNumberOrStringWithReset();
                    break;
                }
                int startingObjectNumber = ((Number) startingObjectNumberOrTrailer).intValue();
                int numEntries = ((Number) parser.getToken()).intValue();
                int currNumber = startingObjectNumber;
                for (int i = 0; i < numEntries; i++) {
                    long tenDigitNum = parser.getIntSurroundedByWhitespace();  // ( (Number) getToken() ).longValue();
                    int generationNum = parser.getIntSurroundedByWhitespace(); // ( (Number) getToken() ).intValue();
                    char usedOrFree = parser.getCharSurroundedByWhitespace();  // ( (String) getToken() ).charAt( 0 );
                    if (usedOrFree == 'n')         // Used
                        addUsedEntry(currNumber, tenDigitNum, generationNum);
                    else if (usedOrFree == 'f')    // Free
                        addFreeEntry(currNumber, (int) tenDigitNum, generationNum);
                    //System.out.println("xref entry " + (i+1) + " of " + numEntries + "   tenDigitNum: " + tenDigitNum + ", generationNum: " + generationNum + ", usedOrFree: " + usedOrFree + ", objNum: " + currNumber);

                    currNumber++;
//countOfAllEntries++;
                }
            }
//System.out.println("Done reading xref table entries : " + (new java.util.Date()) + "  count: " + countOfAllEntries);
        }
        catch (IOException e) {
             logger.log(Level.SEVERE, "Error parsing xRef table entries.", e);
        }
    }

    /**
     * @param library        The Document's Library
     * @param xrefStreamHash Dictionary for XRef stream
     * @param streamInput    Decoded stream bytes for XRef stream
     */
    public void addXRefStreamEntries(Library library, Hashtable xrefStreamHash, InputStream streamInput) {
//System.out.println("Starting to read xref stream     : " + (new java.util.Date()));
//int countOfAllEntries = 0;
        try {
            int size = library.getInt(xrefStreamHash, "Size");
            Vector<Number> objNumAndEntriesCountPairs =
                    (Vector) library.getObject(xrefStreamHash, "Index");
            if (objNumAndEntriesCountPairs == null) {
                objNumAndEntriesCountPairs = new Vector<Number>(2);
                objNumAndEntriesCountPairs.add(0);
                objNumAndEntriesCountPairs.add(size);
            }
            Vector fieldSizesVec = (Vector) library.getObject(xrefStreamHash, "W");
            int[] fieldSizes = null;
            if (fieldSizesVec != null) {
                fieldSizes = new int[fieldSizesVec.size()];
                for (int i = 0; i < fieldSizesVec.size(); i++)
                    fieldSizes[i] = ((Number) fieldSizesVec.get(i)).intValue();
            }

            int fieldTypeSize = fieldSizes[0];
            int fieldTwoSize = fieldSizes[1];
            int fieldThreeSize = fieldSizes[2];
            for (int xrefSubsection = 0; xrefSubsection < objNumAndEntriesCountPairs.size(); xrefSubsection += 2) {
                int startingObjectNumber = ((Number) objNumAndEntriesCountPairs.get(xrefSubsection)).intValue();
                int entriesCount = ((Number) objNumAndEntriesCountPairs.get(xrefSubsection + 1)).intValue();
                int afterObjectNumber = startingObjectNumber + entriesCount;
                for (int objectNumber = startingObjectNumber; objectNumber < afterObjectNumber; objectNumber++) {
                    int entryType = Entry.TYPE_USED;    // Default value is 1
                    if (fieldTypeSize > 0)
                        entryType = Utils.readIntWithVaryingBytesBE(streamInput, fieldTypeSize);
                    if (entryType == Entry.TYPE_FREE) {
                        int nextFreeObjectNumber = Utils.readIntWithVaryingBytesBE(
                                streamInput, fieldTwoSize);
                        int generationNumberIfReused = Utils.readIntWithVaryingBytesBE(
                                streamInput, fieldThreeSize);
                        addFreeEntry(objectNumber, nextFreeObjectNumber, generationNumberIfReused);
                    } else if (entryType == Entry.TYPE_USED) {
                        long filePositionOfObject = Utils.readLongWithVaryingBytesBE(
                                streamInput, fieldTwoSize);
                        int generationNumber = 0;       // Default value is 0
                        if (fieldThreeSize > 0) {
                            generationNumber = Utils.readIntWithVaryingBytesBE(
                                    streamInput, fieldThreeSize);
                        }
                        addUsedEntry(objectNumber, filePositionOfObject, generationNumber);
                    } else if (entryType == Entry.TYPE_COMPRESSED) {
                        int objectNumberOfContainingObjectStream = Utils.readIntWithVaryingBytesBE(
                                streamInput, fieldTwoSize);
                        int indexWithinObjectStream = Utils.readIntWithVaryingBytesBE(
                                streamInput, fieldThreeSize);
                        addCompressedEntry(
                                objectNumber, objectNumberOfContainingObjectStream, indexWithinObjectStream);
                    }
//countOfAllEntries++;
                }
            }
        }
        catch (IOException e) {
             logger.log(Level.SEVERE, "Error parsing xRef stream entries.", e);
        }
//System.out.println("Done reading xref stream entries : " + (new java.util.Date()) + "  count: " + countOfAllEntries);
    }

    public Entry getEntryForObject(Integer objectNumber) {
        Entry entry =  m_hObjectNumber2Entry.get(objectNumber);
        if (entry != null)
            return entry;

        if (m_bIsCrossReferenceTable && !m_bHaveTriedLoadingPeer &&
                m_xrefPeer == null && m_PTrailer != null) {
            // Lazily load m_xrefPeer, using m_PTrailer
            m_PTrailer.loadXRefStmIfApplicable();
            m_xrefPeer = m_PTrailer.getCrossReferenceStream();
            m_bHaveTriedLoadingPeer = true;
        }
        if (m_xrefPeer != null) {
            entry = m_xrefPeer.getEntryForObject(objectNumber);
            if (entry != null)
                return entry;
        }

        if (!m_bHaveTriedLoadingPrevious &&
                m_xrefPrevious == null && m_PTrailer != null) {
            // Lazily load m_xrefPrevious, using m_PTrailer
            m_PTrailer.onDemandLoadAndSetupPreviousTrailer();
            m_bHaveTriedLoadingPrevious = true;
        }
        if (m_xrefPrevious != null) {
            entry = m_xrefPrevious.getEntryForObject(objectNumber);
            if (entry != null)
                return entry;
        }
        return entry;
    }

    public void addToEndOfChainOfPreviousXRefs(CrossReference prev) {
        if (m_xrefPrevious == null)
            m_xrefPrevious = prev;
        else
            m_xrefPrevious.addToEndOfChainOfPreviousXRefs(prev);
    }

    protected void addFreeEntry(int objectNumber, int nextFreeObjectNumber, int generationNumberIfReused) {
        FreeEntry entry = new FreeEntry(objectNumber, nextFreeObjectNumber, generationNumberIfReused);
//        m_vXRefEntries.add(entry);
    }

    protected void addUsedEntry(int objectNumber, long filePositionOfObject, int generationNumber) {
        UsedEntry entry = new UsedEntry(objectNumber, filePositionOfObject, generationNumber);
//        m_vXRefEntries.add(entry);

        m_hObjectNumber2Entry.put(objectNumber, entry);
    }

    protected void addCompressedEntry(int objectNumber, int objectNumberOfContainingObjectStream, int indexWithinObjectStream) {
        CompressedEntry entry = new CompressedEntry(objectNumber, objectNumberOfContainingObjectStream, indexWithinObjectStream);
//        m_vXRefEntries.add(entry);

        m_hObjectNumber2Entry.put(objectNumber, entry);
    }


    public static class Entry {
        public static final int TYPE_FREE = 0;
        public static final int TYPE_USED = 1;
        public static final int TYPE_COMPRESSED = 2;

        private int m_iType;
        private int m_iObjectNumber;

        Entry(int type, int objectNumber) {
            m_iType = type;
            m_iObjectNumber = objectNumber;
        }

        int getType() {
            return m_iType;
        }

        int getObjectNumber() {
            return m_iObjectNumber;
        }
    }

    public static class FreeEntry extends Entry {
        private int m_iNextFreeObjectNumber;
        private int m_iGenerationNumberIfReused;

        FreeEntry(int objectNumber, int nextFreeObjectNumber, int generationNumberIfReused) {
            super(TYPE_FREE, objectNumber);
            m_iNextFreeObjectNumber = nextFreeObjectNumber;
            m_iGenerationNumberIfReused = generationNumberIfReused;
        }

        public int getNextFreeObjectNumber() {
            return m_iNextFreeObjectNumber;
        }

        public int getGenerationNumberIfReused() {
            return m_iGenerationNumberIfReused;
        }
    }

    public static class UsedEntry extends Entry {
        private long m_lFilePositionOfObject;
        private int m_iGenerationNumber;

        UsedEntry(int objectNumber, long filePositionOfObject, int generationNumber) {
            super(TYPE_USED, objectNumber);
            m_lFilePositionOfObject = filePositionOfObject;
            m_iGenerationNumber = generationNumber;
        }

        public long getFilePositionOfObject() {
            return m_lFilePositionOfObject;
        }

        public int getGenerationNumber() {
            return m_iGenerationNumber;
        }
    }

    public static class CompressedEntry extends Entry {
        private int m_iObjectNumberOfContainingObjectStream;
        private int m_iIndexWithinObjectStream;

        CompressedEntry(int objectNumber, int objectNumberOfContainingObjectStream, int indexWithinObjectStream) {
            super(TYPE_COMPRESSED, objectNumber);
            m_iObjectNumberOfContainingObjectStream = objectNumberOfContainingObjectStream;
            m_iIndexWithinObjectStream = indexWithinObjectStream;
        }

        public int getObjectNumberOfContainingObjectStream() {
            return m_iObjectNumberOfContainingObjectStream;
        }

        public int getIndexWithinObjectStream() {
            return m_iIndexWithinObjectStream;
        }
    }
}
