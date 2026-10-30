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
package org.icepdf.ri.common.views.annotations.signatures;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

/**
 * Runs signature validation off the AWT event thread.  Validation can reach the network for
 * revocation status and issuer certificates, and a slow server would otherwise freeze the viewer
 * (GH-643).  Results are handed back on the event thread.
 * <p>
 * The network settings come from the core defaults ({@link SignatureValidationOptions#defaults()},
 * set by system properties) with the user's "check revocation online" preference on top.
 */
public final class SignatureValidationService {

    private static final AtomicInteger threadCount = new AtomicInteger();

    // a couple of threads: enough that one slow server doesn't hold up a second signature, few
    // enough not to open a connection per signature on a heavily signed document.
    private static final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "icepdf-signature-validation-" + threadCount.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private SignatureValidationService() {
    }

    /**
     * The options validation runs with: the core defaults, online or not as the user's preference
     * says.
     *
     * @return current validation options
     */
    public static SignatureValidationOptions options() {
        SignatureValidationOptions defaults = SignatureValidationOptions.defaults();
        try {
            Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
            return defaults.withOnline(preferences.getBoolean(
                    ViewerPropertiesManager.PROPERTY_SIGNATURE_VALIDATION_ONLINE, defaults.isOnline()));
        } catch (RuntimeException e) {
            // no preferences store (embedded, sandboxed): the system properties decide.
            return defaults;
        }
    }

    /**
     * Validates unless the validator already has a result.
     *
     * @param validator signature to validate
     * @return completes on the event thread with the validator, or exceptionally with the
     * validation's failure
     */
    public static CompletableFuture<SignatureValidator> ensureValidated(SignatureValidator validator) {
        SignatureValidationOptions options = options();
        return run(validator, () -> validator.ensureValidated(options));
    }

    /**
     * Runs a fresh validation, whatever an earlier one found.
     *
     * @param validator signature to validate
     * @param options   network settings, null for {@link #options()}
     * @return completes on the event thread with the validator, or exceptionally with the
     * validation's failure
     */
    public static CompletableFuture<SignatureValidator> validate(SignatureValidator validator,
                                                                 SignatureValidationOptions options) {
        SignatureValidationOptions effective = options != null ? options : options();
        return run(validator, () -> validator.validate(effective));
    }

    private interface Validation {
        void run() throws Exception;
    }

    private static CompletableFuture<SignatureValidator> run(SignatureValidator validator, Validation validation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validation.run();
                return validator;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).whenCompleteAsync((v, t) -> {
            // only to hop onto the event thread
        }, SwingUtilities::invokeLater);
    }

    /**
     * The failure inside a future's exception.
     */
    public static Throwable cause(Throwable throwable) {
        return throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
    }
}
