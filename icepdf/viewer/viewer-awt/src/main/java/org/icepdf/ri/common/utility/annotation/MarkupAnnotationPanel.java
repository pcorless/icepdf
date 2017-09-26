/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.*;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * MarkupAnnotationPanel allows users to easily search, sort, filter and view markup annotations and their popup
 * annotation children.  The view main purpose to make working with a large number of markup annotations as easy
 * as possible.  The view shows all markup annotation in an open document.
 *
 * @since 6.3
 */
public class MarkupAnnotationPanel extends JPanel implements ItemListener, ActionListener {

    private static final Logger logger =
            Logger.getLogger(MarkupAnnotationPanel.class.toString());

    public String COLUMN_PROPERTY = "Column";

    public enum SortColumn {PAGE, AUTHOR, DATE, TYPE, COLOR}

    public enum FilterAuthorColumn {
        ALL, AUTHOR_CURRENT, AUTHOR_OTHER,
    }

    public enum FilterSubTypeColumn {
        ALL, TEXT, HIGHLIGHT, STRIKEOUT, UNDERLINE, LINE, SQUARE, CIRCLE, INK, FREETEXT
    }

    // layouts constraint
    protected GridBagConstraints constraints;

    private PropertiesManager propertiesManager;
    private Preferences preferences;
    private SwingController controller;
    protected ResourceBundle messageBundle;

    private JPanel markupAnnotationPanel;

    private JTextField searchTextField;
    private JButton searchButton;
    private JButton clearSearchButton;

    private ArrayList<Action> sortActions;
    private Action sortAction;
    private ArrayList<Action> filterAuthorActions;
    private Action filterAuthorAction;
    private ArrayList<Action> filterTypeActions;
    private Action filterTypeAction;
    private Action filterColorAction;


    private JButton quickColorDropDownButton;

    private MarkupAnnotationHandlerPanel markupAnnotationHandlerPanel;

    private JLabel statusLabel;

