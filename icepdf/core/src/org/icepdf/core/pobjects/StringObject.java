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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.security.SecurityManager;

/**
 * <p>This class represents a PDF String Object.  A <code>StringObject</code>
 * consists of a series of bytes in the range 0 to 255. A <code>StringObject</code>
 * can be written in two ways:</p>
 * <ul>
 * <li>Literal Strings - {@see LiteralStringObject}  </li>
 * <li>Hexadecimal Strings - {@see HexStringObject}  </li>
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
    public String toString();

    /**
     * <p>Gets a literal StringBuffer representation of this object's data.</p>
     *
     * @return a StringBuffer representing the objects data.
     */
    public StringBuilder getLiteralStringBuffer();

    /**
     * <p>Gets a literal String representation of this object's data.
     *
     * @return a String representation of the object's data.
     */
    public String getLiteralString();

    /**
     * <p>Gets a hexadecimal StringBuffer representation of this objects data.</p>
     *
     * @return a StringBufffer representation of the objects data.
     */
    public StringBuilder getHexStringBuffer();

    /**
     * <p>Gets a hexadecimal String representation of this object's data. </p>
     *
     * @return a String representation of the object's data.
     */
    public String getHexString();

    /**
     * Gets the unsigned integer value of this object's data specified by
     * the start index and offset parameters.
     *
     * @param start  the beginning index, inclusive.
     * @param offset the number of string characters to read.
     * @return integer value of the specified range of characters.
     */
    public int getUnsignedInt(int start, int offset);

    /**
     * Gets a literal String representation of this objects data using the
     * specified font and format.
     *
     * @param fontFormat the type of PDF font which will be used to display
     *                   the text.  Valid values are CID_FORMAT and SIMPLE_FORMAT for Adobe
     *                   Composite and Simple font types respectively
     * @param font       font used to render the literal string data.
     * @return StringBuffer which contains all renderaable characters for the
     *         given font.
     */
    public StringBuilder getLiteralStringBuffer(final int fontFormat, FontFile font);

    /**
     * The length of the underlying objects data.
     *
     * @return length of objct's data.
     */
    public int getLength();

    /**
     * Sets the parent PDF object's reference.
     *
     * @param reference parent object reference.
     */
    public void setReference(Reference reference);

    /**
     * Sets the parent PDF object's reference.
     *
     * @return returns the reference used for encryption.
     */
    public Reference getReference();

    /**
     * Gets the decrypted literal string value of the data using the key provided by the
     * security manager.
     *
     * @param securityManager security manager associated with parent document.
     * @return decrypted stream. 
     */
    public String getDecryptedLiteralString(SecurityManager securityManager);

}
