package org.icepdf.ri.common;

import org.icepdf.core.views.swing.AnnotationComponent;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.EtchedBorder;

public class AnnotationLinkPanel extends JPanel {
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final int DEFAULT_LINK_STYLE_INDEX = 0; // Default to 'Solid' border
    private static final int DEFAULT_LINK_THICKNESS_INDEX = 2; // Default to 1.0f thick border
    private final ValueLabelItem[] DEFAULT_LINK_STYLE_LIST = new ValueLabelItem[] {
                                          new ValueLabelItem(BorderStyle.BORDER_STYLE_SOLID, "Solid"),
                                          new ValueLabelItem(BorderStyle.BORDER_STYLE_DASHED, "Dashed"),
                                          new ValueLabelItem(BorderStyle.BORDER_STYLE_BEVELED, "Beveled"),
                                          new ValueLabelItem(BorderStyle.BORDER_STYLE_INSET, "Inset"),
                                          new ValueLabelItem(BorderStyle.BORDER_STYLE_UNDERLINE, "Underline")};
    private final ValueLabelItem[] DEFAULT_LINK_THICKNESS_LIST = new ValueLabelItem[] {
                                          new ValueLabelItem(0.25f, "0.25"),
                                          new ValueLabelItem(0.5f, "0.50"),
                                          new ValueLabelItem(1.0f, "1.00"),
                                          new ValueLabelItem(1.5f, "1.50"),
                                          new ValueLabelItem(2.0f, "2.00"),
                                          new ValueLabelItem(3.0f, "3.00"),
                                          new ValueLabelItem(4.0f, "4.00"),
                                          new ValueLabelItem(5.0f, "5.00"),
                                          new ValueLabelItem(10.0f, "10.00")};

    private SwingController controller;
    private ResourceBundle messageBundle;

    private AnnotationComponent currentAnnotation;

    private JButton colorButton;
    private JComboBox linkTypeBox, linkStyleBox, linkThicknessBox, linkHighlightBox, zoomBox;
    private JTextField pageField;
    private JLabel pageLabel;

    public AnnotationLinkPanel(SwingController controller) {
        super(new FlowLayout(FlowLayout.CENTER, 0, 0), true);

        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        // Setup the basics of the panel
        setFocusable(true);
        setBorder(new EmptyBorder(10, 5, 1, 5));

        // Add the tabbed pane to the overall panel
        JPanel innerPane = new JPanel(new GridLayout(2, 1, 0, 0));
        innerPane.add(generateAppearancePane());
        innerPane.add(generateActionPane());
        add(innerPane);

        // Start the panel disabled until an annotation is clicked
        disablePanel();
    }

    public AnnotationComponent getCurrentAnnotation() {
        return currentAnnotation;
    }

    public void setCurrentAnnotation(
            AnnotationComponent currentAnnotation) {
        this.currentAnnotation = currentAnnotation;
    }

