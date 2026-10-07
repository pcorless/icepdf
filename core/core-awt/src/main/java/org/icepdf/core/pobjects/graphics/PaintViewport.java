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
package org.icepdf.core.pobjects.graphics;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;

/**
 * The region of the page a paint was asked for (the caller's clip), for code that sizes an
 * offscreen buffer.  {@code Page.paint} widens the graphics clip to the whole page box, so print
 * popups can reach outside the page; code that only reads {@code g.getClip()} then sizes buffers to
 * the whole page at the current zoom - unbounded at deep zoom.
 * <p>
 * The viewport is recorded with the exact Graphics2D the page paints into, and only that
 * graphics gets it back: an offscreen buffer (a transparency group, a form, a mask) has a device
 * space of its own, where the page's viewport would be wrong.  Callers there get null and keep
 * using their clip.  It is thread-local: several threads paint pages, and the same page, at once.
 */
public final class PaintViewport {

    private static final class Entry {
        final Graphics2D target;
        final Rectangle device;

        Entry(Graphics2D target, Rectangle device) {
            this.target = target;
            this.device = device;
        }
    }

    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private PaintViewport() {
    }

    /**
     * Records the viewport for paints into {@code g} on this thread.
     *
     * @param g        the graphics the page content paints into
     * @param userClip the caller's clip, in {@code g}'s current user space
     * @return the previous value, for {@link #restore}
     */
    public static Object set(Graphics2D g, Shape userClip) {
        Entry previous = CURRENT.get();
        Rectangle device = g.getTransform().createTransformedShape(userClip).getBounds();
        device.grow(1, 1);
        CURRENT.set(new Entry(g, device));
        return previous;
    }

    /** Restores what {@link #set} replaced. */
    public static void restore(Object previous) {
        if (previous instanceof Entry) {
            CURRENT.set((Entry) previous);
        } else {
            CURRENT.remove();
        }
    }

    /**
     * The viewport in {@code g}'s current user space, or null when {@code g} is not the graphics the
     * viewport was recorded for (an offscreen buffer) or none was recorded.
     */
    public static Rectangle2D inUserSpace(Graphics2D g) {
        Entry entry = CURRENT.get();
        if (entry == null || entry.target != g) {
            return null;
        }
        try {
            AffineTransform toUser = g.getTransform().createInverse();
            return toUser.createTransformedShape(entry.device).getBounds2D();
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }
}
