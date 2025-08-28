package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.*;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.acroform.TextFieldDictionary.TextFieldType.TEXT_AREA;
import static org.icepdf.core.pobjects.acroform.TextFieldDictionary.TextFieldType.TEXT_INPUT;
import static org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary.Quadding.CENTERED;
import static org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary.Quadding.RIGHT_JUSTIFIED;
import static org.icepdf.core.util.SystemProperties.INTERACTIVE_ANNOTATIONS;

public class TextWidgetComponent extends AbstractAnnotationComponent<TextWidgetAnnotation>
        implements PropertyChangeListener {

    private static final Logger logger =
            Logger.getLogger(TextWidgetComponent.class.toString());

    private boolean textContentChange;
    private JTextComponent textWidgetComponent;

    public TextWidgetComponent(TextWidgetAnnotation annotation,
                               final DocumentViewController documentViewController,
                               final AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        if (!(INTERACTIVE_ANNOTATIONS || annotation.allowScreenOrPrintRenderingOrInteraction())) {
            return;
        }
        this.setFocusable(true);
        isRollover = false;
        isShowInvisibleBorder = true;
        isResizable = true;
        isMovable = true;

        if (!annotation.allowScreenOrPrintRenderingOrInteraction()) {
            isEditable = false;
            isRollover = false;
            isMovable = false;
            isResizable = false;
            isShowInvisibleBorder = false;
        }

        TextFieldDictionary textFieldDictionary = annotation.getFieldDictionary();
        TextFieldDictionary.TextFieldType textFieldType = textFieldDictionary.getTextFieldType();

        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (textFieldType == TEXT_INPUT) {
            textWidgetComponent = new ScalableTextField(documentViewModel);
            VariableTextFieldDictionary.Quadding quadding = textFieldDictionary.getQuadding();
            if (quadding == CENTERED) {
                ((ScalableTextField) textWidgetComponent).setHorizontalAlignment(JTextField.CENTER);
            } else if (quadding == RIGHT_JUSTIFIED) {
                ((ScalableTextField) textWidgetComponent).setHorizontalAlignment(JTextField.RIGHT);
            }
            SpellCheckLoader.addSpellChecker(textWidgetComponent);
        } else if (textFieldType == TEXT_AREA) {
            ScalableTextArea textArea = new ScalableTextArea(documentViewModel);
            textWidgetComponent = textArea;
            textArea.setLineWrap(false);
            SpellCheckLoader.addSpellChecker(textWidgetComponent);
        } else if (textFieldType == TextFieldDictionary.TextFieldType.TEXT_PASSWORD) {
            textWidgetComponent = new ScalablePasswordField(documentViewModel);
        }

        if (textWidgetComponent == null) {
            logger.warning("Could not valid text widget component of type: " + textFieldType);
            return;
        }

        if ((annotation.getFieldDictionary()).getMaxLength() > 0) {
            textWidgetComponent.setDocument(new JTextFieldLimit((annotation.getFieldDictionary()).getMaxLength()));
        }
        textWidgetComponent.setFont(new Font("Helvetica", Font.PLAIN, 10));
        textWidgetComponent.setMargin(new Insets(0, 0, 0, 0));
        textWidgetComponent.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                textContentChange = true;
            }

            public void removeUpdate(DocumentEvent e) {
                textContentChange = true;
            }

            public void changedUpdate(DocumentEvent e) {
                textContentChange = true;
            }
        });
        textWidgetComponent.setEditable(false);
        textWidgetComponent.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        GridLayout grid = new GridLayout(1, 1, 0, 0);
        this.setLayout(grid);
        this.add(textWidgetComponent);

        if (INTERACTIVE_ANNOTATIONS && annotation.allowScreenOrPrintRenderingOrInteraction()) {
            textWidgetComponent.setFocusable(true);
            textWidgetComponent.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    super.focusGained(e);
                    if (textWidgetComponent.getText().length() > 0) {
                        textWidgetComponent.setSelectionStart(0);
                        textWidgetComponent.setSelectionEnd(textWidgetComponent.getText().length());
                    }
                }
            });
        } else {
            textWidgetComponent.setFocusable(false);
        }

        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        textWidgetComponent.setOpaque(false);

        assignTextValue();

        textWidgetComponent.addKeyListener(new TabKeyListener());
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener(this);

        this.annotation.addPropertyChangeListener(this);

        revalidate();
    }

    @Override
    public void focusGained(FocusEvent e) {
        super.focusGained(e);
        documentViewController.assignSelectedAnnotation(this);
        textWidgetComponent.requestFocus();
    }

    private void assignTextValue() {
        String contents = (String) annotation.getFieldDictionary().getFieldValue();
        if ((contents == null || contents.equals("")) &&
                annotation.getFieldDictionary().getParent() != null) {
            contents = (String) annotation.getFieldDictionary().getParent().getFieldValue();
        }
        if (contents != null && textWidgetComponent != null) {
            contents = contents.replace('\r', '\n');
            textWidgetComponent.setText(contents);
        } else {
            Appearance appearance = annotation.getAppearances().get(
                    TextWidgetAnnotation.APPEARANCE_STREAM_NORMAL_KEY);
            AppearanceState appearanceState = appearance.getSelectedAppearanceState();
            if (appearanceState.getShapes() != null &&
                    appearanceState.getShapes().getPageText() != null) {
                textWidgetComponent.setText(pageLinesToString(appearanceState.getShapes().getPageText().getPageLines()));
            }
        }
    }

    public void setAppearanceStream() {
        assignTextValue();
        textWidgetComponent.setOpaque(false);
        annotation.getFieldDictionary().setFieldValue(textWidgetComponent.getText(), annotation.getPObjectReference());
        textWidgetComponent.revalidate();
    }

    @Override
    public void dispose() {
        super.dispose();
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.removePropertyChangeListener(this);
    }

    public void propertyChange(PropertyChangeEvent evt) {

        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();

        boolean isEditable = !(annotation.getFieldDictionary().isReadOnly());

        if ("valueFieldReset".equals(prop)) {
            assignTextValue();
            resetAppearanceShapes();
        } else if ("focusOwner".equals(prop) &&
                oldValue instanceof ScalableField) {
            ScalableField textField = (ScalableField) oldValue;
            if (textField.equals(textWidgetComponent)) {
                textField.setEditable(false);
                if (textContentChange && isEditable) {
                    textContentChange = false;
                    TextFieldDictionary textFieldDictionary = annotation.getFieldDictionary();
                    textFieldDictionary.setFieldValue(textWidgetComponent.getText(), annotation.getPObjectReference());
                    resetAppearanceShapes();
                }
                textField.setActive(false);
            }
        } else if ("focusOwner".equals(prop) &&
                newValue instanceof ScalableField) {
            ScalableField textField = (ScalableField) newValue;
            if (textField.equals(textWidgetComponent)) {
                if (isEditable) {
                    textField.setEditable(true);
                    textField.setActive(true);
                }
            }
        }
        getParent().validate();
        getParent().repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        isShowInvisibleBorder = false;
    }

    @Override
    public void resetAppearanceShapes() {
        setAppearanceStream();
        annotation.resetAppearanceStream(getToPageSpaceTransform());
    }

    @Override
    public void validate() {
        if (textWidgetComponent != null) {
            VariableTextFieldDictionary variableText = annotation.getFieldDictionary();
            if (annotation.getFieldDictionary().getDefaultAppearance() == null) {
                Appearance appearance = annotation.getAppearances().get(annotation.getCurrentAppearance());
                AppearanceState appearanceState = appearance.getSelectedAppearanceState();
                Resources resource = appearanceState.getResources();
                String currentContentStream = appearanceState.getOriginalContentStream();
                annotation.generateDefaultAppearance(currentContentStream, resource, variableText);
            }
            textWidgetComponent.setFont(
                    new Font(variableText.getFontName().toString(),
                            Font.PLAIN,
                            (int) (variableText.getSize() * documentViewController.getDocumentViewModel().getViewZoom())));
        }
        super.validate();
    }

    public boolean isActive() {
        return textWidgetComponent != null && ((ScalableField) textWidgetComponent).isActive();
    }

    public void setActive(boolean active) {
        if (textWidgetComponent != null) {
            ((ScalableField) textWidgetComponent).setActive(active);
        }
    }

    public void setEditable(boolean editable) {
        if (textWidgetComponent != null) {
            (textWidgetComponent).setEditable(editable);
        }
    }

    public String toString() {
        if (annotation.getEntries() != null) {
            return annotation.getEntries().toString();
        }
        return super.toString();
    }

    private String pageLinesToString(ArrayList<LineText> lineText) {
        if (lineText != null) {
            StringBuilder lines = new StringBuilder();
            for (LineText line : lineText) {
                for (WordText word : line.getWords()) {
                    lines.append(word.toString()).append(" ");
                }
            }
            return lines.toString();
        } else {
            return "";
        }
    }

    private static class TabKeyListener extends KeyAdapter {
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                e.consume();
                if (e.isShiftDown()) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent();
                } else {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
                }
            }
        }
    }

    private static class JTextFieldLimit extends PlainDocument {
        private final int limit;

        JTextFieldLimit(int limit) {
            super();
            this.limit = limit;
        }

        public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
            if (str == null)
                return;

            if ((getLength() + str.length()) <= limit) {
                super.insertString(offset, str, attr);
            }
            if (getLength() == limit) {
                KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                focusManager.focusNextComponent();
            }
        }
    }
}
