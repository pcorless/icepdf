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
package org.icepdf.core.pobjects.security;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * PDF's standard security handler allows access permissions and up to two passwords
 * to be specified for a document.  The purpose of this class is to encapsulate
 * the algorithms used by the Standard Security Handler.
 * <p/>
 * All of the algorithms used for encryption related calculations are based
 * on the suto code described in the Adobe PDF Specification 1.5.
 *
 * @since 1.1
 */
class StandardEncryption {

    private static final Logger logger =
            Logger.getLogger(StandardEncryption.class.toString());

    /**
     * Padding String used in PDF encryption related algorithms
     * < 28 BF 4E 5E 4E 75 8A 41 64 00 4E 56 FF FA 01 08
     * 2E 2E 00 B6 D0 68 3E 80 2F 0C A9 FE 64 53 69 7A >
     */
    private static final byte[] PADDING = {(byte) 0x28, (byte) 0xBF, (byte) 0x4E,
            (byte) 0x5E, (byte) 0x4E, (byte) 0x75,
            (byte) 0x8A, (byte) 0x41, (byte) 0x64,
            (byte) 0x00, (byte) 0x4E, (byte) 0x56,
            (byte) 0xFF, (byte) 0xFA, (byte) 0x01,
            (byte) 0x08, (byte) 0x2E, (byte) 0x2E,
            (byte) 0x00, (byte) 0xB6, (byte) 0xD0,
            (byte) 0x68, (byte) 0x3E, (byte) 0x80,
            (byte) 0x2F, (byte) 0x0C, (byte) 0xA9,
            (byte) 0xFE, (byte) 0x64, (byte) 0x53,
            (byte) 0x69, (byte) 0x7A};

    // Stores data about encryption
    private EncryptionDictionary encryptionDictionary;

    // Standard encryption key
    private byte[] encryptionKey;

    // last used object reference
    private Reference objectReference;

    // last used RC4 encryption key
    private byte[] rc4Key = null;

    // user password;
    private String userPassword = "";

    // user password;
    private String ownerPassword = "";

    /**
     * Create a new instance of the StandardEncryption object.
     *
     * @param encryptionDictionary standard encryption dictionary values
     */
    public StandardEncryption(EncryptionDictionary encryptionDictionary) {
        this.encryptionDictionary = encryptionDictionary;
    }

    /**
     * General encryption algorithm 3.1 for encryption of data using an
     * encryption key.
     */
    public byte[] generalEncryptionAlgorithm(Reference objectReference,
                                             byte[] encryptionKey,
                                             byte[] inputData) {

        if (objectReference == null || encryptionKey == null ||
                inputData == null) {
            // throw security exception
        }

        // optimization, if the encryptionKey and objectReference are the
        // same there is no reason to calculate a new key.
        if (rc4Key == null || this.encryptionKey != encryptionKey ||
                this.objectReference != objectReference) {

            this.objectReference = objectReference;

            // Step 1 to 3, bytes
            byte[] step3Bytes = resetObjectReference(objectReference);

            // Step 4: Use the first (n+5) byes, up to a max of 16 from the MD5
            // hash
            int n = encryptionKey.length;
            rc4Key = new byte[Math.min(n + 5, 16)];
            System.arraycopy(step3Bytes, 0, rc4Key, 0, rc4Key.length);
        }

        // Set up an RC4 cipher and try to decrypt:
        byte[] finalData = null; // return data if all goes well
        try {
            // Use above as key for the RC4 encryption function.
            SecretKeySpec key = new SecretKeySpec(rc4Key, "RC4");
            Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.DECRYPT_MODE, key);

            // finally add the stream or string data
            finalData = rc4.doFinal(inputData);

        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (IllegalBlockSizeException ex) {
            logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
        } catch (BadPaddingException ex) {
            logger.log(Level.FINE, "BadPaddingException.", ex);
        } catch (NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        }

        return finalData;
    }

