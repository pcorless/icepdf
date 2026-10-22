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
/**
 * Annotation editing behind {@link org.icepdf.fx.view.PdfView}: building new annotations, the
 * undoable edits and their history, move/resize geometry, and the per-page UI layer that draws
 * selection handles, popup notes and inline editors.  Internal: not exported by the module, and
 * not API.
 */
package org.icepdf.fx.view.internal.annot;
