/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.utility.annotation.properties.FreeTextAnnotationPanel;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;
import org.icepdf.ri.common.widgets.DragDropColorList;
import org.icepdf.ri.util.Resources;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class AnnotationSummaryPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(AnnotationSummaryPanel.class.getName());
    private static final boolean USE_JFILECHOOSER;

    static {
        USE_JFILECHOOSER = Defs.booleanProperty("org.icepdf.ri.viewer.jfilechooser", false);
    }

    private final ResourceBundle messageBundle;
    private final SummaryController controller;

    protected final GridBagConstraints constraints;
    protected JPanel annotationsPanel;

    // font configuration
    private JComboBox<ValueLabelItem> fontNameBox;
    private JComboBox<ValueLabelItem> fontSizeBox;
    protected JPanel statusToolbarPanel;

    private static final int DEFAULT_FONT_SIZE_INDEX = 5;

    private static final List<ValueLabelItem> AVAILABLE_FONTS = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()).filter(f -> new Font(f, Font.PLAIN, 12).canDisplayUpTo("&èéàôö'%+<>ß") == -1)
            .sorted(Comparator.naturalOrder()).map(s -> new ValueLabelItem(s, s)).collect(Collectors.toList());


    public AnnotationSummaryPanel(final Frame frame, final Controller controller) {
        setLayout(new BorderLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setFocusable(true);
        constraints = new AbsoluteGridBagConstraints();
        this.messageBundle = controller.getMessageBundle();
        this.controller = createController(frame, controller);

        buildStatusToolBarPanel();

        // add key listeners for ctr, 0, -, = : reset, decrease and increase font size.
        addFontSizeBindings();
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                fontSizeBox.setSelectedIndex(Math.max(0, Math.min(fontSizeBox.getSelectedIndex() - e.getWheelRotation(),
                        fontSizeBox.getItemCount() - 1)));
            }
        });
    }

    protected SummaryController createController(final Frame frame, final Controller controller) {
        return new SummaryController(frame, controller, this);
    }

    public SummaryController getController() {
        return controller;
    }

    JPanel getAnnotationsPanel() {
        return annotationsPanel;
    }

    private void addFontSizeBindings() {
        final InputMap inputMap = getInputMap(WHEN_FOCUSED);
        final ActionMap actionMap = getActionMap();

        /// ctrl-- to increase font size.
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-increase");
        actionMap.put("font-size-increase", new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (fontSizeBox.getSelectedIndex() + 1 < fontSizeBox.getItemCount()) {
                    fontSizeBox.setSelectedIndex(fontSizeBox.getSelectedIndex() + 1);
                }
            }
        });

        // ctrl-0 to default font size.
        key = KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-default");
        actionMap.put("font-size-default", new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                fontSizeBox.setSelectedIndex(DEFAULT_FONT_SIZE_INDEX);
            }
        });

        // ctrl-- to decrease font size.
        key = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-decrease");
        actionMap.put("font-size-decrease", new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (fontSizeBox.getSelectedIndex() - 1 >= 0) {
                    fontSizeBox.setSelectedIndex(fontSizeBox.getSelectedIndex() - 1);
                }
            }
        });
    }

    public int getFontSize() {
        return (int) ((ValueLabelItem) fontSizeBox.getSelectedItem()).getValue();
    }

    public JComboBox<ValueLabelItem> getFontNameBox() {
        return fontNameBox;
    }

    public JComboBox<ValueLabelItem> getFontSizeBox() {
        return fontSizeBox;
    }

    protected void buildStatusToolBarPanel() {
        final ViewerPropertiesManager propertiesManager = controller.getController().getPropertiesManager();
        final ValueLabelItem[] sizes = FreeTextAnnotationPanel.generateFontSizeNameList(messageBundle);
        final List<Integer> sizeValues = Arrays.stream(sizes).map(vli -> (int) vli.getValue()).collect(Collectors.toList());
        final int labelSize = new JLabel().getFont().getSize();
        int idx = Collections.binarySearch(sizeValues, labelSize);
        if (idx < 0) {
            idx = Math.min(sizeValues.size() - 1, -(idx + 1));
        }
        fontSizeBox = new JComboBox<>(sizes);
        fontNameBox = new JComboBox<>(AVAILABLE_FONTS.toArray(new ValueLabelItem[0]));
        final int size = propertiesManager.checkAndStoreIntProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE, sizeValues.get(idx));
        fontNameBox.setRenderer(new FontFamilyBoxRenderer(new JLabel().getFont().getSize()));
        SummaryController.applySelectedValue(fontSizeBox, size);
        SummaryController.applySelectedValue(fontNameBox, propertiesManager.checkAndStoreStringProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_NAME, new JLabel().getFont().getFamily()));

        statusToolbarPanel = new JPanel(new GridBagLayout());
        statusToolbarPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(statusToolbarPanel, fontNameBox, 0, 0, 1, 1);
        addGB(statusToolbarPanel, new JLabel(messageBundle.getString("viewer.annotationSummary.fontSize.label")),
                1, 0, 1, 1);
        addGB(statusToolbarPanel, fontSizeBox, 2, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        final JButton headerButton = new JButton(messageBundle.getString("viewer.annotationSummary.header.button.hide"));
        headerButton.addActionListener(new ActionListener() {
            private boolean hidden = false;

            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                if (hidden) {
                    controller.showAllHeaders();
                } else {
                    controller.hideAllHeaders();
                }
                hidden = !hidden;
                final String basekey = "viewer.annotationSummary.header.button.";
                final String key;
                key = hidden ? basekey + "show" : basekey + "hide";
                headerButton.setText(messageBundle.getString(key));
            }
        });
        addGB(statusToolbarPanel, headerButton, 4, 0, 1, 1);
        final JButton compactButton = new JButton(messageBundle.getString("viewer.annotationSummary.compact.button"));
        compactButton.addActionListener(actionEvent -> controller.compactPanel());
        addGB(statusToolbarPanel, compactButton, 5, 0, 1, 1);
        final JButton saveButton = new JButton(messageBundle.getString("viewer.annotationSummary.save.button"));
        saveButton.addActionListener(this::savePerformed);
        final JButton exportButton = new JButton(messageBundle.getString("viewer.annotationSummary.export.button"));
        exportButton.addActionListener(this::exportPerformed);
        final JButton importButton = new JButton(messageBundle.getString("viewer.annotationSummary.import.button"));
        importButton.addActionListener(this::importPerformed);
        final List<JButton> fileButtons = Arrays.asList(saveButton, exportButton, importButton);
        fileButtons.forEach(b -> b.setVisible(controller.canSave()));
        addGB(statusToolbarPanel, saveButton, 6, 0, 1, 1);
        addGB(statusToolbarPanel, exportButton, 7, 0, 1, 1);
        addGB(statusToolbarPanel, importButton, 8, 0, 1, 1);
        constraints.weightx = 1;
        addGB(statusToolbarPanel, new JLabel(), 3, 0, 1, 1);
        controller.addFontListeners();
    }


    public void refreshPanelLayout() {
        removeAll();

        annotationsPanel = new JPanel(new AbsoluteGridBagLayout());
        annotationsPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        controller.addPotentialLinkMouseListener();
        add(annotationsPanel, BorderLayout.CENTER);
        add(statusToolbarPanel, BorderLayout.SOUTH);

        final ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        final int numberOfPanels = colorLabels.isEmpty() ? 1 : colorLabels.size();
        constraints.weightx = 1.0 / (float) numberOfPanels;
        constraints.weighty = 1.0f;
        constraints.insets = new Insets(0, 5, 0, 0);
        constraints.fill = GridBagConstraints.BOTH;
        int k = 0;
        for (final ColorLabelPanel annotationColumnPanel : controller.getAnnotationNamedColorPanels()) {
            if (annotationColumnPanel.getNumberOfComponents() > 0) {
                addGB(annotationsPanel, annotationColumnPanel, ++k, 0, 1, 1);
            }
        }
        revalidate();
        repaint();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(controller::refreshLinks);
                controller.refreshCoordinates();
            }
        }, 500);
        annotationsPanel.addMouseWheelListener(e -> AnnotationSummaryPanel.this.dispatchEvent(
                SwingUtilities.convertMouseEvent(annotationsPanel, e, AnnotationSummaryPanel.this)));
    }

    public void refreshAnnotationPanel() {
        annotationsPanel.revalidate();
        annotationsPanel.validate();
        annotationsPanel.repaint();
    }

    private void addGB(final JPanel layout, final Component component,
                       final int x, final int y,
                       final int rowSpan, final int colSpan) {
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

    /**
     * Adds to the layout using absolute coordinates
     *
     * @param layout    The layout
     * @param component The component to add
     * @param x         The x coordinate
     * @param y         The y coordinate
     * @param width     The width of the component
     * @param height    The height of the component
     */
    void addAbsolute(final JPanel layout, final Component component, final int x, final int y, final int width, final int height) {
        constraints.anchor = AbsoluteGridBagConstraints.ABSOLUTE;
        component.setBounds(x, y, width, height);
        layout.add(component, constraints);

    }

    /**
     * Adds a LinkLabel given a component location
     *
     * @param component The component
     * @param l         The label to add
     */
    public void addLabel(final AnnotationSummaryComponent component, final LinkLabel l) {
        final Component c = component.asComponent();
        final int x = computeX(c, l);
        final int y = computeY(c, l);
        final Point point = SwingUtilities.convertPoint(c.getParent(), x, y, annotationsPanel);
        final ImageIcon image = l.getImageIcon();
        addAbsolute(annotationsPanel, l, (int) point.getX(), (int) point.getY(), image.getIconWidth(), image.getIconHeight());
    }

    /**
     * Updates a label
     *
     * @param c The component
     * @param l The LinkLabel
     */
    public void updateLabel(final Component c, final LinkLabel l) {
        final int x = computeX(c, l);
        final int y = computeY(c, l);
        final Point point = SwingUtilities.convertPoint(c.getParent(), x, y, annotationsPanel);
        l.setLocation((int) point.getX(), (int) point.getY());
    }

    /**
     * Removes a label
     *
     * @param l The LinkLabel to remove
     */
    public void removeLabel(final LinkLabel l) {
        annotationsPanel.remove(l);
    }

    private int computeX(final Component c, final LinkLabel l) {
        final boolean left = l.isLeft();
        final boolean full = l.isFull();
        final ImageIcon image = l.getImageIcon();
        final int x = 8;
        return left ? x - (full ? (image.getIconWidth() * 2 / 3) : image.getIconWidth() / 3) : x + c.getWidth() -
                (full ? image.getIconWidth() / 4 : image.getIconWidth() * 2 / 3);
    }

    private int computeY(final Component c, final LinkLabel l) {
        final boolean bottom = l.isBottom();
        final ImageIcon image = l.getImageIcon();
        return bottom ? c.getY() + c.getHeight() - image.getIconHeight() : c.getY();
    }

    private void importPerformed(final ActionEvent actionEvent) {
        final String extension = controller.getImportExportHandler().getFileExtension();
        if (!USE_JFILECHOOSER) {
            final FileDialog chooser = new FileDialog(controller.getFrame());
            chooser.setMode(FileDialog.LOAD);
            chooser.setMultipleMode(false);
            final File defaultFile = new File(ViewerPropertiesManager.getInstance()
                    .checkAndStoreStringProperty(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_IMPORT_FILE,
                            System.getProperty("user.home") + File.separator + "export." + extension));
            final File defaultDir = defaultFile.getParentFile();
            if (defaultDir != null && defaultDir.exists()) {
                chooser.setDirectory(defaultDir.getAbsolutePath());
            }
            if (defaultFile.exists()) {
                chooser.setFile(defaultFile.getAbsolutePath());
            }
            chooser.setFilenameFilter((file, s) -> s.endsWith("." + extension));
            chooser.setVisible(true);
            final String filename = chooser.getFile();
            final String dir = chooser.getDirectory();
            if (filename != null && dir != null) {
                final String path = dir + filename;
                importFile(path);
            }
        } else {
            final JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(false);
            final File defaultFile = new File(ViewerPropertiesManager.getInstance()
                    .checkAndStoreStringProperty(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_IMPORT_FILE,
                            System.getProperty("user.home") + File.separator + "export." + extension));
            final File defaultDir = defaultFile.getParentFile();
            if (defaultDir != null && defaultDir.exists()) {
                chooser.setCurrentDirectory(defaultDir);
            }
            if (defaultFile.exists()) {
                chooser.setSelectedFile(defaultFile);
            }
            chooser.setFileFilter(new ExtensionFileFilter(extension));
            final int ret = chooser.showSaveDialog(controller.getFrame());
            if (ret == JFileChooser.APPROVE_OPTION) {
                final File file = chooser.getSelectedFile();
                final String path = file.getAbsolutePath();
                importFile(path);
            }
        }
    }

    private void importFile(final String path) {
        ViewerPropertiesManager.getInstance().set(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_IMPORT_FILE,
                path);
        try (final InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(path)))) {
            controller.importFormat(in, true);
        } catch (final FileNotFoundException ignored) {

        } catch (final IOException e) {
            LOG.log(Level.SEVERE, "Error getting " + path, e);
        }
    }

    private void savePerformed(final ActionEvent actionEvent) {
        controller.exportFormat(controller.getDefaultSummaryOutputStream());
    }

    private void exportPerformed(final ActionEvent actionEvent) {
        final String extension = controller.getImportExportHandler().getFileExtension();
        if (!USE_JFILECHOOSER) {
            final FileDialog chooser = new FileDialog(controller.getFrame());
            chooser.setMode(FileDialog.SAVE);
            chooser.setMultipleMode(false);
            final File defaultFile = new File(ViewerPropertiesManager.getInstance()
                    .checkAndStoreStringProperty(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_EXPORT_FILE,
                            System.getProperty("user.home") + File.separator + "export." + extension));
            final File defaultDir = defaultFile.getParentFile();
            if (defaultDir != null && defaultDir.exists()) {
                chooser.setDirectory(defaultDir.getAbsolutePath());
            }
            if (defaultFile.exists()) {
                chooser.setFile(defaultFile.getAbsolutePath());
            }
            chooser.setFilenameFilter((file, s) -> s.endsWith("." + extension));
            chooser.setVisible(true);
            final String file = chooser.getFile();
            final String dir = chooser.getDirectory();
            if (file != null && dir != null) {
                final String[] nameAndExt = file.split("\\.");
                String name = file;
                if (nameAndExt.length < 2) {
                    name = file + "." + extension;
                } else if (!nameAndExt[nameAndExt.length - 1].equals(extension)) {
                    name = String.join(".", Arrays.copyOfRange(nameAndExt, 0, nameAndExt.length - 1)) + "." + extension;
                }
                final File parent = new File(dir + name).getParentFile();
                if (Files.isWritable(parent.toPath())) {
                    ViewerPropertiesManager.getInstance().set(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_EXPORT_FILE,
                            dir + name);
                    try {
                        controller.exportFormat(new FileOutputStream(dir + name));
                    } catch (final FileNotFoundException ignored) {

                    }
                } else {
                    Resources.showMessageDialog(
                            controller.getFrame(),
                            JOptionPane.INFORMATION_MESSAGE,
                            messageBundle,
                            "viewer.dialog.saveAs.cantwrite.title",
                            "viewer.dialog.saveAs.cantwrite.msg",
                            new File(dir + name).getParentFile().getName());
                    exportPerformed(actionEvent);
                }
            }
        } else {
            final JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(false);
            final File defaultFile = new File(ViewerPropertiesManager.getInstance().checkAndStoreStringProperty(
                    ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_EXPORT_FILE, System.getProperty("user.home")
                            + File.separator + "export." + extension));
            final File defaultDir = defaultFile.getParentFile();
            if (defaultDir != null && defaultDir.exists()) {
                chooser.setCurrentDirectory(defaultDir);
            }
            if (defaultFile.exists()) {
                chooser.setSelectedFile(defaultFile);
            }
            chooser.setFileFilter(new ExtensionFileFilter(extension));
            final int ret = chooser.showSaveDialog(controller.getFrame());
            if (ret == JFileChooser.APPROVE_OPTION) {
                final File file = chooser.getSelectedFile();
                final File parent = file.getParentFile();
                if (Files.isWritable(parent.toPath())) {
                    ViewerPropertiesManager.getInstance().set(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_EXPORT_FILE,
                            file.getAbsolutePath());
                    try {
                        controller.exportFormat(new FileOutputStream(file));
                    } catch (final FileNotFoundException ignored) {

                    }
                } else {
                    Resources.showMessageDialog(
                            controller.getFrame(),
                            JOptionPane.INFORMATION_MESSAGE,
                            messageBundle,
                            "viewer.dialog.saveAs.cantwrite.title",
                            "viewer.dialog.saveAs.cantwrite.msg",
                            file.getParentFile().getName());
                    exportPerformed(actionEvent);
                }
            }
        }
    }

    private static class AbsoluteGridBagLayout extends GridBagLayout {

        @Override
        public void layoutContainer(final Container parent) {
            final Map<Component, GridBagConstraints> toRemove = new HashMap<>();
            for (final Component comp : parent.getComponents()) {
                if (comptable.get(comp).anchor == AbsoluteGridBagConstraints.ABSOLUTE) {
                    toRemove.put(comp, comptable.get(comp));
                }
            }
            for (final Component comp : toRemove.keySet()) {
                parent.remove(comp);
            }
            super.layoutContainer(parent);
            for (final Map.Entry<Component, GridBagConstraints> entry : toRemove.entrySet()) {
                final Component comp = entry.getKey();
                parent.add(comp, entry.getValue());
                parent.setComponentZOrder(comp, 0);
                comp.setBounds(comp.getBounds());
            }
        }
    }

    private static class AbsoluteGridBagConstraints extends GridBagConstraints {
        public static final int ABSOLUTE = Integer.MAX_VALUE;
    }

    private static class FontFamilyBoxRenderer extends JLabel implements ListCellRenderer<ValueLabelItem> {

        private final int fontSize;

        private FontFamilyBoxRenderer(final int size) {
            this.fontSize = size;
        }

        @Override
        public Component getListCellRendererComponent(final JList<? extends ValueLabelItem> jList,
                                                      final ValueLabelItem valueLabelItem,
                                                      final int i, final boolean b, final boolean b1) {
            if (valueLabelItem != null) {
                final String name = (String) valueLabelItem.getValue();
                setText(name);
                setFont(new Font(name, Font.PLAIN, fontSize));
            } else {
                setText("");
            }
            return this;
        }
    }

    private static class ExtensionFileFilter extends FileFilter {

        private final String extension;

        private ExtensionFileFilter(final String extension) {
            this.extension = requireNonNull(extension);
        }

        @Override
        public boolean accept(final File file) {
            return Files.isDirectory(file.toPath()) || file.getName().endsWith("." + extension);
        }

        @Override
        public String getDescription() {
            return "OpenDocument Spreadsheet (*.ods)";
        }
    }
}
