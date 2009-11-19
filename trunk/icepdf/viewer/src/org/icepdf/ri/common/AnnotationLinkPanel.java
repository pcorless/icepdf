package org.icepdf.ri.common;

import org.icepdf.core.views.swing.AnnotationComponent;

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.EtchedBorder;

public class AnnotationLinkPanel extends JPanel {
    private AnnotationComponent annotationComponent;

    private JButton colorButton;
    private JComboBox linkTypeBox, linkStyleBox, linkThicknessBox, linkHighlightBox, zoomBox;
    private JTextField pageField;
    private JLabel pageLabel;

    public AnnotationLinkPanel() {
        super(new FlowLayout(FlowLayout.CENTER, 0, 0));

        // Setup the basics of the panel
        setBorder(new EmptyBorder(10, 5, 1, 5));

        // Add the tabbed pane to the overall panel
        JPanel innerPane = new JPanel(new GridLayout(2, 1, 0, 0));
        innerPane.add(generateAppearancePane());
        innerPane.add(generateActionPane());
        add(innerPane);

        // Start the panel disabled until an annotation is clicked
        disablePanel();
    }

    public AnnotationComponent getAnnotationComponent() {
        return annotationComponent;
    }

    public void setAnnotationComponent(
            AnnotationComponent annotationComponent) {
        this.annotationComponent = annotationComponent;
    }

    public void applyAnnotationToUI(AnnotationComponent newAnnotation) {
        if ((newAnnotation == null) || (newAnnotation.getAnnotation() == null)) {
            return;
        }

        setAnnotationComponent(newAnnotation);

        enablePanel();

        colorButton.setBackground(annotationComponent.getAnnotation().getBorderColor());
    }

    public void applyUIToAnnotation(AnnotationComponent newAnnotation) {
        if ((newAnnotation == null) || (newAnnotation.getAnnotation() == null)) {
            return;
        }

        setAnnotationComponent(newAnnotation);

        annotationComponent.getAnnotation().setBorderColor(colorButton.getBackground());
    }

    public void enablePanel() {
        togglePaneStatus(true);
    }

    public void disablePanel() {
        togglePaneStatus(false);
    }

    protected void togglePaneStatus(boolean enabled) {
        safeEnable(colorButton, enabled);
        safeEnable(linkTypeBox, enabled);
        safeEnable(linkStyleBox, enabled);
        safeEnable(linkThicknessBox, enabled);
        safeEnable(linkHighlightBox, enabled);
        safeEnable(zoomBox, enabled);
        safeEnable(pageField, enabled);
    }

    protected boolean safeEnable(JComponent comp, boolean enabled) {
        if (comp != null) {
            comp.setEnabled(enabled);

            return true;
        }

        return false;
    }

    protected JPanel generateAppearancePane() {
        // Create and setup an Appearance panel
        JPanel appearancePane = new JPanel(new GridLayout(5, 2, 5, 5));
        appearancePane.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                                                  "Appearance",
                                                  TitledBorder.LEFT,
                                                  TitledBorder.DEFAULT_POSITION));

        linkTypeBox = new JComboBox(new String[] {"Invisible Rectangle"});
        appearancePane.add(new JLabel("Link Type:"));
        appearancePane.add(linkTypeBox);

        linkStyleBox = new JComboBox(new String[] {"Solid"});
        appearancePane.add(new JLabel("Line Style:"));
        appearancePane.add(linkStyleBox);

        linkThicknessBox = new JComboBox(new String[] {"Thin"});
        appearancePane.add(new JLabel("Link Thickness:"));
        appearancePane.add(linkThicknessBox);

        linkHighlightBox = new JComboBox(new String[] {"Invert"});
        appearancePane.add(new JLabel("Highlight Style:"));
        appearancePane.add(linkHighlightBox);

        colorButton = new JButton();
        colorButton.setAction(new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                Color chosenColor =
                        JColorChooser.showDialog(colorButton,
                                                 "Annotation Link - Color",
                                                 colorButton.getBackground());
                if (chosenColor != null) {
                    colorButton.setBackground(chosenColor);

                    applyUIToAnnotation(annotationComponent);
                }
            }
        });
        colorButton.setBackground(Color.RED);
        appearancePane.add(new JLabel("Color:"));
        appearancePane.add(colorButton);

        return appearancePane;
    }

    protected JPanel generateActionPane() {
        // Create and setup an Action panel
        JPanel pageNumberSubpane = new JPanel(new GridLayout(2, 3, 5, 5));
        pageNumberSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
        pageNumberSubpane.add(new JLabel("Page:"));
        pageField = new JTextField();
        pageNumberSubpane.add(pageField);
        pageLabel = new JLabel(generatePageLabelText(-1));
        pageNumberSubpane.add(pageLabel);
        pageNumberSubpane.add(new JLabel("Zoom:"));
        zoomBox = new JComboBox(new String[] {"Fit Page"});
        pageNumberSubpane.add(zoomBox);

        JPanel pageNumberPane = new JPanel(new BorderLayout(5, 5));
        JRadioButton pageNumberRadio = new JRadioButton("Use Page Number", true);
        pageNumberPane.add(pageNumberRadio, BorderLayout.NORTH);
        pageNumberPane.add(pageNumberSubpane, BorderLayout.CENTER);

        JPanel namedDestSubpane = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        namedDestSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
        namedDestSubpane.add(new JLabel("Name:"));
        namedDestSubpane.add(new JLabel("X"));
        namedDestSubpane.add(new JButton("Browse..."));

        JPanel namedDestPane = new JPanel(new BorderLayout(5, 5));
        JRadioButton namedDestRadio = new JRadioButton("Use Named Destination", false);
        namedDestPane.add(namedDestRadio, BorderLayout.NORTH);
        namedDestPane.add(namedDestSubpane, BorderLayout.CENTER);

        ButtonGroup actionButtonGroup = new ButtonGroup();
        actionButtonGroup.add(pageNumberRadio);
        actionButtonGroup.add(namedDestRadio);

        JPanel actionPane = new JPanel(new GridLayout(2, 1, 2, 2));
        actionPane.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                                              "Actions",
                                              TitledBorder.LEFT,
                                              TitledBorder.DEFAULT_POSITION));
        actionPane.add(pageNumberPane);
        actionPane.add(namedDestPane);

        return actionPane;
    }

    private String generatePageLabelText(int totalPage) {
        if (totalPage > -1) {
            return "of " + totalPage;
        }

        return "of ?";
    }
}
