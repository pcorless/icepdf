package org.icepdf.core.pobjects.acroform.signature.handlers;

import javax.security.auth.callback.CallbackHandler;

public class Pkcs11SignerHandler implements SignerHandler {

    public Pkcs11SignerHandler(String name, String library, int slot, CallbackHandler callbackHandler) {

    }

    @Override
    public byte[] signData(byte[] data) {
        return null;
    }
}
