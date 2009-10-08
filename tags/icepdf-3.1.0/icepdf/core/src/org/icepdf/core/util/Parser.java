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
package org.icepdf.core.util;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.io.ConservativeSizingByteArrayOutputStream;
import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.graphics.TilingPattern;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * put your documentation comment here
 */
public class Parser {

    private static final Logger logger =
            Logger.getLogger(Parser.class.toString());

    public static final int PARSE_MODE_NORMAL = 0;
    public static final int PARSE_MODE_OBJECT_STREAM = 1;

    // InputStream has to support mark(), reset(), and markSupported()
    // DO NOT close this, since we have two cases: read everything up front, and progressive reads
    private InputStream reader;
    boolean lastTokenHString = false;
    private Stack<Object> stack = new Stack<Object>();
    private int parseMode;

    public Parser(SeekableInput r) {
        this(r, PARSE_MODE_NORMAL);
    }

    public Parser(SeekableInput r, int pm) {
        reader = r.getInputStream();
        parseMode = pm;
    }

    public Parser(InputStream r) {
        this(r, PARSE_MODE_NORMAL);
    }

    public Parser(InputStream r, int pm) {
        reader = new BufferedInputStream(r);
        parseMode = pm;
    }

    /**
     * Get an object from the pdf input DataInputStream.
     *
     * @param library all found objects in the pdf document
     * @return the next object in the DataInputStream.  Null is returned
     *         if there are no more objects left in the DataInputStream or
     *         a I/O error is encountered.
     * @throws PDFException error getting object from library
     */
    public Object getObject(Library library) throws PDFException {
        int deepnessCount = 0;
        boolean inObject = false; // currently parsing tokens in an object
        boolean complete = false; // flag used for do loop.
        Object nextToken;
        Reference objectReference = null;
        try {
            do { //while (!complete);

                // keep track of currently parsed objects reference

                // get the next token inside the object stream
                try {
                    nextToken = getToken();
//System.out.println("Parser.getObject()  nextToken: " + nextToken);
                    // commented out for performance reasons
                    //Thread.yield();
                }
                catch (IOException e) {
                    // eat it as it is what is expected
//                    if (Debug.ex){
//                        Debug.ex(e);
//                    }
                    return null;
                }

                // check for specific primative object types returned by getToken()
                if (nextToken instanceof StringObject
                        || nextToken instanceof Name
                        || nextToken instanceof Number) {
                    // Very Important, store the PDF object reference information,
                    // as it is needed when to decrypt an encrypted string.
                    if (nextToken instanceof StringObject) {
                        StringObject tmp = (StringObject) nextToken;
                        tmp.setReference(objectReference);
                    }
                    stack.push(nextToken);
                }
                // mark that we have entered a object declaration
                else if (nextToken.equals("obj")) {
                    // Since we can return objects on "endstream", then we can
                    //  leave straggling "endobj", which would deepnessCount--,
                    //  even though they're done in a separate method invocation
                    // Hence, "obj" does /deepnessCount = 1/ instead of /deepnessCount++/
                    deepnessCount = 1;
                    inObject = true;
                    Number generationNumber = (Number) (stack.pop());
                    Number objectNumber = (Number) (stack.pop());
                    objectReference = new Reference(objectNumber,
                            generationNumber);
                }
                // mark that we have reached the end of the object
                else if (nextToken.equals("endobj")) {
                    deepnessCount--;
//System.out.println("Parser.getObject()  endobj  objectReference: " + objectReference + "  deepnessCount: " + deepnessCount);
                    if (inObject) {
                        // set flag to false, as we are done parsing an Object
                        inObject = false;
                        // return PObject,
                        return addPObject(library, objectReference);
                        // else, we ignore as the endStream token also returns a
                        // PObject.
                    } else
                        return null;
                }
                // found endstream object, we will return the PObject containing
                // the stream as there can be no further tokens.  This addresses
                // an incorrect a syntax error with OpenOffice document where
                // the endobj tag is missing on some Stream objects.
                else if (nextToken.equals("endstream")) {
                    deepnessCount--;
                    // do nothing, but don't add it to the stack
                    if (inObject) {
                        inObject = false;
                        // return PObject,
                        return addPObject(library, objectReference);
                    }
                }

                // found a stream object, streams are allways defined inside
                // of a object so we will always have a dictionary (hash) that
                // has the length and filter definitions in it
                else if (nextToken.equals("stream")) {
//System.out.println("Parser.getObject()  stream");
                    deepnessCount++;
                    // pop dictionary that defines the stream
                    Hashtable streamHash = (Hashtable) stack.pop();
//System.out.println("Parser.getObject()  stream  streamHash: " + streamHash);
                    // find the length of the stream
                    int streamLength = library.getInt(streamHash, "Length");
//System.out.println("Parser.getObject()  stream  streamLength: " + streamLength);

                    SeekableInputConstrainedWrapper streamInputWrapper;
                    try {
                        // a stream token's end of line marker can be either:
                        // - a carriage return and a line feed
                        // - just a line feed, and not by a carriage return alone.
                        /*
                        reader.mark(5);
                        byte[] charBuffer = new byte[5];
                        reader.read(charBuffer);
                        System.out.println("looking at " + objectReference + " " + streamHash);
                        System.out.println("Stream bytes " + charBuffer[0] + " " + charBuffer[1] + " " + charBuffer[2] + " " + charBuffer[3] + " " + charBuffer[4]);
                        reader.reset();
                        */

                        // check for carage return and line feed, but reset if
                        // just a carriage return as it is a valid stream byte
                        reader.mark(2);

                        // alway eat a 13,against the spec but we have several examples of this.
                        int curChar = reader.read();
                        if (curChar == 13) {
                            reader.mark(1);
                            if (reader.read() != 10) {
                                reader.reset();
                            }
                        }
                        // always eat a 10
                        else if (curChar == 10) {
                            // eat the stream character
                        }
                        // reset the rest
                        else {
                            reader.reset();
                        }

                        /*
                        reader.mark(5);
                        charBuffer = new byte[5];
                        reader.read(charBuffer);
                        System.out.println("Stream bytes " + charBuffer[0] + " " + charBuffer[1] + " " + charBuffer[2] +" " + charBuffer[3] + " " + charBuffer[4]);
                        reader.reset();
                        */

                        if (reader instanceof SeekableInput) {
                            SeekableInput streamDataInput = (SeekableInput) reader;
                            long filePositionOfStreamData = streamDataInput.getAbsolutePosition();
                            long lengthOfStreamData;
                            // If the stream has a length that we can currently use
                            // such as a R that has been parsed or an integer
                            if (streamLength > 0) {
                                lengthOfStreamData = streamLength;
                                streamDataInput.seekRelative(streamLength);
                                // Read any extraneous data coming after the length, but before endstream
//                                long skipped = skipUntilEndstream( null );
                                lengthOfStreamData += skipUntilEndstream(null);
                            } else {
                                lengthOfStreamData = captureStreamData(null);
                            }
                            streamInputWrapper = new SeekableInputConstrainedWrapper(
                                    streamDataInput, filePositionOfStreamData, lengthOfStreamData, false);
                        } else { // reader is just regular InputStream (BufferedInputStream)
//System.out.println("Parser.getObject()  stream  NOT SeekableInput");
                            ConservativeSizingByteArrayOutputStream out;
                            // If the stream in from a regular InputStream,
                            //  then the PDF was probably linearly traversed,
                            //  in which case it doesn't matter if they have
                            //  specified the stream length, because we can't
                            //  trust that anyway
//System.out.println("Parser.getObject()  stream  NOT SeekableInput  linear traversal: " + library.isLinearTraversal());
                            if (!library.isLinearTraversal() && streamLength > 0) {
                                byte[] buffer = new byte[streamLength];
                                int totalRead = 0;
                                while (totalRead < buffer.length) {
                                    int currRead = reader.read(buffer, totalRead, buffer.length - totalRead);
//System.out.println("Parser.getObject()  stream  NOT SeekableInput  currRead: " + currRead);
//String s = new String(buffer, totalRead, currRead);
//System.out.println(s);
                                    if (currRead <= 0)
                                        break;
                                    totalRead += currRead;
//System.out.println("Parser.getObject()  stream  NOT SeekableInput  totalRead: " + totalRead);
                                }
                                out = new ConservativeSizingByteArrayOutputStream(
                                        buffer, library.memoryManager);
                                // Read any extraneous data coming after the length, but before endstream
//                                long skipped = skipUntilEndstream( out );
                                skipUntilEndstream(out);
                            }
                            // if stream doesn't have a length, read the stream
                            // until end stream has been found
                            else {
//System.out.println("Parser.getObject()  stream  NOT SeekableInput  No trusted streamLength");
                                out = new ConservativeSizingByteArrayOutputStream(
                                        16 * 1024, library.memoryManager);
                                captureStreamData(out);
                            }

                            int size = out.size();
                            out.trim();
                            byte[] buffer = out.relinquishByteArray();

                            SeekableInput streamDataInput = new SeekableByteArrayInputStream(buffer);
                            long filePositionOfStreamData = 0L;
                            long lengthOfStreamData = size;
                            streamInputWrapper = new SeekableInputConstrainedWrapper(
                                    streamDataInput, filePositionOfStreamData, lengthOfStreamData, true);
                        }
                    }
                    catch (IOException e) {
                        return null;
                    }
                    PTrailer trailer = null;
                    // set the stream know objects if possible
                    Stream stream = null;
                    //Hashtable streamHash1 = (Hashtable) stack.pop();
                    Name type = (Name) library.getObject(streamHash, "Type");
                    Name subtype = (Name) library.getObject(streamHash, "Subtype");
                    if (type != null) {
                        // new Tiling Pattern Object, will have a stream. 
                        if (type.equals("Pattern")) {
                            stream = new TilingPattern(library, streamHash, streamInputWrapper);
                        } else if (type.equals("XRef")) {
                            stream = new Stream(library, streamHash, streamInputWrapper);
                            stream.init();
                            InputStream in = stream.getInputStreamForDecodedStreamBytes();
                            CrossReference xrefStream = new CrossReference();
                            if (in != null) {
                                try {
                                    xrefStream.addXRefStreamEntries(library, streamHash, in);
                                }
                                finally {
                                    try {
                                        in.close();
                                    }
                                    catch (IOException e) {
                                        logger.log(Level.FINE, "Error appending stream entries.", e);
                                    }
                                }
                            }
                            stream.dispose(false);

                            // XRef dict is both Trailer dict and XRef stream dict.
                            // PTrailer alters its dict, so copy it to keep everything sane
                            Hashtable trailerHash = (Hashtable) streamHash.clone();
                            trailer = new PTrailer(library, trailerHash, null, xrefStream);
                        } else if (type.equals("ObjStm")) {
                            stream = new ObjectStream(library, streamHash, streamInputWrapper);
                        }
                    }
                    if (subtype != null) {
                        // new form object
                        if (subtype.equals("Form") && !"pattern".equals(type)) {
                            stream = new Form(library, streamHash, streamInputWrapper);
                        }
                    }
                    if (trailer != null) {
                        stack.push(trailer);
                    } else {
                        // finally create a generic stream object which will be parsed
                        // at a later time
                        if (stream == null) {
                            stream = new Stream(library, streamHash, streamInputWrapper);
                        }
                        stack.push(stream);
                    }
                }
                // end if (stream)

                // boolean objects are added to stack
                else if (nextToken.equals("true")) {
                    stack.push(new Boolean(true));
                } else if (nextToken.equals("false")) {
                    stack.push(new Boolean(false));
                }
                // Indirect Reference object found
                else if (nextToken.equals("R")) {
                    // generationNumber number important for revisions
                    Number generationNumber = (Number) (stack.pop());
                    Number objectNumber = (Number) (stack.pop());
                    stack.push(new Reference(objectNumber,
                            generationNumber));
                } else if (nextToken.equals("[")) {
                    deepnessCount++;
                    stack.push(nextToken);
                }
                // Found an array
                else if (nextToken.equals("]")) {
                    deepnessCount--;
                    Vector v = new Vector();
                    Object obj = stack.pop();
                    while (!((obj instanceof String)
                            && (obj.equals("[")))) {
                        v.insertElementAt(obj, 0);
                        obj = stack.pop();
                    }
                    stack.push(v);
                } else if (nextToken.equals("<<")) {
//System.out.println("Parser.getObject()  <<  deepnessCount: " + deepnessCount + " -> " + (deepnessCount+1));
                    deepnessCount++;
                    stack.push(nextToken);
                }
                // Found a Dictionary
                else if (nextToken.equals(">>")) {
//System.out.println("Parser.getObject()  >>  deepnessCount: " + deepnessCount + " -> " + (deepnessCount-1));
                    deepnessCount--;
                    Hashtable hashTable = new Hashtable();
//System.out.println("Parser.getObject()  >>  stack.empty: " + stack.isEmpty());
                    if (!stack.isEmpty()) {
                        Object obj = stack.pop();
                        // put all of the dictionary definistion into the
                        // the hashTabl
                        while (!((obj instanceof String)
                                && (obj.equals("<<"))) && !stack.isEmpty()) {
                            Object key = stack.pop();
//System.out.println("Parser.getObject()  >>    key: " + key);
//System.out.println("Parser.getObject()  >>    value: " + obj);
                            hashTable.put(key, obj);
                            if (!stack.isEmpty()) {
                                obj = stack.pop();
                            } else {
                                break;
                            }
                        }
                        obj = hashTable.get("Type");
//System.out.println("Parser.getObject()  >>  Type: " + obj);
                        // Process the know first level dictionaries.
                        if (obj != null && obj instanceof Name) {
                            Name n = (Name) obj;
//System.out.println("Parser.getObject()  >>  Name: " + n);
                            if (n.equals("Catalog")) {
                                stack.push(new Catalog(library, hashTable));
                            } else if (n.equals("Pages")) {
                                stack.push(new PageTree(library, hashTable));
                            } else if (n.equals("Page")) {
                                stack.push(new Page(library, hashTable));
                            } else if (n.equals("Font")) {
                                stack.push(FontFactory.getInstance()
                                        .getFont(library, hashTable));
                            } else if (n.equals("FontDescriptor")) {
                                stack.push(new FontDescriptor(library, hashTable));
                            } else if (n.equals("CMap")) {
                                stack.push(hashTable);
                            } else if (n.equals("Annot")) {
                                stack.push(Annotation.buildAnnotation(library, hashTable));
                            } else
                                stack.push(hashTable);
                        }
                        // everything else gets pushed onto the stack
                        else {
//System.out.println("Parser.getObject()  >>  Not Name");
                            stack.push(hashTable);
                        }

//System.out.println("Parser.getObject()  >>  deepnessCount: " + deepnessCount);
                        if (deepnessCount == 0)
                            return stack.pop();
                    }
                }
                // end of if >> (dictionary

//                    // read encryp information
//                    if (startxrefDictionary.containsKey("Encrypt")) {
//
//                        // read ID information needed for encryption
//                        Vector fileID = null;
//                        if (startxrefDictionary.containsKey("ID")){
//                            // get the files identifier vector
//                            fileID  = (Vector)startxrefDictionary.get("ID");
//                        }
//
//                        // Try and find encrypt dictionary
//                        Object encrypt = startxrefDictionary.get("Encrypt");
//                        System.out.println(encrypt.getClass());
//                        if (encrypt instanceof Reference ){
//                            Reference encryptReference = (Reference)encrypt;
//                            SecurityManager securityManager =
//                                new SecurityManager (library,
//                                                     encryptReference,
//                                                     fileID);
//                        }
//                        else if (encrypt instanceof Dictionary){
//
//
//                        }
//
//                        // initiate the security manager.
//                        //org.icepdf.core.pobjects.security.SecurityManager.getInstance();
//                    }

                else if (nextToken.equals("xref")) {
//System.out.println("xref found");
                    CrossReference xrefTable = new CrossReference();
                    xrefTable.addXRefTableEntries(this);
                    stack.push(xrefTable);
                } else if (nextToken.equals("trailer")) {
                    CrossReference xrefTable = null;
                    if (stack.peek() instanceof CrossReference)
                        xrefTable = (CrossReference) stack.pop();
                    stack.clear();
                    Hashtable trailerDictionary = (Hashtable) getObject(library);
                    //System.out.println("trailer");
                    //System.out.println("  trailerDictionary: " + trailerDictionary);
                    //System.out.println("  xref table: " + xrefTable);
                    return new PTrailer(library, trailerDictionary, xrefTable, null);
                }
                // comments
                else if (nextToken instanceof String &&
                        ((String) nextToken).startsWith("%")) {
                    // Comment, ignored for now
                }
                // everything else gets pushed onto the stack
                else {
                    stack.push(nextToken);
                }
                if (parseMode == PARSE_MODE_OBJECT_STREAM && deepnessCount == 0 && stack.size() > 0) {
                    return stack.pop();
                }
            }
            while (!complete);
        }
//        catch (PDFSecurityException e) {
//            throw e;
//        }
        catch (Exception e) {
            logger.log(Level.FINE, "Fatal error parsing PDF file stream.", e);
            return null;
        }
        // return the top of the statck
        return stack.pop();
    }

