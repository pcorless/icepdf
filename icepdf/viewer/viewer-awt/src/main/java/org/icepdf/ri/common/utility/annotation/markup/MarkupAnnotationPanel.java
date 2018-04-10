/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.annotation.markup;

import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.*;
import org.icepdf.ri.common.utility.annotation.AnnotationPanel;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.icepdf.ri.util.PropertiesManager.PROPERTY_SEARCH_MARKUP_PANEL_CASE_SENSITIVE_ENABLED;
import static org.icepdf.ri.util.PropertiesManager.PROPERTY_SEARCH_MARKUP_PANEL_REGEX_ENABLED;

/**
 * MarkupAnnotationPanel allows users to easily search, sort, filter and view markup annotations and their popup
 * annotation children.  The view main purpose to make working with a large number of markup annotations as easy
 * as possible.  The view shows all markup annotation in an open document.
 *
 * @since 6.3
 */
public class MarkupAnnotationPanel extends JPanel implements ActionListener, PropertyChangeListener,
        MutableDocument, DocumentListener {

    private static final Logger logger =
            Logger.getLogger(MarkupAnnotationPanel.class.toString());

    public static boolean PRIVATE_PROPERTY_ENABLED;

    static {
        PRIVATE_PROPERTY_ENABLED = Defs.booleanProperty(
                "org.icepdf.core.page.annotation.privateProperty.enabled", false);
    }

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

    private Preferences preferences;
    private Controller controller;
    protected ResourceBundle messageBundle;

    private AnnotationPanel parentPanel;
    private JPanel markupAnnotationPanel;

    private JTextField searchTextField;
    private JButton searchButton;
    private JButton clearSearchButton;

    private DropDownButton filterDropDownButton;
    private JMenu colorFilterMenuItem;

    private ArrayList<Action> sortActions;
    private Action sortAction;
    private ArrayList<Action> filterAuthorActions;
    private Action filterAuthorAction;
    private ArrayList<Action> filterTypeActions;
    private Action filterTypeAction;
    private Action filterColorAction;

    private JCheckBoxMenuItem regexMenuItem;
    private JCheckBoxMenuItem caseSensitiveMenutItem;

    private QuickPaintAnnotationButton quickPaintAnnotationButton;

    private MarkupAnnotationHandlerPanel markupAnnotationHandlerPanel;

    public MarkupAnnotationPanel(SwingController controller) {
        this.messageBundle = controller.getMessageBundle();
        preferences = PropertiesManager.getInstance().getPreferences();
        this.controller = controller;
        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setFocusable(true);

        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);
        addPropertyChangeListener(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, this);

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

        preferences = controller.getPropertiesManager().getPreferences();
        buildGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);
    }

    public void setParentPanel(AnnotationPanel parentPanel) {
        this.parentPanel = parentPanel;
    }

    public void setAnnotationUtilityToolbar(JToolBar annotationUtilityToolbar) {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        addGB(this, annotationUtilityToolbar, 0, 0, 1, 1);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        String propertyName = evt.getPropertyName();
        if (propertyName.equals(PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE)) {
            AnnotationComponent annotationComponent = markupAnnotationHandlerPanel.getSelectedAnnotation();

            if (annotationComponent != null && newValue instanceof Color) {
                Annotation annotation = annotationComponent.getAnnotation();
                annotation.setColor((Color) newValue);

                // save the action state back to the document structure.
                controller.getDocumentViewController().updateAnnotation(annotationComponent);
                annotationComponent.resetAppearanceShapes();
                annotationComponent.repaint();

                // repaint the tree
                if (filterColorAction != null) {
                    markupAnnotationHandlerPanel.refreshMarkupTree();
                } else {
                    markupAnnotationHandlerPanel.repaint();
                }
                // store the last used colour for the annoation type
                if (annotation != null) {
                    if (annotation instanceof TextMarkupAnnotation) {
                        TextMarkupAnnotation textMarkupAnnotation = (TextMarkupAnnotation) annotation;
                        if (textMarkupAnnotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
                            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_UNDERLINE_COLOR,
                                    ((Color) newValue).getRGB());
                        } else if (textMarkupAnnotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
                            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_STRIKE_OUT_COLOR,
                                    ((Color) newValue).getRGB());
                        }
                    } else if (annotation instanceof LineAnnotation) {
                        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_LINE_COLOR,
                                ((Color) newValue).getRGB());
                    } else if (annotation instanceof SquareAnnotation) {
                        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_SQUARE_COLOR,
                                ((Color) newValue).getRGB());
                    } else if (annotation instanceof CircleAnnotation) {
                        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_CIRCLE_COLOR,
                                ((Color) newValue).getRGB());
                    } else if (annotation instanceof InkAnnotation) {
                        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_INK_COLOR,
                                ((Color) newValue).getRGB());
                    } else if (annotation instanceof FreeTextAnnotation) {
                        // set the free text font colour,
//                        ((FreeTextAnnotation)annotation).setFontColor((Color) newValue);
//                        annotationComponent.resetAppearanceShapes();
//                        annotationComponent.repaint();
//                        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_COLOR,
//                                ((Color) newValue).getRGB());
                    }
                }
            }
        } else if (propertyName.equals(PropertyConstants.ANNOTATION_SELECTED) ||
                propertyName.equals(PropertyConstants.ANNOTATION_FOCUS_GAINED)) {
            AnnotationComponent annotationComponent = (AnnotationComponent) newValue;
            if (annotationComponent != null &&
                    annotationComponent.getAnnotation() instanceof MarkupAnnotation) {
                parentPanel.setSelectedTab(PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_MARKUP);
                quickPaintAnnotationButton.setColor(annotationComponent.getAnnotation().getColor(), false);
                quickPaintAnnotationButton.setEnabled(true);
                // update the status bar
                applyAnnotationStatusLabel(annotationComponent.getAnnotation());
            }
        } else if (propertyName.equals(PropertyConstants.ANNOTATION_UPDATED) ||
                propertyName.equals(PropertyConstants.ANNOTATION_SUMMARY_UPDATED)) {
            AnnotationComponent annotationComponent = (AnnotationComponent) newValue;
            if (annotationComponent != null &&
                    annotationComponent.getAnnotation() instanceof MarkupAnnotation) {
                // update the status bar
                applyAnnotationStatusLabel(annotationComponent.getAnnotation());
            } else if (annotationComponent != null &&
                    annotationComponent.getAnnotation() instanceof PopupAnnotation) {
                // update the status bar
                applyAnnotationStatusLabel(((PopupAnnotation) annotationComponent.getAnnotation()).getParent());
            }
        }
    }

    /**
     * Apply &lt;Public | Private&gt; | &lt;Color label name, if exists&gt; message format for any named colours
     *
     * @param annotation annotation to generate status lable from.
     */
    protected void applyAnnotationStatusLabel(Annotation annotation) {
        // check for a colour label.

        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        String colorLabelString = null;
        if (colorLabels != null) {
            for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
                if (annotation.getColor() != null && colorLabel.getColor().equals(annotation.getColor())) {
                    colorLabelString = colorLabel.getLabel();
                    break;
                }
            }
        }
        StringBuilder statusLabel = new StringBuilder();
        // append private/public start.
        if (PRIVATE_PROPERTY_ENABLED) {
            if (annotation.getFlagPrivateContents()) {
                statusLabel.append(messageBundle.getString("viewer.utilityPane.markupAnnotation.view.privateToggleButton.label"));
            } else {
                statusLabel.append(messageBundle.getString("viewer.utilityPane.markupAnnotation.view.publicToggleButton.label"));
            }
        }
        if (colorLabelString != null) {
            if (PRIVATE_PROPERTY_ENABLED) statusLabel.append(" | ");
            statusLabel.append(colorLabelString);
        }
        if (statusLabel.length() > 0) {
            markupAnnotationHandlerPanel.setProgressLabel(statusLabel.toString());
        }
    }

    public QuickPaintAnnotationButton getQuickPaintAnnotationButton() {
        return quickPaintAnnotationButton;
    }


    private void buildGUI() {
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        markupAnnotationPanel = new JPanel(new GridBagLayout());
        addGB(this, markupAnnotationPanel, 0, 1, 1, 1);

        buildSearchBar();
        buildMarkupAnnotationCommentView();
        buildSortFilterToolBar();

    }

    protected void buildSearchBar() {
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchTextField = new JTextField();
        searchTextField.addActionListener(this);
        searchTextField.getDocument().addDocumentListener(this);
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

        String iconSize = preferences.get(PropertiesManager.PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE);

        DropDownButton sortDropDownButton = new DropDownButton(controller, "",
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.sort.sortButton.tooltip"),
                "sort", iconSize, SwingViewBuilder.buildButtonFont());

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

        // build out the base filter
        filterDropDownButton = new DropDownButton(controller, "",
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.filter.filterButton.tooltip"),
                "filter", iconSize, SwingViewBuilder.buildButtonFont());

        JMenu authorFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byAuthor.label"));
        JMenu typeFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byType.label"));
        colorFilterMenuItem = new JMenu(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.toolbar.filter.option.byColor.label"));

        // build out author submenu, all, current user, other users
        defaultColumn = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_FILTER_AUTHOR_COLUMN, FilterAuthorColumn.ALL.toString());
        filterAuthorAction = buildMenuItemGroup(authorFilterMenuItem, filterAuthorActions, defaultColumn, filterAuthorAction);

        // build out markup annotation types.
        defaultColumn = preferences.get(PropertiesManager.PROPERTY_ANNOTATION_FILTER_TYPE_COLUMN, FilterSubTypeColumn.ALL.toString());
        filterTypeAction = buildMenuItemGroup(typeFilterMenuItem, filterTypeActions, defaultColumn, filterTypeAction);

        refreshColorPanel();

        // regex and case sensitivity configuration.
        boolean isRegex = preferences.getBoolean(PROPERTY_SEARCH_MARKUP_PANEL_REGEX_ENABLED, true);
        boolean isCaseSensitive = preferences.getBoolean(PROPERTY_SEARCH_MARKUP_PANEL_CASE_SENSITIVE_ENABLED, false);
        // add case sensitive button
        regexMenuItem = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.regexCheckbox.label"), isRegex);
        regexMenuItem.addActionListener(this);
        caseSensitiveMenutItem = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.caseSenstiveCheckbox.label"), isCaseSensitive);
        caseSensitiveMenutItem.addActionListener(this);

        if (isRegex || isCaseSensitive) {
            regexMenuItem.setEnabled(isRegex);
            caseSensitiveMenutItem.setEnabled(isCaseSensitive);
        }

        // put it all together.
        filterDropDownButton.add(authorFilterMenuItem);
        filterDropDownButton.add(colorFilterMenuItem);
        filterDropDownButton.add(typeFilterMenuItem);
        filterDropDownButton.addSeparator();
        filterDropDownButton.add(regexMenuItem);
        filterDropDownButton.add(caseSensitiveMenutItem);

        quickPaintAnnotationButton = new QuickPaintAnnotationButton(
                controller,
                messageBundle,
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.quickColorButton.label"),
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.quickColorButton.tooltip"),
                "paint_bucket",
                iconSize,
                SwingViewBuilder.buildButtonFont());
        quickPaintAnnotationButton.setEnabled(false);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(1, 0, 1, 1);
        constraints.weightx = 0.05;
        constraints.weighty = 0;

        addGB(filterSortToolPanel, sortDropDownButton, 0, 0, 1, 1);
        addGB(filterSortToolPanel, filterDropDownButton, 1, 0, 1, 1);

        constraints.weightx = 0.90;
        addGB(filterSortToolPanel, new JLabel(" "), 2, 0, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 0.05;
        addGB(filterSortToolPanel, quickPaintAnnotationButton, 3, 0, 1, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.weightx = 1.0;
        constraints.weighty = 0.01;
        addGB(markupAnnotationPanel, filterSortToolPanel, 0, 1, 1, 1);

        // create the annotation worker an build the panel.
        sortAndFilterAnnotationData();

    }

    public void refreshColorPanel() {
        // update the quick color drop down.
        if (quickPaintAnnotationButton != null) {
            quickPaintAnnotationButton.refreshColorPanel();
        }

        colorFilterMenuItem.removeAll();
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
        markupAnnotationHandlerPanel = new MarkupAnnotationHandlerPanel(controller, this);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.weightx = 1.0;
        constraints.weighty = 1.9;
        addGB(markupAnnotationPanel, markupAnnotationHandlerPanel, 0, 2, 1, 1);
    }

    @Override
    public void refreshDocumentInstance() {
        markupAnnotationHandlerPanel.refreshDocumentInstance();
    }

    @Override
    public void disposeDocument() {
        markupAnnotationHandlerPanel.disposeDocument();
        quickPaintAnnotationButton.removePropertyChangeListener(PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE, controller);
    }

    protected void sortAndFilterAnnotationData() {
        // push the work off the a worker thread
        SortColumn sortType = (SortColumn) sortAction.getValue(COLUMN_PROPERTY);
        FilterSubTypeColumn filterType = (FilterSubTypeColumn) filterTypeAction.getValue(COLUMN_PROPERTY);
        FilterAuthorColumn filterAuthor = (FilterAuthorColumn) filterAuthorAction.getValue(COLUMN_PROPERTY);
        Color filterColor = (Color) filterColorAction.getValue(COLUMN_PROPERTY);

        if (!filterAuthor.equals(FilterAuthorColumn.ALL) ||
                !filterType.equals(FilterSubTypeColumn.ALL) ||
                filterColor != null) {
            filterDropDownButton.setSelected(true);
        } else {
            filterDropDownButton.setSelected(false);
        }

        // setup search pattern
        Pattern searchPattern = null;
        String searchTerm = searchTextField.getText();
        if (searchTerm != null && !searchTerm.isEmpty()) {
            searchPattern = Pattern.compile(searchTerm);
            // todo we can add search flags at a later date, via drop down on search or checkboxes.
        }

        markupAnnotationHandlerPanel.sortAndFilterAnnotationData(
                searchPattern, sortType, filterType, filterAuthor, filterColor,
                regexMenuItem.isSelected(), caseSensitiveMenutItem.isSelected());
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        try {
            sortAndFilterAnnotationData();
        } catch (PatternSyntaxException syntaxException) {
            logger.warning("Error processing search pattern syntax");
            markupAnnotationHandlerPanel.setProgressLabel(syntaxException.getMessage());
        }
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        try {
            sortAndFilterAnnotationData();
        } catch (PatternSyntaxException syntaxException) {
            logger.warning("Error processing search pattern syntax");
            markupAnnotationHandlerPanel.setProgressLabel(syntaxException.getMessage());
        }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        try {
            sortAndFilterAnnotationData();
        } catch (PatternSyntaxException syntaxException) {
            logger.warning("Error processing search pattern syntax");
            markupAnnotationHandlerPanel.setProgressLabel(syntaxException.getMessage());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == searchButton || source == searchTextField) {
            try {
                sortAndFilterAnnotationData();
            } catch (PatternSyntaxException syntaxException) {
                logger.warning("Error processing search pattern syntax");
                markupAnnotationHandlerPanel.setProgressLabel(syntaxException.getMessage());
            }
        } else if (source == clearSearchButton) {
            searchTextField.setText("");
            sortAndFilterAnnotationData();
        } else if (source == regexMenuItem) {
            caseSensitiveMenutItem.setEnabled(!regexMenuItem.isSelected());
            preferences.putBoolean(PROPERTY_SEARCH_MARKUP_PANEL_REGEX_ENABLED, regexMenuItem.isSelected());
        } else if (source == caseSensitiveMenutItem) {
            regexMenuItem.setEnabled(!caseSensitiveMenutItem.isSelected());
            preferences.putBoolean(PROPERTY_SEARCH_MARKUP_PANEL_CASE_SENSITIVE_ENABLED, caseSensitiveMenutItem.isSelected());
        }
    }

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

    public class FilterColorAction extends AbstractAction {

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
