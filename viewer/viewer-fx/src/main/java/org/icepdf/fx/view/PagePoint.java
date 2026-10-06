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
 * A point on a page in PDF user space (points, y up, the page's own coordinate system), the space
 * the core's text and annotation geometry use.  Independent of zoom and rotation.
 *
 * @param pageIndex zero-based page
 * @param x         user-space x
 * @param y         user-space y
 */
public record PagePoint(int pageIndex, double x, double y) {

    /** As an AWT point, for the core's text APIs ({@code TextSequence.caretAt} and friends). */
    public java.awt.geom.Point2D.Double toAwt() {
        return new java.awt.geom.Point2D.Double(x, y);
    }
}
