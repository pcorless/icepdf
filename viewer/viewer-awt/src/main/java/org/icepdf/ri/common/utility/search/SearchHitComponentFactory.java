/*
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
package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.Controller;

import java.awt.geom.Rectangle2D;

/**
 * Class managing the creation of SearchHitComponents
 */
public interface SearchHitComponentFactory {

    /**
     * Creates a component with the given parameters
     *
     * @param text       The text highlighted
     * @param bounds     The bounds of the highlight
     * @param page       The page
     * @param controller The document controller
     * @return The created component
     */
    SearchHitComponent createComponent(String text, Rectangle2D.Double bounds, Page page, Controller controller);

    /**
     * Creates a component with the given parameters
     *
     * @param wordText   The WordText object
     * @param page       The page
     * @param controller The document controller
     * @return The created component
     */
    SearchHitComponent createComponent(WordText wordText, Page page, Controller controller);
}