package org.icepdf.ri.common;

import javax.swing.*;
import java.awt.*;

public interface ViewBuilder {

    JFrame buildViewerFrame();

    JMenuBar buildCompleteMenuBar();

    JToolBar buildCompleteToolBar(boolean embeddableComponent);

    void buildContents(Container cp, boolean embeddableComponent);

}
