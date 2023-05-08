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

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Utils;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PDF's standard security handler allows access permissions and up to two passwords
 * to be specified for a document.  The purpose of this class is to encapsulate
 * the algorithms used by the Standard Security Handler.
 * <br>
 * All of the algorithms used for encryption related calculations are based
 * on the suto code described in the Adobe PDF Specification 1.5.
 *
 * @since 1.1
 */
class StandardEncryption {

    private static final Logger logger =
            Logger.getLogger(StandardEncryption.class.toString());

    /**
     * The application shall not decrypt data but shall direct the input stream
     * to the security handler for decryption (NO SUPPORT)
     */
    public static final String ENCRYPTION_TYPE_NONE = "None";
    /**
     * The application shall ask the security handler for the encryption key and
     * shall implicitly decrypt data with "Algorithm 1: Encryption of data using
     * the RC4 or AES algorithms", using the RC4 algorithm.
     */
    public static final String ENCRYPTION_TYPE_V2 = "V2";
    public static final String ENCRYPTION_TYPE_V3 = "V3";
    /**
     * (PDF 1.6) The application shall ask the security handler for the
     * encryption key and shall implicitly decrypt data with "Algorithm 1:
     * Encryption of data using the RC4 or AES algorithms", using the AES
     * algorithm in Cipher Block Chaining (CBC) mode with a 16-byte block size
     * and an initialization vector that shall be randomly generated and placed
     * as the first 16 bytes in the stream or string.
     */
    public static final String ENCRYPTION_TYPE_AES_V2 = "AESV2";

