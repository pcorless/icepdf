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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.pobjects.security;

import org.icepdf.core.pobjects.Reference;

import java.io.InputStream;

/**
 * The interface for objects which defines a Security Handler for a PDF
 * document.  A custom Security Handlers should implement this interface.
 *
 * @since 1.1
 */
public interface SecurityHandlerInterface {

    /**
     * Determines whether the supplied password is authorized to view the
     * PDF document.  If a password is rejected, the user should be restricted
     * from viewing the document.
     *
     * @param password password to authorize
     * @return true, if the password was authorized successfully; false, otherwise.
     */
    public boolean isAuthorized(String password);

    /**
     * Determines whether the supplied user password is authorized to view the
     * PDF document.  If a password is rejected, the user should be restricted
     * from viewing the document.
     *
     * @param password password to authorize
     * @return true, if the password was authorized successfully; false, otherwise.
     */
    public boolean isUserAuthorized(String password);

    /**
     * Determines whether the supplied owner password is authorized to view the
     * PDF document.  If a password is rejected, the user should be restricted
     * from viewing the document.
     *
     * @param password password to authorize
     * @return true, if the password was authorized successfully; false, otherwise.
     */
    public boolean isOwnerAuthorized(String password);

    /**
     * Encrypt the PDF data bytestream or string.
     *
     * @param objectReference reference to PDF object being encrypted; this object
     *                        contains the PDF object number and revision.
     * @param encryptionKey   encryption key used by encryption algorithm.
     * @param data            byte data to be encrypted;  either represents an object stream
     *                        or string value.
     * @return the encrypted stream or string  byte data
     */
    public byte[] encrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data);

    /**
     * Decrypt the PDF data bytestream or string.
     *
     * @param objectReference reference to PDF object being encrypted; this object
     *                        contains the PDF object number and revision.
     * @param encryptionKey   encryption key used by decryption algorithm.
     * @param data            byte data to be decrypted;  either represents an object stream
     *                        or string value.
     * @return the decrypted stream or string byte data
     */
    public byte[] decrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data);

    public InputStream getEncryptionInputStream(
            Reference objectReference,
            byte[] encryptionKey,
            InputStream input);

    /**
     * Gets the encryption key used by the security handler for encrypting data.
     *
     * @return byte data representing encryption key
     */
    public byte[] getEncryptionKey();

    /**
     * Gets the encryption key used by the security handler for decryption data.
     *
     * @return byte data representing encryption key
     */
    public byte[] getDecryptionKey();

    /**
     * Gets the name of the default security handler.
     *
     * @return string representing security handler name
     */
    public String getHandlerName();

    /**
     * Gets the PDF permissions object associated with this document's
     * security handler.
     *
     * @return security handlers permissions object
     */
    public Permissions getPermissions();

    /**
     * Initiate the security handler
     */
    public void init();

    /**
     * Dispose of the security handler.
     */
    public void dispose();
}
