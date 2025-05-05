package org.icepdf.core.pobjects.acroform.signature.handlers;

import javax.security.auth.callback.*;
import java.io.Console;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the CallbackHandler interface that handles different types of callbacks
 * and interacts with the user through the console for input or output when working with KeyStores.
 */
public class ConsoleCallbackHandler implements CallbackHandler {

    private static final Logger logger = Logger.getLogger(ConsoleCallbackHandler.class.getName());

    public ConsoleCallbackHandler() {
        if (System.console() == null) {
            throw new UnsupportedOperationException("Console not available.");
        }
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        Console con = System.console();
        for (Callback callback : callbacks) {
            if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback) callback;
                String prompt = pc.getPrompt();
                prompt = prompt != null && !prompt.isEmpty() ? prompt : "PIN";
                con.format("%s: ", prompt);
                logger.log(Level.INFO,
                        "Password callback with prompt: {0}",
                        prompt);
                char[] password = con.readPassword();
                pc.setPassword(password);
                pc.setPassword("changeit".toCharArray());
            } else if (callback instanceof TextOutputCallback) {
                TextOutputCallback tc = (TextOutputCallback) callback;
                con.format("%s", tc.getMessage());
                logger.log(Level.INFO,
                        "TextOutputCallback type {0} message: {1}",
                        new Object[]{tc.getMessageType(), tc.getMessage()});
                throw new UnsupportedCallbackException(callback);
            } else if (callback instanceof NameCallback) {
                NameCallback nc = (NameCallback) callback;
                String prompt = nc.getPrompt();
                prompt = prompt != null && !prompt.isEmpty() ? prompt : "Name";
                String defaultName = nc.getDefaultName();
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