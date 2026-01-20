/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.acroform.signature.handlers;

import java.io.File;
import java.security.*;

/**
 * This class implements the SignerHandler interface and provides the functionality to sign data using PKCS12 format.
 */
public class Pkcs12SignerHandler extends SignerHandler {

    private final File keystoreFile;

    /**
     * Constructs a Pkcs12SignerHandler object with the provided parameters.
     *
     * @param keyStore        the PKCS12 keystore file
     * @param certAlias       the alias of the certificate in the keystore
     * @param callbackHandler the callback handler to retrieve the password for the keystore
     */
    public Pkcs12SignerHandler(String timeStampAuthorityUrl, File keyStore, String certAlias,
                               PasswordCallbackHandler callbackHandler) {
        super(timeStampAuthorityUrl, certAlias, callbackHandler);
        this.keystoreFile = keyStore;
    }

    @Override
    public KeyStore buildKeyStore() throws KeyStoreException {
        KeyStore.CallbackHandlerProtection chp = new KeyStore.CallbackHandlerProtection(callbackHandler);
        KeyStore.Builder builder = KeyStore.Builder.newInstance(keystoreFile, chp);
        keystore = builder.getKeyStore();
        return keystore;
    }

    @Override
    protected PrivateKey getPrivateKey() throws KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException {
        return (PrivateKey) keystore.getKey(certAlias, callbackHandler.getPassword());
    }
}