    /**
     * Method that should be called when a new AnnotationComponent is selected by the user
     * The associated object will be stored locally as currentAnnotation
     * Then all of it's properties will be applied to the UI pane
     *  For example if the border was red, the color of the background button will
     *  be changed to red
     *
     * @param newAnnotation to set and apply to this UI
     */
    public void setAndApplyAnnotationToUI(AnnotationComponent newAnnotation) {
        if ((newAnnotation == null) || (newAnnotation.getAnnotation() == null)) {
            return;
        }
        setCurrentAnnotation(newAnnotation);

        // Enable the UI for use with the new annotation values
        enablePanel();

        // For convenience grab the Annotation object wrapped by the component
        Annotation ann = currentAnnotation.getAnnotation();

        // Set the background color of the color button first
        colorButton.setBackground(ann.getBorderColor());

        // If we have a border style available we'll set the options from it
        if (ann.getBorderStyle() != null) {
            ValueLabelItem currentItem;

            // Get the border thickness from the object and set the associated combo
            //  box properly
            float thickness = ann.getBorderStyle().getStrokeWidth();
            for (int i = 0; i < DEFAULT_LINK_THICKNESS_LIST.length; i++) {
                currentItem = DEFAULT_LINK_THICKNESS_LIST[i];

                if (Float.parseFloat(currentItem.getValue().toString()) == thickness) {
                    linkThicknessBox.setSelectedIndex(i);
                    break;
                }
            }

            // Get the border style from the object and set the associated combo
            //  box properly
            String style = ann.getBorderStyle().getBorderStyle();
            for (int i = 0; i < DEFAULT_LINK_STYLE_LIST.length; i++) {
                currentItem = DEFAULT_LINK_STYLE_LIST[i];

                if (currentItem.getValue().toString().equals(style)) {
                    linkStyleBox.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /**
     * Method to apply all UI components to our currentAnnotation object
     */
    public void applyUIToAnnotation() {
        applyUIBorderColor();
        applyUIBorderStyle();
        applyUIBorderThickness();
    }

    /**
     * Method to update the currentAnnotation with the selected border color
     */
    public void applyUIBorderColor() {
        if (currentAnnotation != null) {
            currentAnnotation.getAnnotation().setBorderColor(colorButton.getBackground());
        }
    }

    /**
     * Method to update the currentAnnotation border style with the selected style
     */
    public void applyUIBorderThickness() {
        if ((currentAnnotation != null) &&
            (currentAnnotation.getAnnotation() != null) &&
            (currentAnnotation.getAnnotation().getBorderStyle() != null)) {
            if ((linkThicknessBox != null) &&
                    (linkThicknessBox.getSelectedItem() != null)) {
                currentAnnotation.getAnnotation().getBorderStyle().setStrokeWidth(
                        Float.parseFloat(((ValueLabelItem)linkThicknessBox.getSelectedItem()).getValue().toString()));
            }
        }
    }

    /**
     * Method to update the currentAnnotation border thickness with the selected width
     */
    public void applyUIBorderStyle() {
        if ((currentAnnotation != null) &&
            (currentAnnotation.getAnnotation() != null) &&
            (currentAnnotation.getAnnotation().getBorderStyle() != null)) {
            if ((linkStyleBox != null) &&
                    (linkStyleBox.getSelectedItem() != null)) {
                currentAnnotation.getAnnotation().getBorderStyle().setBorderStyle(
                        ((ValueLabelItem)linkStyleBox.getSelectedItem()).getValue().toString());
            }
        }
    }

    public void enablePanel() {
        togglePaneStatus(true);
    }

    public void disablePanel() {
        togglePaneStatus(false);
    }

    /**
     * Method to enable or disable each component in this panel
     *
     * @param enabled to apply to each element
     */
    protected void togglePaneStatus(boolean enabled) {
        safeEnable(colorButton, enabled);
        safeEnable(linkTypeBox, enabled);
        safeEnable(linkStyleBox, enabled);
        safeEnable(linkThicknessBox, enabled);
        safeEnable(linkHighlightBox, enabled);
        safeEnable(zoomBox, enabled);
        safeEnable(pageField, enabled);
    }

    /**
     * Convenience method to ensure a component is safe to toggle the enabled state on
     *
     * @param comp to toggle
     * @param enabled the status to use
     * @return true on success
     */
    protected boolean safeEnable(JComponent comp, boolean enabled) {
        if (comp != null) {
            comp.setEnabled(enabled);

            return true;
        }

        return false;
    }

    /**
     * Method to create and customize the appearance section of the panel
     *
     * @return completed panel
     */
    protected JPanel generateAppearancePane() {
        // Create and setup an Appearance panel
        JPanel appearancePane = new JPanel(new GridLayout(4, 2, 5, 5));
        appearancePane.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                                                  messageBundle.getString("viewer.utilityPane.link.appearanceTitle"),
                                                  TitledBorder.LEFT,
                                                  TitledBorder.DEFAULT_POSITION));

        linkTypeBox = new JComboBox(new String[] {"Invisible Rectangle"});
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.linkType")));
        appearancePane.add(linkTypeBox);

        linkStyleBox = new JComboBox(DEFAULT_LINK_STYLE_LIST);
        linkStyleBox.setSelectedIndex(DEFAULT_LINK_STYLE_INDEX);
        linkStyleBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    applyUIBorderStyle();
                }
            }
        });
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.linkStyle")));
        appearancePane.add(linkStyleBox);

        linkThicknessBox = new JComboBox(DEFAULT_LINK_THICKNESS_LIST);
        linkThicknessBox.setSelectedIndex(DEFAULT_LINK_THICKNESS_INDEX);
        linkThicknessBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    applyUIBorderThickness();
                }
            }
        });
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.linkThickness")));
        appearancePane.add(linkThicknessBox);

        colorButton = new JButton();
        colorButton.setAction(new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                Color chosenColor =
                        JColorChooser.showDialog(colorButton,
                                                 messageBundle.getString("viewer.utilityPane.link.colorChooserTitle"),
                                                 colorButton.getBackground());
                if (chosenColor != null) {
                    colorButton.setBackground(chosenColor);

                    applyUIBorderColor();
                }
            }
        });
        colorButton.setBackground(DEFAULT_BORDER_COLOR);
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.colorLabel")));
        appearancePane.add(colorButton);

        return appearancePane;
    }

    /**
     * Method to create and customize the actions section of the panel
     *
     * @return completed panel
     */
    protected JPanel generateActionPane() {
        // Create and setup an Action panel
        JPanel pageNumberSubpane = new JPanel(new GridLayout(2, 3, 5, 5));
        pageNumberSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
        pageNumberSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.pageLabel")));
        pageField = new JTextField();
        pageNumberSubpane.add(pageField);
        pageLabel = new JLabel(generatePageLabelText());
        pageNumberSubpane.add(pageLabel);
        pageNumberSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.zoomLabel")));
        zoomBox = new JComboBox(new String[] {"Fit Page"});
        pageNumberSubpane.add(zoomBox);

        JPanel pageNumberPane = new JPanel(new BorderLayout(5, 5));
        JRadioButton pageNumberRadio = new JRadioButton(messageBundle.getString("viewer.utilityPane.link.usePage"), true);
        pageNumberPane.add(pageNumberRadio, BorderLayout.NORTH);
        pageNumberPane.add(pageNumberSubpane, BorderLayout.CENTER);

        JPanel namedDestSubpane = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        namedDestSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
        namedDestSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.nameLabel")));
        namedDestSubpane.add(new JLabel("X"));
        namedDestSubpane.add(new JButton(messageBundle.getString("viewer.utilityPane.link.browse")));

        JPanel namedDestPane = new JPanel(new BorderLayout(5, 5));
        JRadioButton namedDestRadio = new JRadioButton(messageBundle.getString("viewer.utilityPane.link.useDestination"), false);
        namedDestPane.add(namedDestRadio, BorderLayout.NORTH);
        namedDestPane.add(namedDestSubpane, BorderLayout.CENTER);

        ButtonGroup actionButtonGroup = new ButtonGroup();
        actionButtonGroup.add(pageNumberRadio);
        actionButtonGroup.add(namedDestRadio);

        JPanel actionPane = new JPanel(new GridLayout(2, 1, 2, 2));
        actionPane.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                                              messageBundle.getString("viewer.utilityPane.link.actionsTitle"),
                                              TitledBorder.LEFT,
                                              TitledBorder.DEFAULT_POSITION));
        actionPane.add(pageNumberPane);
        actionPane.add(namedDestPane);

        return actionPane;
    }

    /**
     * Method to update the page label text based on the current page count
     *
     * @return the new text to use
     */
    private String generatePageLabelText() {
        if ((controller != null) &&
            (controller.getDocument() != null)) {
            return "of " + controller.getDocument().getNumberOfPages();
        }

        return "of ?";
    }

    /**
     * Class to associate with a JComboBox
     * Used to allow us to display different text to the user than we set in the backend
     */
    private class ValueLabelItem {
        private Object value;
        private String label;

        public ValueLabelItem(Object value, String label) {
            this.value = value;
            this.label = label;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }
}