    public MarkupAnnotationPanel(SwingController controller, PropertiesManager propertiesManager) {
        this.messageBundle = controller.getMessageBundle();
        preferences = propertiesManager.getPreferences();
        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        this.controller = controller;
        this.propertiesManager = propertiesManager;

        setFocusable(true);

        // assemble sort actions
        sortActions = new ArrayList<>(5);
        sortActions.add(new SortAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.sort.option.byPage.label"), SortColumn.PAGE));
        sortActions.add(new SortAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.sort.option.byAuthor.label"), SortColumn.AUTHOR));
        sortActions.add(new SortAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.sort.option.byDate.label"), SortColumn.DATE));
        sortActions.add(new SortAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.sort.option.byType.label"), SortColumn.TYPE));
        sortActions.add(new SortAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.sort.option.byColor.label"), SortColumn.COLOR));
        // assemble filter by author
        filterAuthorActions = new ArrayList<>(3);
        filterAuthorActions.add(new FilterAuthorAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byAuthor.all.label"), FilterAuthorColumn.ALL));
        filterAuthorActions.add(new FilterAuthorAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byAuthor.current.label"), FilterAuthorColumn.AUTHOR_CURRENT));
        filterAuthorActions.add(new FilterAuthorAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byAuthor.others.label"), FilterAuthorColumn.AUTHOR_OTHER));
        // assemble filter by type
        filterTypeActions = new ArrayList<>(10);
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.all.label"), FilterSubTypeColumn.ALL));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.text.label"), FilterSubTypeColumn.TEXT));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.highlight.label"), FilterSubTypeColumn.HIGHLIGHT));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.underline.label"), FilterSubTypeColumn.UNDERLINE));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.strikeout.label"), FilterSubTypeColumn.STRIKEOUT));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.line.label"), FilterSubTypeColumn.LINE));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.square.label"), FilterSubTypeColumn.SQUARE));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.circle.label"), FilterSubTypeColumn.CIRCLE));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.ink.label"), FilterSubTypeColumn.INK));
        filterTypeActions.add(new FilterTypeAction(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.freeText.label"), FilterSubTypeColumn.FREETEXT));

        buildGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);
    }

    public void setAnnotationUtilityToolbar(JToolBar annotationUtilityToolbar) {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        addGB(this, annotationUtilityToolbar, 0, 0, 1, 1);
    }

    private void buildGUI() {
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);

        markupAnnotationPanel = new JPanel(new GridBagLayout());
        markupAnnotationPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.markupAnnotation.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(this, markupAnnotationPanel, 0, 1, 1, 1);

        buildSearchBar();
        buildMarkupAnnotationCommentView();
        buildSortFilterToolBar();

        buildStatusBar();
    }

    protected void buildSearchBar() {
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchTextField = new JTextField();
        // todo do add graphics for search and clear (binocular an cross...)
        searchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.search.searchButton.label"));
        searchButton.setToolTipText(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.search.searchButton.tooltip"));
        searchButton.addActionListener(this);
        clearSearchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.search.clearButton.label"));
        clearSearchButton.setToolTipText(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.search.clearButton.tooltip"));
        clearSearchButton.addActionListener(this);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 1);
        constraints.weightx = 1.0;
        constraints.weighty = 0;

        constraints.weightx = 0.9;
        addGB(searchPanel, searchTextField, 0, 0, 1, 1);
        constraints.weightx = 0.05;
        addGB(searchPanel, searchButton, 1, 0, 1, 1);
        constraints.weightx = 0.05;
        addGB(searchPanel, clearSearchButton, 2, 0, 1, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0.01;
        addGB(markupAnnotationPanel, searchPanel, 0, 0, 1, 1);
    }

    protected void buildSortFilterToolBar() {
        JPanel filterSortToolPanel = new JPanel(new GridBagLayout());

        DropDownButton sortDropDownButton = new DropDownButton(controller,
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.sort.sortButton.label"),
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.sort.sortButton.tooltip"),
                null, Images.SIZE_LARGE, SwingViewBuilder.buildButtonFont());

        String defaultColumn = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_SORT_COLUMN, SortColumn.PAGE.toString());
        ButtonGroup sortMenuGroup = new ButtonGroup();
        JCheckBoxMenuItem sortMenuItem;
        for (Action sortAction : sortActions) {
            sortMenuItem = new JCheckBoxMenuItem();
            sortMenuItem.setAction(sortAction);
            sortDropDownButton.add(sortMenuItem);
            sortMenuGroup.add(sortMenuItem);
            if (defaultColumn.equals(sortAction.getValue(COLUMN_PROPERTY).toString())) {
                sortMenuItem.setSelected(true);
                this.sortAction = sortAction;
            }
        }
        // todo consider adding descent/ascend sort separator and buttons.

        // build out the base filter
        DropDownButton filterDropDownButton = new DropDownButton(controller,
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.filter.filterButton.label"),
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.filter.filterButton.tooltip"),
                null, Images.SIZE_LARGE, SwingViewBuilder.buildButtonFont());

        JMenu authorFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byAuthor.label"));
        JMenu typeFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.label"));
        JMenu colorFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byColor.label"));

        // build out author submenu, all, current user, other users
        defaultColumn = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_FILTER_AUTHOR_COLUMN, FilterAuthorColumn.ALL.toString());
        filterAuthorAction = buildMenuItemGroup(authorFilterMenuItem, filterAuthorActions, defaultColumn, filterAuthorAction);

        // build out markup annotation types.
        defaultColumn = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_FILTER_TYPE_COLUMN, FilterSubTypeColumn.ALL.toString());
        filterTypeAction = buildMenuItemGroup(typeFilterMenuItem, filterTypeActions, defaultColumn, filterTypeAction);

        // build colour submenu based on colour labels
        ButtonGroup filterColorMenuGroup = new ButtonGroup();
        JCheckBoxMenuItem filterColorAllMenuItem = new JCheckBoxMenuItem();
        filterColorAllMenuItem.setAction(new FilterColorAction(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.filter.option.byColor.all.label"),
                null));
        colorFilterMenuItem.add(filterColorAllMenuItem);
        filterColorMenuGroup.add(filterColorAllMenuItem);
        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        int defaultColor = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FILTER_COLOR_COLUMN, -1);
        JCheckBoxMenuItem filterColorMenuItem;
        if (colorLabels.size() > 0) {
            for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
                filterColorMenuItem = new JCheckBoxMenuItem();
                FilterColorAction sortAction = new FilterColorAction(
                        colorLabel.getLabel(),
                        colorLabel.getColor());
                filterColorMenuItem.setAction(sortAction);
                colorFilterMenuItem.add(filterColorMenuItem);
                filterColorMenuGroup.add(filterColorMenuItem);
                if (defaultColor != -1 && defaultColor == sortAction.getColorRGB()) {
                    filterColorMenuItem.setSelected(true);
                    filterColorAction = sortAction;
                }
            }
        }
        if (defaultColor == -1) {
            filterColorAllMenuItem.setSelected(true);
            filterColorAction = filterColorAllMenuItem.getAction();
        }

        // put it all together.
        filterDropDownButton.add(authorFilterMenuItem);
        filterDropDownButton.add(colorFilterMenuItem);
        filterDropDownButton.add(typeFilterMenuItem);

        quickColorDropDownButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.quickColorButton.label"));
        quickColorDropDownButton.setToolTipText(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.quickColorButton.tooltip"));

        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 1);
        constraints.weightx = 0.05;
        constraints.weighty = 0;

        addGB(filterSortToolPanel, sortDropDownButton, 0, 0, 1, 1);
        addGB(filterSortToolPanel, filterDropDownButton, 1, 0, 1, 1);

        constraints.weightx = 0.90;
        addGB(filterSortToolPanel, new JLabel(" "), 2, 0, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 0.05;
        addGB(filterSortToolPanel, quickColorDropDownButton, 3, 0, 1, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.weightx = 1.0;
        constraints.weighty = 0.01;
        addGB(markupAnnotationPanel, filterSortToolPanel, 0, 1, 1, 1);

        // create the annotation worker an build the panel.
        sortAndFilterAnnotationData();

    }

    public void dispose() {
        markupAnnotationHandlerPanel.dispose();
    }

    private Action buildMenuItemGroup(JMenuItem menuItem, ArrayList<Action> actions, String defaultColumn, Action currentAction) {
        ButtonGroup filterTypeMenuGroup = new ButtonGroup();
        JCheckBoxMenuItem checkBoxMenuItem;
        for (Action action : actions) {
            checkBoxMenuItem = new JCheckBoxMenuItem();
            checkBoxMenuItem.setAction(action);
            menuItem.add(checkBoxMenuItem);
            filterTypeMenuGroup.add(checkBoxMenuItem);
            if (defaultColumn.equals(action.getValue(COLUMN_PROPERTY).toString())) {
                checkBoxMenuItem.setSelected(true);
                currentAction = action;
            }
        }
        return currentAction;
    }

    protected void buildMarkupAnnotationCommentView() {
        markupAnnotationHandlerPanel = new MarkupAnnotationHandlerPanel(controller);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.weightx = 1.0;
        constraints.weighty = 1.9;
        addGB(markupAnnotationPanel, markupAnnotationHandlerPanel, 0, 2, 1, 1);
    }

    protected void buildStatusBar() {
        statusLabel = new JLabel("Status:");
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.weighty = 0.01;
        addGB(markupAnnotationPanel, statusLabel, 0, 3, 1, 1);
    }

    public void setDocument(Document document) {
        markupAnnotationHandlerPanel.setDocument(document);
    }

    protected void sortAndFilterAnnotationData() {
        // push the work off the a worker thread
        SortColumn sortType = (SortColumn) sortAction.getValue(COLUMN_PROPERTY);
        FilterSubTypeColumn filterType = (FilterSubTypeColumn) filterTypeAction.getValue(COLUMN_PROPERTY);
        FilterAuthorColumn filterAuthor = (FilterAuthorColumn) filterAuthorAction.getValue(COLUMN_PROPERTY);
        Color filterColor = (Color) filterColorAction.getValue(COLUMN_PROPERTY);

        if (logger.isLoggable(Level.FINE)) {
            System.out.println(sortType + " " +
                    filterType + " " +
                    filterAuthor + " " +
                    filterColor);
        }

        markupAnnotationHandlerPanel.sortAndFilterAnnotationData(sortType, filterType, filterAuthor, filterColor);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object object = e.getItem();
    }

    /**
     * Gridbag constructor helper
     *
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan
     * @param colSpan
     */
    protected void addGB(JPanel layout, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

    class SortAction extends AbstractAction {
        public SortAction(String label, SortColumn column) {
            this.putValue(Action.NAME, label);
            this.putValue(COLUMN_PROPERTY, column);
        }

        public void actionPerformed(ActionEvent ae) {
            sortAction = this;
            preferences.put(PropertiesManager.PROPERTY_ANNOTATION_SORT_COLUMN,
                    getValue(COLUMN_PROPERTY).toString());
            sortAndFilterAnnotationData();
        }
    }

    class FilterTypeAction extends AbstractAction {
        public FilterTypeAction(String label, FilterSubTypeColumn column) {
            this.putValue(Action.NAME, label);
            this.putValue(COLUMN_PROPERTY, column);
        }

        public void actionPerformed(ActionEvent ae) {
            filterTypeAction = this;
            preferences.put(PropertiesManager.PROPERTY_ANNOTATION_FILTER_TYPE_COLUMN, getValue(COLUMN_PROPERTY).toString());
            sortAndFilterAnnotationData();
        }
    }

    class FilterAuthorAction extends AbstractAction {
        public FilterAuthorAction(String label, FilterAuthorColumn column) {
            this.putValue(Action.NAME, label);
            this.putValue(COLUMN_PROPERTY, column);
        }

        public void actionPerformed(ActionEvent ae) {
            filterAuthorAction = this;
            preferences.put(PropertiesManager.PROPERTY_ANNOTATION_FILTER_AUTHOR_COLUMN, getValue(COLUMN_PROPERTY).toString());
            sortAndFilterAnnotationData();
        }
    }

    class FilterColorAction extends AbstractAction {
        public FilterColorAction(String label, Color color) {
            this.putValue(Action.NAME, label);
            this.putValue(COLUMN_PROPERTY, color);
            if (color != null) this.putValue(Action.SMALL_ICON, new ColorIcon(color));
        }

        public Integer getColorRGB() {
            Object value = getValue(Action.SMALL_ICON);
            if (value != null) {
                return ((ColorIcon) value).getColor().getRGB();
            }
            return null;
        }

        public void actionPerformed(ActionEvent ae) {
            filterColorAction = this;
            if (getValue(COLUMN_PROPERTY) != null) {
                Integer colorValue = ((Color) getValue(COLUMN_PROPERTY)).getRGB();
                preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_FILTER_COLOR_COLUMN, colorValue);
            } else {
                preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_FILTER_COLOR_COLUMN, -1);
            }
            sortAndFilterAnnotationData();
        }
    }
}
