package org.icepdf.core.pobjects.acroform.signature.handlers;

import javax.security.auth.callback.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleCallbackHandler extends PasswordCallbackHandler {

    private static final Logger logger = Logger.getLogger(SimpleCallbackHandler.class.getName());

    public SimpleCallbackHandler(String password) {
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