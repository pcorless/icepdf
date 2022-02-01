package org.icepdf.qa.viewer.project;

import javafx.scene.control.Tab;
import org.icepdf.qa.viewer.common.Mediator;

/**
 * Created by pcorl_000 on 2017-02-07.
 */
public class MetaDataTab extends Tab {

    public MetaDataTab(String title, Mediator mediator) {
        super(title);
        setClosable(false);
    }
}
