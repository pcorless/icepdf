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
    SearchHitComponent createComponent(String text, Rectangle2D.Float bounds, Page page, Controller controller);

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