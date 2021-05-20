package org.icepdf.ri.common;

import javax.swing.*;
import java.awt.*;

/**
 * Represents a view builder
 */
public interface ViewBuilder {

    /**
     * @return The built viewer frame
     */
    JFrame buildViewerFrame();

    /**
     * @return The built menu bar
     */
    JMenuBar buildCompleteMenuBar();

    /**
     * Builds and return the complete tool bar
     *
     * @param embeddableComponent Whether the component will be embedded or not
     * @return the tool bar
     */
    JToolBar buildCompleteToolBar(boolean embeddableComponent);

    /**
     * Builds the contents
     *
     * @param cp                  The container in which the contents will be put
     * @param embeddableComponent Whether the component will be embedded or not
     */
    void buildContents(Container cp, boolean embeddableComponent);

}
