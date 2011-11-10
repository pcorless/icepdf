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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
 * <p>ICEpdf's standard security handler allows access permissions and up to two passwords
 * to be specified for a document: an owner password and a user password. An
 * application's decision to encrypt a document is based on whether the user
 * creating the document specifies any passwords or access restrictions (for example, in a
 * security settings dialog that the user can invoke before saving the PDF file); if so,
 * the document is encrypted, and the permissions and information required to validate
 * the passwords are stored in the encryption dictionary. (An application may
 * also create an encrypted document without any user interaction, if it has some
 * other source of information about what passwords and permissions to use.)</p>
 * <p/>
 * <p>If a user attempts to open an encrypted document that has a user password, the
 * viewer application should prompt for a password. Correctly supplying either
 * password allows the user to open the document, decrypt it, and display it on the
 * screen. If the document does not have a user password, no password is requested;
 * the viewer application can simply open, decrypt, and display the document.
 * Whether additional operations are allowed on a decrypted document depends on
 * which password (if any) was supplied when the document was opened and on
 * any access restrictions that were specified when the document was created:
 * <ul>
 * <li>Opening the document with the correct owner password (assuming it is not
 * the same as the user password) allows full (owner) access to the
 * document. This unlimited access includes the ability to change the
 * document's passwords and access permissions.</li>
 * <p/>
 * <li>Opening the document with the correct user password (or opening a
 * document that does not have a user password) allows additional operations
 * to be performed according to the user access permissions specified in the
 * document's encryption dictionary.</li>
 * </ul>
 * <p/>
 * <p>Access permissions are specified in the form of flags corresponding to the
 * various operations, and the set of operations to which they correspond,
 * depends in turn on the security handler's revision number (also stored in the
 * encryption dictionary). If the revision number is 2 or greater, the
 * operations to which user access can be controlled are as follows:
 * <p/>
 * <ul>
 * <li>Modifying the document's contents</li>
 * <p/>
 * <li>Copying or otherwise extracting text and graphics from the document,
 * including extraction for accessibility purposes (that is, to make the
 * contents of the document accessible through assistive technologies such
 * as screen readers or Braille output devices</li>
 * <p/>
 * <li>Adding or modifying text annotations and interactive form fields</li>
 * <p/>
 * <li>Printing the document</li>
 * </ul>
 * <p/>
 * <p>If the security handler's revision number is 3 or greater, user access to the
 * following operations can be controlled more selectively:
 * <ul>
 * <li>Filling in forms (that is, filling in existing interactive form fields)
 * and signing the document (which amounts to filling in existing signature
 * fields, a type of interactive form field)</li>
 * <p/>
 * <li>Assembling the document: inserting, rotating, or deleting pages and
 * creating navigation elements such as bookmarks or thumbnail images </li>
 * <p/>
 * <li>Printing to a representation from which a faithful digital copy of the
 * PDF content could be generated. Disallowing such printing may result in
 * degradation of output quality (a feature implemented as "Print As Image"
 * in Acrobat)</li>
 * </ul>
 * <p>In addition, revision 3 enables the extraction of text and graphics (in
 * support of accessibility to disabled users or for other purposes) to be
 * controlled separately. Beginning with revision 4, the standard security
 * handler supports crypt filters. The support is limited to the Identity crypt
 * filter and crypt filters named StdCF whose dictionaries contain a CFM value
 * of V2 and an AuthEvent value of DocOpen.</p>
 *
 * @since 1.1
 */
public class StandardSecurityHandler extends SecurityHandler {

    // StandardEncryption holds algorithms specific to adobe standard encryption
    private StandardEncryption standardEnryption = null;

    // encryption key used for encryption,  Standard encryption is symmetric, so
    // only one key is needed.
    private byte[] encryptionKey = null;

    // initiated flag
    private boolean initiated = false;

    // string to store password used for decoding, the user password is always
    // used for encryption, never the user password.
    private String password;

    public StandardSecurityHandler(EncryptionDictionary encryptionDictionary) {
        super(encryptionDictionary);
        // Full name of handler
        handlerName = "Adobe Standard Security";
    }

    public boolean isAuthorized(String password) {

        boolean value = standardEnryption.authenticateUserPassword(password);
        // check password against user password
        if (!value) {
            // check password against owner password
            value = standardEnryption.authenticateOwnerPassword(password);
            // Get user, password, as it is used for generating encryption keys
            if (value) {
                this.password = standardEnryption.getUserPassword();
            }
        } else {
            // assign password for future use
            this.password = password;
        }
        return value;
    }

    public boolean isOwnerAuthorized(String password) {
        // owner password is not stored as it is not used for decryption
        return standardEnryption.authenticateOwnerPassword(password);
    }

    public boolean isUserAuthorized(String password) {
        // owner password is not stored as it is not used for decryption
        boolean value = standardEnryption.authenticateUserPassword(password);
        if (value) {
            this.password = password;
        }
        return value;
    }

    public byte[] encrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data) {

        // use the general encryption algorithm for encryption
        return standardEnryption.generalEncryptionAlgorithm(
                objectReference, encryptionKey, data);
    }

    public byte[] decrypt(Reference objectReference,
                          byte[] encryptionKey,
                          byte[] data) {
        // standard encryption is symmetric, so we can just use enrypt to decrypt
        return encrypt(objectReference, encryptionKey, data);
    }

    public InputStream getEncryptionInputStream(
            Reference objectReference,
            byte[] encryptionKey,
            InputStream input) {
        return standardEnryption.generalEncryptionInputStream(
                objectReference, encryptionKey, input);
    }

    public byte[] getEncryptionKey() {

        if (!initiated) {
            // make sure class instance var have been setup
            this.init();
        }
        // calculate the encryptionKey based on the given user name
        encryptionKey = standardEnryption.encryptionKeyAlgorithm(
                password,
                encryptionDictionary.getKeyLength());

        return encryptionKey;
    }

    public byte[] getDecryptionKey() {
        return getEncryptionKey();
    }

    public Permissions getPermissions() {
        if (!initiated) {
            // make sure class instance var have been setup
            this.init();
        }
        return permissions;
    }

    public String getHandlerName() {
        return this.handlerName;
    }

    public void init() {
        // initiate a new instance
        standardEnryption = new StandardEncryption(encryptionDictionary);
        // initiate permissions
        permissions = new Permissions(encryptionDictionary);
        permissions.init();
        // update flag
        initiated = true;
    }

    public void dispose() {
        standardEnryption = null;
        encryptionKey = null;
        permissions = null;
        // update flag
        initiated = false;
    }
}
