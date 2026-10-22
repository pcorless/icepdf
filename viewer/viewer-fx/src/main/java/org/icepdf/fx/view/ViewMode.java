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
 * How pages are arranged in a {@link PdfView}.  Combined with the cover-page flag these cover the
 * Swing viewer's six view types (one page, one column, two page / two column, left or right).
 */
public enum ViewMode {
    /** One page at a time; scrolling stays within the current page. */
    SINGLE_PAGE(false, false),
    /** All pages stacked in one column. */
    CONTINUOUS(true, false),
    /** One two-page spread at a time. */
    FACING(false, true),
    /** All pages as two-page spreads stacked in one column. */
    FACING_CONTINUOUS(true, true);

    private final boolean continuous;
    private final boolean facing;

    ViewMode(boolean continuous, boolean facing) {
        this.continuous = continuous;
        this.facing = facing;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public boolean isFacing() {
        return facing;
    }
}
