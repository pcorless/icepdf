/*
 * Copyright 2026 Patrick Corless
 *
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

    private final ListView<ContentSet> contentList;

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