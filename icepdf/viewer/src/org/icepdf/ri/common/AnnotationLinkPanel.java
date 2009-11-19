package org.icepdf.ri.common;

import org.icepdf.core.views.swing.AnnotationComponent;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.EtchedBorder;

public class AnnotationLinkPanel extends JPanel {
    private SwingController controller;
    private ResourceBundle messageBundle;

    private AnnotationComponent annotationComponent;

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
                                                  messageBundle.getString("viewer.utilityPane.link.appearanceTitle"),
                                                  TitledBorder.LEFT,
                                                  TitledBorder.DEFAULT_POSITION));

        linkTypeBox = new JComboBox(new String[] {"Invisible Rectangle"});
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.linkType")));
        appearancePane.add(linkTypeBox);

        linkStyleBox = new JComboBox(new String[] {"Solid"});
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.lineStyle")));
        appearancePane.add(linkStyleBox);

        linkThicknessBox = new JComboBox(new String[] {"Thin"});
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.lineThickness")));
        appearancePane.add(linkThicknessBox);

        linkHighlightBox = new JComboBox(new String[] {"Invert"});
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.highlightStyle")));
        appearancePane.add(linkHighlightBox);

        colorButton = new JButton();
        colorButton.setAction(new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                Color chosenColor =
                        JColorChooser.showDialog(colorButton,
                                                 messageBundle.getString("viewer.utilityPane.link.colorChooserTitle"),
                                                 colorButton.getBackground());
                if (chosenColor != null) {
                    colorButton.setBackground(chosenColor);

                    applyUIToAnnotation(annotationComponent);
                }
            }
        });
        colorButton.setBackground(Color.RED);
        appearancePane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.colorLabel")));
        appearancePane.add(colorButton);

        return appearancePane;
    }

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

    private String generatePageLabelText() {
        if ((controller != null) &&
            (controller.getDocument() != null)) {
            return "of " + controller.getDocument().getNumberOfPages();
        }

        return "of ?";
    }
}
