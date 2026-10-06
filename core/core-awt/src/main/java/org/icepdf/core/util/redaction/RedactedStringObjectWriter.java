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

package org.icepdf.core.util.redaction;

import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

/**
 * Rewrites a show operation with the redacted glyphs removed.
 * <p>
 * Removal is all a redaction does, so this is the shared writer with nothing added: the base class
 * emits surviving runs as strings and steps over what was removed with a {@code TJ} adjustment, and
 * a redaction puts nothing in the gap. The sibling that replaces text rather than removing it,
 * {@code TextStringObjectWriter}, is the same walker with the replacement hook implemented.
 *
 * @since 7.2.0
 */
public class RedactedStringObjectWriter extends StringObjectWriter {
}
