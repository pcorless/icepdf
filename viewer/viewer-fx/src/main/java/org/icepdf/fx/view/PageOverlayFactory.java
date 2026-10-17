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

import javafx.scene.Node;
import org.icepdf.core.pobjects.Page;

/**
 * Supplies native JavaFX content drawn over a page: annotations, selection, search hits.
 * <p>
 * Pages are virtualised, so the factory is called when a page scrolls into view and the node is
 * dropped when it scrolls out.  The returned node lives in <b>PDF user space</b> (points, y up):
 * the view applies the page transform for the current zoom and rotation, so the node never needs
 * to know about either.  Strokes therefore scale with zoom; content that should stay a fixed size
 * on screen (handles, cursors) should counter-scale or use a view-space layer.
 */
@FunctionalInterface
public interface PageOverlayFactory {

    /**
     * @return the overlay for the page, or null for none
     */
    Node createOverlay(int pageIndex, Page page);
}
