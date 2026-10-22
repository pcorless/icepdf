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

/**
 * Automatic zoom.  While not {@link #NONE}, the view recomputes {@link PdfView#zoomProperty() zoom}
 * whenever the viewport, rotation or view mode changes.
 */
public enum FitMode {
    /** Zoom is whatever was last set. */
    NONE,
    /** The widest page (or spread) fills the viewport width. */
    WIDTH,
    /** The current page (or spread) fits entirely in the viewport. */
    PAGE
}
