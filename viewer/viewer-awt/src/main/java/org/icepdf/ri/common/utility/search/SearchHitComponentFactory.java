package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Class managing the creation of SearchHitComponents
 */
public final class SearchHitComponentFactory {

    private SearchHitComponentFactory() {
    }

    /**
     * Creates a component with the given parameters
     * @param text The text highlighted
     * @param bounds The bounds of the highlight
     * @param page The page
     * @param documentViewModel The document view model
     * @return The created component
     */
    public static SearchHitComponent createComponent(String text, Rectangle2D.Float bounds, Page page, DocumentViewModel documentViewModel) {
        final SearchHitComponent comp = new DummySearchHitComponent(text);
        comp.setBounds(Page.convertTo(new Rectangle((int) bounds.getX(), (int) bounds.getY(), (int) bounds.getWidth(), (int) bounds.getHeight()), page.getPageTransform(documentViewModel.getPageBoundary(), documentViewModel.getViewRotation(), documentViewModel.getViewZoom())).getBounds());
        return comp;
    }

    /**
     * Creates a component with the given parameters
     * @param wordText The WordText objcet
     * @param page The page
     * @param documentViewModel The document view model
     * @return The created component
     */
    public static SearchHitComponent createComponent(WordText wordText, Page page, DocumentViewModel documentViewModel) {
        return createComponent(wordText.getText(), wordText.getBounds(), page, documentViewModel);
    }
}