    /**
     * Utility Method for getting a PObject from the stack and adding it to the
     * library.  The retrieved PObject has an ObjectReference added to it for
     * decryption purposes.
     *
     * @param library         hashtable of all objects in document
     * @param objectReference PObjet indirect reference data
     * @return a valid PObject.
     */
    public PObject addPObject(Library library, Reference objectReference) {
        Object o = stack.pop();

        // Add the streams object reference which is needed for
        // decrypting encrypted streams
        if (o instanceof Stream) {
            Stream tmp = (Stream) o;
            tmp.setPObjectReference(objectReference);
        }

        // Add the dictionary object reference which is needed for
        // decrypting encrypted string contained in the dictionary
        else if (o instanceof Dictionary) {
            Dictionary tmp = (Dictionary) o;
            tmp.setPObjectReference(objectReference);
        }

        // the the object to the library
        library.addObject(o, objectReference);

        return new PObject(o, objectReference);
    }

    /**
     * Returns the next object found in a content stream.
     *
     * @return next object in the input stream
     * @throws java.io.IOException when the end of the <code>InputStream</code>
     *                             has been encountered.
     */
    public Object getStreamObject() throws IOException {

        Object o = getToken();
        if (o instanceof String) {
            if (o.equals("<<")) {
                Hashtable h = new Hashtable();
                Object o1 = getStreamObject();
                while (!o1.equals(">>")) {
                    h.put(o1, getStreamObject());
                    o1 = getStreamObject();
                }
                o = h;
            }
            // arrays are only used for CID mappings, the hex decoding is delayed
            // as a result using the CID_STREAM flag
            else if (o.equals("[")) {
                Vector v = new Vector();
                Object o1 = getStreamObject();
                while (!o1.equals("]")) {
                    v.addElement(o1);
                    o1 = getStreamObject();
                }
                o = v;
            }
        }
        //System.err.println("GET=" + o + " - " + o.getClass().getName());
        return o;
    }

