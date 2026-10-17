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

import org.icepdf.fx.print.PrintDefaults;

/** Where the application remembers the last print's choices; the print dialog starts from them. */
public interface PrintStore {

    /** The choices of the last print, or null if nothing has been printed yet. */
    PrintDefaults load();

    void save(PrintDefaults defaults);

    /** A store that lives only as long as the viewer, for when the application doesn't supply one. */
    static PrintStore inMemory() {
        return new PrintStore() {
            private PrintDefaults defaults;

            @Override
            public PrintDefaults load() {
                return defaults;
            }

            @Override
            public void save(PrintDefaults defaults) {
                this.defaults = defaults;
            }
        };
    }
}
