/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Parser;

import java.util.Hashtable;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * @author Mark Collette
 * @since 2.0
 */
public class ObjectStream extends Stream {

    private static final Logger logger =
            Logger.getLogger(Form.class.toString());

    private boolean m_bInited;
    private SeekableInput m_DecodedStream;
    private int[] m_iaObjectNumbers;
    private long[] m_laObjectOffset;

    /**
     * Create a new instance of a Stream.
     *
     * @param l                  library containing a hash of all document objects
     * @param h                  hashtable of parameters specific to the Stream object.
     * @param streamInputWrapper Accessor to stream byte data
     */
    public ObjectStream(Library l, Hashtable h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h, streamInputWrapper);
    }

    public synchronized void init() {
        if (m_bInited)
            return;
        m_bInited = true;
        int numObjects = library.getInt(entries, "N");
        long firstObjectsOffset = library.getLong(entries, "First");
        byte[] data = getBytes();
        m_DecodedStream = new SeekableByteArrayInputStream(data);
        m_DecodedStream.beginThreadAccess();
        m_iaObjectNumbers = new int[numObjects];
        m_laObjectOffset = new long[numObjects];
        try{
            Parser parser = new Parser(m_DecodedStream);
            for (int i = 0; i < numObjects; i++) {
                m_iaObjectNumbers[i] = parser.getIntSurroundedByWhitespace();
                m_laObjectOffset[i] = parser.getLongSurroundedByWhitespace() + firstObjectsOffset;
            }
        }
        finally {
            m_DecodedStream.endThreadAccess();
        }

    }

    public boolean loadObject(Library library, int objectIndex) {
//System.out.println("ObjectStream.loadObject()  objectIndex: " + objectIndex);
        init();
        if (m_iaObjectNumbers == null ||
                m_laObjectOffset == null ||
                m_iaObjectNumbers.length != m_laObjectOffset.length ||
                objectIndex < 0 ||
                objectIndex >= m_iaObjectNumbers.length) {
//System.out.println("ObjectStream.loadObject()  init failed");
            return false;
        }
        boolean gotSomething = false;
        try {
            int objectNumber = m_iaObjectNumbers[objectIndex];
            long position = m_laObjectOffset[objectIndex];
//System.out.println("ObjectStream.loadObject()  objectNumber: " + objectNumber + ", position: " + position);
            m_DecodedStream.beginThreadAccess();
            m_DecodedStream.seekAbsolute(position);
            Parser parser = new Parser(m_DecodedStream, Parser.PARSE_MODE_OBJECT_STREAM);
            // Parser.getObject() either does 1 of 3 things:
            // 1. Gets a core object (Dictionary or Stream), adds it to Library by object Reference, returns PObject
            // 2. Gets a non-core-object, leaves it on stack, returns null
            // 3. Gets a non-core-object, returns it
            Object ob = parser.getObject(library);
            if (ob == null) {
                Reference ref = new Reference(objectNumber, 0);
                ob = parser.addPObject(library, ref);
            } else if (!(ob instanceof PObject)) {
                Reference ref = new Reference(objectNumber, 0);
                library.addObject(ob, ref);
            }
            // assign object reference, needed for encrypting and state saving
            if (ob != null && ob instanceof Dictionary){
                ((Dictionary)ob).setPObjectReference(
                        new Reference(objectNumber, 0));
            }

//System.out.println("ObjectStream.loadObject()  ob: " + ob + ",  ob.class: " + ob.getClass().getName());
            gotSomething = (ob != null);
        }
        catch (Exception e) {
             logger.log(Level.FINE, "Error loading PDF object.", e);
        }
        finally {
            m_DecodedStream.endThreadAccess();
        }
        return gotSomething;
    }

    public void dispose(boolean cache) {
        m_bInited = false;
        m_DecodedStream = null;
        m_iaObjectNumbers = null;
        m_laObjectOffset = null;
        super.dispose(cache);
    }
}
