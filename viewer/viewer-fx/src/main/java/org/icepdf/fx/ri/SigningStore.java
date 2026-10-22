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
package org.icepdf.fx.ri;

import org.icepdf.fx.signature.SigningSettings;

/**
 * Where the application keeps the signing settings (keystore, certificate, appearance, drawn
 * signature) between signings; the viewer hands them to the sign dialog and saves what was chosen.
 */
public interface SigningStore {

    /** The settings to start the sign dialog from. */
    SigningSettings settings();

    /** Called after the signer chose Sign, with the settings the dialog updated. */
    void save(SigningSettings settings);

    /** Settings that live only as long as the viewer, for when the application doesn't supply a store. */
    static SigningStore inMemory() {
        SigningSettings settings = new SigningSettings();
        return new SigningStore() {
            @Override
            public SigningSettings settings() {
                return settings;
            }

            @Override
            public void save(SigningSettings changed) {
            }
        };
    }
}
