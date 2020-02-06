package org.icepdf.ri.common.preferences;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ResourceBundle;

public class ExImportPreferencesPanel extends JPanel {

    private final GridBagConstraints constraints;

    public ExImportPreferencesPanel(Controller controller, ViewerPropertiesManager propertiesManager, ResourceBundle messageBundle, Dialog parent) {
        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        final JButton exportButton = new JButton(messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.export.button.label"));
        final JButton importButton = new JButton(messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.import.button.label"));

        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        panel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(5, 5, 5, 5);

        exportButton.addActionListener(actionEvent -> {
            final FileDialog chooser = new FileDialog(parent);
            chooser.setMultipleMode(false);
            chooser.setMode(FileDialog.SAVE);
            chooser.setVisible(true);
            final String dir = chooser.getDirectory();
            final String file = chooser.getFile();
            if (dir != null && file != null) {
                if (ViewerPropertiesManager.exportProperties(new File(dir + file))) {
                    JOptionPane.showMessageDialog(this, messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.export.success.label"), messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.export.success.title"), JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.export.fail.label"), messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.export.fail.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        importButton.addActionListener(actionEvent -> {
            final FileDialog chooser = new FileDialog(parent);
            chooser.setMultipleMode(false);
            chooser.setMode(FileDialog.LOAD);
            chooser.setVisible(true);
            final String dir = chooser.getDirectory();
            final String file = chooser.getFile();
            if (dir != null && file != null) {
                if (ViewerPropertiesManager.importProperties(new File(dir + file))) {
                    JOptionPane.showMessageDialog(this, messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.import.success.label"), messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.import.success.title"), JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.import.fail.label"), messageBundle.getString("viewer.dialog.viewerPreferences.section.eximport.import.fail.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        addGB(panel, exportButton, 0, 0, 1, 1);
        addGB(panel, importButton, 1, 0, 1, 1);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, panel, 0, 0, 1, 1);
        constraints.weighty = 1.0;
        addGB(this, new JLabel(" "), 0, 1, 1, 1);


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
