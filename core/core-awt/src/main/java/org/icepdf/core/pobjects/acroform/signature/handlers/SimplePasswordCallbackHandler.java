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

import javax.security.auth.callback.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PasswordCallbackHandler is a simple implementation of the CallbackHandler interface that is used to provide a
 * password to the SignatureValidator implementations.
 */
public class SimplePasswordCallbackHandler extends PasswordCallbackHandler {

    private static final Logger logger = Logger.getLogger(SimplePasswordCallbackHandler.class.getName());

    public SimplePasswordCallbackHandler(String password) {
        super(password);
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback) callback;
                pc.setPassword(password);
            } else if (callback instanceof TextOutputCallback) {
                TextOutputCallback tc = (TextOutputCallback) callback;
                logger.log(Level.INFO,
                        "TextOutputCallback type {0} message: {1}",
                        new Object[]{tc.getMessageType(), tc.getMessage()});
                throw new UnsupportedCallbackException(callback);
            } else if (callback instanceof NameCallback) {
                throw new UnsupportedCallbackException(callback);
            } else {
                logger.log(Level.WARNING,
                        "Unknown callback type {0}",
                        callback.getClass().getName());
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

}