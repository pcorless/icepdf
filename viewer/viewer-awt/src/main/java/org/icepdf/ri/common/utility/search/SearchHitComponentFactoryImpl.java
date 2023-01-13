package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.Controller;

import java.awt.geom.Rectangle2D;


public class SearchHitComponentFactoryImpl implements SearchHitComponentFactory {

    @Override
    public SearchHitComponent createComponent(String text, Rectangle2D.Double bounds, Page page, Controller controller) {
        return null;
    }

    @Override
    public SearchHitComponent createComponent(WordText wordText, Page page, Controller controller) {
        return null;
    }
}
