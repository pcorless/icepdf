package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.ScalableField;
import org.icepdf.ri.common.views.annotations.ScalableJList;
import org.icepdf.ri.common.views.annotations.ScalableJScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.logging.Logger;

import static org.icepdf.core.util.SystemProperties.INTERACTIVE_ANNOTATIONS;

public class ChoiceListComponent extends AbstractChoiceComponent implements
        AdjustmentListener, FocusListener, PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(AbstractChoiceComponent.class.toString());

    private ScalableJList choiceList;
    private ScalableJScrollPane choiceListPane;

    public ChoiceListComponent(ChoiceWidgetAnnotation annotation, DocumentViewController documentViewController,
                               AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        if (INTERACTIVE_ANNOTATIONS &&
                annotation.allowScreenOrPrintRenderingOrInteraction()) {
            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            final ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();

            DefaultListModel<ChoiceFieldDictionary.ChoiceOption> listModel = new DefaultListModel<>();
            ArrayList<ChoiceFieldDictionary.ChoiceOption> choices = choiceFieldDictionary.getOptions();
            if (choices == null) {
                choices = annotation.generateChoices();
            }
            int i = 0;
            for (ChoiceFieldDictionary.ChoiceOption choice : choices) {
                listModel.add(i++, choice);
            }
            choiceList = new ScalableJList(listModel, documentViewModel);
            final Annotation childAnnotation = annotation;
            choiceList.addListSelectionListener(e -> {
                boolean adjust = choiceList.getValueIsAdjusting();
                if (!adjust) {
                    annotation.getFieldDictionary().setFieldValue(
                            choiceList.getSelectedValue(),
                            childAnnotation.getPObjectReference());
                }
            });
            ChoiceFieldDictionary.ChoiceFieldType choiceFieldType = choiceFieldDictionary.getChoiceFieldType();
            if (choiceFieldType == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_SINGLE_SELECT) {
                choiceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                if (choiceFieldDictionary.getFieldValue() != null &&
                        choiceFieldDictionary.getIndexes() != null &&
                        choiceFieldDictionary.getIndexes().size() == 1) {
                    choiceList.setSelectedIndex(choiceFieldDictionary.getIndexes().get(0));
                }
            } else {
                choiceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                if (choiceFieldDictionary.getIndexes() != null &&
                        choiceFieldDictionary.getIndexes().size() > 0) {
                    ArrayList<Integer> indexes = choiceFieldDictionary.getIndexes();
                    int[] selected = new int[indexes.size()];
                    for (int j = 0, max = indexes.size(); j < max; j++) {
                        selected[j] = indexes.get(j);
                    }
                    choiceList.setSelectedIndices(selected);
                }
            }
            choiceList.setLayoutOrientation(JList.VERTICAL);
            String fontName = "Helvetica";
            if (choiceFieldDictionary.getFontName() != null) fontName = choiceFieldDictionary.getFontName().toString();
            choiceList.setFont(new Font(fontName, Font.PLAIN, (int) choiceFieldDictionary.getSize()));
            choiceList.setFocusable(true);
            choiceListPane = new ScalableJScrollPane(choiceList, documentViewModel);

            choiceListPane.getVerticalScrollBar().addAdjustmentListener(this);
            choiceListPane.getHorizontalScrollBar().addAdjustmentListener(this);
            choiceListPane.setFocusable(false);
            GridLayout grid = new GridLayout(1, 1, 0, 0);
            this.setLayout(grid);
            setOpaque(false);

            this.add(choiceListPane);
        }

        revalidate();
    }

    @Override
    public void focusGained(FocusEvent e) {
        super.focusGained(e);
        choiceList.requestFocus();
    }

    @Override
    public void validate() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();
        String fontName = "Helvetica";
        if (choiceFieldDictionary.getFontName() != null) {
            fontName = choiceFieldDictionary.getFontName().toString();
        }
        choiceList.setFont(new Font(fontName, Font.PLAIN,
                (int) (choiceFieldDictionary.getSize() * documentViewModel.getViewZoom())));
    }

    @Override
    public void dispose() {
        super.dispose();
        choiceListPane.getHorizontalScrollBar().removeAdjustmentListener(this);
        choiceListPane.getVerticalScrollBar().removeAdjustmentListener(this);
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
        if (e.getValue() > 0) {
            choiceList.setActive(true);
            choiceListPane.setActive(true);
            annotation.getFieldDictionary().setFieldValue(
                    choiceList.getSelectedValue(),
                    annotation.getPObjectReference());
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {

        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        if ("valueFieldReset".equals(prop)) {
            ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();
            choiceFieldDictionary.setFieldValue(choiceFieldDictionary.getFieldValue(), annotation.getPObjectReference());
            resetAppearanceShapes();
        } else if ("focusOwner".equals(prop) &&
                newValue instanceof ScalableField) {
            ScalableField choiceField = (ScalableField) newValue;
            boolean isEditable = !(annotation.getFieldDictionary().isReadOnly());
            if (isEditable) {
                if (choiceField.equals(choiceList) ||
                        choiceField.equals(choiceListPane)) {
                    choiceList.setActive(true);
                    choiceListPane.setActive(true);
                    choiceListPane.getVerticalScrollBar().setVisible(true);
                } else {
                    choiceField.setActive(true);
                }
            }
        } else if ("focusOwner".equals(prop) &&
                oldValue instanceof ScalableField) {
            ScalableField choiceField = (ScalableField) oldValue;
            resetAppearanceShapes();
            getParent().validate();
            if (choiceField.equals(choiceList) ||
                    choiceField.equals(choiceListPane)) {
                choiceList.setActive(false);
                choiceListPane.setActive(false);
                choiceListPane.getVerticalScrollBar().setVisible(false);
            } else {
                choiceField.setActive(false);
            }
        }
        getParent().validate();
        getParent().repaint();
    }


    public boolean isActive() {
        return choiceList != null && choiceList.isActive();
    }
}
