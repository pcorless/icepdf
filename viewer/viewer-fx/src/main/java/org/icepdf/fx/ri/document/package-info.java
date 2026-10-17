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
 * One open document in the reference viewer: {@link org.icepdf.fx.ri.document.DocumentSession}
 * opens and saves it for a {@link org.icepdf.fx.view.PdfView},
 * {@link org.icepdf.fx.ri.document.DocumentPane} lays the view out with its panels,
 * {@link org.icepdf.fx.ri.document.PasswordPrompt} asks for passwords and
 * {@link org.icepdf.fx.ri.document.SignatureFlow} runs signing.
 */
package org.icepdf.fx.ri.document;