    /**
     * Utility method used to parse a valid pdf token from an DataIinputStream.
     * Each call to this method return one pdf token.  The Reader object is
     * used to "mark" the location of the last "read".
     *
     * @return the next token in the pdf data stream
     * @throws java.io.IOException if an I/O error occurs.
     */
    public Object getToken() throws IOException {

        int currentByte;
        char currentChar;
        boolean inString = false;  // currently parsing a string
        boolean hexString = false;
        lastTokenHString = false;

        // strip all white space characters
        do {
            currentByte = reader.read();
            // input stream interupted
            if (currentByte < 0) {
                throw new IOException();
            }
            currentChar = (char) currentByte;
        }
        while (isWhitespace(currentChar));

        /**
         *  look the start of different primative pdf objects
         * ( - strints
         * [ - arrays
         * % - comments
         */
        if (currentChar == '(') {
            // mark that we are currrently processing a string
            inString = true;
        } else if (currentChar == ']') {
            // fount end of an array
            return "]";
        } else if (currentChar == '[') {
            // fount begining of an array
            return "[";
        } else if (currentChar == '%') {
            // ignore all the characters after a comment token until
            // we get to the end of the line
            StringBuffer stringBuffer = new StringBuffer();
            do {
                stringBuffer.append(currentChar);
                currentByte = reader.read();
                if (currentByte < 0) {
                    // Final %%EOF might not have CR LF afterwards
                    if (stringBuffer.length() > 0)
                        return stringBuffer.toString();
                    throw new IOException();
                }
                currentChar = (char) currentByte;
            }
            while (currentChar != 13 && currentChar != 10);
            // return all the text that is in the comment
            return stringBuffer.toString();
        }

        // mark this location in the input stream
        reader.mark(1);

        // read the next char from the reader
        char nextChar = (char) reader.read();

        // Check for dictionaries, start '<<' and end '>>'
        if (currentChar == '>' && nextChar == '>') {
            return ">>";
        }
        if (currentChar == '<') {
            // if two "<<" then we have a dictionary
            if (nextChar == '<') {
                return "<<";
            }
            // Otherwise we have a hex number
            else {
                inString = true;
                hexString = true;
            }
        }

        // return to the previous mark
        reader.reset();

        // store the parsed char in the token buffer.
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(currentChar);

        /**
         * Finally parse the contents of a complex token
         */

        int parenthesisCount = 0;
        boolean complete = false;
        // indicates that the current char should be ignored and not added to
        // the current string.
        boolean ignoreChar = false;

        do { // while !complete

            // if we are not parsing a string mark the location
            if (!inString) {
                reader.mark(1);
            }
            // get the next byte and corresponeding char
            currentByte = reader.read();
            // if ther are no more bytes (-1) then we should return previous
            // stringBuffer value, otherwise the last grouping of tokens will
            // be ignored, which is very bad.
            if (currentByte >= 0) {
                currentChar = (char) currentByte;
            } else {
                return stringBuffer.toString();
            }

            // if we are parsing a token that is a string, (...)
            if (inString) {
                if (hexString) {
                    // found the end of a dictionary
                    if (currentChar == '>') {
                        complete = true;
                        stringBuffer.append(currentChar);
                        break;
                    }
                } else {
                    // look for embedded strings
                    if (currentChar == '(') {
                        parenthesisCount++;
                    }
                    if (currentChar == ')') {
                        if (parenthesisCount == 0) {
                            complete = true;
                            stringBuffer.append(currentChar);
                            break;
                        } else {
                            parenthesisCount--;
                        }
                    }
                    // look for  "\" character
                    /**
                     * The escape sequences can be as follows:
                     *   \n  - line feed (LF)
                     *   \r  - Carriage return (CR)
                     *   \t  - Horizontal tab  (HT)
                     *   \b  - backspace (BS)
                     *   \f  - form feed (FF)
                     *   \(  - left parenthesis
                     *   \)  - right parenthesis
                     *   \\  - backslash
                     *   \ddd - character code ddd (octal)
                     *
                     * Note: (\0053) denotes a string containing two characters,
                     *       \005 (Control-E) followed by the digit 3.
                     */
                    if (currentChar == '\\') {
                        // read next char
                        currentChar = (char) reader.read();

                        // check for a digit, if so we have an octal
                        // and we need to handle it correctly
                        if (Character.isDigit(currentChar)) {
                            // store the read digits
                            StringBuffer digit = new StringBuffer();
                            digit.append(currentChar);
                            // octals have a max size of 3 digits, we already
                            // have one, so there can be up 2 more digits.
                            for (int i = 0; i < 2; i++) {
                                // mark the reader incase the next read is not
                                // a digit.
                                reader.mark(1);
                                // read next char
                                currentChar = (char) reader.read();
                                if (Character.isDigit(currentChar)) {
                                    digit.append(currentChar);
                                } else {
                                    // back up the reader just incase
                                    // thre is only 1 or 2 digits in the octal
                                    reader.reset();
                                    break;
                                }
                            }

                            // finally convert digit to a character
                            int charNumber = 0;
                            try {
                                charNumber = Integer.parseInt(digit.toString(), 8);
                            }
                            catch (NumberFormatException e) {
                                logger.log(Level.FINE, "Integer parse error ", e);
                            }
                            // convert the interger from octal to dec.
                            currentChar = (char) charNumber;
                        }
                        // do nothing
                        else if (currentChar == '(' || currentChar == ')'
                                || currentChar == '\\') {
                        }
                        // capture the horizontal tab (HT), tab character is hard
                        // to find, only appears in files with font substitution and
                        // as a result we ahve better luck drawing a space character.
                        else if (currentChar == 't') {
                            currentChar = '\t';
                        }
                        // capture the carriage return (CR)
                        else if (currentChar == 'r') {
                            currentChar = '\r';
                        }
                        // capture the line feed (LF)
                        else if (currentChar == 'n') {
                            currentChar = '\n';
                        }
                        // capture the backspace (BS)
                        else if (currentChar == 'b') {
                            currentChar = '\b';
                        }
                        // capture the form feed (FF)
                        else if (currentChar == 'f') {
                            currentChar = '\f';
                        }
                        // ignor CF, which indicate a '\' lone split line token
                        else if (currentChar == 13) {
                            ignoreChar = true;
                        }
                        // otherwise report the file format error
                        else {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.warning("C=" + ((int) currentChar));
                            }
                        }
                    }
                }
            }
            // if we are not in a string definition we want to break
            // and return the current token, as white spaces or other elements
            // would mean that we are on the next token
            else if (isWhitespace(currentChar)) {
                // return  stringBuffer.toString();

                // we need to return the CR LR, as it is need by stream parsing
                if (currentByte == 13 || currentByte == 10) {
                    reader.reset();
                    break;
                }
                // break on any whitespace
                else {
                    // return  stringBuffer.toString();
                    break;
                }
            } else if (isDelimiter(currentChar)) {
                // reset the reader so we start on this token on the next parse
                reader.reset();
                break;
            }
            // append the current char and keep parsing if needed
            // IgnoreChar is set by the the line split char '\'
            if (!ignoreChar) {
                stringBuffer.append(currentChar);
            }
            // reset the ignorChar flag
            else {
                ignoreChar = false;
            }
        }
        while (!complete);