    /**
     * Padding String used in PDF encryption related algorithms
     * < 28 BF 4E 5E 4E 75 8A 41 64 00 4E 56 FF FA 01 08
     * 2E 2E 00 B6 D0 68 3E 80 2F 0C A9 FE 64 53 69 7A >
     */
    private static final byte[] PADDING = {
            (byte) 0x28, (byte) 0xBF, (byte) 0x4E,
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

    private static final byte[] AES_sAIT = {
            (byte) 0x73, // s
            (byte) 0x41, // A
            (byte) 0x6C, // I
            (byte) 0x54  // T
    };

    // block size of aes key.
    private static final int BLOCK_SIZE = 16;

    // Stores data about encryption
    private final EncryptionDictionary encryptionDictionary;

    // Standard encryption key
    private byte[] encryptionKey;

    // last used object reference
    private Reference objectReference;

    // last used RC4 encryption key
    private byte[] rc4Key = null;

    // user password;
    private String userPassword = null;

    // user password;
    private String ownerPassword = null;

    /**
     * Create a new instance of the StandardEncryption object.
     *
     * @param encryptionDictionary standard encryption dictionary values
     */
    public StandardEncryption(final EncryptionDictionary encryptionDictionary) {
        this.encryptionDictionary = encryptionDictionary;
    }

    /**
     * General encryption algorithm 3.1 for encryption of data using an
     * encryption key.
     *
     * @param objectReference object number of object being encrypted
     * @param encryptionKey   encryption key for document
     * @param algorithmType   V2 or AESV2 standard encryption encryption types.
     * @param inputData       date to encrypted/decrypt.
     * @return encrypted/decrypted data.
     */
    public synchronized byte[] generalEncryptionAlgorithm(final Reference objectReference,
                                                          final byte[] encryptionKey,
                                                          final String algorithmType,
                                                          byte[] inputData,
                                                          final boolean encrypt) {

        if (objectReference == null || encryptionKey == null ||
                inputData == null) {
            // throw security exception
            return null;
        }

        // Algorithm 3.1, version 1-4
        if (encryptionDictionary.getVersion() < 5) {

            // RC4 or AES algorithm detection
            final boolean isRc4 = algorithmType.equals(ENCRYPTION_TYPE_V2);

            // optimization, if the encryptionKey and objectReference are the
            // same there is no reason to calculate a new key.
            if (rc4Key == null || !Arrays.equals(this.encryptionKey, encryptionKey) ||
                    !this.objectReference.equals(objectReference)) {

                this.objectReference = objectReference;

                // Step 1 to 3, bytes
                final byte[] step3Bytes = resetObjectReference(objectReference, isRc4);

                // Step 4: Use the first (n+5) byes, up to a max of 16 from the MD5
                // hash
                final int n = encryptionKey.length;
                rc4Key = new byte[Math.min(n + 5, BLOCK_SIZE)];
                System.arraycopy(step3Bytes, 0, rc4Key, 0, rc4Key.length);
            }

            // if we are encrypting we need to properly pad the byte array.
            final int encryptionMode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;

            // Set up an RC4 cipher and try to decrypt:
            byte[] finalData = null; // return data if all goes well
            try {
                // Use above as key for the RC4 encryption function.
                if (isRc4) {
                    // Use above as key for the RC4 encryption function.
                    final SecretKeySpec key = new SecretKeySpec(rc4Key, "RC4");
                    final Cipher rc4 = Cipher.getInstance("RC4");
                    rc4.init(encryptionMode, key);
                    // finally add the stream or string data
                    finalData = rc4.doFinal(inputData);
                } else {
                    final SecretKeySpec key = new SecretKeySpec(rc4Key, "AES");
                    final Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");

                    // decrypt the data.
                    if (encryptionMode == Cipher.DECRYPT_MODE) {
                        // calculate 16 byte initialization vector.
                        final byte[] initialisationVector = new byte[BLOCK_SIZE];
                        //  should never happen as it would mean a string that won't encrypted properly as it
                        // would be missing full length 16 byte public key.
                        if (inputData.length < BLOCK_SIZE) {
                            final byte[] tmp = new byte[BLOCK_SIZE];
                            System.arraycopy(inputData, 0, tmp, 0, inputData.length);
                            inputData = tmp;
                        }
                        // grab the public key.
                        System.arraycopy(inputData, 0, initialisationVector, 0, BLOCK_SIZE);
                        final IvParameterSpec iVParameterSpec =
                                new IvParameterSpec(initialisationVector);

                        // trim the input, get rid of the key and expose the data to decrypt
                        final byte[] intermData = new byte[inputData.length - BLOCK_SIZE];
                        System.arraycopy(inputData, BLOCK_SIZE, intermData, 0, intermData.length);

                        // finally add the stream or string data
                        aes.init(Cipher.DECRYPT_MODE, key, iVParameterSpec);
                        finalData = aes.doFinal(intermData);
                    } else {
                        // padding is taken care of by PKCS5Padding, so we don't have to touch the data.
                        final IvParameterSpec iVParameterSpec = new IvParameterSpec(generateIv());
                        aes.init(encryptionMode, key, iVParameterSpec);
                        finalData = aes.doFinal(inputData);

                        // add randomness to the start
                        final byte[] output = new byte[iVParameterSpec.getIV().length + finalData.length];
                        System.arraycopy(iVParameterSpec.getIV(), 0, output, 0, BLOCK_SIZE);
                        System.arraycopy(finalData, 0, output, BLOCK_SIZE, finalData.length);
                        finalData = output;
                    }
                }

            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final IllegalBlockSizeException ex) {
                logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
            } catch (final BadPaddingException ex) {
                logger.log(Level.FINE, "BadPaddingException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            } catch (final InvalidAlgorithmParameterException ex) {
                logger.log(Level.FINE, "InvalidAlgorithmParameterException", ex);
            }

            return finalData;
        }
        // Algorithm 3.1a, version 5
        else if (encryptionDictionary.getVersion() == 5) {
            // Use the 32-byte file encryption key for the AES-256 symmetric
            // key algorithm, along with the string or stream data to be encrypted.

            // Use the AES algorithm in Cipher Block Chaining (CBC) mode, which
            // requires an initialization vector. The block size parameter is
            // set to 16 bytes, and the initialization vector is a 16-byte random
            // number that is stored as the first 16 bytes of the encrypted
            // stream or string.
            try {
                final SecretKeySpec key = new SecretKeySpec(encryptionKey, "AES");
                final Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");

                // calculate 16 byte initialization vector.
                final byte[] initialisationVector = new byte[BLOCK_SIZE];
                System.arraycopy(inputData, 0, initialisationVector, 0, BLOCK_SIZE);

                // trim the input
                final byte[] intermData = new byte[inputData.length - BLOCK_SIZE];
                System.arraycopy(inputData, BLOCK_SIZE, intermData, 0, intermData.length);

                final IvParameterSpec iVParameterSpec =
                        new IvParameterSpec(initialisationVector);

                aes.init(Cipher.DECRYPT_MODE, key, iVParameterSpec);

                // finally add the stream or string data
                return aes.doFinal(intermData);

            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final IllegalBlockSizeException ex) {
                logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
            } catch (final BadPaddingException ex) {
                logger.log(Level.FINE, "BadPaddingException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            } catch (final InvalidAlgorithmParameterException ex) {
                logger.log(Level.FINE, "InvalidAlgorithmParameterException", ex);
            }
        }
        return null;
    }

    /**
     * Generates a recure random 16 byte (128 bit) public key for string to be
     * encryped using AES.
     *
     * @return 16 byte public key.
     */
    private byte[] generateIv() {
        final SecureRandom random = new SecureRandom();
        final byte[] ivBytes = new byte[BLOCK_SIZE];
        random.nextBytes(ivBytes);
        return ivBytes;
    }

    /**
     * General encryption algorithm 3.1 for encryption of data using an
     * encryption key.
     * <p>
     * Must be synchronized for stream decoding.
     */
    public synchronized InputStream generalEncryptionInputStream(
            final Reference objectReference,
            final byte[] encryptionKey,
            final String algorithmType,
            final InputStream input, final boolean encrypt) {
        if (objectReference == null || encryptionKey == null || input == null) {
            // throw security exception
            return null;
        }

        // Algorithm 3.1, version 1-4
        if (encryptionDictionary.getVersion() < 5) {
            // RC4 or AES algorithm detection
            final boolean isRc4 = algorithmType.equals(ENCRYPTION_TYPE_V2);

            // optimization, if the encryptionKey and objectReference are the
            // same there is no reason to calculate a new key.
            if (rc4Key == null || !Arrays.equals(this.encryptionKey, encryptionKey) ||
                    !this.objectReference.equals(objectReference)) {

                this.objectReference = objectReference;

                // Step 1 to 3, bytes
                final byte[] step3Bytes = resetObjectReference(objectReference, isRc4);

                // Step 4: Use the first (n+5) byes, up to a max of 16 from the MD5
                // hash
                final int n = encryptionKey.length;
                rc4Key = new byte[Math.min(n + 5, BLOCK_SIZE)];
                System.arraycopy(step3Bytes, 0, rc4Key, 0, rc4Key.length);
            }

            // if we are encrypting we need to properly pad the byte array.
            final int encryptionMode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
            // Set up an RC4 cipher and try to decrypt:
            try {
                // Use above as key for the RC4 encryption function.
                if (isRc4) {
                    final SecretKeySpec key = new SecretKeySpec(rc4Key, "RC4");
                    final Cipher rc4 = Cipher.getInstance("RC4");
                    rc4.init(Cipher.DECRYPT_MODE, key);
                    // finally add the stream or string data
                    return new CipherInputStream(input, rc4);
                }
                // use above a key for the AES encryption function.
                else {
                    final SecretKeySpec key = new SecretKeySpec(rc4Key, "AES");
                    final Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    if (encryptionMode == Cipher.DECRYPT_MODE) {
                        // calculate 16 byte initialization vector.
                        final byte[] initialisationVector = new byte[BLOCK_SIZE];
                        input.read(initialisationVector);
                        final IvParameterSpec iVParameterSpec = new IvParameterSpec(initialisationVector);
                        aes.init(Cipher.DECRYPT_MODE, key, iVParameterSpec);
                        // finally add the stream or string data
                        return new CipherInputStream(input, aes);
                    } else {
                        final IvParameterSpec iVParameterSpec = new IvParameterSpec(generateIv());
                        aes.init(encryptionMode, key, iVParameterSpec);
                        final ByteArrayOutputStream outputByteArray = new ByteArrayOutputStream();
                        // finally add the stream or string data
                        try (input; final CipherOutputStream cos = new CipherOutputStream(outputByteArray, aes)) {
                            final byte[] data = new byte[4096];
                            int read;
                            while ((read = input.read(data)) != -1) {
                                cos.write(data, 0, read);
                            }
                        }
                        byte[] finalData = outputByteArray.toByteArray();
                        // add randomness to the start
                        final byte[] output = new byte[iVParameterSpec.getIV().length + finalData.length];
                        System.arraycopy(iVParameterSpec.getIV(), 0, output, 0, BLOCK_SIZE);
                        System.arraycopy(finalData, 0, output, BLOCK_SIZE, finalData.length);
                        finalData = output;
                        return new ByteArrayInputStream(finalData);

                    }

                }
            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            } catch (final InvalidAlgorithmParameterException | IOException ex) {
                logger.log(Level.FINE, "InvalidAlgorithmParameterException", ex);
            }
        }
        // Algorithm 3.1a, version 5
        else if (encryptionDictionary.getVersion() == 5) {
            // Use the 32-byte file encryption key for the AES-256 symmetric
            // key algorithm, along with the string or stream data to be encrypted.

            // Use the AES algorithm in Cipher Block Chaining (CBC) mode, which
            // requires an initialization vector. The block size parameter is
            // set to 16 bytes, and the initialization vector is a 16-byte random
            // number that is stored as the first 16 bytes of the encrypted
            // stream or string.
            try {
                // use above a key for the AES encryption function.
                final SecretKeySpec key = new SecretKeySpec(encryptionKey, "AES");
                final Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");

                // calculate 16 byte initialization vector.
                final byte[] initialisationVector = new byte[BLOCK_SIZE];
                input.read(initialisationVector);

                final IvParameterSpec iVParameterSpec =
                        new IvParameterSpec(initialisationVector);

                aes.init(Cipher.DECRYPT_MODE, key, iVParameterSpec);

                // finally add the stream or string data
                return new CipherInputStream(input, aes);

            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            } catch (final InvalidAlgorithmParameterException | IOException ex) {
                logger.log(Level.FINE, "InvalidAlgorithmParameterException", ex);
            }
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
     * <br>
     * If using the AES algorithm, extend the encryption key an additional
     * 4 bytes by adding the value "sAlT", which corresponds to the hexadecimal
     * values 0x73, 0x41, 0x6C, 0x54. (This addition is done for backward
     * compatibility and is not intended to provide additional security.)
     * </ul>
     *
     * @param objectReference pdf object reference or the identifier of the
     *                        inderect object in the case of a string.
     * @param isRc4           if true use the RC4 stream cipher, if false use the AES
     *                        symmetric block cipher.
     * @return Byte [] manipulated as specified.
     */
    public byte[] resetObjectReference(final Reference objectReference, final boolean isRc4) {

        // Step 1: separate object and generation numbers for objectReference
        final int objectNumber = objectReference.getObjectNumber();
        final int generationNumber = objectReference.getGenerationNumber();

        // Step 2:
        // v > 1 n is the value of Length divided by 8.
        int n = 5;
        if (encryptionDictionary.getVersion() > 1) {
            n = encryptionDictionary.getKeyLength() / 8;//enencryptionKey.length;
        }
        // extend the original n-byte encryption key to n + 5 bytes

        int paddingLength = 5;
        if (!isRc4) {
            paddingLength += 4;
        }

        final byte[] step2Bytes = new byte[n + paddingLength];

        // make the copy
        System.arraycopy(encryptionKey, 0, step2Bytes, 0, n);

        // appending the low-order 3 bytes of the object number
        step2Bytes[n] = (byte) (objectNumber & 0xff);
        step2Bytes[n + 1] = (byte) (objectNumber >> 8 & 0xff);
        step2Bytes[n + 2] = (byte) (objectNumber >> 16 & 0xff);
        // appending low-order 2 bytes of the generation number low-order
        step2Bytes[n + 3] = (byte) (generationNumber & 0xff);
        step2Bytes[n + 4] = (byte) (generationNumber >> 8 & 0xff);

        // if using AES algorithm extend by four bytes "sAIT" (0x73, 0x41, 0x6c, 0x54)
        if (!isRc4) {
            step2Bytes[n + 5] = AES_sAIT[0];
            step2Bytes[n + 6] = AES_sAIT[1];
            step2Bytes[n + 7] = AES_sAIT[2];
            step2Bytes[n + 8] = AES_sAIT[3];
        }

        // Step 3: Initialize the MD5 hash function and pass in step2Bytes
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException builtin) {
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
    public byte[] encryptionKeyAlgorithm(final String password, final int keyLength) {

        final int revision = encryptionDictionary.getRevisionNumber();
        if (revision < 5) {
            // Step 1:  pad the password
            byte[] paddedPassword = padPassword(password);

            // Step 2: initialize the MD5 hash function
            MessageDigest md5 = null;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            }
            // and pass in padded password from step 1
            md5.update(paddedPassword);

            // Step 3: Pass the value of the encryption dictionary's 0 entry
            final byte[] bigO = Utils.convertByteCharSequenceToByteArray(
                    encryptionDictionary.getBigO());
            md5.update(bigO);

            // Step 4: treat P as an unsigned 4-byte integer
            for (int i = 0, p = encryptionDictionary.getPermissions(); i < 4; i++,
                    p >>= 8) {
                md5.update((byte) (p & 0xFF));
            }

            // Step 5: Pass in the first element of the file's file identifies array
            final String firstFileID = encryptionDictionary.getLiteralString(encryptionDictionary.getFileID().get(0));
            final byte[] fileID = Utils.convertByteCharSequenceToByteArray(firstFileID);
            md5.update(fileID);

            // Step 6: If document metadata is not being encrypted, pass 4 bytes with
            // the value of 0xFFFFFFFF to the MD5 hash, Security handlers of revision 4 or greater)
            if (revision >= 4 &&
                    !encryptionDictionary.isEncryptMetaData()) {
                for (int i = 0; i < 4; ++i) {
                    md5.update((byte) 0xFF);
                }
            }

            // Step 7: Finish Hash.
            paddedPassword = md5.digest();

            // key length
            int keySize = revision == 2 ? 5 : keyLength / 8;
            if (keySize > paddedPassword.length) {
                keySize = paddedPassword.length;
            }
            final byte[] out = new byte[keySize];

            // Step 8: Do the following 50 times: take the output from the previous
            // MD5 hash and pass it as a input into a new MD5 hash;
            // only for R >= 3
            try {
                if (revision >= 3) {
                    for (int i = 0; i < 50; i++) {
                        md5.update(paddedPassword, 0, keySize);
                        md5.digest(paddedPassword, 0, paddedPassword.length);
                    }
                }
            } catch (final DigestException e) {
                logger.log(Level.WARNING, "Error creating MD5 digest.", e);
            }

            // Step 9: Set the encryption key to the first n bytes of the output from
            // the MD5 hash

            // truncate out to the appropriate value
            System.arraycopy(paddedPassword,
                    0,
                    out,
                    0,
                    keySize);
            // assign instance
            encryptionKey = out;

            return out;
        }
        // algorithm 3.2a for Revision 5
        else if (revision == 5 || revision == 6) {
            return decryptRev56(password);
        } else {
            logger.warning("Unknown encryption revision : " + revision);
            return null;
        }
    }

    private static byte[] truncate(final byte[] arr, final int size) {
        if (size >= 0) {
            if (arr.length <= size) {
                return arr;
            } else {
                final byte[] ret = new byte[size];
                System.arraycopy(arr, 0, ret, 0, size);
                return ret;
            }
        } else {
            throw new IllegalArgumentException("Invalid size : " + size);
        }
    }

    private static boolean isRev56User(final byte[] password, final byte[] user, final byte[] userKey, final int revision) {

        final byte[] uHash = new byte[32];
        final byte[] uValidationSalt = new byte[8];
        System.arraycopy(user, 0, uHash, 0, 32);
        System.arraycopy(user, 32, uValidationSalt, 0, 8);

        final byte[] hash = revision == 5 ?
                computeSha256(password, uValidationSalt, userKey) :
                computeHashRev6(password, uValidationSalt, userKey);

        return Arrays.equals(hash, uHash);
    }

    private byte[] decryptRev56(final String password) {
        final String preppedPassword = encryptionDictionary.getRevisionNumber() == 6 ? SaslPrep.saslPrepQuery(password) : password;
        final byte[] passwordBytes = preppedPassword.getBytes(StandardCharsets.UTF_8);

        final byte[] ownerPassword = Utils.convertByteCharSequenceToByteArray(encryptionDictionary.getBigO());
        final byte[] userPassword = Utils.convertByteCharSequenceToByteArray(encryptionDictionary.getBigU());
        final byte[] truncated = truncate(passwordBytes, 127);
        final boolean isOwnerPassword = isRev56User(truncated, ownerPassword, userPassword, encryptionDictionary.getRevisionNumber());
        final boolean isUserPassword = isRev56User(truncated, userPassword, null, encryptionDictionary.getRevisionNumber());

        final byte[] salt = new byte[8];
        final byte[] ePassword;
        final byte[] uPassword;
        if (isOwnerPassword) {
            System.arraycopy(ownerPassword, 40, salt, 0, 8);
            ePassword = Utils.convertByteCharSequenceToByteArray(
                    encryptionDictionary.getBigOE());
            uPassword = userPassword;
            encryptionDictionary.setAuthenticatedOwnerPassword(true);
        } else if (isUserPassword) {
            System.arraycopy(userPassword, 40, salt, 0, 8);
            encryptionDictionary.setAuthenticatedUserPassword(true);
            uPassword = null;
            ePassword = Utils.convertByteCharSequenceToByteArray(
                    encryptionDictionary.getBigUE());
        } else {
            logger.warning("Incorrect user password");
            ePassword = null;
            uPassword = null;
        }
        if (ePassword != null) {
            final byte[] hash = encryptionDictionary.getRevisionNumber() == 5 ?
                    computeSha256(passwordBytes, salt, uPassword) :
                    computeHashRev6(passwordBytes, salt, uPassword);
            encryptionKey = AES256CBC(hash, ePassword);
            // 5.)Decrypt the 16-byte Perms string using AES-256 in ECB mode
            // with an initialization vector of zero and the file encryption
            // key as the key.
            final byte[] perms = Utils.convertByteCharSequenceToByteArray(
                    encryptionDictionary.getPerms());
            final byte[] decryptedPerms = AES256CBC(encryptionKey, perms);

            // Verify that bytes 9-11 of the result are the characters 'a', 'd', 'b'.
            if (decryptedPerms[9] != (byte) 'a' ||
                    decryptedPerms[10] != (byte) 'd' ||
                    decryptedPerms[11] != (byte) 'b') {
                logger.warning("User password is incorrect.");
                return null;
            }
            // Bytes 0-3 of the decrypted Perms entry, treated as a
            // little-endian integer, are the user permissions. They should
            // match the value in the P key.
            final int permissions = (decryptedPerms[0] & 0xff) |
                    ((decryptedPerms[1] & 0xff) << 8) |
                    ((decryptedPerms[2] & 0xff) << 16) |
                    ((decryptedPerms[2] & 0xff) << 24);
            final int pPermissions = encryptionDictionary.getPermissions();
            if (pPermissions != permissions) {
                logger.warning("Perms and P do not match");
            }
        }
        return encryptionKey;
    }

    /**
     * Revision 5 algorithm. Simply uses SHA-256
     *
     */
    private static byte[] computeSha256(final byte[] input, final byte[] password, final byte[] userKey) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input);
            md.update(password);
            return userKey == null ? md.digest() : md.digest(userKey);
        } catch (final NoSuchAlgorithmException e) {
            logger.log(Level.WARNING, e, () -> "SHA-256 not found");
            return null;
        }
    }


    /**
     * Adapted from PDFBox. Revision 6 algorithm for PDF encryption
     *
     * @param password The plaintext password
     * @param salt     The 8 byte user or owner salt
     * @param u        The 48 byte user key if we're hashing the owner password
     * @return The hash
     */
    private static byte[] computeHashRev6(final byte[] password, final byte[] salt, final byte[] u) {
        final byte[] userKey;
        if (u == null) {
            userKey = new byte[0];
        } else if (u.length > 48) {
            userKey = new byte[48];
            System.arraycopy(u, 0, userKey, 0, 48);
        } else if (u.length == 48) {
            userKey = u;
        } else {
            logger.warning("u shorter than 48 bytes");
            return null;
        }
        final String[] REV6_HASHES = {"SHA-256", "SHA-384", "SHA-512"};
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password, 0, Math.min(password.length, 127));
            md.update(salt, 0, salt.length);
            md.update(userKey, 0, userKey.length);
            byte[] k = md.digest();
            byte[] e = null;
            for (int round = 0; round < 64 || (e[e.length - 1] & 0xFF) > round - 32; round++) {
                final byte[] k1;
                if (userKey.length >= 48) {
                    k1 = new byte[64 * (password.length + k.length + 48)];
                } else {
                    k1 = new byte[64 * (password.length + k.length)];
                }

                int pos = 0;
                for (int i = 0; i < 64; i++) {
                    System.arraycopy(password, 0, k1, pos, password.length);
                    pos += password.length;
                    System.arraycopy(k, 0, k1, pos, k.length);
                    pos += k.length;
                    if (userKey.length >= 48) {
                        System.arraycopy(userKey, 0, k1, pos, 48);
                        pos += 48;
                    }
                }

                final byte[] kFirst = new byte[16];
                final byte[] kSecond = new byte[16];
                System.arraycopy(k, 0, kFirst, 0, 16);
                System.arraycopy(k, 16, kSecond, 0, 16);

                final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                final SecretKeySpec keySpec = new SecretKeySpec(kFirst, "AES");
                final IvParameterSpec ivSpec = new IvParameterSpec(kSecond);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                e = cipher.doFinal(k1);

                final byte[] eFirst = new byte[16];
                System.arraycopy(e, 0, eFirst, 0, 16);
                final BigInteger bi = new BigInteger(1, eFirst);
                final BigInteger remainder = bi.mod(new BigInteger("3"));
                final String nextHash = REV6_HASHES[remainder.intValue()];

                md = MessageDigest.getInstance(nextHash);
                k = md.digest(e);
            }

            if (k.length > 32) {
                final byte[] kTrunc = new byte[32];
                System.arraycopy(k, 0, kTrunc, 0, 32);
                return kTrunc;
            } else {
                return k;
            }
        } catch (final GeneralSecurityException e) {
            logger.log(Level.WARNING, e, () -> "Error computing hash");
            return null;
        }
    }


