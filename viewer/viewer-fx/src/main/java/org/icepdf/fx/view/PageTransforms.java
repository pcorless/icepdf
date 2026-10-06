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

import javafx.geometry.Point2D;
import javafx.scene.transform.Affine;
import org.icepdf.core.pobjects.Page;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;

/**
 * Coordinate mapping between PDF user space and a page's view space (logical px at a zoom and
 * rotation, origin at the page's top-left), built on the core's own
 * {@link Page#getPageTransform}, so the FX side can never disagree with what the renderer drew.
 * <p>
 * Re-mapping between two renders is {@code to ∘ from⁻¹}: that one transform carries a stale
 * tile, or the unrotated preview, onto the current zoom and rotation exactly, whatever the angle.
 */
public final class PageTransforms {

    private PageTransforms() {
    }

    /** PDF user space → page view space. */
    public static AffineTransform pageToView(Page page, int boundary, float rotation, float zoom) {
        return page.getPageTransform(boundary, rotation, zoom);
    }

    /** View space of one render → view space of another, for the same page. */
    public static AffineTransform between(AffineTransform fromPageToView, AffineTransform toPageToView) {
        AffineTransform result = new AffineTransform(toPageToView);
        try {
            result.concatenate(fromPageToView.createInverse());
        } catch (NoninvertibleTransformException e) {
            // only a zero zoom is non-invertible; there is nothing sensible to show.
            return new AffineTransform(0, 0, 0, 0, 0, 0);
        }
        return result;
    }

    public static Affine toFx(AffineTransform at) {
        return new Affine(at.getScaleX(), at.getShearX(), at.getTranslateX(),
                at.getShearY(), at.getScaleY(), at.getTranslateY());
    }

    public static void setFx(Affine target, AffineTransform at) {
        target.setToTransform(at.getScaleX(), at.getShearX(), at.getTranslateX(),
                at.getShearY(), at.getScaleY(), at.getTranslateY());
    }

    /** Page view point → PDF user space, or null if the transform is degenerate. */
    public static Point2D viewToPage(AffineTransform pageToView, double x, double y) {
        try {
            java.awt.geom.Point2D p = pageToView.inverseTransform(new java.awt.geom.Point2D.Double(x, y), null);
            return new Point2D(p.getX(), p.getY());
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }

    /** PDF user space point → page view space. */
    public static Point2D pageToView(AffineTransform pageToView, double x, double y) {
        java.awt.geom.Point2D p = pageToView.transform(new java.awt.geom.Point2D.Double(x, y), null);
        return new Point2D(p.getX(), p.getY());
    }
}
