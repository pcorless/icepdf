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

package org.icepdf.core.util.updater;

/**
 * Specifies which write mode to use when saving change to a PDF document.
 *
 * @since 7.2
 */
public enum WriteMode {
    /**
     * Appends all changes to the end of the current PDF document.
     */
    INCREMENT_UPDATE,
    /**
     * Rewrites file removing modified object from the PDF document.
     */
    FULL_UPDATE,
}
