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
package org.icepdf.ri.common.views.annotations.signing;

/**
 * How a signature appearance arranges its image and its text.
 * <p>
 * Which of these reads better depends on the image, which is why it is the signer's choice rather
 * than a fixed decision: a mark that covers little of its own bounding box has room for text over
 * it, and one with heavy strokes does not.
 */
public enum SignatureAppearanceLayout {

    /**
     * Image on the left, text right aligned beside it, never drawn over one another.  The image
     * gives up width as the text needs it, so the font size the signer chose is the one they get.
     * <p>
     * The arrangement a reader is most likely to recognise as a digital signature, and the one that
     * cannot fail: the text says who signed and why, and here it is always legible.
     */
    SIDE_BY_SIDE,

    /**
     * Image across the whole field with the text right aligned over it.
     * <p>
     * Gives the mark the full width rather than the share left over beside the text, which matters
     * when the mark is the point and the details are a footnote.  What it cannot promise is that a
     * line of text will not land on a stroke - that depends on the signer's own image, so it is
     * worth looking at the result before signing with it.
     */
    OVERLAY;

    /**
     * @param name  a stored layout name, which may be anything at all - preferences are a file a
     *              user can edit, and a name that was valid in an older version may not be now
     * @param value what to use when the name is not one of these
     * @return the layout of that name, or {@code value}
     */
    public static SignatureAppearanceLayout valueOf(String name, SignatureAppearanceLayout value) {
        for (SignatureAppearanceLayout layout : values()) {
            if (layout.name().equals(name)) {
                return layout;
            }
        }
        return value;
    }
}
