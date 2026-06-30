/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Name;

/**
 * Immutable description of a transparency group's attributes (PDF §11.6.6).
 * Used to record the page-level {@code /Group} entry -- a group attribute on the
 * page dictionary that ICEpdf otherwise discards -- so a compositor can decide
 * whether the page content should be rendered into a shared transparency-group
 * buffer rather than painted straight onto the page backdrop.
 *
 * @since 7.5
 */
public class TransparencyGroup {

    private final boolean isolated;
    private final boolean knockout;
    private final Name colorSpace;

    public TransparencyGroup(boolean isolated, boolean knockout, Name colorSpace) {
        this.isolated = isolated;
        this.knockout = knockout;
        this.colorSpace = colorSpace;
    }

    /** Transparency group {@code /I} (isolated) flag. */
    public boolean isIsolated() {
        return isolated;
    }

    /** Transparency group {@code /K} (knockout) flag. */
    public boolean isKnockout() {
        return knockout;
    }

    /** Group colour-space name ({@code /CS}), may be null. */
    public Name getColorSpace() {
        return colorSpace;
    }
}
