/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.utility.search;

import org.icepdf.ri.common.DropDownButton;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.PropertiesManager.*;

public class SearchToolBar extends JToolBar implements ActionListener {

    private JLabel searchLabel;
    private JTextField searchTextField;
    private DropDownButton searchFilterDropDownButton;
    private JButton nextSearchResult;
    private JButton previousSearchButton;

    private JMenuItem advancedSearchMenuItem;
    private JCheckBoxMenuItem caseSensitiveCheckbox;
    private JCheckBoxMenuItem wholeWordCheckbox;
    private JCheckBoxMenuItem commentsCheckbox;

    private SimpleSearchHelper simpleSearchHelper;
    private SwingController controller;
    private Preferences preferences;
    private ResourceBundle messageBundle;

    private String lastSearchPhrase;

    public SearchToolBar(SwingController controller,
                         String name,
                         DropDownButton searchFilterDropDownButton,
                         JButton previousSearchButton,
                         JButton nextSearchResult) {
        super(name);
        this.controller = controller;

        this.searchFilterDropDownButton = searchFilterDropDownButton;
        this.nextSearchResult = nextSearchResult;
        this.previousSearchButton = previousSearchButton;

        messageBundle = controller.getMessageBundle();

        buildGui();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        if (source == null)
            return;
        String searchText = searchTextField.getText();
        if (!searchText.equals(lastSearchPhrase)) {
            lastSearchPhrase = searchText;
            createNewSearch();
        }

        if (source == advancedSearchMenuItem) {
            controller.showSearchPanel(lastSearchPhrase);
        } else if (source == nextSearchResult) {
            simpleSearchHelper.nextResult();
        } else if (source == previousSearchButton) {
            simpleSearchHelper.previousResult();
        } else if (source == wholeWordCheckbox) {
            preferences.putBoolean(PROPERTY_QUICK_SEARCH_WHOLE_WORDS_ENABLED, wholeWordCheckbox.isSelected());
        } else if (source == caseSensitiveCheckbox) {
            preferences.putBoolean(PROPERTY_QUICK_SEARCH_CASE_SENSITIVE_ENABLED, caseSensitiveCheckbox.isSelected());
        } else if (source == commentsCheckbox) {
            preferences.putBoolean(PROPERTY_QUICK_SEARCH_SEARCH_COMMENTS_ENABLED, commentsCheckbox.isSelected());
        }
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        searchLabel.setEnabled(enabled);
        searchTextField.setEnabled(enabled);
        searchFilterDropDownButton.setEnabled(enabled);
        nextSearchResult.setEnabled(enabled);
        previousSearchButton.setEnabled(enabled);
    }

    private void createNewSearch() {

        if (simpleSearchHelper != null) {
            simpleSearchHelper.dispose();
        }

        SimpleSearchHelper.Builder builder =
                new SimpleSearchHelper.Builder(controller, searchTextField.getText());
        simpleSearchHelper = builder.setCaseSensitive(caseSensitiveCheckbox.isSelected())
                .setWholeWord(wholeWordCheckbox.isSelected())
                .setComments(commentsCheckbox.isSelected()).build();
    }

    private void buildGui() {
        // build out label and search field as they don't need ViewBuilder utilities.
        searchLabel = new JLabel(messageBundle.getString("viewer.toolbar.tool.search.label"));
        searchTextField = new JTextField("", 10);
        searchTextField.setToolTipText(messageBundle.getString("viewer.toolbar.tool.search.input.tooltip"));
        searchTextField.addActionListener(this);

        // build out the base structure
        this.add(searchLabel);
        this.add(searchTextField);
        this.add(searchFilterDropDownButton);
        this.add(previousSearchButton);
        this.add(nextSearchResult);

        // attach base listeners.
        nextSearchResult.addActionListener(this);
        previousSearchButton.addActionListener(this);

        // build out the filter menuItems and hook up preference api.

        // apply default preferences
        preferences = controller.getPropertiesManager().getPreferences();
        boolean isWholeWord = preferences.getBoolean(PROPERTY_QUICK_SEARCH_WHOLE_WORDS_ENABLED, false);
        boolean isCaseSensitive = preferences.getBoolean(PROPERTY_QUICK_SEARCH_CASE_SENSITIVE_ENABLED, false);
        boolean isComments = preferences.getBoolean(PROPERTY_QUICK_SEARCH_SEARCH_COMMENTS_ENABLED, false);

        advancedSearchMenuItem = new JMenuItem(messageBundle.getString(
                "viewer.toolbar.search.advancedSearch.label"));
        advancedSearchMenuItem.addActionListener(this);
        wholeWordCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.toolbar.search.wholeWordCheckbox.label"), isWholeWord);
        wholeWordCheckbox.addActionListener(this);
        caseSensitiveCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.toolbar.search.caseSensitiveCheckbox.label"), isCaseSensitive);
        caseSensitiveCheckbox.addActionListener(this);
        commentsCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.toolbar.search.comments.label"), isComments);
        commentsCheckbox.addActionListener(this);

        searchFilterDropDownButton.add(advancedSearchMenuItem);
        commentsCheckbox.addActionListener(this);
        searchFilterDropDownButton.addSeparator();
        searchFilterDropDownButton.add(wholeWordCheckbox);
        searchFilterDropDownButton.add(caseSensitiveCheckbox);
        searchFilterDropDownButton.add(commentsCheckbox);
    }

}