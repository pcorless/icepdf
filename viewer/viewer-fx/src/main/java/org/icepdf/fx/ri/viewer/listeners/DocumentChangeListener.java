package org.icepdf.fx.ri.viewer.listeners;

import javafx.beans.value.ChangeListener;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.viewer.ViewerModel;

public class DocumentChangeListener implements ChangeListener<Document> {

    private ViewerModel model;

    public DocumentChangeListener(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void changed(javafx.beans.value.ObservableValue<? extends Document> observable, Document oldDocument,
                        Document newDocument) {
        if (oldDocument != null) {
            oldDocument.dispose();
        }
        if (newDocument != null) {
            model.toolbarDisabled.set(false);
        } else {
            model.toolbarDisabled.set(true);
        }
    }
}
