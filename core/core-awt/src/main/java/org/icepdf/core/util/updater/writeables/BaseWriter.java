package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Base writer, responsible for setting up the writers and writing the base pobjects.
 */
public class BaseWriter {

    protected static final byte[] SPACE = " ".getBytes();
    protected static final byte[] NEWLINE = "\r\n".getBytes();
    protected static final byte[] TRUE = "true".getBytes();
    protected static final byte[] FALSE = "false".getBytes();
    protected static final byte[] NULL = "null".getBytes();
    protected static final byte[] REFERENCE = "R".getBytes();

    protected static final byte[] BEGIN_OBJECT = "obj\r\n".getBytes();
    protected static final byte[] END_OBJECT = "\r\nendobj\r\n".getBytes();

    private static NameWriter nameWriter;
    private static DictionaryWriter dictionaryWriter;
    private static ReferenceWriter referenceWriter;
    private static HexStringObjectWriter hexStringObjectWriter;
    private static LiteralStringWriter literalObjectWriter;
    private static ArrayWriter arrayWriter;
    private static AffineTransformWriter affineTransformWriter;
    private static PObjectWriter pObjectWriter;
    protected static StreamWriter streamWriter;

    private static XRefTableWriter xRefTableWriter;
    private static TrailerWriter trailerWriter;
    private static CompressedXrefTableWriter compressedXrefTableWriter;

    private CountingOutputStream output;
    private SecurityManager securityManager;
    private PTrailer trailer;
    private long startingPosition;
    private List<Entry> entries;
    private long xrefPosition;

    public BaseWriter(){

    }

    public BaseWriter(PTrailer trailer, SecurityManager securityManager, CountingOutputStream output,
                      long startingPosition) {
        this.output = output;
        this.trailer = trailer;
        this.securityManager = securityManager;
        this.startingPosition = startingPosition;
        entries = new ArrayList<>(256);
    }

    public void initializeWriters() {
        // reuse instances of primitive pdf types.
        streamWriter = new StreamWriter();
        nameWriter = new NameWriter();
        dictionaryWriter = new DictionaryWriter();
        referenceWriter = new ReferenceWriter();
        hexStringObjectWriter = new HexStringObjectWriter();
        literalObjectWriter = new LiteralStringWriter();
        arrayWriter = new ArrayWriter();
        affineTransformWriter = new AffineTransformWriter();
        pObjectWriter = new PObjectWriter();
        xRefTableWriter = new XRefTableWriter();
        trailerWriter = new TrailerWriter();
        compressedXrefTableWriter = new CompressedXrefTableWriter();
    }

    public long getBytesWritten() {
        return output.getCount();
    }

    public void writePObject(PObject pobject) throws IOException {
        entries.add(new Entry(pobject.getReference(), startingPosition + output.getCount()));
        if (pobject.getObject() instanceof Stream) {
            Stream stream = (Stream) pobject.getObject();
            Reference reference = stream.getPObjectReference();
            if (stream.isDeleted()) {
                entries.add(new Entry(reference)); // empty reference, no bytes needed
                return;
            }
            streamWriter.write((Stream) pobject.getObject(), securityManager, output);

        } else {
            pObjectWriter.write(pobject, output);
        }
    }

    public void writeXRefTable() throws IOException {
        xrefPosition = xRefTableWriter.writeXRefTable(entries, startingPosition, output);
    }

    public void writeTrailer() throws IOException {
        trailerWriter.writeTrailer(trailer, xrefPosition, entries, output);
    }

    public void writeCompressedXrefTable() throws IOException {
        compressedXrefTableWriter.writeCompressedXrefTable(trailer, securityManager, entries, startingPosition, output);
    }

    public void writeNewLine() throws IOException {
        output.write(NEWLINE);
    }

    protected void writeValue(Object val, CountingOutputStream output) throws IOException {
        if (val == null) {
            output.write(NULL);
        } else if (val instanceof Name) {
            nameWriter.write((Name) val, output);
        } else if (val instanceof Reference) {
            referenceWriter.write((Reference) val, output);
        } else if (val instanceof Boolean) {
            writeBoolean((Boolean) val, output);
        } else if (val instanceof Integer) {
            writeInteger((Integer) val, output);
        } else if (val instanceof Long) {
            writeLong((Long) val, output);
        } else if (val instanceof Number) {
            writeReal((Number) val, output);
        } else if (val instanceof String) {
            literalObjectWriter.write((String) val, output);
        } else if (val instanceof LiteralStringObject) {
            literalObjectWriter.write(((LiteralStringObject) val), output);
        } else if (val instanceof HexStringObject) {
            hexStringObjectWriter.write((HexStringObject) val, output);
        } else if (val instanceof List) {
            arrayWriter.write((List) val, output);
        } else if (val instanceof Dictionary) {
            writeDictionary((Dictionary) val, output);
        } else if (val instanceof HashMap) {
            dictionaryWriter.write((HashMap) val, output);
        } else if (val instanceof AffineTransform) {
            affineTransformWriter.write((AffineTransform) val, output);
        } else {
            throw new IllegalArgumentException("Unknown value:" + val.getClass().getName());
        }
    }

    protected void writeName(Name name, CountingOutputStream output) throws IOException {
        nameWriter.write(name, output);
    }

    protected void writeDictionary(Dictionary dictionary, CountingOutputStream output) throws IOException {
        dictionaryWriter.write(dictionary, output);
    }

    protected void writeBoolean(boolean bool, CountingOutputStream output) throws IOException {
        if (bool) {
            output.write(TRUE);
        } else {
            output.write(FALSE);
        }
    }

    protected void writeInteger(int i, CountingOutputStream output) throws IOException {
        String str = Integer.toString(i);
        writeByteString(str, output);
    }

    protected void writeLong(long i, CountingOutputStream output) throws IOException {
        String str = Long.toString(i);
        writeByteString(str, output);
    }

    protected void writeReal(Number r, CountingOutputStream output) throws IOException {
        String str = r.toString();
        writeByteString(str, output);
    }

    protected void writeByteString(String str, CountingOutputStream output) throws IOException {
        int val;
        for (int i = 0, len = str.length(); i < len; i++) {
            val = ((int) str.charAt(i)) & 0xFF;
            output.write(val);
        }
    }

}
