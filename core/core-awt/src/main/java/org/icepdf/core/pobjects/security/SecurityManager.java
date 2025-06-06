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
package org.icepdf.core.pobjects.security;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.security.Security;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The Security Manager class manages the encryption of encrypted
 * PDF documents.  The class is initiated by the Document class if a
 * Crypt key is found in the document's trailer.  The singleton pattern
 * is implemented so that it can be called from anywhere with the PDF
 * object structure.</p>
 * <br>
 * <p>There is currently only support for Adobe Standard encryption which is
 * supported by the StandardSecurityHandler.  Additional support for custom
 * security handlers, public-key handlers and crypt filters is currently under
 * development.</p>
 * <br>
 * <p>The Security Manager needs tobe compliant with Sun Java JCE 1.2.1 implementation.
 * The security manager assumes that
 * org.bouncycastle.jce.provider.BouncyCastleProvider can be found on the class
 * path and will try to load the class accordingly.  However, if you have another
 * crypto API that you would like to use, the system property
 * org.icepdf.core.pobjects.security.provider can be set to the provider's class path.</p>
 *
 * @since 1.1
 */
public class SecurityManager {

    private static final Logger logger =
            Logger.getLogger(SecurityManager.class.toString());

    // Default Encryption dictionary, which also contians keys need for
    // standard, crypt and public security handlers.
    private final EncryptionDictionary encryptDictionary;

    // Pointer to class which implements the SecurityHandler interface
    private final SecurityHandler securityHandler;

    // key caches, fairly expensive calculation
    private byte[] encryptionKey;
    private byte[] decryptionKey;

    /**
     * Disposes of the security handler instance.
     */
    public void dispose() {

    }

    /**
     * Creates new instance of SecurityManager object.
     *
     * @param library              library of documents PDF objects
     * @param encryptionDictionary encryption dictionary key values
     * @param fileID               fileID of PDF document
     * @throws PDFSecurityException if the security provider could not be found
     */
    public SecurityManager(Library library, DictionaryEntries encryptionDictionary,
                           List fileID)
            throws PDFSecurityException {

        // create dictionary for document
        encryptDictionary =
                new EncryptionDictionary(library, encryptionDictionary, fileID);

        // create security Handler based on dictionary entries.
        if (encryptDictionary.getPreferredSecurityHandlerName().getName().
                equalsIgnoreCase("Standard")) {
            securityHandler = new StandardSecurityHandler(encryptDictionary);
            // initiate the handler
            securityHandler.init();
        } else {
            throw new PDFSecurityException("Security Provider Not Found.");
        }
    }

    /**
     * Gets the permission associated with the document's encryption handler.
     *
     * @return permission object
     */
    public Permissions getPermissions() {
        return securityHandler.getPermissions();
    }

    /**
     * Gets the SecurityHandler associated with this Security Manager.
     *
     * @return security handler object.
     */
    public SecurityHandler getSecurityHandler() {
        return securityHandler;
    }

    /**
     * Gets the encryption dictionary associated with the document encryption
     * handler.
     *
     * @return encryption dictionary
     */
    public EncryptionDictionary getEncryptionDictionary() {
        return encryptDictionary;
    }

    /**
     * Gets the encryption key used by the security handler when encrypting data.
     *
     * @return encryption key used to encrypt the data
     */
    public byte[] getEncryptionKey() {
        if (encryptionKey == null) {
            encryptionKey = securityHandler.getEncryptionKey();
        }
        return encryptionKey;
    }

    /**
     * Gets the decrypt key used by the security handler when decrypting data.
     *
     * @return decryption key used to encrypt the data
     */
    public byte[] getDecryptionKey() {
        if (decryptionKey == null) {
            decryptionKey = securityHandler.getDecryptionKey();
        }
        return decryptionKey;
    }

    /**
     * Encrypt the <code>data</code> using the <code>encryptionKey</code> and
     * <code>objectReference</code> of the PDF stream or String object.
     *
     * @param objectReference PDF objects number and revision number
     * @param encryptionKey   encryption key used to encrypt the data
     * @param data            byte data of a PDF Stream or String object
     * @return encrypted data
     */
    public byte[] encrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data) {

        return securityHandler.encrypt(objectReference, encryptionKey, data);
    }

    /**
     * Decrypt the <code>data</code> using the <code>encryptionKey</code> and
     * <code>objectReference</code> of the PDF stream or String object.
     *
     * @param objectReference PDF objects number and revision number
     * @param encryptionKey   encryption key used to decrypt the data
     * @param data            byte data of a PDF Stream or String object
     * @return decrypted data
     */
    public byte[] decrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data) {

        return securityHandler.decrypt(objectReference, encryptionKey, data);
    }

    /**
     * Return a new InputStream, from which read operations will return
     * data, read and decrypt from the InputStream parameter
     * <code>objectReference</code> of the PDF stream or String object.
     *
     * @param objectReference         PDF objects number and revision number
     * @param encryptionKey           encryption key used to decrypt the data
     * @param input                   InputStream giving access to encrypted data
     * @param decodeParams            crypt filter optional parameters, can be null.
     * @param returnInputIfNullResult If results end up being null, then return input instead of null
     * @return InputStream giving access to decrypted data
     */
    public InputStream decryptInputStream(
            Reference objectReference,
            byte[] encryptionKey,
            DictionaryEntries decodeParams,
            InputStream input,
            boolean returnInputIfNullResult) {
        InputStream result = securityHandler.decryptInputStream(
                objectReference, encryptionKey, decodeParams, input);
        if (returnInputIfNullResult && result == null)
            result = input;
        return result;
    }

    /**
     * Return a new InputStream, from which read operations will return
     * data, read and decrypt from the InputStream parameter
     * <code>objectReference</code> of the PDF stream or String object.
     *
     * @param objectReference         PDF objects number and revision number
     * @param encryptionKey           encryption key used to decrypt the data
     * @param input                   InputStream giving access to encrypted data
     * @param decodeParams            crypt filter optional parameters, can be null.
     * @param returnInputIfNullResult If results end up being null, then return input instead of null
     * @return InputStream giving access to decrypted data
     */
    public InputStream encryptInputStream(
            Reference objectReference,
            byte[] encryptionKey,
            DictionaryEntries decodeParams,
            InputStream input,
            boolean returnInputIfNullResult) {
        InputStream result = securityHandler.encryptInputStream(
                objectReference, encryptionKey, decodeParams, input);
        if (returnInputIfNullResult && result == null)
            result = input;
        return result;
    }

    /**
     * Determines whether the supplied password is authorized to view the
     * PDF document.  If a password is rejected, the user should be restricted
     * from viewing the document.
     *
     * @param password password to authorize
     * @return true, if the password was authorized successfully; false, otherwise.
     */
    public boolean isAuthorized(String password) {
        return securityHandler.isAuthorized(password);
    }
}
