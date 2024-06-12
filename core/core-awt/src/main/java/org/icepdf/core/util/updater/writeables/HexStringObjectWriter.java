package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

import java.io.IOException;

public class HexStringObjectWriter extends BaseWriter {

    private static final byte[] BEGIN_HEX_STRING = "<".getBytes();
    private static final byte[] END_HEX_STRING = ">".getBytes();

    private static final String HEX_REGEX = "(?=[<>\\\\])";
    private static final String HEX_REPLACEMENT = "\\\\";

    public HexStringObjectWriter(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        HexStringObject writeable = (HexStringObject) pObject.getObject();
        if (pObject.isDoNotEncrypt()) {
            writeRaw(writeable.getHexString().replaceAll(HEX_REGEX, HEX_REPLACEMENT), output);
        } else if (securityManager != null) {
            if (writeable.isModified()) {
                // encryption will take care of any escape issue.
                String writeableString = writeable.encryption(writeable.getHexString(), pObject.getReference(),
                        securityManager);
                writeRaw(writeableString.replaceAll(HEX_REGEX, HEX_REPLACEMENT), output);
            } else {
                // just need to write the string data as is, string data will already be in the correct state
                writeRaw(writeable.toString(), output);
            }
        } else {
            // plain string make sure it's properly escaped.
            writeRaw(writeable.getHexString().replaceAll(HEX_REGEX, HEX_REPLACEMENT), output);
        }
    }

    public void write(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        writeByteString(writeable, output);
        output.write(END_HEX_STRING);
    }

    public void writeRaw(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        byte[] textBytes = Utils.convertByteCharSequenceToByteArray(writeable);
        output.write(textBytes);
        output.write(END_HEX_STRING);
    }
}