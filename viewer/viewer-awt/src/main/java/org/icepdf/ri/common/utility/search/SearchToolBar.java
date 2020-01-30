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

import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchToolBar extends JToolBar implements ActionListener, BaseSearchComponent {

    private JLabel searchLabel;
    private JTextField searchTextField;
    private JButton nextSearchResult;
    private JButton previousSearchButton;

    private SearchFilterButtonWrapper wrapper;
    private JMenuItem advancedSearchMenuItem;

    private SimpleSearchHelper simpleSearchHelper;
    private SearchTextTask searchTextTask;
    private SwingController controller;
    private ResourceBundle messageBundle;

    private String lastSearchPhrase;

    public SearchToolBar(SwingController controller,
                         String name,
                         JButton previousSearchButton,
                         JButton nextSearchResult) {
        super(name);
        this.controller = controller;

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
        }

        if (source == advancedSearchMenuItem) {
            controller.showSearchPanel(lastSearchPhrase);
        } else if (source == nextSearchResult && simpleSearchHelper != null) {
            simpleSearchHelper.nextResult();
        } else if (source == previousSearchButton && simpleSearchHelper != null) {
            simpleSearchHelper.previousResult();
        }
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        searchLabel.setEnabled(enabled);
        searchTextField.setEnabled(enabled);
        wrapper.setEnabled(enabled);
        nextSearchResult.setEnabled(enabled);
        previousSearchButton.setEnabled(enabled);
    }

    private void createNewSearch() {

        if (simpleSearchHelper != null) {
            simpleSearchHelper.dispose();
        }

        simpleSearchHelper = wrapper.getSimpleSearchHelper(controller, searchTextField.getText());

    }

    private void createNewFullSearch() {
        if (searchTextTask != null && !searchTextTask.isCancelled() && !searchTextTask.isDone()) {
            searchTextTask.cancel(true);
        }
        // reset high light states.
        createNewSearch();
        controller.getDocumentSearchController().clearAllSearchHighlight();
        controller.getDocumentViewController().getViewContainer().repaint();

        searchTextField.setForeground(Color.BLACK);
        // do a quick check to make sure we have valida expression.
        if (wrapper.isRegex()) {
            try {
                Pattern.compile(searchTextField.getText());
            } catch (PatternSyntaxException e) {
                searchTextField.setForeground(Color.RED);
                return;
            }

        }
        searchTextTask = wrapper.getSearchTask(this, controller, searchTextField.getText());
        wrapper.setEnabled(false);

        // start the task and the timer
        searchTextTask.execute();
    }

    @Override
    public void notifySearchFiltersChanged() {
        createNewFullSearch();
    }

    private void buildGui() {
        // build out label and search field as they don't need ViewBuilder utilities.
        searchLabel = new JLabel(messageBundle.getString("viewer.toolbar.tool.search.label"));
        searchTextField = new JTextField("", 10);
        searchTextField.setToolTipText(messageBundle.getString("viewer.toolbar.tool.search.input.tooltip"));
        searchTextField.addActionListener(this);
        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                doUpdate();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                doUpdate();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                doUpdate();
            }

            private void doUpdate() {
                createNewFullSearch();
            }
        });
        searchTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    nextSearchResult.doClick();
                }
            }
        });


        // attach base listeners.
        nextSearchResult.addActionListener(this);
        previousSearchButton.addActionListener(this);

        // build out the filter menuItems and hook up preference api.


        advancedSearchMenuItem = new JMenuItem(messageBundle.getString(
                "viewer.toolbar.search.advancedSearch.label"));
        advancedSearchMenuItem.addActionListener(this);

        this.wrapper = new SearchFilterButtonWrapper(this, controller, "viewer.toolbar.tool.search.filter.tooltip");
        wrapper.getButton().add(advancedSearchMenuItem, 0);

        // build out the base structure
        this.add(searchLabel);
        this.add(searchTextField);
        this.add(wrapper.getButton());
        this.add(previousSearchButton);
        this.add(nextSearchResult);
    }

    public void focusTextField() {
        if (searchTextField != null) {
            searchTextField.requestFocusInWindow();
        }
    }

    @Override
    public void updateProgressControls(String message) {
        if (searchTextTask.isCancelled() || searchTextTask.isDone()) {
            wrapper.setEnabled(true);
        }
    }

    @Override
    public void addFoundCommentEntry(SearchTextTask.CommentsResult outlineResult, SearchTextTask searchTextTask) {
    }

    @Override
    public void addFoundOutlineEntry(SearchTextTask.OutlineResult outlineResult, SearchTextTask searchTextTask) {

    }

    @Override
    public void addFoundTextEntry(SearchTextTask.TextResult outlineResult, SearchTextTask searchTextTask) {

    }

    @Override
    public void addFoundDestinationEntry(SearchTextTask.DestinationsResult outlineResult, SearchTextTask searchTextTask) {

    }

    @Override
    public MessageFormat setupSearchingMessageForm() {
        return null;
    }

    @Override
    public MessageFormat setupSearchResultMessageForm() {
        return null;
    }

    @Override
    public MessageFormat setupSearchCompletionMessageForm() {
        return null;
    }
}