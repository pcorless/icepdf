package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

public abstract class AbstractStringObject implements StringObject {

    // Reference is need for standard encryption
    protected Reference reference;
    // if isModified, string is always unencrypted, otherwise is the raw string data which can be
    // encrypted or not.
    protected StringBuilder stringData;

    // modified string need to be encrypted when writing to file.
    protected boolean isModified;

    /**
     * Gets the decrypted stringData value of the data using the key provided by the
     * security manager.
     *
     * @param securityManager security manager associated with parent document.
     */
    public String getDecryptedLiteralString(SecurityManager securityManager) {
        if (!isModified) {
            return encryption(getLiteralString(), reference, securityManager);
        } else {
            return getLiteralString();
        }
    }


    /**
     * Decrypts or encrypts a string.
     *
     * @param string          string to encrypt or decrypt
     * @param securityManager security manager for document.
     * @return encrypted or decrypted string, depends on value of decrypt param.
     */
    public String encryption(String string, Reference reference, SecurityManager securityManager) {
        // get the security manager instance
        if (securityManager != null && reference != null) {
            // get the key
            byte[] key = securityManager.getDecryptionKey();

            // convert string to bytes.
            byte[] textBytes = Utils.convertByteCharSequenceToByteArray(string);

            // Decrypt/encrypt String
            textBytes = securityManager.decrypt(reference, key, textBytes);

            // convert back to a string
            return Utils.convertByteArrayToByteString(textBytes);
        }
        return string;
    }

    /**
     * Sets the parent PDF object's reference.
     *
     * @param reference parent object reference.
     */
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    /**
     * Indicates a string has been modified.
     *
     * @return string has been modified and may no longer be encrypted.
     */
    @Override
    public boolean isModified() {
        return isModified;
    }
}
