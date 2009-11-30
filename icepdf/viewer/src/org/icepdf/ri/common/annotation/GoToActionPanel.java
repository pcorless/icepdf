package org.icepdf.ri.common.annotation;

import org.icepdf.ri.common.SwingController;
import org.icepdf.core.views.swing.AnnotationComponentImpl;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * GoTo Action panel used for setting an GoTo Action type properties.  GoTo
 * actions store a PDF Destination data structor which can either be a named
 * destination or a vector of properties that specifies a page location.
 *
 * @since 4.0
 */
public class GoToActionPanel extends AnnotationPanelAdapter {

    private SwingController controller;
    private ResourceBundle messageBundle;

    public GoToActionPanel(SwingController controller) {
        super(new FlowLayout(FlowLayout.CENTER, 5, 5), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        /**
         * Take care of actions fields.
         */
        // todo action fields or move to another class.
//        org.icepdf.core.pobjects.actions.Action action =
//                newAnnotation.getAction().getAction();
//        if (action != null){
//            Annotation annot = newAnnotation.getAction();
//            System.out.println("annot" + annot);
//            System.out.println("action" + action);
//        } else{
//           Destination dest =
//                   ((LinkAnnotation)newAnnotation.getAction()).getDestination();
//            System.out.println("dest " + dest);
//        }
    }

    public void setAnnotationComponent(AnnotationComponentImpl annotaiton) {
        
    }

    /**
     * Method to create and customize the actions section of the panel
     *
     * @return completed panel
     */
    protected JPanel generateActionPane() {
        // Create and setup an Action panel
/**        JPanel pageNumberSubpane = new JPanel(new GridLayout(2, 3, 5, 5));
 pageNumberSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
 pageNumberSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.pageLabel")));
 pageField = new JTextField();
 pageNumberSubpane.add(pageField);
 pageLabel = new JLabel(generatePageLabelText());
 pageNumberSubpane.add(pageLabel);
 pageNumberSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.link.zoomLabel")));
 zoomBox = new JComboBox(new String[]{"Fit Page"});
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
 JRadioButton namedDestRadio =
 new JRadioButton(messageBundle.getString("viewer.utilityPane.link.useDestination"), false);
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
 */
        return null;
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
}
