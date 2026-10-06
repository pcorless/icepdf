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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * An annotation action the view does not perform itself, handed to
 * {@link PdfView#onAnnotationActionProperty()}: a URI to open, a file to launch, a remote GoTo,
 * JavaScript and so on.  In-document navigation (GoTo destinations and the page named actions) is
 * handled by the view.  Opening links is left to the application on purpose - it decides whether to
 * confirm, which browser, and whether to allow it at all.
 *
 * @param annotation the annotation clicked (usually a link)
 * @param action     its action, never null
 */
public record AnnotationActionEvent(Annotation annotation, Action action) {
}
