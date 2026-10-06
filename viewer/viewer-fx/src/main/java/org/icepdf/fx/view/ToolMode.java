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
 * What a primary-button drag on a page does in a {@link PdfView}.  Whatever the mode, a middle-button
 * drag or holding Space while dragging pans, and the wheel / pinch zoom and scroll.
 */
public enum ToolMode {
    /** Drag selects text; the cursor is an I-beam over text. */
    TEXT_SELECT,
    /** Drag pans the view (hand tool). */
    PAN
}