    /**
     * General encryption algorithm 3.1 for encryption of data using an
     * encryption key.
     */
    public InputStream generalEncryptionInputStream(
            Reference objectReference,
            byte[] encryptionKey,
            InputStream input) {
        if (objectReference == null || encryptionKey == null || input == null) {
            // throw security exception
            return null;
        }

        // optimization, if the encryptionKey and objectReference are the
        // same there is no reason to calculate a new key.
        if (rc4Key == null || this.encryptionKey != encryptionKey ||
                this.objectReference != objectReference) {

            this.objectReference = objectReference;

            // Step 1 to 3, bytes
            byte[] step3Bytes = resetObjectReference(objectReference);

            // Step 4: Use the first (n+5) byes, up to a max of 16 from the MD5
            // hash
            int n = encryptionKey.length;
            rc4Key = new byte[Math.min(n + 5, 16)];
            System.arraycopy(step3Bytes, 0, rc4Key, 0, rc4Key.length);
        }

        // Set up an RC4 cipher and try to decrypt:
        try {
            // Use above as key for the RC4 encryption function.
            SecretKeySpec key = new SecretKeySpec(rc4Key, "RC4");
            Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.DECRYPT_MODE, key);

            // finally add the stream or string data
            CipherInputStream cin = new CipherInputStream(input, rc4);
            return cin;
        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        }