    /**
     * ToDo: xjava.security.Padding,  look at class for interface to see
     * if PDFPadding class could/should be built
     * <br>
     * Pad or truncate the password string to exactly 32 bytes.  If the
     * password is more than 32 bytes long, use only its first 32 bytes; if it
     * is less than 32 bytes long, pad it by appending the required number of
     * additional bytes from the beginning of the PADDING string.
     * <br>
     * NOTE: This is algorithm is the <b>1st</b> step of <b>algorithm 3.2</b>
     * and is commonly used by other methods in this class
     *
     * @param password password to padded
     * @return returned updated password with appropriate padding applied
     */
    protected static byte[] padPassword(final String password) {

        // create the standard 32 byte password
        final byte[] paddedPassword = new byte[32];

        // Passwords can be null, if so set it to an empty string
        if (password == null || "".equals(password)) {
            return PADDING;
        }


        final int passwordLength = Math.min(password.length(), 32);

        final byte[] bytePassword =
                Utils.convertByteCharSequenceToByteArray(password);
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
     * <br>
     * AESv3 passwords are not handle by this method, instead use
     * {@link #generalEncryptionAlgorithm(Reference, byte[], String, byte[], boolean)}
     * If the result is not null then the encryptionDictionary will container
     * values for isAuthenticatedOwnerPassword and isAuthenticatedUserPassword.
     *
     * @param ownerPassword    owner pasword string. If there is no owner,
     *                         password use the user password instead.
     * @param userPassword     user password.
     * @param isAuthentication if true, only steps 1-4 of the algorithm will be
     *                         completed.  If false, all 8 steps of the algorithm will be
     *                         completed
     *                         <b>Note : </b><br>
     *                         There may be a bug in this algorithm when all 8 steps are called.
     *                         1-4 are work properly, but 1-8 can not generate an O value that is
     *                         the same as the orgional documents O.  This is not a currently a
     *                         problem as we do not author PDF documents.
     */
    public byte[] calculateOwnerPassword(String ownerPassword,
                                         final String userPassword,
                                         final boolean isAuthentication) {
        // Step 1:  padd the owner password, use the userPassword if empty.
        if ("".equals(ownerPassword) && !"".equals(userPassword)) {
            ownerPassword = userPassword;
        }
        byte[] paddedOwnerPassword = padPassword(ownerPassword);

        // Step 2: Initialize the MD5 hash function and pass in step 2.
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            logger.log(Level.FINE, "Could not fint MD5 Digest", e);
        }
        // and pass in padded password from step 1
        paddedOwnerPassword = md5.digest(paddedOwnerPassword);

        // Step 3: Do the following 50 times: take the output from the previous
        // MD5 hash and pass it as input into a new MD5 hash;
        // only for R = 3
        if (encryptionDictionary.getRevisionNumber() >= 3) {
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
        if (encryptionDictionary.getRevisionNumber() >= 3) {
            dataSize = encryptionDictionary.getKeyLength() / 8;
        }
        if (dataSize > paddedOwnerPassword.length) {
            dataSize = paddedOwnerPassword.length;
        }

        // truncate the byte array RC4 encryption key
        final byte[] encryptionKey = new byte[dataSize];

        System.arraycopy(paddedOwnerPassword, 0, encryptionKey, 0, dataSize);

        // Key is needed by algorithm 3.7, Authenticating owner password
        if (isAuthentication) {
            return encryptionKey;
        }

        // Step 5: Pad or truncate the user password string
        final byte[] paddedUserPassword = padPassword(userPassword);

        // Step 6: Encrypt the result of step 4, using the RC4 encryption
        // function with the encryption key obtained in step 4
        byte[] finalData = null;
        try {
            // Use above as key for the RC4 encryption function.
            SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
            final Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.ENCRYPT_MODE, key);

            // finally add the stream or string data
            finalData = rc4.update(paddedUserPassword);


            // Step 7: Do the following 19 times: Take the output from the previous
            // invocation of the RC4 function and pass it as input to a new
            // invocation of the function; use an encryption key generated by taking
            // each byte of the encryption key in step 4 and performing an XOR
            // operation between that byte and the single-byte value of the
            // iteration counter
            if (encryptionDictionary.getRevisionNumber() >= 3) {

                // key to be made on each interaction
                final byte[] indexedKey = new byte[encryptionKey.length];
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

        } catch (final NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (final NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (final InvalidKeyException ex) {
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
     * <br>
     * AESv3 passwords are not handle by this method, instead use
     * {@link #generalEncryptionAlgorithm(Reference, byte[], String, byte[], boolean)}
     * If the result is not null then the encryptionDictionary will container
     * values for isAuthenticatedOwnerPassword and isAuthenticatedUserPassword.
     *
     * @param userPassword user password.
     * @return byte array representing the U value for the encryption dictionary
     */
    public byte[] calculateUserPassword(final String userPassword) {

        // Step 1: Create an encryption key based on the user password String,
        // as described in Algorithm 3.2
        final byte[] encryptionKey = encryptionKeyAlgorithm(
                userPassword,
                encryptionDictionary.getKeyLength());

        // Algorithm 3.4 steps, 2 - 3
        if (encryptionDictionary.getRevisionNumber() == 2) {
            // Step 2: Encrypt the 32-byte padding string show in step 1, using
            // an RC4 encryption function with the encryption key from the
            // preceding step

            // 32-byte padding string
            final byte[] paddedUserPassword = PADDING.clone();
            // encrypt the data
            byte[] finalData = null;
            try {
                // Use above as key for the RC4 encryption function.
                final SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                final Cipher rc4 = Cipher.getInstance("RC4");
                rc4.init(Cipher.ENCRYPT_MODE, key);

                // finally encrypt the padding string
                finalData = rc4.doFinal(paddedUserPassword);

            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final IllegalBlockSizeException ex) {
                logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
            } catch (final BadPaddingException ex) {
                logger.log(Level.FINE, "BadPaddingException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            }
            // Step 3: return the result of step 2 as the value of the U entry
            return finalData;
        }
        // algorithm 3.5 steps, 2 - 6
        else if (encryptionDictionary.getRevisionNumber() >= 3 &&
                encryptionDictionary.getRevisionNumber() < 5) {
            // Step 2: Initialize the MD5 hash function and pass the 32-byte
            // padding string shown in step 1 of Algorithm 3.2 as input to
            // this function
            final byte[] paddedUserPassword = PADDING.clone();
            MessageDigest md5 = null;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (final NoSuchAlgorithmException e) {
                logger.log(Level.FINE, "MD5 digester could not be found", e);
            }
            // and pass in padded password 32-byte padding string
            md5.update(paddedUserPassword);

            // Step 3: Pass the first element of the files identify array to the
            // hash function and finish the hash.
            final String firstFileID = encryptionDictionary.getLiteralString(encryptionDictionary.getFileID().get(0));
            final byte[] fileID = Utils.convertByteCharSequenceToByteArray(firstFileID);
            byte[] encryptData = md5.digest(fileID);

            // Step 4: Encrypt the 16 byte result of the hash, using an RC4
            // encryption function with the encryption key from step 1
            //System.out.println("R=3 " + encryptData.length);

            // The final data should be 16 bytes long
            // currently no checking for this.

            try {
                // Use above as key for the RC4 encryption function.
                SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                final Cipher rc4 = Cipher.getInstance("RC4");
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
                final byte[] indexedKey = new byte[encryptionKey.length];
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

            } catch (final NoSuchAlgorithmException ex) {
                logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
            } catch (final NoSuchPaddingException ex) {
                logger.log(Level.FINE, "NoSuchPaddingException.", ex);
            } catch (final InvalidKeyException ex) {
                logger.log(Level.FINE, "InvalidKeyException.", ex);
            }
            // Step 6: Append 16 bytes of arbitrary padding to the output from
            // the final invocation of the RC4 function and return the 32-byte
            // result as the value of the U entry.
            final byte[] finalData = new byte[32];
            System.arraycopy(encryptData, 0, finalData, 0, BLOCK_SIZE);
            System.arraycopy(PADDING, 0, finalData, BLOCK_SIZE, BLOCK_SIZE);

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
     * dictionary U value, false otherwise.
     */
    public boolean authenticateUserPassword(final String userPassword) {
        // Step 1: Perform all but the last step of Algorithm 3.4(Revision 2) or
        // Algorithm 3.5 (Revision 3) using the supplied password string.
        final byte[] tmpUValue = calculateUserPassword(userPassword);

        final byte[] bigU = Utils.convertByteCharSequenceToByteArray(
                encryptionDictionary.getBigU());

        final byte[] trunkUValue;
        // compare all 32 bytes.
        if (encryptionDictionary.getRevisionNumber() == 2) {
            trunkUValue = new byte[32];
            System.arraycopy(tmpUValue, 0, trunkUValue, 0, trunkUValue.length);
        }
        // truncate to first 16 bytes for R >= 3
        else if (encryptionDictionary.getRevisionNumber() >= 3 &&
                encryptionDictionary.getRevisionNumber() < 5) {
            trunkUValue = new byte[BLOCK_SIZE];
            System.arraycopy(tmpUValue, 0, trunkUValue, 0, trunkUValue.length);
        } else {
            return false;
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
        if (found) {
            this.userPassword = userPassword;
        }
        return found;
    }

    /**
     * Authenticating the owner password,  algorithm 3.7
     */
    public boolean authenticateOwnerPassword(final String ownerPassword) {
        // Step 1: Computer an encryption key from the supplied password string,
        // as described in steps 1 to 4 of algorithm 3.3.
        final byte[] encryptionKey = calculateOwnerPassword(ownerPassword,
                "", true);

        // Step 2: start decryption of O
        byte[] decryptedO;
        try {
            // get bigO value
            final byte[] bigO = Utils.convertByteCharSequenceToByteArray(
                    encryptionDictionary.getBigO());
            if (encryptionDictionary.getRevisionNumber() == 2) {
                // Step 2 (R == 2):  decrypt the value of the encryption dictionary
                // O entry, using an RC4 encryption function with the encryption
                // key computed in step 1.

                // Use above as key for the RC4 encryption function.
                final SecretKeySpec key = new SecretKeySpec(encryptionKey, "RC4");
                final Cipher rc4 = Cipher.getInstance("RC4");
                rc4.init(Cipher.DECRYPT_MODE, key);
                decryptedO = rc4.doFinal(bigO);
            }
            // Step 2 (R >= 3): Do the following 19 times: Take the output from the previous
            // invocation of the RC4 function and pass it as input to a new
            // invocation of the function; use an encryption key generated by taking
            // each byte of the encryption key in step 4 and performing an XOR
            // operation between that byte and the single-byte value of the
            // iteration counter
            else {//if (encryptionDictionary.getRevisionNumber() >= 3){
                // key to be made on each interaction
                final byte[] indexedKey = new byte[encryptionKey.length];

                decryptedO = bigO;
                // start the 19->0? interactions
                for (int i = 19; i >= 0; i--) {

                    // build new key for each i xor on each byte
                    for (int j = 0; j < indexedKey.length; j++) {
                        indexedKey[j] = (byte) (encryptionKey[j] ^ (byte) i);
                    }
                    // create new key and init rc4
                    final SecretKeySpec key = new SecretKeySpec(indexedKey, "RC4");
                    final Cipher rc4 = Cipher.getInstance("RC4");
                    rc4.init(Cipher.ENCRYPT_MODE, key);
                    // encrypt the old data with the new key
                    decryptedO = rc4.update(decryptedO);
                }
            }

            // Step 3: The result of step 2 purports to be the user password.
            // Authenticate this user password using Algorithm 3.6.  If it is found
            // to be correct, the password supplied is the correct owner password.

            final String tmpUserPassword = Utils.convertByteArrayToByteString(decryptedO);
            //System.out.println("tmp user password " + tmpUserPassword);
            final boolean isValid = authenticateUserPassword(tmpUserPassword);

            if (isValid) {
                this.userPassword = tmpUserPassword;
                this.ownerPassword = ownerPassword;
                // setup permissions if valid
            } else {
                this.userPassword = "";
                this.ownerPassword = "";
            }

            return isValid;
        } catch (final NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (final IllegalBlockSizeException ex) {
            logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
        } catch (final BadPaddingException ex) {
            logger.log(Level.FINE, "BadPaddingException.", ex);
        } catch (final NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (final InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        }

        return false;

    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getOwnerPassword() {
        return ownerPassword;
    }

    /**
     * Utility to decrypt the encryptedString via the intermediateKey.  AES
     * encryption with cypher block chaining and no padding.
     *
     * @param intermediateKey key to use for decryption
     * @param encryptedString byte[] to decrypt
     * @return decrypted byte[].
     */
    private static byte[] AES256CBC(final byte[] intermediateKey, final byte[] encryptedString) {
        byte[] finalData = null;
        try {
            // AES with cipher block chaining and no padding
            final SecretKeySpec key = new SecretKeySpec(intermediateKey, "AES");
            final Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
            // empty initialization vector
            final IvParameterSpec iVParameterSpec =
                    new IvParameterSpec(new byte[BLOCK_SIZE]);
            // go!
            aes.init(Cipher.DECRYPT_MODE, key, iVParameterSpec);
            // finally add the stream or string data
            finalData = aes.doFinal(encryptedString);
        } catch (final NoSuchAlgorithmException ex) {
            logger.log(Level.FINE, "NoSuchAlgorithmException.", ex);
        } catch (final IllegalBlockSizeException ex) {
            logger.log(Level.FINE, "IllegalBlockSizeException.", ex);
        } catch (final BadPaddingException ex) {
            logger.log(Level.FINE, "BadPaddingException.", ex);
        } catch (final NoSuchPaddingException ex) {
            logger.log(Level.FINE, "NoSuchPaddingException.", ex);
        } catch (final InvalidKeyException ex) {
            logger.log(Level.FINE, "InvalidKeyException.", ex);
        } catch (final InvalidAlgorithmParameterException ex) {
            logger.log(Level.FINE, "InvalidAlgorithmParameterException", ex);
        }
        return finalData;
    }

    /**
     * Compare two byte arrays to the specified max index.  No check is made
     * for an index out of bounds error.
     *
     * @param byteArray1 byte array to compare
     * @param byteArray2 byte array to compare
     * @param range      number of elements to compare starting at zero.
     * @return true if the
     */
    private static boolean byteCompare(final byte[] byteArray1, final byte[] byteArray2, final int range) {
        for (int i = 0; i < range; i++) {
            if (byteArray1[i] != byteArray2[i]) {
                return false;
            }
        }
        return true;
    }
}
