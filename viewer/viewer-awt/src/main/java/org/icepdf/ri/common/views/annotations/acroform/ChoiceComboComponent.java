package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.ScalableField;
import org.icepdf.ri.common.views.annotations.ScalableJComboBox;

import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Logger;

import static org.icepdf.core.util.SystemProperties.INTERACTIVE_ANNOTATIONS;

public class ChoiceComboComponent extends AbstractChoiceComponent implements FocusListener, PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(ChoiceComboComponent.class.toString());

    private ScalableJComboBox comboBoxList;

    public ChoiceComboComponent(ChoiceWidgetAnnotation annotation, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        if (INTERACTIVE_ANNOTATIONS &&
                annotation.allowScreenOrPrintRenderingOrInteraction()) {

            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            final ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();

            ArrayList<ChoiceFieldDictionary.ChoiceOption> choices = choiceFieldDictionary.getOptions();
            if (choices == null) {
                choices = annotation.generateChoices();
            }
            Vector<ChoiceFieldDictionary.ChoiceOption> items = new Vector<>(choices.size());
            for (ChoiceFieldDictionary.ChoiceOption choice : choices) {
                items.addElement(choice);
            }
            comboBoxList = new ScalableJComboBox(items, documentViewModel);
            if (choiceFieldDictionary.getFieldValue() != null &&
                    choiceFieldDictionary.getIndexes() != null &&
                    choiceFieldDictionary.getIndexes().size() == 1) {
                comboBoxList.setSelectedIndex(choiceFieldDictionary.getIndexes().get(0));
            }

            comboBoxList.setOpaque(false);
            final Annotation fieldAnnotation = annotation;
            comboBoxList.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    ChoiceFieldDictionary.ChoiceOption option =
                            (ChoiceFieldDictionary.ChoiceOption) e.getItem();
                    choiceFieldDictionary.setFieldValue(option.getValue(),
                            fieldAnnotation.getPObjectReference());
                }
            });

            String fontName = "Helvetica";
            if (choiceFieldDictionary.getFontName() != null) fontName = choiceFieldDictionary.getFontName().toString();
            comboBoxList.setFont(new Font(fontName, Font.PLAIN, (int) choiceFieldDictionary.getSize()));
            comboBoxList.setFocusable(true);

            GridLayout grid = new GridLayout(1, 1, 0, 0);
            this.setLayout(grid);
            this.add(comboBoxList);

            revalidate();
        }
    }

    @Override
    public void focusGained(FocusEvent e) {
        super.focusGained(e);
        comboBoxList.requestFocus();
    }

    @Override
    public void validate() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();

        String fontName = "Helvetica";
        if (choiceFieldDictionary.getFontName() != null) {
            fontName = choiceFieldDictionary.getFontName().toString();
        }
        comboBoxList.setFont(new Font(fontName, Font.PLAIN,
                (int) (choiceFieldDictionary.getSize() * documentViewModel.getViewZoom())));
        super.validate();
    }

    public void propertyChange(PropertyChangeEvent evt) {

        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();

        if ("focusOwner".equals(prop) &&
                oldValue instanceof ScalableField) {
            ScalableField choiceField = (ScalableField) oldValue;
            if (choiceField.equals(comboBoxList)) {
                resetAppearanceShapes();
                getParent().validate();
                choiceField.setActive(false);
                getParent().validate();
                getParent().repaint();
            }
        } else if ("focusOwner".equals(prop) && newValue instanceof ScalableField) {
            ScalableField choiceField = (ScalableField) newValue;
            boolean isEditable = !(annotation.getFieldDictionary().isReadOnly());
            if (isEditable && choiceField.equals(comboBoxList)) {
                choiceField.setActive(true);
                getParent().validate();
                getParent().repaint();
            }
        } else if ("valueFieldReset".equals(prop)) {
            ChoiceFieldDictionary choiceFieldDictionary = annotation.getFieldDictionary();
            choiceFieldDictionary.setFieldValue(choiceFieldDictionary.getFieldValue(), annotation.getPObjectReference());
            resetAppearanceShapes();
        }
    }

    public boolean isActive() {
        return comboBoxList != null && comboBoxList.isActive();
    }

}
