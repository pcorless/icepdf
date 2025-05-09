package org.icepdf.core.pobjects.acroform.signature.handlers;

import javax.security.auth.callback.CallbackHandler;

/**
 * PasswordCallbackHandler is a simple implementation of the CallbackHandler interface that is used to provide a
 * password
 * to the SignatureValidator implementations.
 */
public abstract class PasswordCallbackHandler implements CallbackHandler {

    protected char[] password;

    /**
     * Create a new PasswordCallbackHandler with the given password.
     *
     * @param password
     */
    public PasswordCallbackHandler(String password) {
        this.password = password.toCharArray();
    }

    /**
     * Create a new PasswordCallbackHandler with the given password.
     * @param password
     */
    public PasswordCallbackHandler(char[] password) {
        this.password = password;
    }

    /**
     * Get the password as a char array.
     * @return
     */
    protected char[] getPassword() {
        return password;
    }
}
