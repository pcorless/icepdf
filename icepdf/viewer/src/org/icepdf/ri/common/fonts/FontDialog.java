package org.icepdf.ri.common.fonts;

import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ResourceBundle;


/**
 * This class is a reference implementation for displaying a PDF file's
 * font information.   The dialog will start a worker thread that will read all the document's font objects and
 * build a tree view of the all the fonts.  This font view is primarily for debug purposes to make it easier to track
 * font substitution results.  The dialog also provides an easy way to refresh the
 * "\.icesoft\icepdf-viewer\pdfviewerfontcache.properties' with out manually deleting the file and restarted the viewer.
 * <p>
 * {@link org.icepdf.ri.common.fonts.FindFontsTask}
 *
 * @since 6.1.3
 */
@SuppressWarnings("serial")
public class FontDialog extends EscapeJDialog implements ActionListener, WindowListener {

    // pointer to document which will be searched
    private SwingController controller;

    // font look up start on creation, but ok button will kill the the process and close the dialog.
    private JButton okButton;
    // clear and rescan system for fonts and rewrite file.
    private JButton resetFontCacheButton;
    // panel that does the font lookup.
    private FontHandlerPanel fontHandlerPanel;

    // message bundle for internationalization
    private ResourceBundle messageBundle;

    // layouts constraint
    private GridBagConstraints constraints;

    /**
     * Create a new instance of SearchPanel.
     *
     * @param controller root SwingController
     */
    public FontDialog(Frame frame, SwingController controller, boolean isModal) {
        super(frame, isModal);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        fontHandlerPanel = new FontHandlerPanel(controller);
        // kicks off the swing worker to do the font lookup off the awt thread.
        fontHandlerPanel.setDocument(controller.getDocument());
        setGui();
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        setTitle(messageBundle.getString("viewer.dialog.fonts.title"));
        setResizable(true);

        addWindowListener(this);

        /**
         * Build search GUI
         */
        // content Panel
        JPanel fontPropertiesPanel = new JPanel(new GridBagLayout());
        fontPropertiesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.fonts.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        this.setLayout(new BorderLayout(15, 15));
        this.add(fontPropertiesPanel, BorderLayout.CENTER);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 15, 1, 15);

        // add te font tree panel
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(10, 15, 10, 15);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(fontPropertiesPanel, fontHandlerPanel, 0, 1, 2, 1);

        resetFontCacheButton = new JButton(messageBundle.getString("viewer.dialog.fonts.resetCache.label"));
        resetFontCacheButton.setToolTipText(messageBundle.getString("viewer.dialog.fonts.resetCache.tip"));
        resetFontCacheButton.addActionListener(this);
        constraints.insets = new Insets(2, 10, 2, 10);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        addGB(fontPropertiesPanel, resetFontCacheButton, 0, 2, 1, 1);

        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.addActionListener(this);
        constraints.insets = new Insets(2, 10, 2, 10);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        addGB(fontPropertiesPanel, okButton, 1, 3, 1, 1);

        setSize(640, 480);
        setLocationRelativeTo(getOwner());
    }

    /**
     * Insure the font search process is killed when the dialog is closed via the 'esc' key.
     */
    @Override
    public void dispose() {
        super.dispose();
        closeWindowOperations();
    }

    /**
     * Two main actions are handle here, ok/close and reset the font cache.
     *
     * @param event awt action event.
     */
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == okButton) {
            // clean up the timer and worker thread.
            closeWindowOperations();
            dispose();
        } else if (event.getSource() == resetFontCacheButton) {
            // reset the font properties cache.
            resetFontCacheButton.setEnabled(false);
            SwingWorker worker = new SwingWorker() {
                public Object construct() {
                    PropertiesManager properties = new PropertiesManager(
                            System.getProperties(),
                            ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE));
                    FontPropertiesManager fontPropertiesManager = new FontPropertiesManager(properties,
                            System.getProperties(), messageBundle);
                    fontPropertiesManager.clearProperties();
                    fontPropertiesManager.readDefaulFontPaths(null);
                    fontPropertiesManager.saveProperties();
                    resetFontCacheButton.setEnabled(true);

                    Runnable doSwingWork = new Runnable() {
                        public void run() {
                            resetFontCacheButton.setEnabled(true);
                        }
                    };
                    SwingUtilities.invokeLater(doSwingWork);
                    return null;
                }
            };
            worker.setThreadPriority(Thread.MIN_PRIORITY);
            worker.start();
        }
    }

    protected void closeWindowOperations() {
        // clean up the timer and worker thread.
        fontHandlerPanel.dispose();
        setVisible(false);
    }


    /**
     * Gridbag constructor helper
     *
     * @param panel     parent adding component too.
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowspane of field
     * @param colSpan   colspane of field.
     */
    private void addGB(JPanel panel, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        panel.add(component, constraints);
    }

    public void windowOpened(WindowEvent e) {
    }

    public void windowClosing(WindowEvent e) {
        closeWindowOperations();
    }

    public void windowClosed(WindowEvent e) {
        closeWindowOperations();
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }
}
