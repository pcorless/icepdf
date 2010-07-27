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
package org.icepdf.core.util;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.OutputStream;
import java.io.IOException;

/**
 * @since 4.0
 */
public class IncrementalUpdater {
    private static final Logger logger =
        Logger.getLogger(IncrementalUpdater.class.getName());

    private static final boolean PRETTY = false;

    private static final byte[] SPACE = " ".getBytes();
    private static final byte[] NEWLINE = "\r\n".getBytes();
    private static final byte[] TRUE = "true".getBytes();
    private static final byte[] FALSE = "false".getBytes();
    private static final byte[] NAME = "/".getBytes();
    private static final byte[] REFERENCE = "R".getBytes();
    private static final byte[] LITERAL_STRING_ESCAPE = "\\".getBytes();

    private static final byte[] BEGIN_OBJECT = "obj\r\n".getBytes();
    private static final byte[] END_OBJECT = "\r\nendobj\r\n".getBytes();
    private static final byte[] BEGIN_DICTIONARY = "<<".getBytes();
    private static final byte[] END_DICTIONARY = ">>".getBytes();
    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();
    private static final byte[] BEGIN_LITERAL_STRING = "(".getBytes();
    private static final byte[] END_LITERAL_STRING = ")".getBytes();
    private static final byte[] BEGIN_HEX_STRING = "<".getBytes();
    private static final byte[] END_HEX_STRING = ">".getBytes();

    private static final byte[] XREF = "xref\r\n".getBytes();
    private static final byte[] TRAILER = "trailer\r\n".getBytes();
    private static final byte[] STARTXREF = "\r\n\r\nstartxref\r\n".getBytes();
    private static final byte[] COMMENT_EOF = "\r\n%%EOF\r\n".getBytes();


    private CountingOutputStream output;
    private long startingPosition;
    private long xrefPosition;
    private ArrayList<Entry> entries;

    /**
     * For simplicity, expose this one single method for appending an
     * incremental update.
     *
     * @param document The Document that's being saved
     * @param out OutputStream to write the incremental update to
     * @param documentLength The pre-existing PDF's file length
     * @return The number of bytes written in the incremental update
     * @throws IOException
     */
    public static long appendIncrementalUpdate(
        Document document, OutputStream out, long documentLength)
            throws IOException {
        // Iterate over StateManager entries, writing changed objects
        // (like Annotation, Page, Annots array) to output stream via
        // IncrementalUpdater
        
        if (logger.isLoggable(Level.FINE)) {
            if (document.getStateManager().isChanged())
                logger.fine("Have changes, will append incremental update");
            else
                logger.fine("No changes, will not append incremental update");
        }
        
        // If no changes, don't append an incremental update
        if (!document.getStateManager().isChanged())
            return 0L;

        IncrementalUpdater updater = new IncrementalUpdater(
            out, documentLength);
        updater.begin();
        
        Iterator<PObject> changes =
            document.getStateManager().iteratorSortedByObjectNumber();
        while (changes.hasNext()) {
            PObject pobject = changes.next();
            updater.writeObject(pobject.getReference(), pobject.getObject());
        }

        // Write out xref table, based on IncrementalUpdater entries
        updater.writeXRefTable();

        // Write trailer
        updater.writeTrailer(document.getStateManager().getTrailer());

        return updater.getIncrementalUpdateLength();
    }


    private IncrementalUpdater(OutputStream out, long sp) {
        output = new CountingOutputStream(out);
        startingPosition = sp;
        entries = new ArrayList<Entry>(32);
    }

    /**
     * Start the process of writing the incremental update
     * @throws IOException
     */
    private void begin() throws IOException {
        // Old EOF might been immediately after %%EOF, so if we just start
        // writing, we'll still be in a comment. Write a newline to terminate
        // the potentially still active comment.
        output.write(NEWLINE);
    }

    /**
     * Write a top-level indirectly referenced PDF object, which may be a
     * dictionary type of object, or simply a primitive.
     * @throws IOException
     */
    private void writeObject(Reference ref, Object obj) throws IOException {
        if (obj instanceof Dictionary) {
            writeObjectDictionary((Dictionary) obj);
        }
        else {
            writeObjectValue(ref, obj);
        }
    }