        return null;
    }

    /**
     * Step 1-3 of the general encryption algorithm 3.1.  The procedure
     * is as follows:
     * <ul>
     * Treat the object number and generation number as binary integers, extend
     * the original n-byte encryption key to n + 5 bytes by appending the
     * low-order 3 bytes of the object number and the low-order 2 bytes of the
     * generation number in that order, low-order byte first. (n is 5 unless
     * the value of V in the encryption dictionary is greater than 1, in which
     * case the n is the value of Length divided by 8.)
     * </ul>
     *
     * @param objectReference pdf object reference or the identifier of the
     *                        inderect object in the case of a string.
     * @return Byte [] manipulated as specified.
     */
    public byte[] resetObjectReference(Reference objectReference) {

        // Step 1: separate object and generation numbers for objectReference
        int objectNumber = objectReference.getObjectNumber();
        int generationNumber = objectReference.getGenerationNumber();

        // Step 2:
        int n = encryptionKey.length;
        // extend the original n-byte encryption key to n + 5 bytes
        byte[] step2Bytes = new byte[n + 5];
        // make the copy
        System.arraycopy(encryptionKey, 0, step2Bytes, 0, n);

        // appending the low-order 3 bytes of the object number
        step2Bytes[n] = (byte) (objectNumber & 0xff);
        step2Bytes[n + 1] = (byte) (objectNumber >> 8 & 0xff);
        step2Bytes[n + 2] = (byte) (objectNumber >> 16 & 0xff);
        // appending low-order 2 bytes of the generation number low-order
        step2Bytes[n + 3] = (byte) (generationNumber & 0xff);
        step2Bytes[n + 4] = (byte) (generationNumber >> 8 & 0xff);

        // Step 3: Initialize the MD5 hash function and pass in step2Bytes
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException builtin) {
        }
        // and pass in padded password from step 1
        md5.update(step2Bytes);

        // finally return the modified object reference
        return md5.digest();
    }

    /**
     * Encryption key algorithm 3.2 for computing an encryption key given
     * a password string.
     */
    public byte[] encryptionKeyAlgorithm(String password, int keyLength) {

        // Step 1:  pad the password
        byte[] paddedPassword = padPassword(password);

        // Step 2: initialize the MD5 hash function
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        }
        // and pass in padded password from step 1
        md5.update(paddedPassword);

        // Step 3: Pass the value of the encryption dictionary's 0 entry
        String tmp = encryptionDictionary.getBigO();
        byte[] bigO = new byte[tmp.length()];
        for (int i = 0; i < tmp.length(); i++) {
            bigO[i] = (byte) tmp.charAt(i);
        }
        md5.update(bigO);

        // Step 4: treat P as an unsigned 4-byte integer
        for (int i = 0, p = encryptionDictionary.getPermissions(); i < 4; i++,
                p >>= 8) {
            md5.update((byte) p);
        }

        // Step 5: Pass in the first element of the file's file identifies array
        String firstFileID =
                ((StringObject) encryptionDictionary.getFileID().elementAt(0)).getLiteralString();
        byte[] fileID = new byte[firstFileID.length()];
        for (int i = 0; i < firstFileID.length(); i++) {
            fileID[i] = (byte) firstFileID.charAt(i);
        }
        paddedPassword = md5.digest(fileID);

        // Step 6: If document metadata is not being encrypted, pass 4 bytes with
        // the value of 0xFFFFFFFF to the MD5 hash, Only used when R=3 and
        // encrypting.

        // Step 7: Finish Hash.

        // Step 8: Do the following 50 times: take the output from the previous
        // MD5 hash and pass it as ainput into a new MD5 hash;
        // only for R = 3
        if (encryptionDictionary.getRevisionNumber() == 3) {
            for (int i = 0; i < 50; i++) {
                paddedPassword = md5.digest(paddedPassword);
            }
        }

        // Step 9: Set the encryption key to the first n bytes of the output from
        // the MD5 hash
        byte[] out = null;
        int n = 5;
        // n = 5 when R = 2
        if (encryptionDictionary.getRevisionNumber() == 2) {
            out = new byte[n];
        } else if (encryptionDictionary.getRevisionNumber() == 3) {
            n = keyLength / 8;
            out = new byte[n];
        }
        // truncate out to the appropriate value
        System.arraycopy(paddedPassword,
                0,
                out,
                0,
                n);
        // assign instance
        encryptionKey = out;

        return out;
    }

    /**
     * ToDo: xjava.security.Padding,  look at class for interface to see
     * if PDFPadding class could/should be built
     * <p/>
     * Pad or truncate the password string to exactly 32 bytes.  If the
     * password is more than 32 bytes long, use only its first 32 bytes; if it
     * is less than 32 bytes long, pad it by appending the required number of
     * additional bytes from the beginning of the PADDING string.
     * <p/>
     * NOTE: This is algorithm is the <b>1st</b> step of <b>algorithm 3.2</b>
     * and is commonly used by other methods in this class
     *
     * @param password password to padded
     * @return returned updated password with appropriate padding applied
     */
    protected static byte[] padPassword(String password) {

        // create the standard 32 byte password
        byte[] paddedPassword = new byte[32];

        // Passwords can be null, if so set it to an empty string
        if (password == null || "".equals(password)) {
            return PADDING;
        }


        int passwordLength = Math.min(password.length(), 32);

        byte[] bytePassword = new byte[password.length()];
        for (int i = 0; i < password.length(); i++) {
            bytePassword[i] = (byte) password.charAt(i);
        }
        // copy passwords bytes, but truncate the password is > 32 bytes
        System.arraycopy(bytePassword, 0, paddedPassword, 0, passwordLength);

        // pad the password if it is < 32 bytes
        System.arraycopy(PADDING,
                0,
                paddedPassword,
                // start copy at end of string
                passwordLength,
                // append need bytes from PADDING
                32 - passwordLength);

        return paddedPassword;
    }

    /**
     * Computing Owner password value, Algorithm 3.3.
     *
     * @param ownerPassword    owner pasword string. If there is no owner,
     *                         password use the user password instead.
     * @param userPassword     user password.
     * @param isAuthentication if true, only steps 1-4 of the algorithm will be
     *                         completed.  If false, all 8 steps of the algorithm will be
     *                         completed
     *                         <b>Note : </b><br />
     *                         There may be a bug in this algorithm when all 8 steps are called.
     *                         1-4 are work properly, but 1-8 can not generate an O value that is
     *                         the same as the orgional documents O.  This is not a currently a
     *                         problem as we do not author PDF documents.
     */
    public byte[] calculateOwnerPassword(String ownerPassword,
                                         String userPassword,
                                         boolean isAuthentication) {
        // Step 1:  padd the owner password, use the userPassword if empty.
        if ("".equals(ownerPassword) && !"".equals(userPassword)) {
            ownerPassword = userPassword;
        }
        byte[] paddedOwnerPassword = padPassword(ownerPassword);

        // Step 2: Initialize the MD5 hash function and pass in step 2.
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            logger.log(Level.FINE, "Could not fint MD5 Digest", e);
        }
        // and pass in padded password from step 1
        paddedOwnerPassword = md5.digest(paddedOwnerPassword);

        // Step 3: Do the following 50 times: take the output from the previous
        // MD5 hash and pass it as input into a new MD5 hash;
        // only for R = 3
        if (encryptionDictionary.getRevisionNumber() == 3) {
            for (int i = 0; i < 50; i++) {
                paddedOwnerPassword = md5.digest(paddedOwnerPassword);
            }
        }

        // Step 4: Create an RC4 encryption key using the first n bytes of the
        // final MD5 hash, where n is always 5 for revision 2 and the value
        // of the encryption dictionary's Length entry for revision 3.
        // Set up an RC4 cipher and try to encrypt:

        // grap the needed n bytes.
        int dataSize = 5; // default for R == 2
        if (encryptionDictionary.getRevisionNumber() == 3) {
            dataSize = encryptionDictionary.getKeyLength() / 8;
        }
        // truncate the byte array RC4 encryption key
        byte[] encryptionKey = new byte[dataSize];

        System.arraycopy(paddedOwnerPassword, 0, encryptionKey, 0, dataSize);

        // Key is needed by algorithm 3.7, Authenticating owner password
        if (isAuthentication) {
            return encryptionKey;
        }

        // Step 5: Pad or truncate the user password string
        byte[] paddedUserPassword = padPassword(userPassword);

        // Step 6: Encrypt the result of step 4, using the RC4 encryption
        // function with the encryption key obtained in step 4
        byte[] finalData = null;
        try {
            // Use above as key for the RC4 encryption function.
            SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
            Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.ENCRYPT_MODE, key);

            // finally add the stream or string data
            finalData = rc4.update(paddedUserPassword);


            // Step 7: Do the following 19 times: Take the output from the previous
            // invocation of the RC4 function and pass it as input to a new
            // invocation of the function; use an encryption key generated by taking
            // each byte of the encryption key in step 4 and performing an XOR
            // operation between that byte and the single-byte value of the
            // iteration counter
            if (encryptionDictionary.getRevisionNumber() == 3) {

                // key to be made on each interaction
                byte[] indexedKey = new byte[encryptionKey.length];
                // start the 19? interactions
                for (int i = 1; i <= 19; i++) {

                    // build new key for each i xor on each byte
                    for (int j = 0; j < encryptionKey.length; j++) {
                        indexedKey[j] = (byte) (encryptionKey[j] ^ i);
                    }
                    // create new key and init rc4
                    key = new SecretKeySpec(indexedKey, "RC4");
                    //Cipher tmpRc4 = Cipher.getInstance("RC4");
                    rc4.init(Cipher.ENCRYPT_MODE, key);
                    // encrypt the old data with the new key
                    finalData = rc4.update(finalData);
                }
            }

        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        }
        catch (NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        }


        // Debug Code.
