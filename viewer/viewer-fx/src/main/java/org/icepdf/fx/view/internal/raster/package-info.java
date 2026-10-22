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
 * The raster engine behind {@link org.icepdf.fx.view.PdfView}: the page tile grid, the render
 * jobs that paint tiles with the core off the FX thread, the byte-budgeted tile cache, and the
 * shared Java2D/JavaFX pixel buffers.  Internal: not exported by the module, and not API.
 */
package org.icepdf.fx.view.internal.raster;
