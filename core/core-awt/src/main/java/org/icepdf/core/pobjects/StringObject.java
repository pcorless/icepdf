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

import org.icepdf.core.pobjects.security.SecurityManager;

/**
 * <p>This class represents a PDF String Object.  A <code>StringObject</code>
 * consists of a series of bytes in the range 0 to 255. A <code>StringObject</code>
 * can be written in two ways:</p>
 * <ul>
 * <li>Literal Strings - {@link LiteralStringObject}  </li>
 * <li>Hexadecimal Strings - {@link HexStringObject}  </li>
 * </ul>
 * <p>The methods define in this interface are common to both Literal and
 * Hexadecimal Strings Object.</p>
 *
 * @since 2.0
 */
public interface StringObject {

    /**
     * <p>Returns a string representation of the object.</p>
     *
     * @return a string representing the object.
     */
    String toString();

    /**
     * <p>Gets a literal String representation of this object's data.
     *
     * @return a String representation of the object's data.
     */
    String getLiteralString();

    /**
     * <p>Gets a hexadecimal String representation of this object's data. </p>
     *
     * @return a String representation of the object's data.
     */
    String getHexString();

    /**
     * The string's raw bytes, as they appeared in the file: no character-code interpretation, and
     * for a hexadecimal string no hex decoding artefacts.  This is the input to
     * {@link org.icepdf.core.pobjects.fonts.Font#toCodes(byte[])}, which is the only thing that
     * knows how wide a character code is.
     *
     * @return the string's bytes; never null, may be empty.
     */
    byte[] getRawBytes();

    /**
     * The string's bytes, decrypted, with no character interpretation.  The primitive for a string
     * holding binary data rather than text; {@link #getDecryptedLiteralString} is the text
     * counterpart.
     *
     * @param securityManager security manager associated with parent document.
     * @return the decrypted bytes; never null, may be empty.
     */
    byte[] getDecryptedRawBytes(SecurityManager securityManager);

    /**
     * The length of the underlying objects data.
     *
     * @return length of object's data.
     */
    int getLength();

    /**
     * Sets the parent PDF object's reference.
     *
     * @param reference parent object reference.
     */
    void setReference(Reference reference);

    /**
     * Gets the decrypted literal string value of the data using the key provided by the
     * security manager.
     *
     * @param securityManager security manager associated with parent document.
     * @return decrypted stream.
     */
    String getDecryptedLiteralString(SecurityManager securityManager);

    /**
     * Indicated the string data has been modified and may need to be encrypted if persisted
     *
     * @return true if the object has been modified.
     */
    boolean isModified();

}
