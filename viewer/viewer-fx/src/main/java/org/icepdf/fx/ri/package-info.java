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
 * The reference viewer: {@link org.icepdf.fx.ri.PdfViewer}, a whole viewer component (document tabs,
 * tool rail, side panels, dialogs) built from the public components, and how an application
 * configures it - {@link org.icepdf.fx.ri.ViewerFeatures} for what the product offers,
 * {@link org.icepdf.fx.ri.UserLayout} for what the user arranged, {@link org.icepdf.fx.ri.Theme}
 * for the look, and the stores it remembers things in (file and colour history, signing, printing).
 */
package org.icepdf.fx.ri;
