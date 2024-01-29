package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

import java.io.IOException;

public class LiteralStringWriter extends BaseWriter {

    protected static final byte[] BEGIN_LITERAL_STRING = "(".getBytes();
    protected static final byte[] END_LITERAL_STRING = ")".getBytes();

    protected static final String LITERAL_REGEX = "(?=[()\\\\])";
    protected static final String LITERAL_REPLACEMENT = "\\\\";

    public LiteralStringWriter(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        LiteralStringObject writeable = (LiteralStringObject) pObject.getObject();
        if (pObject.isDoNotEncrypt()) {
            writeRaw(writeable.getLiteralString(), output);
        } else if (securityManager != null) {
            if (writeable.isModified()) {
                // encryption will take care of any escape issue.
                String writeableString = writeable.encryption(writeable.getLiteralString(), pObject.getReference(),
                        securityManager);
                writeRaw(writeableString, output);
            } else {
                // just need to write the string data as is, string data will already be in the correct state
                writeRaw(writeable.getLiteralString(), output);
            }
        } else {
            // plain string make sure it's properly escaped.
            String escaped = writeable.getLiteralString().replaceAll(LITERAL_REGEX, LITERAL_REPLACEMENT);
            writeRaw(escaped, output);
        }
    }

    public void write(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        writeByteString(writeable, output);
        output.write(END_LITERAL_STRING);
    }

    public void writeRaw(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        byte[] textBytes = Utils.convertByteCharSequenceToByteArray(writeable);
        output.write(textBytes);
        output.write(END_LITERAL_STRING);
    }
}
