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
package org.icepdf.ri.common.preferences;

import org.icepdf.core.pobjects.Page;
import org.icepdf.ri.common.ColorChooserButton;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractDocumentView;
import org.icepdf.ri.common.views.PageViewDecorator;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Contains general setting for the viewer reference implementation.
 *
 * @since 6.3
 */
public class GeneralPreferencesPanel extends JPanel implements PropertyChangeListener {

    // layouts constraint
    private GridBagConstraints constraints;

    private Preferences preferences;

    private ColorChooserButton highlightColorChooserButton;
    private ColorChooserButton selectionColorChooserButton;

    private ColorChooserButton paperShadowColorChooserButton;
    private ColorChooserButton paperColorChooserButton;
    private ColorChooserButton paperBorderColorChooserButton;
    private ColorChooserButton viewBackgroundColorChooserButton;

    public GeneralPreferencesPanel(SwingController swingController, PropertiesManager propertiesManager,
                                   ResourceBundle messageBundle) {
        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        preferences = propertiesManager.getPreferences();

        Color highlightColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_TEXT_HIGHLIGHT_COLOR, Page.highlightColor.getRGB()));
        Color selectionColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_TEXT_SELECTION_COLOR, Page.selectionColor.getRGB()));

        highlightColorChooserButton = new ColorChooserButton(highlightColor);
        highlightColorChooserButton.addPropertyChangeListener("background", this);
        selectionColorChooserButton = new ColorChooserButton(selectionColor);
        selectionColorChooserButton.addPropertyChangeListener("background", this);

        JPanel generalPreferences = new JPanel(new GridBagLayout());
        generalPreferences.setAlignmentY(JPanel.TOP_ALIGNMENT);

        generalPreferences.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.general.selection.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        addGB(generalPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.textSelectionColor.label")),
                0, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(generalPreferences, selectionColorChooserButton, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(generalPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.searchHighlightColor.label")),
                0, 1, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(generalPreferences, highlightColorChooserButton, 1, 1, 1, 1);

        constraints.anchor = GridBagConstraints.NORTH;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, generalPreferences, 0, 0, 1, 1);

        // build out the page view colours

        Color paperShadowColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_PAGE_VIEW_SHADOW_COLOR, PageViewDecorator.pageShadowColor.getRGB()));
        Color paperColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_PAGE_VIEW_PAPER_COLOR, PageViewDecorator.pageColor.getRGB()));
        Color paperBorderColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_PAGE_VIEW_BORDER_COLOR, PageViewDecorator.pageBorderColor.getRGB()));
        Color viewBackgroundColor = new Color(preferences.getInt(
                PropertiesManager.PROPERTY_PAGE_VIEW_BACKGROUND_COLOR, AbstractDocumentView.backgroundColour.getRGB()));

        paperShadowColorChooserButton = new ColorChooserButton(paperShadowColor);
        paperShadowColorChooserButton.addPropertyChangeListener("background", this);
        paperColorChooserButton = new ColorChooserButton(paperColor);
        paperColorChooserButton.addPropertyChangeListener("background", this);
        paperBorderColorChooserButton = new ColorChooserButton(paperBorderColor);
        paperBorderColorChooserButton.addPropertyChangeListener("background", this);
        viewBackgroundColorChooserButton = new ColorChooserButton(viewBackgroundColor);
        viewBackgroundColorChooserButton.addPropertyChangeListener("background", this);

        JPanel pageViewPreferences = new JPanel(new GridBagLayout());
        pageViewPreferences.setAlignmentY(JPanel.TOP_ALIGNMENT);

        pageViewPreferences.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.general.pageView.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(pageViewPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.pageView.shadowColor.label")),
                0, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(pageViewPreferences, paperShadowColorChooserButton, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(pageViewPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.pageView.paperColor.label")),
                0, 1, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(pageViewPreferences, paperColorChooserButton, 1, 1, 1, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(pageViewPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.pageView.borderColor.label")),
                0, 2, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(pageViewPreferences, paperBorderColorChooserButton, 1, 2, 1, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(pageViewPreferences, new JLabel(
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.general.pageView.backgroundColor.label")),
                0, 3, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(pageViewPreferences, viewBackgroundColorChooserButton, 1, 3, 1, 1);
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, pageViewPreferences, 0, 1, 1, 1);

        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 2, 1, 1);

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object source = evt.getSource();
        if (source == highlightColorChooserButton) {
            Page.highlightColor = highlightColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_TEXT_HIGHLIGHT_COLOR, Page.highlightColor.getRGB());
        } else if (source == selectionColorChooserButton) {
            Page.selectionColor = selectionColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_TEXT_SELECTION_COLOR, Page.selectionColor.getRGB());
        } else if (source == paperShadowColorChooserButton) {
            PageViewDecorator.pageShadowColor = paperShadowColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_PAGE_VIEW_SHADOW_COLOR, PageViewDecorator.pageShadowColor.getRGB());
        } else if (source == paperColorChooserButton) {
            PageViewDecorator.pageColor = paperColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_PAGE_VIEW_PAPER_COLOR, PageViewDecorator.pageColor.getRGB());
        } else if (source == paperBorderColorChooserButton) {
            PageViewDecorator.pageBorderColor = paperBorderColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_PAGE_VIEW_BACKGROUND_COLOR, PageViewDecorator.pageBorderColor.getRGB());
        } else if (source == viewBackgroundColorChooserButton) {
            AbstractDocumentView.backgroundColour = viewBackgroundColorChooserButton.getBackground();
            preferences.putInt(PropertiesManager.PROPERTY_PAGE_VIEW_BACKGROUND_COLOR, AbstractDocumentView.backgroundColour.getRGB());
        }
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
