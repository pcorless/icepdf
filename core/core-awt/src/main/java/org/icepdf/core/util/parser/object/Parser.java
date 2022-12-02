package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;
import org.icepdf.core.pobjects.structure.CrossReferenceTable;
import org.icepdf.core.pobjects.structure.CrossReferenceUsedEntry;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.ByteBufferUtil;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public class Parser {

    // legacy xref markers.
    //                                              x    r    e    f
    public static final byte[] XREF_MARKER = new byte[]{114, 101, 102}; // 120
    //                                                       t    r    a    i   l    e    r
    public static final byte[] TRAILER_MARKER = new byte[]{116, 114, 97, 105, 108, 101, 114};

    // object markers                                      o    b   j
    public static final byte[] OBJ_MARKER = new byte[]{32, 111, 98, 106};
    public static final byte[] SPACE_MARKER = new byte[]{32};
    //                                                      e    n    d    o    b   j
    public static final byte[] END_OBJ_MARKER = new byte[]{101, 110, 100, 111, 98, 106};

    // stream marking
    //                                                     s    t    r    e    a   m
    public static final byte[] STREAM_MARKER = new byte[]{115, 116, 114, 101, 97, 109};
    //                                                         e    n    d    s    t    r    e    a   m
    public static final byte[] END_STREAM_MARKER = new byte[]{101, 110, 100, 115, 116, 114, 101, 97, 109};

    private Library library;

    public Parser(Library library) {
        this.library = library;
    }

    public PObject getPObject(ByteBuffer byteBuffer, int objectOffsetStart)
            throws IOException, ObjectStateException {

        int objectOffsetEnd = 0;
        int streamOffsetStart;
        int streamOffsetEnd;
        ByteBuffer streamByteBuffer;
        synchronized (library.getMappedFileByteBufferLock()) {
            // scan ahead to find the end objc position
            byteBuffer.position(objectOffsetStart);
            boolean foundEndObjMarker = ByteBufferUtil.findString(byteBuffer, END_OBJ_MARKER);
            if (foundEndObjMarker) {
                objectOffsetEnd = byteBuffer.position() - END_OBJ_MARKER.length;
            } else {
                // corner case were there is no endobj but xref delimits last object.
                objectOffsetEnd = byteBuffer.position() - XREF_MARKER.length;
            }
            // scan looking for the stream object end
            // copy the bytes to a new buffer so we can work on the bytes without thread position issues.
            streamByteBuffer = ByteBufferUtil.copyObjectStreamSlice(byteBuffer, objectOffsetStart, objectOffsetEnd);
        }
        // grab the pieces of the object
        Lexer lexer = new Lexer(library);
        lexer.setByteBuffer(streamByteBuffer);
        // number
        Object token = lexer.nextToken();
        int objectNumber;
        if (token instanceof Integer) {
            objectNumber = (Integer) token;
        } else {
            token = lexer.nextToken();
            if (token instanceof Integer) {
                objectNumber = (Integer) token;
            } else {
                throw new ObjectStateException();
            }
        }
        // generation
        token = lexer.nextToken();
        int objectGeneration;
        if (token instanceof Integer) {
            objectGeneration = (Integer) token;
        } else {
            throw new ObjectStateException();
        }
        // int = 1 obj
        Object objectOp = lexer.nextToken();
        if (!(objectOp instanceof Integer && ((Integer) objectOp) == OperandNames.OP_obj)) {
            throw new ObjectStateException();
        }
        // dictionary or single value
        Object objectData = lexer.nextToken(new Reference(objectNumber, objectGeneration));
        // stream or endobj
        Object streamOrEndObj = lexer.nextToken();
        if (streamOrEndObj instanceof Integer && ((Integer) streamOrEndObj) == OperandNames.OP_stream) {
            lexer.skipWhiteSpace();
            // stream offset
            streamOffsetStart = streamByteBuffer.position();
            int streamLength = library.getInt((DictionaryEntries) objectData, Dictionary.LENGTH_KEY);
            // doublc check a streamLength = zero, some encoders are lazy and there is actually data.
            if (streamLength == 0 && streamByteBuffer.limit() - streamOffsetStart > 0) {
                streamLength = streamByteBuffer.limit() - streamOffsetStart;
            }
            streamOffsetEnd = streamOffsetStart + streamLength;

            if (streamOffsetEnd <= 0 || streamOffsetEnd > streamByteBuffer.limit()) {
                // work backwards to find end stream location
                int lookBackLength = streamByteBuffer.limit() - (END_STREAM_MARKER.length + 4); // white space padding
                streamOffsetEnd = ByteBufferUtil.findReverseString(streamByteBuffer, streamByteBuffer.limit(),
                        lookBackLength, END_STREAM_MARKER);
            }

            // search just to see if any white space should be removed between streamOffsetEnd and the actual endstream
            // we need to back out space (0x20), LF (0x0A) or CR (0x0D)
//            streamOffsetEnd = lexer.skipUntilEndstream(streamOffsetEnd);

            // trim the buffer to the stream start end.
            streamByteBuffer.position(streamOffsetStart);
            streamByteBuffer.limit(streamOffsetEnd);
            streamByteBuffer = streamByteBuffer.compact();
            streamByteBuffer.position(0);
            streamByteBuffer.limit(streamOffsetEnd - streamOffsetStart);
        } else {
            streamByteBuffer = null;
        }
        // push dictionary through factory to build correct instance.
        return ObjectFactory.getInstance(library, objectNumber, objectGeneration, objectData, streamByteBuffer);
    }

    public PObject getCompressedObject(ByteBuffer streamObjectByteBuffer, int objectNumber,
                                       int objectOffsetStart) throws IOException, ObjectStateException {
        // grab the pieces of the object
        streamObjectByteBuffer.position(objectOffsetStart);
        Lexer lexer = new Lexer(library);
        lexer.setByteBuffer(streamObjectByteBuffer);
        // dictionary or single value
        Object objectData = lexer.nextToken(new Reference(objectNumber, 0));
        // push dictionary through factory to build correct instance.
        return ObjectFactory.getInstance(library, objectNumber, 0, objectData, null);
    }

    public CrossReference getCrossReference(ByteBuffer byteBuffer, int starXref)
            throws CrossReferenceStateException, ObjectStateException, IOException {
        // mark our position
        ByteBuffer lookAheadBuffer;
        synchronized (library.getMappedFileByteBufferLock()) {
            byteBuffer.position(starXref);
            // make sure we have an xref declaration
            int bytesLeft = byteBuffer.limit() - starXref;
            lookAheadBuffer = ByteBuffer.allocateDirect(bytesLeft < 48 ? bytesLeft : 48);
            while (lookAheadBuffer.hasRemaining()) {
                lookAheadBuffer.put(byteBuffer.get());
            }
        }
        lookAheadBuffer.flip();
        boolean foundXrefMarker = ByteBufferUtil.findString(lookAheadBuffer, XREF_MARKER);
        Lexer objectLexer = new Lexer(library);
        synchronized (library.getMappedFileByteBufferLock()) {
            // see if we found xref marking and thus an < 1.5 formatted xref table.
            if (foundXrefMarker) {
                // update the xref position as we will have removed any white space.
                starXref += lookAheadBuffer.position() - XREF_MARKER.length;
                // scan ahead to find the trailer position
                byteBuffer.position(starXref + XREF_MARKER.length);
                boolean foundTrailerMarker = ByteBufferUtil.findString(byteBuffer, TRAILER_MARKER);
                if (!foundTrailerMarker || byteBuffer.position() == byteBuffer.limit()) {
                    throw new CrossReferenceStateException();
                }
                // parse the dictionary using our lexer, so we can look for a /hrefstm entry.
                int startTrailer = byteBuffer.position() - TRAILER_MARKER.length;
                objectLexer.setByteBuffer(byteBuffer);
                Object token = objectLexer.nextToken();
                if (token instanceof DictionaryEntries) {
                    DictionaryEntries xrefDictionary = (DictionaryEntries) token;
                    return parseCrossReferenceTable(xrefDictionary, objectLexer, byteBuffer, starXref + XREF_MARKER.length, startTrailer);
                }
            }
            // if there is an entry we can ignore parsing the table as it's redundant and just parse the strmObject
            return parseCrossReferenceStream(objectLexer, byteBuffer, starXref);
        }
    }

    private CrossReference parseCrossReferenceTable(DictionaryEntries dictionaryEntries, Lexer objectLexer, ByteBuffer byteBuffer,
                                                    int start, int end) throws IOException {
        // allocate to a new buffer as the data is well-defined.
        ByteBuffer xrefTableBuffer = ByteBufferUtil.copyObjectStreamSlice(byteBuffer, start, end);
        CrossReferenceTable crossReferenceTable = new CrossReferenceTable(library, dictionaryEntries);
        objectLexer.setByteBuffer(xrefTableBuffer);
        // parse the sub groupings
        while (true) {
            Integer startObjectNumber = (Integer) objectLexer.nextToken();
            // buffer end will result in a null token
            if (startObjectNumber == null) break;
            int numberOfObjects = (Integer) objectLexer.nextToken();
            int currentNumber = startObjectNumber;
            for (int i = 0; i < numberOfObjects; i++) {
                int offset = (Integer) objectLexer.nextToken();
                int generation = (Integer) objectLexer.nextToken();
                int state = (Integer) objectLexer.nextToken();
                if (state == OperandNames.OP_n) {
                    crossReferenceTable.addEntry(
                            new CrossReferenceUsedEntry(currentNumber, generation, offset));
                }else if (state == OperandNames.OP_f) {    // Free
                    // check for the first entry 0000000000 65535 f  and
                    // an object range where the first entry isn't zero.  The
                    // code below will treat the first entry as zero and then
                    // start counting.
                    if (i == 0 && startObjectNumber > 0 && generation == 65535) {
                        // offset the count, so we start counting after the zeroed entry
                        currentNumber--;
                    }
                }
                currentNumber++;
            }
        }

        return crossReferenceTable;
    }

    private CrossReference parseCrossReferenceStream(Lexer objectLexer, ByteBuffer byteBuffer, int offset)
            throws IOException, ObjectStateException {
        // use parser to get xref stream object.
        CrossReferenceStream crossReferenceStream = (CrossReferenceStream) getPObject(byteBuffer, offset).getObject();
        crossReferenceStream.initialize();
        return crossReferenceStream;
    }
}