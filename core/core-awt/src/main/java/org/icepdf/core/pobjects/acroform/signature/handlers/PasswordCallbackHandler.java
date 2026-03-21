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
