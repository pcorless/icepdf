package org.icepdf.qa.viewer.project;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.ContentSet;
import org.icepdf.qa.viewer.common.Mediator;

import java.util.List;

/**
 * Created by pcorl_000 on 2017-02-14.
 */
public class ContentSetSelectorDialog extends AbstractDialog<ObservableList<ContentSet>> {

    private ListView<ContentSet> contentList;

    public ContentSetSelectorDialog(Mediator mediator, List<String> selectedContent) {
        super(mediator);

        setTitle("Capture Set Selection");
        setHeaderText("Select content sets to add to capture set.");

        // read the available content sets
        List<ContentSet> contentSets = ConfigSerializer.retrieveAllContentSets();
        for (String contentName : selectedContent) {
            for (ContentSet contentSet : contentSets) {
                if (contentName.equals(contentSet.getName())) {
                    contentSets.remove(contentSet);
                    break;
                }
            }
        }
        final ObservableList<ContentSet> stringList = FXCollections.observableArrayList(contentSets);
        contentList = new ListView<>(stringList);
        contentList.setMaxHeight(75);
        contentList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(8, 8, 8, 8));
        grid.setHgap(4);
        grid.setVgap(4);
        Insets labelInsets = new Insets(0, 10, 0, 0);
        Insets inputInsets = new Insets(0, 0, 0, 0);

        Label captureSetNameLabel = new Label("Content sets: ");
        GridPane.setMargin(captureSetNameLabel, labelInsets);
        GridPane.setMargin(contentList, inputInsets);
        GridPane.setValignment(captureSetNameLabel, VPos.TOP);
        GridPane.setConstraints(contentList, 1, 4, 3, 1);

        // setup create button mask and canel.
        ButtonType buttonTypeOk = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(buttonTypeOk, buttonTypeCancel);
        setResultConverter(buttonType -> {
            // validation passed, save the project file.
            if (buttonType == buttonTypeOk) {
                return contentList.getSelectionModel().getSelectedItems();
            }
            return null;
        });
        grid.add(captureSetNameLabel, 1, 1);
        grid.add(contentList, 2, 1);
        getDialogPane().setContent(grid);
    }

}