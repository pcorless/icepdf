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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Lightweight, inert marker delimiting the start and end of a transparency
 * group on the {@link org.icepdf.core.pobjects.graphics.Shapes} stack.
 * <br>
 * ICEpdf has historically carried no explicit group boundaries -- a buffered
 * group is a single {@link FormDrawCmd} wrapping the group's own sub-shapes, and
 * the page-level group (the page dictionary's {@code /Group} entry) had no
 * representation at all.  These markers make the boundaries explicit so a
 * compositor can identify a contiguous run of group content (e.g. the page
 * transparency group, or a sibling-group run) and render it into a shared buffer.
 * <br>
 * {@link #paintOperand} is intentionally a no-op: a marker paints nothing and
 * does not alter the current shape, so the default {@link
 * org.icepdf.core.pobjects.graphics.Shapes#paint} loop is unaffected whether or
 * not any consumer reads the markers.  The marker simply carries the group's
 * attributes ({@code isolated}, {@code knockout}, colour-space name, blend mode,
 * and bounding box) for whatever compositor chooses to act on them.
 *
 * @since 7.5
 */
public class GroupDrawCmd extends AbstractDrawCmd {

    private final boolean start;
    private final boolean isolated;
    private final boolean knockout;
    private final Name csName;
    private final Name blend;
    private final Rectangle2D bBox;

    /**
     * @param start    true for a group-start marker, false for a group-end marker.
     * @param isolated transparency group {@code /I} flag.
     * @param knockout transparency group {@code /K} flag.
     * @param csName   group colour-space name (e.g. DeviceCMYK), may be null.
     * @param blend    blend mode applied to the group, may be null.
     * @param bBox     group bounding box in the parent space, may be null.
     */
    public GroupDrawCmd(boolean start, boolean isolated, boolean knockout,
                        Name csName, Name blend, Rectangle2D bBox) {
        this.start = start;
        this.isolated = isolated;
        this.knockout = knockout;
        this.csName = csName;
        this.blend = blend;
        this.bBox = bBox;
    }

    public boolean isStart() {
        return start;
    }

    public boolean isIsolated() {
        return isolated;
    }

    public boolean isKnockout() {
        return knockout;
    }

    public Name getCsName() {
        return csName;
    }

    public Name getBlend() {
        return blend;
    }

    public Rectangle2D getbBox() {
        return bBox;
    }

    /**
     * No-op: a marker paints nothing and leaves the current shape unchanged.
     */
    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        return currentShape;
    }
}
