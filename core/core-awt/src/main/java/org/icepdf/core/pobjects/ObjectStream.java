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

import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.Lexer;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Mark Collette
 * @since 2.0
 */
public class ObjectStream extends Stream {

    private static final Logger logger =
            Logger.getLogger(ObjectStream.class.toString());

    public static final Name TYPE = new Name("ObjStm");
    public static final Name N_KEY = new Name("N");
    public static final Name FIRST_KEY = new Name("First");

    private ByteBuffer decodedByteBufer;
    private int[] objectNumbers;
    private float[] objectOffset;

    public ObjectStream(Library library, DictionaryEntries dictionaryEntries, byte[] rawBytes) {
        super(library, dictionaryEntries, rawBytes);
    }

    public void initialize() {
        if (inited) {
            return;
        }
        int numObjects = library.getInt(entries, N_KEY);
        long firstObjectsOffset = library.getLong(entries, FIRST_KEY);
        // get the stream data
        decodedByteBufer = getDecodedStreamByteBuffer(0);
        objectNumbers = new int[numObjects];
        objectOffset = new float[numObjects];
        try {
            Lexer lexer = new Lexer(library);
            lexer.setByteBuffer(decodedByteBufer);
            for (int i = 0; i < numObjects; i++) {
                objectNumbers[i] = (Integer) lexer.nextToken();
                objectOffset[i] = ((Integer) lexer.nextToken()) + firstObjectsOffset;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Error loading object stream instance: " + this.toString(), e);
        }
        inited = true;
    }

    public PObject decompressObject(Parser parser, int objectIndex) throws IOException, ObjectStateException {
        initialize();
        if (objectNumbers == null || objectOffset == null || objectNumbers.length != objectOffset.length ||
                objectIndex < 0 || objectIndex >= objectNumbers.length) {
            return null;
        }

        int objectNumber = objectNumbers[objectIndex];
        int position = (int) objectOffset[objectIndex];

        decodedByteBufer.position(position);

        return parser.getCompressedObject(decodedByteBufer, objectNumber, position);
    }
}