        /**
         * Return what we found
         */
        // if a hex string decode it as needed
        if (hexString) {
            lastTokenHString = true;
            return new HexStringObject(stringBuffer);
        }

        // do a little clean up for any object that may have been missed..
        // this mainly for the the document trailer information
        // a orphaned string
        if (inString) {
            return new LiteralStringObject(stringBuffer);
        }
        // return a new name
        else if (stringBuffer.charAt(0) == '/') {
            return new Name(stringBuffer.deleteCharAt(0));
        }
        // if a number try and parse it
        else {
            boolean foundDigit = false;
            boolean foundDecimal = false;
            for (int i = stringBuffer.length() - 1; i >= 0; i--) {
                char curr = stringBuffer.charAt(i);
                if (curr == '.')
                    foundDecimal = true;
                else if (curr >= '0' && curr <= '9')
                    foundDigit = true;
            }
            // Only bother trying to interpret as a number if contains a digit somewhere,
            //   to reduce NumberFormatExceptions
            if (foundDigit) {
                try {
                    if (foundDecimal)
                        return Float.valueOf(stringBuffer.toString());
                    else {
                        return Integer.valueOf(stringBuffer.toString());
                    }
                }
                catch (NumberFormatException ex) {
                    // Debug.trace("Number format exception " + ex);
                }
            }
        }
        return stringBuffer.toString();
    }

    public Object getNumberOrStringWithMark(int maxLength) throws IOException {
        reader.mark(maxLength);

        StringBuffer sb = new StringBuffer(maxLength);
        boolean readNonWhitespaceYet = false;
        boolean foundDigit = false;
        boolean foundDecimal = false;

        for (int i = 0; i < maxLength; i++) {
            int curr = reader.read();
            if (curr < 0)
                break;
            char currChar = (char) curr;
            if (isWhitespace(currChar)) {
                if (readNonWhitespaceYet)
                    break;
            } else if (isDelimiter(currChar)) {
                // Number or string has delimiter immediately after it,
                //   which we'll have to unread.
                // Had hoped it would be whitespace, so wouldn't have to unread
                reader.reset();
                reader.mark(maxLength);
                for (int j = 0; j < i; j++)
                    reader.read();

                readNonWhitespaceYet = true;
                break;
            } else {
                readNonWhitespaceYet = true;
                if (currChar == '.')
                    foundDecimal = true;
                else if (currChar >= '0' && curr <= '9')
                    foundDigit = true;
                sb.append(currChar);
            }
        }

        // Only bother trying to interpret as a number if contains a digit somewhere,
        //   to reduce NumberFormatExceptions
        if (foundDigit) {
            try {
                if (foundDecimal)
                    return Float.valueOf(sb.toString());
                else {
                    return Integer.valueOf(sb.toString());
                }
            }
            catch (NumberFormatException ex) {
                // Debug.trace("Number format exception " + ex);
            }
        }

        if (sb.length() > 0)
            return sb.toString();
        return null;
    }

    public void ungetNumberOrStringWithReset() throws IOException {
        reader.reset();
    }

    public int getIntSurroundedByWhitespace() {
        int num = 0;
        boolean makeNegative = false;
        boolean readNonWhitespace = false;
        try {
            while (true) {
                int curr = reader.read();
                if (curr < 0)
                    break;
                if (Character.isWhitespace((char) curr)) {
                    if (readNonWhitespace)
                        break;
                } else if (curr == '-') {
                    makeNegative = true;
                    readNonWhitespace = true;
                } else if (curr >= '0' && curr <= '9') {
                    num *= 10;
                    num += (curr - '0');
                    readNonWhitespace = true;
                }
            }
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error detecting int.", e);
        }
        if (makeNegative)
            num = num * -1;
        return num;
    }

    public long getLongSurroundedByWhitespace() {
        long num = 0L;
        boolean makeNegative = false;
        boolean readNonWhitespace = false;
        try {
            while (true) {
                int curr = reader.read();
                if (curr < 0)
                    break;
                if (Character.isWhitespace((char) curr)) {
                    if (readNonWhitespace)
                        break;
                } else if (curr == '-') {
                    makeNegative = true;
                    readNonWhitespace = true;
                } else if (curr >= '0' && curr <= '9') {
                    num *= 10L;
                    num += ((long) (curr - '0'));
                    readNonWhitespace = true;
                }
            }
        }
        catch (IOException e) {
           logger.log(Level.FINE, "Error detecting long.", e);
        }
        if (makeNegative)
            num = num * -1L;
        return num;
    }

    public char getCharSurroundedByWhitespace() {
        char alpha = 0;
        try {
            while (true) {
                int curr = reader.read();
                if (curr < 0)
                    break;
                char c = (char) curr;
                if (!Character.isWhitespace(c)) {
                    alpha = c;
                    break;
                }
            }
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error detecting char.", e);
        }
        return alpha;
    }

    int hexToInt(String hex) {
        hex = hex.substring(1, hex.length() - 1).toUpperCase();
        return Integer.parseInt(hex, 16 /* radix */);
    }

    /**
     * @param hh
     */
    String hexToString(String hh) {
        hh = hh.substring(1, hh.length() - 1).toUpperCase();
        StringBuffer sb = new StringBuffer();
        if (hh.charAt(0) == 'F'
                && hh.charAt(1) == 'E'
                && hh.charAt(2) == 'F'
                && hh.charAt(3) == 'F') {
            byte b[] = new byte[4];
            for (int i = 1; i < hh.length() / 4; i++) {
                b[0] = (byte) hh.charAt(i * 4);
                b[1] = (byte) hh.charAt(i * 4 + 1);
                b[2] = (byte) hh.charAt(i * 4 + 2);
                b[3] = (byte) hh.charAt(i * 4 + 3);
                sb.append((char) Integer.parseInt(new String(b), 16));
            }
        } else {
            byte b[] = new byte[2];
            for (int i = 0; i < hh.length() / 2; i++) {
                try {
                    b[0] = (byte) hh.charAt(i * 2);
                    b[1] = (byte) hh.charAt(i * 2 + 1);
                    sb.append((char) Short.parseShort(new String(b), 16));
                }
                catch (Exception e) {
                }
            }
        }

        return sb.toString();
    }

    /**
     * @return true if ate the ending EI delimiter
     * @throws java.io.IOException
     */
    boolean readLineForInlineImage(OutputStream out) throws IOException {
        // The encoder might not have put EI on its own line (as it should),
        //  but might just put it right after the data
        final int STATE_PRE_E = 0;
        final int STATE_PRE_I = 1;
        final int STATE_PRE_WHITESPACE = 2;
        int state = STATE_PRE_E;

        while (true) {
            int c = reader.read();
            if (c < 0)
                break;
            if (state == STATE_PRE_E && c == 'E') {
                state++;
                continue;
            } else if (state == STATE_PRE_I && c == 'I') {
                state++;
                continue;
            } else if (state == STATE_PRE_WHITESPACE && isWhitespace((char) (0xFF & c))) {
                // It's hard to tell if the EI + whitespace is part of the
                //  image data or not, given that many PDFs are mis-encoded,
                //  and don't give whitespace when necessary. So, instead of
                //  assuming the need for whitespace, we're going to assume
                //  that this is the real EI, and apply a heuristic to prove
                //  ourselves wrong.
                boolean imageDataFound = isStillInlineImageData(reader, 32);
                if (imageDataFound) {
                    out.write('E');
                    out.write('I');
                    out.write(c);
                    state = STATE_PRE_E;

                    if (c == '\r' || c == '\n') {
                        break;
                    }
                } else
                    return true;
            } else {
                // If we got a fragment of the EI<whitespace> sequence, then we withheld
                //  what we had so far.  But if we're here, that fragment was incomplete,
                //  so that was actual embedded data, and not the delimiter, so we have
                //  to write it out.
                if (state > STATE_PRE_E)
                    out.write('E');
                if (state > STATE_PRE_I)
                    out.write('I');
                state = STATE_PRE_E;

                out.write((byte) c);
                if (c == '\r' || c == '\n') {
                    break;
                }
            }
        }
        // If the input ends right after the EI, but with no whitespace,
        //  then we're still done
        if (state == STATE_PRE_WHITESPACE)
            return true;
        return false;
    }

    /**
     * @return
     * @throws java.io.IOException
     */
    byte readByte() throws IOException {
        //return reader.readByte();
        return (byte) reader.read();
    }

    /**
     * White space characters defined by ' ', '\t', '\r', '\n', '\f'
     *
     * @param c
     */
    public static final boolean isWhitespace(char c) {
        return ((c == ' ') || (c == '\t') || (c == '\r') ||
                (c == '\n') || (c == '\f'));
    }

    private static final boolean isDelimiter(char c) {
        return ((c == '[') || (c == ']') ||
                (c == '(') || (c == ')') ||
                (c == '<') || (c == '>') ||
                (c == '{') || (c == '}') ||
                (c == '/') || (c == '%'));
    }

    /**
     * This is not necessarily an exhaustive list of characters one would
     * expect in a Content Stream, it's a heuristic for whether the data
     * might still be part of an inline image, or the lattercontent stream
     */
    private static boolean isExpectedInContentStream(char c) {
        return ((c >= 'a' && c <= 'Z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                isWhitespace(c) ||
                isDelimiter(c) ||
                (c == '\\') ||
                (c == '\'') ||
                (c == '\"') ||
                (c == '*') ||
                (c == '.'));
    }

    /**
     * We want to be conservative in deciding that we're still in the inline
     * image, since we haven't found any of these cases before now.
     */
    private static boolean isStillInlineImageData(
            InputStream reader, int numBytesToCheck)
            throws IOException {
        boolean imageDataFound = false;
        boolean onlyWhitespaceSoFar = true;
        reader.mark(numBytesToCheck);
        byte[] toCheck = new byte[numBytesToCheck];
        int numReadToCheck = reader.read(toCheck);
        for (int i = 0; i < numReadToCheck; i++) {
            char charToCheck = (char) (((int) toCheck[i]) & 0xFF);

            // If the very first thing we read is a Q or S token
            boolean typicalTextTokenInContentStream =
                    (charToCheck == 'Q' || charToCheck == 'q' ||
                            charToCheck == 'S' || charToCheck == 's');
            if (onlyWhitespaceSoFar &&
                    typicalTextTokenInContentStream &&
                    (i + 1 < numReadToCheck) &&
                    isWhitespace((char) (((int) toCheck[i + 1]) & 0xFF))) {
                break;
            }
            if (!isWhitespace(charToCheck))
                onlyWhitespaceSoFar = false;

            // If we find some binary image data
            if (!isExpectedInContentStream(charToCheck)) {
                imageDataFound = true;
                break;
            }
        }
        reader.reset();
        return imageDataFound;
    }

    /**
     * @return
     * @throws java.io.IOException
     */
    String peek2() throws IOException {
        reader.mark(2);
        char c[] = new char[2];
        c[0] = (char) reader.read();
        c[1] = (char) reader.read();
        String s = new String(c);
        reader.reset();
        return s;
    }

    private long captureStreamData(OutputStream out) throws IOException {
        long numBytes = 0;
        while (true) {
            // read bytes
            int nextByte = reader.read();
            // look to see if we have the ending tag
            if (nextByte == 'e') {
                reader.mark(10);
                if (reader.read() == 'n' &&
                        reader.read() == 'd' &&
                        reader.read() == 's' &&
                        reader.read() == 't' &&
                        reader.read() == 'r' &&
                        reader.read() == 'e' &&
                        reader.read() == 'a' &&
                        reader.read() == 'm') {
                    break;
                } else {
                    reader.reset();
                }
            } else if (nextByte < 0)
                break;
            // write the bytes
            if (out != null)
                out.write(nextByte);
            numBytes++;
        }
        return numBytes;
    }

    private long skipUntilEndstream(OutputStream out) throws IOException {
        long skipped = 0L;
        while (true) {
            reader.mark(10);
            // read bytes
            int nextByte = reader.read();
            if (nextByte == 'e' &&
                    reader.read() == 'n' &&
                    reader.read() == 'd' &&
                    reader.read() == 's' &&
                    reader.read() == 't' &&
                    reader.read() == 'r' &&
                    reader.read() == 'e' &&
                    reader.read() == 'a' &&
                    reader.read() == 'm') {
                reader.reset();
                break;
            } else if (nextByte < 0)
                break;
            else {
                if (nextByte == 0x0A || nextByte == 0x0D || nextByte == 0x20)
                    continue;
                if (out != null)
                    out.write(nextByte);
            }
            skipped++;
        }
        return skipped;
    }
}