//        String O = encryptionDictionary.getBigO();
//        System.out.print("Original O " + O.length() + " ");
//        byte[] bigO = new byte[O.length()];
//        for (int i=0; i < bigO.length; i++){
//            //bigO[i] = (byte)O.charAt(i);
//            System.out.print((int)O.charAt(i));
//        }
//        System.out.println();
//
//        System.out.print("new      O " + finalData.length + " ");
//        for (int i=0; i < finalData.length; i++){
//            System.out.print((int)finalData[i]);
//        }
//        System.out.println();

        // Step 8: return the final invocation of the RC4 function as O
        return finalData;
    }

    /**
     * Computing Owner password value, Algorithm 3.4 is respected for
     * Revision = 2 and Algorithm 3.5 is respected for Revisison = 3, null
     * otherwise.
     *
     * @param userPassword user password.
     * @return byte array representing the U value for the encryption dictionary
     */
    public byte[] calculateUserPassword(String userPassword) {

        // Step 1: Create an encryption key based on the user password String,
        // as described in Algorithm 3.2
        byte[] encryptionKey = encryptionKeyAlgorithm(
                userPassword,
                encryptionDictionary.getKeyLength());

        // Algorithm 3.4 steps, 2 - 3
        if (encryptionDictionary.getRevisionNumber() == 2) {
            // Step 2: Encrypt the 32-byte padding string show in step 1, using
            // an RC4 encryption function with the encryption key from the
            // preceding step

            // 32-byte padding string
            byte[] paddedUserPassword = PADDING.clone();
            // encrypt the data
            byte[] finalData = null;
            try {
                // Use above as key for the RC4 encryption function.
                SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                Cipher rc4 = Cipher.getInstance("RC4");
                rc4.init(Cipher.ENCRYPT_MODE, key);

                // finally encrypt the padding string
                finalData = rc4.doFinal(paddedUserPassword);

            } catch (NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (IllegalBlockSizeException ex) {
                logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
            } catch (BadPaddingException ex) {
                logger.log(Level.FINE, "BadPaddingException.", ex);
            } catch (NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            }
            // Step 3: return the result of step 2 as the value of the U entry
            return finalData;
        }
        // algorithm 3.5 steps, 2 - 6
        else if (encryptionDictionary.getRevisionNumber() == 3) {
            // Step 2: Initialize the MD5 hash function and pass the 32-byte
            // padding string shown in step 1 of Algorithm 3.2 as input to
            // this function
            byte[] paddedUserPassword = PADDING.clone();
            MessageDigest md5 = null;
            try {
                md5 = MessageDigest.getInstance("MD5");
            }
            catch (NoSuchAlgorithmException e) {
                logger.log(Level.FINE, "MD5 digester could not be found",e);
            }
            // and pass in padded password 32-byte padding string
            md5.update(paddedUserPassword);

            // Step 3: Pass the first element of the files identify array to the
            // hash function and finish the hash.
            String firstFileID = ((StringObject) encryptionDictionary.getFileID().elementAt(0)).getLiteralString();
            byte[] fileID = new byte[firstFileID.length()];
            for (int i = 0; i < firstFileID.length(); i++) {
                fileID[i] = (byte) firstFileID.charAt(i);
            }
            byte[] encryptData = md5.digest(fileID);

            // Step 4: Encrypt the 16 byte result of the hash, using an RC4
            // encryption function with the encryption key from step 1
            //System.out.println("R=3 " + encryptData.length);

            // The final data should be 16 bytes long
            // currently no checking for this.

            try {
                // Use above as key for the RC4 encryption function.
                SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                Cipher rc4 = Cipher.getInstance("RC4");
                rc4.init(Cipher.ENCRYPT_MODE, key);

                // finally encrypt the padding string
                encryptData = rc4.update(encryptData);

                // Step 5: Do the following 19 times: Take the output from the previous
                // invocation of the RC4 function and pass it as input to a new
                // invocation of the function; use an encryption key generated by taking
                // each byte of the encryption key in step 4 and performing an XOR
                // operation between that byte and the single-byte value of the
                // iteration counter

                // key to be made on each interaction
                byte[] indexedKey = new byte[encryptionKey.length];
                // start the 19? interactions
                for (int i = 1; i <= 19; i++) {

                    // build new key for each i xor on each byte
                    for (int j = 0; j < encryptionKey.length; j++) {
                        indexedKey[j] = (byte) (encryptionKey[j] ^ (byte) i);
                    }
                    // create new key and init rc4
                    key = new SecretKeySpec(indexedKey, "RC4");
                    rc4.init(Cipher.ENCRYPT_MODE, key);
                    // encrypt the old data with the new key
                    encryptData = rc4.update(encryptData);
                }

            } catch (NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            }
            catch (NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            }
            // Step 6: Append 16 bytes of arbitrary padding to the output from
            // the final invocation of the RC4 function and return the 32-byte
            // result as the value of the U entry.
            byte[] finalData = new byte[32];
            System.arraycopy(encryptData, 0, finalData, 0, 16);
            System.arraycopy(PADDING, 0, finalData, 16, 16);

            return finalData;
        } else {
            return null;
        }
    }

    /**
     * Authenticating the user password,  algorithm 3.6
     *
     * @param userPassword user password to check for authenticity
     * @return true if the userPassword matches the value the encryption
     *         dictionary U value, false otherwise.
     */
    public boolean authenticateUserPassword(String userPassword) {
        // Step 1: Perform all but the last step of Algorithm 3.4(Revision 2) or
        // Algorithm 3.5 (Revision 3) using the supplied password string.
        byte[] tmpUValue = calculateUserPassword(userPassword);

        String tmp = encryptionDictionary.getBigU();
        byte[] bigU = new byte[tmp.length()];
        for (int i = 0; i < tmp.length(); i++) {
            bigU[i] = (byte) tmp.charAt(i);
        }


        byte[] trunkUValue;
        // compare all 32 bytes.
        if (encryptionDictionary.getRevisionNumber() == 2) {
            trunkUValue = new byte[32];
            System.arraycopy(tmpUValue, 0, trunkUValue, 0, trunkUValue.length);
        }
        // truncate to first 16 bytes for R = 3
        else {
            trunkUValue = new byte[16];
            System.arraycopy(tmpUValue, 0, trunkUValue, 0, trunkUValue.length);
        }

        // Step 2: If the result of step 1 is equal o the value of the
        // encryption dictionary's U entry, the password supplied is the correct
        // user password.

        boolean found = true;
        for (int i = 0; i < trunkUValue.length; i++) {
            if (trunkUValue[i] != bigU[i]) {
                found = false;
                break;
            }
        }
        return found;
    }

    /**
     * Authenticating the owner password,  algorithm 3.7
     */
    public boolean authenticateOwnerPassword(String ownerPassword) {
        // Step 1: Computer an encryption key from the supplied password string,
        // as described in steps 1 to 4 of algorithm 3.3.
        byte[] encryptionKey = calculateOwnerPassword(ownerPassword,
                "", true);

        // Step 2: start decryption of O
        byte[] decryptedO = null;
        try {
            // get bigO value
            String tmp = encryptionDictionary.getBigO();
            byte[] bigO = new byte[tmp.length()];
            for (int i = 0; i < tmp.length(); i++) {
                bigO[i] = (byte) tmp.charAt(i);
            }
            if (encryptionDictionary.getRevisionNumber() == 2) {
                // Step 2 (R == 2):  decrypt the value of the encryption dictionary
                // O entry, using an RC4 encryption function with the encryption
                // key computed in step 1.

                // Use above as key for the RC4 encryption function.
                SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                Cipher rc4 = Cipher.getInstance("RC4");
                rc4.init(Cipher.DECRYPT_MODE, key);
                decryptedO = rc4.doFinal(bigO);
            }
            // Step 2 (R == 3): Do the following 19 times: Take the output from the previous
            // invocation of the RC4 function and pass it as input to a new
            // invocation of the function; use an encryption key generated by taking
            // each byte of the encryption key in step 4 and performing an XOR
            // operation between that byte and the single-byte value of the
            // iteration counter
            else {//if (encryptionDictionary.getRevisionNumber() == 3){
                // key to be made on each interaction
                byte[] indexedKey = new byte[encryptionKey.length];

                decryptedO = bigO;
                // start the 19->0? interactions
                for (int i = 19; i >= 0; i--) {

                    // build new key for each i xor on each byte
                    for (int j = 0; j < indexedKey.length; j++) {
                        indexedKey[j] = (byte) (encryptionKey[j] ^ (byte) i);
                    }
                    // create new key and init rc4
                    SecretKeySpec key = new SecretKeySpec(indexedKey, "RC4");
                    Cipher rc4 = Cipher.getInstance("RC4");
                    rc4.init(Cipher.ENCRYPT_MODE, key);
                    // encrypt the old data with the new key
                    decryptedO = rc4.update(decryptedO);
                }
            }

        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (IllegalBlockSizeException ex) {
            logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
        } catch (BadPaddingException ex) {
            logger.log(Level.FINE, "BadPaddingException.", ex);
        } catch (NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        }
        // Step 3: The result of step 2 purports to be the user password.
        // Authenticate this user password using Algorithm 3.6.  If it is found
        // to be correct, the password supplied is the correct owner password.

        String tmpUserPassword = "";

        for (byte aDecryptedO : decryptedO) {
            tmpUserPassword += (char) aDecryptedO;
        }
        //System.out.println("tmp user password " + tmpUserPassword);
        boolean isValid = authenticateUserPassword(tmpUserPassword);

        if (isValid) {
            userPassword = tmpUserPassword;
            this.ownerPassword = ownerPassword;
            // setup permissions if valid
        }

        return isValid;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getOwnerPassword() {
        return ownerPassword;
    }
}
