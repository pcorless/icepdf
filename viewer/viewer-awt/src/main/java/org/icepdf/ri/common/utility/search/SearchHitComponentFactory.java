package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.geom.Rectangle2D;

/**
 * Class managing the creation of SearchHitComponents
 */
public interface SearchHitComponentFactory {

    /**
     * Creates a component with the given parameters
     *
     * @param text              The text highlighted
     * @param bounds            The bounds of the highlight
     * @param page              The page
     * @param documentViewModel The document view model
     * @return The created component
     */
    SearchHitComponent createComponent(String text, Rectangle2D.Float bounds, Page page, DocumentViewModel documentViewModel);

    /**
     * Creates a component with the given parameters
     *
     * @param wordText          The WordText object
     * @param page              The page
     * @param documentViewModel The document view model
     * @return The created component
     */
    SearchHitComponent createComponent(WordText wordText, Page page, DocumentViewModel documentViewModel);
}