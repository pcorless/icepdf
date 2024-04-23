package org.icepdf.core.pobjects.acroform.signature.handlers;

import javax.security.auth.callback.CallbackHandler;

public abstract class PasswordCallbackHandler implements CallbackHandler {

    protected String password;

    public PasswordCallbackHandler(String password) {
        this.password = password;
    }

    protected char[] getPassword() {
        return password.toCharArray();
    }
}
