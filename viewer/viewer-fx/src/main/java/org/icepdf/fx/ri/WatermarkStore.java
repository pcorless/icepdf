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

import org.icepdf.core.util.watermark.Watermark;

/** Where the application remembers the last watermark applied; the watermark dialog starts from it. */
public interface WatermarkStore {

    /** The last watermark applied, or null if none has been yet. */
    Watermark load();

    void save(Watermark watermark);

    /** A store that lives only as long as the viewer, for when the application doesn't supply one. */
    static WatermarkStore inMemory() {
        return new WatermarkStore() {
            private Watermark watermark;

            @Override
            public Watermark load() {
                return watermark;
            }

            @Override
            public void save(Watermark watermark) {
                this.watermark = watermark;
            }
        };
    }
}
