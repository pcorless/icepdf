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
            writeRaw(writeable.getHexString(), output);
        } else {
            write(new HexStringObject(writeable.toString().replaceAll(HEX_REGEX, HEX_REPLACEMENT),
                    pObject.getReference(), securityManager), output);
        }
    }

    public void write(HexStringObject writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        writeByteString(writeable.getHexString().replaceAll(HEX_REGEX, HEX_REPLACEMENT), output);
        output.write(END_HEX_STRING);
    }

    public void writeRaw(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        byte[] textBytes = Utils.convertByteCharSequenceToByteArray(writeable.replaceAll(HEX_REGEX, HEX_REPLACEMENT));
        output.write(textBytes);
        output.write(END_HEX_STRING);
    }
}