    /**
     * Write an xref table describing all of the objects that have already
     * been written out.
     * @throws IOException
     */
    private void writeXRefTable() throws IOException {
        // Link each deleted entry to the next deleted entry by iterating
        // backwards through the entries, which are sorted by object number.

        // End chain by pointing to head. If none del, chain back on itself.
        int nextDeletedObjectNumber = 0;
        for(int i = entries.size()-1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.isDeleted()) {
                entry.setNextDeletedObjectNumber(nextDeletedObjectNumber);
                nextDeletedObjectNumber =
                    entry.getReference().getObjectNumber();
            }
        }

        // Insert pseudo-entry for object number zero - the head of freed list
        // The generation number is 65535, but we increment it when writing
        Entry zero = new Entry(new Reference(0, 65534));
        zero.setNextDeletedObjectNumber(nextDeletedObjectNumber);
        entries.add(0, zero);

        output.write(NEWLINE);
        xrefPosition = startingPosition + output.getCount();
        output.write(XREF);
        for(int i = 0; i < entries.size();) {
            i += writeXrefSubSection(i);
        }
        output.write(NEWLINE);
    }

    /**
     * Write out the current xref sub-section, commencing at beginIndex in entries
     * @param beginIndex The index into entries, where the xref sub-section begins
     * @return The length of the current sub-section
     * @throws IOException
     */
    private int writeXrefSubSection(int beginIndex) throws IOException {
        int beginObjNum = entries.get(beginIndex).getReference().getObjectNumber();

        // Determine how many entries in this sub-section
        int nextContiguous = beginObjNum + 1;
        for(int i = beginIndex+1; i < entries.size(); i++) {
            if (entries.get(i).getReference().getObjectNumber() == nextContiguous)
                nextContiguous++;
            else
                break;
        }
        int subSectionLength = nextContiguous - beginObjNum;

        // Output sub-section header
        writeInteger(beginObjNum);
        output.write(SPACE);
        writeInteger(subSectionLength);
        output.write(NEWLINE);

        for(int i = beginIndex; i < (beginIndex+subSectionLength); i++) {
            Entry entry = entries.get(i);
            if (entry.isDeleted()) {
                // 10-digit-integer:nextFreeObjectNumber SPACE 5-digit-integer:generationNumber SPACE 'f' CRLF
                writeZeroPaddedLong(entry.getNextDeletedObjectNumber(), 10);
                output.write(' ');
                writeZeroPaddedLong(entry.getReference().getGenerationNumber()+1, 5);
                output.write(' ');
                output.write('f');
                output.write('\r');
                output.write('\n');
            }
            else {
                // 10-digit-integer:byteOffset SPACE 5-digit-integer:generationNumber SPACE 'n' CRLF
                writeZeroPaddedLong(entry.getPosition(), 10);
                output.write(' ');
                writeZeroPaddedLong(entry.getReference().getGenerationNumber(), 5);
                output.write(' ');
                output.write('n');
                output.write('\r');
                output.write('\n');
            }
        }

        return subSectionLength;
    }

    /**
     * Write the new trailer, and link it back to the pre-existing ending trailer
     * @param prevTrailer The pre-existing PDF's ending trailer
     * @throws IOException
     */
    private void writeTrailer(PTrailer prevTrailer) throws IOException {
        Hashtable<Object,Object> newTrailer = (Hashtable<Object,Object>)
            prevTrailer.getDictionary().clone();
        int oldSize = prevTrailer.getNumberOfObjects();
        int greatestWritten = getGreatestObjectNumberWritten();
        int newSize = Math.max(oldSize, greatestWritten+1);
        newTrailer.put(new Name("Size"), new Integer(newSize));
        long prevTrailerPos = prevTrailer.getPosition();
        newTrailer.put(new Name("Prev"), new Long(prevTrailerPos));
        long xrefPos = xrefPosition;

        // If the previous trailer doesn't know it's position, then we're
        // likely updating a file loaded via linear traversal, which means
        // that we can't chain back to to the previous trailer, and will
        // have to make this new trailer fail input validation, to continue
        // forcing linear traversal. Ideally in such a way that both ICEpdf
        // and Acrobat will support.
        //TODO See if this works with ICEpdf and Acrobat
        if (prevTrailerPos == 0) {
            xrefPos = -1;
        }

        output.write(TRAILER);
        this.writeDictionary(newTrailer);
        output.write(STARTXREF);
        this.writeLong(xrefPos);
        output.write(NEWLINE);
        output.write(COMMENT_EOF);
    }

    /**
     * Flush the OutputStream that we wrap
     * @throws IOException
     */
    private void flush() throws IOException {
        output.flush();
    }

    /**
     * @return The number of bytes written as part of the incremental update
     */
    private long getIncrementalUpdateLength() {
        return output.getCount();
    }

    /**
     * Write a Dictionary as a top-level PDF object
     * @throws IOException
     */
    private void writeObjectDictionary(Dictionary obj) throws IOException {
        logger.log(Level.FINER, "writeObjectDictionary()  obj: {0}", obj);
        if (obj == null)
            throw new IllegalArgumentException("Object must be non-null");
        Reference ref = obj.getPObjectReference();
        logger.log(Level.FINER, "writeObjectDictionary()  ref: {0}", ref);
        if (ref == null)
            throw new IllegalArgumentException("Reference must be non-null for object: " + obj);

        if (obj.isDeleted()) {
            //if (!obj.isNew()) {
            //    addEntry( new Entry(ref) );
            //}
            // Make deleted entries for unused object numbers resulting from
            // deleting newly created objects
            addEntry( new Entry(ref) );
            return;
        }
        addEntry( new Entry(ref, startingPosition + output.getCount()) );
        
        writeInteger(ref.getObjectNumber());
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber());
        output.write(SPACE);
        output.write(BEGIN_OBJECT);
        writeDictionary(obj);
        output.write(END_OBJECT);
    }

    /**
     * Write a non-Dictionary type of value as a top-level PDF object.
     * For example, any array or primitive that was specified as an
     * indirect reference.
     * @throws IOException
     */
    private void writeObjectValue(Reference ref, Object obj) throws IOException {
        logger.log(Level.FINER, "writeObjectValue()  obj: {0}", obj);
        if (ref == null)
            throw new IllegalArgumentException("Reference must be non-null for object: " + obj);
        if (obj == null)
            throw new IllegalArgumentException("Object must be non-null");
        addEntry( new Entry(ref, startingPosition + output.getCount()) );

        writeInteger(ref.getObjectNumber());
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber());
        output.write(SPACE);
        output.write(BEGIN_OBJECT);
        writeValue(obj);
        output.write(END_OBJECT);
    }


    /**
     * For Dictionary values, we're going to assume that all indirect
     * objects are already in the file, and that all direct objects
     * should be inlined
     * @throws IOException
     */
    private void writeDictionary(Dictionary dict) throws IOException {
        logger.log(Level.FINER, "writeDictionary()  dict: {0}", dict);
        try {
            writeDictionary(dict.getEntries());
        }
        catch(IllegalArgumentException e) {
            String dictString = (dict.getPObjectReference() != null)
                ? dict.getPObjectReference().toString() : dict.toString();
            throw new IllegalArgumentException(
                e.getMessage() + " in dictionary: " + dictString, e);
        }
    }

    /**
     * The internal implementation of writing out the dictionary's delimiters
     * and Hashtable entries.
     * @throws IOException
     */
    private void writeDictionary(Hashtable<Object,Object> dictEntries) throws IOException {
        logger.log(Level.FINER, "writeDictionary()  dictEntries: {0}", dictEntries);
        output.write(BEGIN_DICTIONARY);
        if (PRETTY)
            output.write(NEWLINE);
        Enumeration<Object> keys = dictEntries.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement(); //Likely a Name
            Object val = dictEntries.get(key);
            writeName(key.toString());
            output.write(SPACE);
            try {
                writeValue(val);
            }
            catch(IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " for key: " + key, e);
            }
            if (PRETTY)
                output.write(NEWLINE);
            else
                output.write(SPACE); // Technically unnecessary
        }
        output.write(END_DICTIONARY);
    }

    /**
     * Determines the type of the value, and delegates to the appropriate
     * method for handling that type
     * @throws IOException
     */
    private void writeValue(Object val) throws IOException {
        if (val == null) {
            writeByteString("null");
        }
        else if (val instanceof Name) {
            writeName( (Name) val );
        }
        else if (val instanceof Reference) {
            writeReference( (Reference) val );
        }
        else if (val instanceof Boolean) {
            writeBoolean( ((Boolean) val).booleanValue() );
        }
        else if (val instanceof Integer) {
            writeInteger( ((Integer) val).intValue() );
        }
        else if (val instanceof Long) {
            writeLong( ((Long) val).longValue() );
        }
        else if (val instanceof Number) {
            writeReal( (Number) val );
        }
        else if (val instanceof String) {
            logger.severe("Found invalid java.lang.String being written out: "
                + val.toString());
            throw new IllegalArgumentException(
                "invalid type of java.lang.String. " +
                "Should use LiteralStringObject or HexStringObject");
        }
        else if (val instanceof LiteralStringObject) {
            writeLiteralString((LiteralStringObject) val);
        }
        else if (val instanceof HexStringObject) {
            writeHexString((HexStringObject) val);
        }
        else if (val instanceof Vector) {
            writeArray( (Vector) val );
        }
        else if (val instanceof Dictionary) {
            writeDictionary( (Dictionary) val );
        }
        else if (val instanceof Hashtable) {
            writeDictionary( (Hashtable<Object,Object>) val );
        }
        else {
            throw new IllegalArgumentException("unknown value type of: " +
                val.getClass().getName());
        }
    }

    private void writeName(Name name) throws IOException {
        writeName(name.getName());
    }

    private void writeName(String name) throws IOException {
        output.write(NAME);

        // The String value of a name should be output as UTF-8, but the
        // UTF-8 bytes are then escaped such that non-ASCII, ASCII control,
        // and whitespace characters are replaces with # then the 2 digit
        // hex value of the character. The # symbol itself is escaped with
        // its hex value (#23). Escape characters outside the range of
        // EXCLAMATION MARK(21h) (!) to TILDE (7Eh) (~). Decimal 33-126.

        final int pound = 0x23;
        byte[] bytes = name.getBytes("UTF-8");
        for(int b : bytes) {
            b &= 0xFF;
            if (b == pound || b < 0x21 || b > 0x7E) {
                output.write(pound);
                int hexVal = ((b >> 4) & 0x0F);
                int hexDigit = hexVal + ( (hexVal >= 10) ? 'A' : '0' );
                output.write(hexDigit);
                hexVal = (b & 0x0F);
                hexDigit = hexVal + ( (hexVal >= 10) ? 'A' : '0' );
                output.write(hexDigit);
            }
            else {
                output.write(b);
            }
        }
    }

    private void writeReference(Reference ref) throws IOException {
        writeInteger(ref.getObjectNumber());
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber());
        output.write(SPACE);
        output.write(REFERENCE);
    }

    private void writeArray(Vector array) throws IOException {
        output.write(BEGIN_ARRAY);
        final int size = array.size();
        for(int i = 0; i < size; i++) {
            writeValue(array.get(i));
            if (i < (size-1))
                output.write(SPACE);
        }
        output.write(END_ARRAY);
    }

    private void writeBoolean(boolean b) throws IOException {
        if (b)
            output.write(TRUE);
        else
            output.write(FALSE);
    }
    
    private void writeInteger(int i) throws IOException {
        String str = Integer.toString(i);
        writeByteString(str);
    }

    private void writeLong(long i) throws IOException {
        String str = Long.toString(i);
        writeByteString(str);
    }

    private void writeReal(float r) throws IOException {
        //TODO Need to ensure not using scientific or engineering notation
        String str = Float.toString(r);
        writeByteString(str);
    }

    private void writeReal(Number r) throws IOException {
        //TODO Need to ensure not using scientific or engineering notation
        String str = r.toString();
        writeByteString(str);
    }

    /**
     * Whether the LiteralStringOject is encrypted or not, has unicode
     * characters or not, it's already containing the values to write,
     * as byte values held in a StringBuffer. 
     * @param lso LiteralStringObject to write
     * @throws IOException
     */
    private void writeLiteralString(LiteralStringObject lso) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        writeByteString(lso.getLiteralString());
        output.write(END_LITERAL_STRING);
    }

    /**
     * Whether the HexStringObject is encrypted or not, has unicode
     * characters or not, it's already containing the values to write,
     * as byte values held in a StringBuffer. 
     * @param hso HexStringObject to write
     * @throws IOException
     */
    private void writeHexString(HexStringObject hso) throws IOException {
        output.write(BEGIN_HEX_STRING);
        writeByteString(hso.getHexString());
        output.write(END_HEX_STRING);
    }
    
    /**
     * If, in the future, we have a case for outputting raw strings, instead
     * of only LiteralStringObject and HexStringObject, then we should probably
     * just create a LiteralStringObject from the java.lang.String. But, if
     * that's not possible, or if we need some code for the basis of that,
     * we should make use of this code here. 
     */
    /*
    private void writeLiteralString(String str) throws IOException {
        // PDFDocEncoding or the UTF-16BE
        // U+FEFF, indicating that the string is encoded in the UTF-16BE (big-endian)
        
        //TODO What about Unicode FEFF strings?
        //TODO For encryption, use the Reference from the containing pobject
        output.write(BEGIN_LITERAL_STRING);
        int lastInsertedLineBreak = 0;
        final int len = str.length();
        for(int i = 0; i < len; i++) {
            int val = ((int) str.charAt(i)) & 0xFFFF;
            if ((val & 0x80) != 0) {
                output.write(LITERAL_STRING_ESCAPE);
                int octalDigit = ((val >> 6) & 0x03) + '0';
                output.write(octalDigit);
                octalDigit = ((val >> 3) & 0x07) + '0';
                output.write(octalDigit);
                octalDigit = (val & 0x07) + '0';
                output.write(octalDigit);
            }
            else if (val == '\n') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write('n');
            }
            else if (val == '\r') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write('r');
            }
            else if (val == '\t') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write('t');
            }
            else if (val == '\b') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write('b');
            }
            else if (val == '\f') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write('f');
            }
            else if (val == '(' || val == ')' || val == '\\') {
                output.write(LITERAL_STRING_ESCAPE);
                output.write(val);
            }
            else {
                output.write(val);

                // Some parsers may have limits on the length of string lines,
                // so we'll break up string lines into manageable chunks. This
                // isn't so much about making output pretty as avoiding buffer
                // overflows in other parsers

                // Try to find a word break to wrap the line at, or else do it
                // by 80 chars
                if ( ((i-lastInsertedLineBreak) >= 70 && val == ' ') ||
                     ((i-lastInsertedLineBreak) >= 80) )
                {
                    lastInsertedLineBreak = i;
                    output.write(LITERAL_STRING_ESCAPE);
                    output.write(NEWLINE);
                }
            }
        }
        output.write(END_LITERAL_STRING);
    }
    */

    /**
     * This method is not used for writing Unicode strings, and does NOT do
     * character set conversion to Latin1, WinAnsi, MacRoman, PDFDocEncoding
     * or the UTF-16BE. It simply writes out the byte value in each char. This
     * method is intended for ASCII strings and binary bytes stored in strings.
     * @param str ASCII String, or encrypted byte data from a StringObject.
     * @throws IOException
     */
    private void writeByteString(String str) throws IOException {
        int len = str.length();
        for(int i = 0; i < len; i++) {
            int val = ((int) str.charAt(i)) & 0xFF;
            output.write(val);
        }
    }

    private void writeZeroPaddedLong(long val, int len) throws IOException {
        String str = Long.toString(val);
        if (str.length() > len)
            str = str.substring(str.length() - len);
        int padding = len - str.length();
        for(int i = 0; i < padding; i++)
            output.write('0');
        writeByteString(str);
    }

    /**
     * Add Entry object in ascending object number sequence. Since we know that
     * we're iterating over StateManager's PObjects in ascending object number
     * sequence, appending to the end of our entries should be sufficient.
     * @param entry Entry to be added
     */
    private void addEntry(Entry entry) {
        int entryObjNum = entry.getReference().getObjectNumber();
        int index = entries.size();
        while (index > 0) {
            Entry prev = entries.get(index-1);
            int prevObjNum = prev.getReference().getObjectNumber();
            if (prevObjNum == entryObjNum) {
                // StateManager is allowing double entries for same reference
                throw new IllegalArgumentException(
                    "Multiple entries with same object number: " + entryObjNum);
            }
            else if (prevObjNum < entryObjNum)
                break;
            index--;
        }
        entries.add(index, entry);
    }

    private int getGreatestObjectNumberWritten() {
        // entries are insertion sorted by ascending object number, so the
        // greatest object number is in the Entry at the greatest index
        return (entries.isEmpty()) ? 0 :
            entries.get(entries.size()-1).getReference().getObjectNumber();
    }


    private static class Entry {
        private static final long POSITION_DELETED = -1;

        private Reference reference;
        private long position;
        private int nextDeletedObjectNumber;

        /**
         * This is for new or modified objects, that hve been written out
         */
        Entry(Reference ref, long pos) {
            reference = ref;
            position = pos;
        }

        /**
         * This is for deleted objects
         */
        Entry(Reference ref) {
            reference = ref;
            position = POSITION_DELETED;
        }

        Reference getReference() {
            return reference;
        }

        boolean isDeleted() {
            return position == POSITION_DELETED;
        }

        long getPosition() {
            return position;
        }

        void setNextDeletedObjectNumber(int nextDelObjNum) {
            nextDeletedObjectNumber = nextDelObjNum;
        }

        int getNextDeletedObjectNumber() {
            return nextDeletedObjectNumber;
        }
    }
}
