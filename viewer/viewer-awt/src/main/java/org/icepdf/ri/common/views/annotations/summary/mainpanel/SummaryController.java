package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import com.twelvemonkeys.io.FileSeekableStream;
import com.twelvemonkeys.io.SeekableInputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggableAnnotationPanel;
import org.icepdf.ri.common.widgets.DragDropColorList;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.Pair;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main controller
 */
public class SummaryController implements MutableDocument {

    private static final Logger LOG =
            Logger.getLogger(SummaryController.class.toString());

    private static final ImportExportHandler IMPORT_EXPORT_HANDLER = new NoOpImportExportHandler();

    private final Frame frame;
    private final Controller controller;
    protected final DragAndLinkManager dragManager;

    private final AnnotationSummaryPanel summaryPanel;

    protected ArrayList<ColorLabelPanel> annotationNamedColorPanels;
    private int fontSize;

    private final Map<Reference, AnnotationSummaryBox> annotationToBox;
    private final Map<Reference, ColorLabelPanel> annotationToColorPanel;

    private final GroupManager groupManager;
    private final ImportExportHandler ieHandler;

    private final MouseListener mouseListener;
    private final ComponentListener componentListener;
    private final PropertyChangeListener propertyListener;

    private final Map<AnnotationSummaryComponent, LinkLabel> directLinks;
    private final Map<AnnotationSummaryComponent, LinkLabel> leftLinks;
    private final Map<AnnotationSummaryComponent, LinkLabel> rightLinks;
    private final Map<LinkLabel, LinkLabel> siblingLabels;

    private final List<Integer> xCoordinates;
    private final Map<Color, List<Integer>> yCoordinates;

    private boolean hasLoaded = false;
    private boolean hasManuallyChanged = false;
    private boolean hasAutomaticallyChanged = false;

    public SummaryController(final Frame frame, final Controller controller, final AnnotationSummaryPanel summaryPanel) {
        this.frame = frame;
        this.controller = controller;
        this.summaryPanel = summaryPanel;
        this.annotationNamedColorPanels = new ArrayList<>();
        this.annotationToBox = new HashMap<>();
        this.annotationToColorPanel = new HashMap<>();
        this.directLinks = new HashMap<>();
        this.leftLinks = new HashMap<>();
        this.rightLinks = new HashMap<>();
        this.siblingLabels = new HashMap<>();
        this.xCoordinates = new ArrayList<>();
        this.yCoordinates = new HashMap<>();
        this.dragManager = createDragAndLinkManager();
        this.groupManager = createGroupManager();
        this.ieHandler = IMPORT_EXPORT_HANDLER;
        this.mouseListener = new SummaryMouseListener();
        this.componentListener = new SummaryComponentListener();
        this.propertyListener = new PropertiesListener();
        this.fontSize = new JLabel().getFont().getSize();
        LinkLabel.rescaleAll(192 / fontSize);
        summaryPanel.addComponentListener(componentListener);
        controller.getDocumentViewController().getPropertyChangeSupport().addPropertyChangeListener(propertyListener);
    }

    protected DragAndLinkManager createDragAndLinkManager() {
        return new DragAndLinkManager(this);
    }

    protected GroupManager createGroupManager() {
        return new GroupManager(this);
    }

    protected ImportExportHandler getImportExportHandler() {
        return IMPORT_EXPORT_HANDLER;
    }

    public Frame getFrame() {
        return frame;
    }

    public void clear() {
        dragManager.clear();
        groupManager.clear();
        xCoordinates.clear();
        yCoordinates.clear();
    }

    public void save() {
        exportFormat(getDefaultSummaryOutputStream());
    }

    public void setHasManuallyChanged() {
        setHasManuallyChanged(true);
    }

    public void setHasManuallyChanged(final boolean changed) {
        hasManuallyChanged = changed;
        hasAutomaticallyChanged = changed;
    }

    public void setHasAutomaticallyChanged(final boolean changed) {
        hasAutomaticallyChanged = changed;
    }

    public boolean hasManuallyChanged() {
        return hasManuallyChanged;
    }

    public boolean hasAutomaticallyChanged() {
        return hasAutomaticallyChanged;
    }

    public boolean hasChanged() {
        return hasManuallyChanged || (hasLoaded && hasAutomaticallyChanged);
    }

    public String getFontName() {
        return ((ValueLabelItem) summaryPanel.getFontNameBox().getSelectedItem()).getLabel();
    }

    public boolean hasLoaded() {
        return hasLoaded;
    }


    /**
     * Exports the summary to a file
     *
     * @param output The output stream
     */
    public void exportFormat(final OutputStream output) {
        final ResourceBundle messageBundle = controller.getMessageBundle();
        try {
            ieHandler.exportFormat(annotationNamedColorPanels, output);
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, e, () -> "Couldn't export summary");
            showMessageDialog(MessageFormat.format(messageBundle.getString("viewer.summary.export.failure.label")
                                    .replace("'", "''")
                            , e.getMessage() == null ? e.toString() : e.getMessage()),
                    messageBundle.getString("viewer.summary.export.failure.title"), JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                output.close();
            } catch (final IOException ignored) {
            }
        }
        setHasManuallyChanged(false);
        hasLoaded = true;
    }

    /**
     * Checks if a format file can be imported
     *
     * @param inputStream The inputStream
     * @return The map if the file is compatible, null otherwise
     */
    public Map<AnnotationSummaryComponent, Pair<Integer, Integer>> canImport(final InputStream inputStream, final boolean partial) {
        try {
            final Map<AnnotationSummaryComponent, Pair<Integer, Integer>> compToCell =
                    ieHandler.validateImport(annotationNamedColorPanels, inputStream, partial);
            return compToCell != null && (partial || compToCell.keySet().containsAll(
                    new HashSet<>(annotationToBox.values()))) ? compToCell : null;
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Imports the summary from a file
     *
     * @param inputStream The input stream
     * @return true if the import was unsuccessful and the user wants to delete the file
     */
    public boolean importFormat(final SeekableInputStream inputStream, final boolean partial) {
        try {
            final Map<AnnotationSummaryComponent, Pair<Integer, Integer>> compToCell = canImport(inputStream, partial);
            inputStream.reset();
            return importFormat(compToCell, inputStream);
        } catch (final IOException ignored) {
            return false;
        }
    }

    public void setLoaded(final boolean hasLoaded) {
        this.hasLoaded = hasLoaded;
    }

    /**
     * Imports the summary from a file
     *
     * @param compToCell  The precomputed map of component to cell
     * @param inputStream The input stream
     * @return true if the import was unsuccessful and the user wants to delete the file
     */
    public boolean importFormat(final Map<AnnotationSummaryComponent, Pair<Integer, Integer>> compToCell,
                                final InputStream inputStream) {
        final ResourceBundle messageBundle = controller.getMessageBundle();
        try {
            if (compToCell != null) {
                clear();
                ieHandler.importFormat(compToCell, inputStream);
                refreshCoordinates();
                refreshLinks();
                setHasManuallyChanged(false);
                hasLoaded = true;
                return false;
            } else if (!isEmpty()) {
                return JOptionPane.showConfirmDialog(frame,
                        messageBundle.getString("viewer.summary.import.different.label"),
                        messageBundle.getString("viewer.summary.import.failure.title"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION;
            } else {
                return true;
            }
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, e, () -> "Error importing summary");
            return JOptionPane.showConfirmDialog(frame,
                    MessageFormat.format(messageBundle.getString("viewer.summary.import.failure.label").replace("'", "''"),
                            e.getMessage() == null ? e.toString() : e.getMessage()),
                    messageBundle.getString("viewer.summary.import.failure.title"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION;
        } finally {
            try {
                inputStream.close();
            } catch (final IOException ignored) {

            }
        }
    }

    /**
     * @return true if there are no annotations in the summary
     */
    public boolean isEmpty() {
        return annotationNamedColorPanels.isEmpty() || annotationNamedColorPanels.stream().allMatch(clp ->
                clp.getNumberOfComponents() == 0);
    }

    /**
     * Compacts the panel by reducing the blank space
     */
    void compactPanel() {
        setHasManuallyChanged();
        final UUID uuid = UUID.randomUUID();
        annotationNamedColorPanels.forEach(clp -> clp.compact(uuid));
    }

    void addFontListeners() {
        summaryPanel.getFontSizeBox().addItemListener(itemEvent -> {
            final JComboBox<ValueLabelItem> fontSizeBox = (JComboBox<ValueLabelItem>) itemEvent.getSource();
            final ViewerPropertiesManager propertiesManager = controller.getPropertiesManager();
            final ValueLabelItem tmp = (ValueLabelItem) fontSizeBox.getSelectedItem();
            // fire the font size property change event.
            if (tmp != null) {
                if (annotationNamedColorPanels != null) {
                    for (final ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                        colorLabelPanel.firePropertyChange(PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE,
                                0, (int) tmp.getValue());
                    }
                }
                propertiesManager.setInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE,
                        (int) tmp.getValue());
                setFontSize((int) tmp.getValue());
            }
        });
        summaryPanel.getFontNameBox().addItemListener(itemEvent -> {
            final JComboBox<ValueLabelItem> fontNameBox = (JComboBox<ValueLabelItem>) itemEvent.getSource();
            final ValueLabelItem item = (ValueLabelItem) fontNameBox.getSelectedItem();
            final String familyName = item != null ? (String) item.getValue() : null;
            if (annotationNamedColorPanels != null) {
                for (final ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                    colorLabelPanel.updateFontFamily(familyName);
                }
            }
            controller.getPropertiesManager().set(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_NAME, familyName);
        });
    }

    public void forceChangeFontName(final String name) {
        applySelectedValue(summaryPanel.getFontNameBox(), name);
    }

    public void forceChangeFontSize(final int fontSize) {
        applySelectedValue(summaryPanel.getFontSizeBox(), fontSize);
    }

    public void dispatch(final MouseEvent e) {
        summaryPanel.getAnnotationsPanel().dispatchEvent(SwingUtilities.convertMouseEvent((Component) e.getSource(), e,
                summaryPanel.getAnnotationsPanel()));
    }

    void addPotentialLinkMouseListener() {
        final MouseAdapter potentialLinkMouseAdapter = new PotentialLinkMouseListener();
        summaryPanel.getAnnotationsPanel().addMouseMotionListener(potentialLinkMouseAdapter);
        summaryPanel.getAnnotationsPanel().addMouseListener(potentialLinkMouseAdapter);
    }

    public void setFontSize(final int fontSize) {
        setHasManuallyChanged();
        this.fontSize = fontSize;
        LinkLabel.rescaleAll(192 / fontSize);
        final UUID uuid = UUID.randomUUID();
        annotationNamedColorPanels.forEach(c -> c.getDraggableAnnotationPanel().checkForOverlap(uuid));
        refreshLinks();
    }

    public int getFontSize() {
        return summaryPanel.getFontSize();
    }

    public String getLabelFor(final Color c) {
        final DragDropColorList.ColorLabel colorLabel = getColorLabelFor(c);
        return colorLabel != null ? colorLabel.getLabel() : null;
    }

    /**
     * Returns a ColorLabel for a given color
     *
     * @param c The color
     * @return The colorlabel or null
     */
    public DragDropColorList.ColorLabel getColorLabelFor(final Color c) {
        for (final ColorLabelPanel panel : annotationNamedColorPanels) {
            final DragDropColorList.ColorLabel colorLabel = panel.getColorLabel();
            if (colorLabel.getColor().equals(c)) {
                return colorLabel;
            }
        }
        return null;
    }

    /**
     * Returns a ColorLabel for a given label
     *
     * @param s The label
     * @return The colorlabel or null
     */
    public DragDropColorList.ColorLabel getColorLabelFor(final String s) {
        for (final ColorLabelPanel panel : annotationNamedColorPanels) {
            final DragDropColorList.ColorLabel colorLabel = panel.getColorLabel();
            if (colorLabel.getLabel().equals(s)) {
                return colorLabel;
            }
        }
        return null;
    }

    public Color getColorFor(final String s) {
        final DragDropColorList.ColorLabel colorLabel = getColorLabelFor(s);
        return colorLabel != null ? colorLabel.getColor() : null;
    }

    public DragAndLinkManager getDragAndLinkManager() {
        return dragManager;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public void showMessageDialog(final String content, final String title, final int type) {
        JOptionPane.showMessageDialog(summaryPanel, content, title, type);
    }

    public String showInputDialog(final String content, final String title, final int type,
                                  final String initialValue, final Icon icon) {
        return (String) JOptionPane.showInputDialog(summaryPanel, content, title, type, icon, null, initialValue);
    }

    public void refreshPanelLayout() {
        summaryPanel.refreshPanelLayout();
    }

    /**
     * Refreshes the components coordinates cache
     */
    void refreshCoordinates() {
        xCoordinates.clear();
        yCoordinates.clear();
        if (summaryPanel.getAnnotationsPanel() != null) {
            Arrays.stream(summaryPanel.getAnnotationsPanel().getComponents()).filter(c -> c instanceof ColorLabelPanel).forEach(clp -> {
                final DraggableAnnotationPanel dap = ((ColorLabelPanel) clp).getDraggableAnnotationPanel();
                final List<Integer> yCoords = new ArrayList<>();
                Arrays.stream(dap.getComponents()).forEach(c -> {
                    yCoords.add(c.getY());
                    yCoords.add(c.getY() + c.getHeight());
                });
                if (dap.getComponentCount() > 0) {
                    final Component firstComp = dap.getComponent(0);
                    xCoordinates.add(convertDragSpaceToSummarySpace(dap, firstComp.getX(), 0).x);
                    xCoordinates.add(convertDragSpaceToSummarySpace(dap, firstComp.getX() + firstComp.getWidth(), 0).x);
                    yCoordinates.put(((ColorLabelPanel) clp).getColorLabel().getColor(), yCoords.stream().map(y ->
                            convertDragSpaceToSummarySpace(dap, 0, y).y).collect(Collectors.toList()));
                }
            });
        }
    }

    private Point convertDragSpaceToSummarySpace(final DraggableAnnotationPanel dap, final int x, final int y) {
        final Point point = new Point(x, y);
        return SwingUtilities.convertPoint(dap, point, summaryPanel.getAnnotationsPanel());
    }

    protected ColorLabelPanel createColorLabelPanel(final Frame frame, final DragDropColorList.ColorLabel colorLabel) {
        return new ColorLabelPanel(frame, colorLabel, this);
    }

    @Override
    public void refreshDocumentInstance() {
        if (controller.getDocument() != null) {
            // get the named colour and build out the draggable panels.
            final Document document = controller.getDocument();
            final ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
            final int numberOfPanels = colorLabels != null ? colorLabels.size() : 1;
            if (annotationNamedColorPanels != null) annotationNamedColorPanels.clear();
            annotationNamedColorPanels = new ArrayList<>(numberOfPanels);
            annotationToColorPanel.clear();
            annotationToBox.clear();

            groupManager.refreshGroups();

            if (colorLabels != null && !colorLabels.isEmpty()) {
                // build a panel for each color
                for (final DragDropColorList.ColorLabel colorLabel : colorLabels) {
                    final ColorLabelPanel annotationColumnPanel = createColorLabelPanel(frame, colorLabel);
                    annotationNamedColorPanels.add(annotationColumnPanel);
                    annotationColumnPanel.addMouseListener(mouseListener);
                    for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                        final List<Annotation> annots = document.getPageTree().getPage(i).getAnnotations();
                        final List<Annotation> annotations = annots == null ? Collections.emptyList() : new ArrayList<>(annots);
                        for (final Annotation annotation : annotations) {
                            if (annotation instanceof MarkupAnnotation
                                    && colorLabel.getColor().equals(annotation.getColor())) {
                                annotationToColorPanel.put(annotation.getPObjectReference(), annotationColumnPanel);
                                final AnnotationSummaryBox box = annotationColumnPanel.addAnnotation((MarkupAnnotation) annotation);
                                if (box != null) {
                                    annotationToBox.put(annotation.getPObjectReference(), box);
                                }
                            }
                        }
                    }
                    groupManager.addGroupsToPanel(annotationColumnPanel);
                }
                // check to make sure a label has
            } else {
                // other wise just one big panel with all the named colors.
                final ColorLabelPanel annotationColumnPanel = createColorLabelPanel(frame, null);
                annotationNamedColorPanels.add(annotationColumnPanel);
                for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                    final List<Annotation> annotations = document.getPageTree().getPage(i).getAnnotations();
                    if (annotations != null) {
                        for (final Annotation annotation : annotations) {
                            if (annotation instanceof MarkupAnnotation) {
                                annotationToColorPanel.put(annotation.getPObjectReference(), annotationColumnPanel);
                                final AnnotationSummaryBox box = annotationColumnPanel.addAnnotation((MarkupAnnotation) annotation);
                                if (box != null) {
                                    annotationToBox.put(annotation.getPObjectReference(), box);
                                }
                            }
                        }
                    }
                }
            }
        }
        refreshPanelLayout();
        tryImportSummaryFile();
    }

    private void tryImportSummaryFile() {
        final SeekableInputStream inputStream = getDefaultSummaryInputStream();
        if (inputStream != null) {
            if (importFormat(inputStream, true)) {
                deleteSummaryFile();
            }
        }
        setHasManuallyChanged(false);
    }

    public OutputStream getDefaultSummaryOutputStream() {
        final File file = getDefaultSummaryFile();
        if (file != null) {
            try {
                return new FileOutputStream(file);
            } catch (final FileNotFoundException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public SeekableInputStream getDefaultSummaryInputStream() {
        final File file = getDefaultSummaryFile();
        if (file != null) {
            try {
                return new FileSeekableStream(file);
            } catch (final FileNotFoundException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public void deleteSummaryFile() {
        final File file = getDefaultSummaryFile();
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    public File getDefaultSummaryFile() {
        if (controller.getDocument() != null) {
            final String location = controller.getDocument().getDocumentLocation();
            final String folder = location.substring(0, location.lastIndexOf('/'));
            final String filename = location.substring(location.lastIndexOf('/') + 1, location.lastIndexOf('.'));
            return new File(folder + '/' + filename + '.' + IMPORT_EXPORT_HANDLER.getFileExtension());
        } else {
            return null;
        }
    }

    /**
     * Gets the color panel for a given annotation
     *
     * @param annot  The annotation
     * @param cached If the result is to be retrieved from the cache or through an exhaustive search
     * @return The color panel or null
     */
    ColorLabelPanel getColorPanelFor(final MarkupAnnotation annot, final boolean cached) {
        if (cached && annotationToColorPanel.containsKey(annot.getPObjectReference())) {
            return annotationToColorPanel.get(annot.getPObjectReference());
        } else {
            final ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
            if (colorLabels != null && !colorLabels.isEmpty() && annotationNamedColorPanels != null) {
                for (final ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                    if (annotationColumnPanel.getColorLabel().getColor().equals(annot.getColor())) {
                        return annotationColumnPanel;
                    }
                }
                return null;
            } else if (annotationNamedColorPanels != null && !annotationNamedColorPanels.isEmpty()) {
                return annotationNamedColorPanels.get(0);
            } else {
                return null;
            }
        }
    }

    /**
     * Gets the color panel for a given component
     *
     * @param component The component
     * @return The color panel, or null
     */
    ColorLabelPanel getColorPanelFor(final AnnotationSummaryComponent component) {
        return getColorPanelFor(component.getColor());
    }

    /**
     * Gets the color panel for a given color
     *
     * @param color The color
     * @return The colorpanel, or null
     */
    ColorLabelPanel getColorPanelFor(final Color color) {
        final Color withoutAlpha = new Color(color.getRed(), color.getGreen(), color.getBlue());
        for (final ColorLabelPanel p : annotationNamedColorPanels) {
            if (p.getColorLabel().getColor().equals(withoutAlpha)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Get the visible color to the left of a color panel
     *
     * @param panel The panel
     * @return The color or null if the panel is the leftmost one
     */
    public Color getLeftColor(final DraggableAnnotationPanel panel) {
        return getColorForIdx(idxOf(panel) - 1);
    }

    /**
     * Get the visible color to the right of a color panel
     *
     * @param panel The panel
     * @return The color or null if the panel is the rightmost one
     */
    public Color getRightColor(final DraggableAnnotationPanel panel) {
        return getColorForIdx(idxOf(panel) + 1);
    }

    private int idxOf(final DraggableAnnotationPanel panel) {
        final List<Component> components = Arrays.asList(summaryPanel.getAnnotationsPanel().getComponents());
        return components.stream().filter(c -> c instanceof ColorLabelPanel).map(c -> ((ColorLabelPanel) c)
                .getDraggableAnnotationPanel()).collect(Collectors.toList()).indexOf(panel);
    }

    private Color getColorForIdx(final int idx) {
        final List<Component> components = Arrays.stream(summaryPanel.getAnnotationsPanel().getComponents()).filter(c ->
                c instanceof ColorLabelPanel).collect(Collectors.toList());
        return idx >= 0 && idx < components.size() ? ((ColorLabelPanel) components.get(idx)).getColorLabel().getColor() : null;
    }

    static void applySelectedValue(final JComboBox<ValueLabelItem> comboBox, final Object value) {
        ValueLabelItem currentItem;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            currentItem = comboBox.getItemAt(i);
            if (currentItem.getValue().equals(value)) {
                comboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    @Override
    public void disposeDocument() {
        annotationNamedColorPanels.clear();
    }

    public Controller getController() {
        return controller;
    }

    public ArrayList<ColorLabelPanel> getAnnotationNamedColorPanels() {
        return annotationNamedColorPanels;
    }


    /**
     * Moves a component to another color
     *
     * @param c        The component to move
     * @param color    The new color
     * @param oldColor The old color
     */
    public void moveTo(final AnnotationSummaryComponent c, final Color color, final Color oldColor) {
        moveTo(c, color, oldColor, true);
    }

    /**
     * Moves a component to another color
     *
     * @param c        The component to move
     * @param color    The new color
     * @param oldColor The old color
     */
    public void moveTo(final AnnotationSummaryComponent c, final Color color, final Color oldColor, final boolean keepY) {
        dragManager.unlinkComponent(c, false);
        groupManager.removeFromGroup(c);
        final ColorLabelPanel oldPanel = getColorPanelFor(oldColor);
        if (oldPanel != null) {
            oldPanel.removeComponent(c);
        }
        final ColorLabelPanel panel = getColorPanelFor(color);
        if (panel != null) {
            panel.addComponent(c, keepY ? c.asComponent().getY() : -1);
        }
        refreshPanelLayout();
    }

    boolean isLeftOf(final Color left, final Color right) {
        final int leftIdx = idxOf(getColorPanelFor(left).getDraggableAnnotationPanel());
        final int rightIdx = idxOf(getColorPanelFor(right).getDraggableAnnotationPanel());
        return leftIdx < rightIdx;
    }

    boolean isDirectlyLeftOf(final Color left, final Color right) {
        final int leftIdx = idxOf(getColorPanelFor(left).getDraggableAnnotationPanel());
        final int rightIdx = idxOf(getColorPanelFor(right).getDraggableAnnotationPanel());
        return leftIdx == rightIdx - 1;
    }

    boolean isRightOf(final Color right, final Color left) {
        final int leftIdx = idxOf(getColorPanelFor(left).getDraggableAnnotationPanel());
        final int rightIdx = idxOf(getColorPanelFor(right).getDraggableAnnotationPanel());
        return rightIdx > leftIdx;
    }

    boolean isDirectlyRightOf(final Color right, final Color left) {
        final int leftIdx = idxOf(getColorPanelFor(left).getDraggableAnnotationPanel());
        final int rightIdx = idxOf(getColorPanelFor(right).getDraggableAnnotationPanel());
        return rightIdx == leftIdx + 1;
    }

    private int idxOf(final Color c) {
        for (int i = 0; i < annotationNamedColorPanels.size(); ++i) {
            if (annotationNamedColorPanels.get(i).getColorLabel().getColor().equals(c)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Refreshes all the links
     */
    public void refreshLinks() {
        directLinks.values().forEach(summaryPanel::removeLabel);
        leftLinks.values().forEach(summaryPanel::removeLabel);
        rightLinks.values().forEach(summaryPanel::removeLabel);
        directLinks.clear();
        leftLinks.clear();
        rightLinks.clear();
        siblingLabels.clear();
        final Map<AnnotationSummaryComponent, Set<AnnotationSummaryComponent>> linkedComponents = dragManager.getLinkedComponents();
        final List<AnnotationSummaryComponent> keys = linkedComponents.keySet().stream().sorted(Comparator.comparingInt(asc ->
                idxOf(asc.getColor()))).collect(Collectors.toList());
        for (final AnnotationSummaryComponent key : keys) {
            final List<AnnotationSummaryComponent> values = linkedComponents.get(key).stream().sorted(Comparator.comparingInt(asc ->
                    idxOf(asc.getColor()))).collect(Collectors.toList());
            for (final AnnotationSummaryComponent value : values) {
                if (isDirectlyLeftOf(key.getColor(), value.getColor())) {
                    if (!directLinks.containsKey(key)) {
                        final LinkLabel label = new LinkLabel(false, true, isBottom(key, value, true, true));
                        label.addMouseListener(new LinkMouseListener(label, key, value));
                        summaryPanel.addLabel(key, label);
                        directLinks.put(key, label);
                    }
                } else if (isLeftOf(key.getColor(), value.getColor())) {
                    if (!rightLinks.containsKey(key) && !leftLinks.containsKey(value)) {
                        addLabels(key, value, true);
                    }
                }
            }
        }
        summaryPanel.refreshAnnotationPanel();
    }


    private void addLabels(final AnnotationSummaryComponent key, final AnnotationSummaryComponent value, final boolean left) {
        final boolean isBottom = isBottom(key, value, false, left);
        final LinkLabel keyLabel = new LinkLabel(!left, false, isBottom);
        keyLabel.addMouseListener(new LinkMouseListener(keyLabel, key, value));
        final LinkLabel valueLabel = new LinkLabel(left, false, isBottom);
        valueLabel.addMouseListener(new LinkMouseListener(valueLabel, value, key));
        summaryPanel.addLabel(key, keyLabel);
        summaryPanel.addLabel(value, valueLabel);
        if (left) {
            rightLinks.put(key, keyLabel);
            leftLinks.put(value, valueLabel);
        } else {
            leftLinks.put(key, keyLabel);
            rightLinks.put(value, valueLabel);
        }
        siblingLabels.put(keyLabel, valueLabel);
        siblingLabels.put(valueLabel, keyLabel);
    }

    private boolean isBottom(final AnnotationSummaryComponent key, final AnnotationSummaryComponent value, final boolean full, final boolean left) {
        return (full && ((rightLinks.containsKey(key) && !rightLinks.get(key).isBottom) ||
                (leftLinks.containsKey(value) && !leftLinks.get(value).isBottom))) ||
                (!full && left && ((directLinks.containsKey(key) && !directLinks.get(key).isBottom) ||
                        (rightLinks.containsKey(value) && !rightLinks.get(value).isBottom)));
    }

    /**
     * Indicates that a component has moved
     *
     * @param comp The component that moved
     */
    public void componentMoved(final AnnotationSummaryComponent comp) {
        moveLabel(comp, leftLinks);
        moveLabel(comp, rightLinks);
        moveLabel(comp, directLinks);
        summaryPanel.revalidate();
        summaryPanel.repaint();
        refreshCoordinates();
    }

    private void moveLabel(final AnnotationSummaryComponent comp, final Map<AnnotationSummaryComponent, LinkLabel> map) {
        if (map.containsKey(comp)) {
            final LinkLabel label = map.get(comp);
            final Component c = comp.asComponent();
            summaryPanel.updateLabel(c, label);
        }
    }

    void showAllHeaders() {
        setAllHeaders(true);
    }

    void hideAllHeaders() {
        setAllHeaders(false);
    }

    void setAllHeaders(final boolean visible) {
        setHasManuallyChanged();
        annotationNamedColorPanels.forEach(clp -> {
            final DraggableAnnotationPanel dap = clp.getDraggableAnnotationPanel();
            Arrays.stream(dap.getComponents()).forEach(c -> {
                if (c instanceof AnnotationSummaryComponent) {
                    ((AnnotationSummaryComponent) c).setHeaderVisibility(visible);
                }
            });
            SwingUtilities.invokeLater(() -> dap.checkForOverlap(UUID.randomUUID()));
        });
    }

    private class PotentialLinkMouseListener extends MouseAdapter {

        private LinkLabel currentLabel;
        private AnnotationSummaryComponent c1;
        private AnnotationSummaryComponent c2;

        @Override
        public void mouseClicked(final MouseEvent e) {
            if (currentLabel != null && currentLabel.isVisible() && c1 != null && c2 != null) {
                dragManager.linkComponents(c1, c2);
                if (c1.asComponent().getY() != c2.asComponent().getY()) {
                    getColorPanelFor(c2).getDraggableAnnotationPanel().moveComponentToY(c2.asComponent(), c1.asComponent().getY(), UUID.randomUUID());
                }
                removeLabel();
            }
        }

        @Override
        public void mouseMoved(final MouseEvent e) {
            final int x = e.getX();
            final int y = e.getY();
            int xIdx = Collections.binarySearch(xCoordinates, x);
            if (xIdx < 0) {
                xIdx = -1 * (xIdx + 1);
                if (xIdx > 0 && xIdx < xCoordinates.size() && xIdx % 2 == 0) {
                    final Color color1 = getColorForIdx(xIdx / 2 - 1);
                    final AnnotationSummaryComponent comp1 = getComponentForY(color1, y);
                    if (comp1 != null) {
                        final Color color2 = getColorForIdx(xIdx / 2);
                        final AnnotationSummaryComponent comp2 = getComponentForY(color2, y);
                        if (comp2 != null) {
                            this.c1 = comp1;
                            this.c2 = comp2;
                            if (!dragManager.areLinked(c1, color2) && !dragManager.areLinked(c2, color1)) {
                                final int labelX = xCoordinates.get(xIdx - 1);
                                final AnnotationSummaryComponent yComp = c1.asComponent().getY() > c2.asComponent().getY() ? c1 : c2;
                                final int labelY = SwingUtilities.convertPoint(getColorPanelFor(yComp.getColor()).getDraggableAnnotationPanel(),
                                        new Point(yComp.asComponent().getX(), yComp.asComponent().getY()), summaryPanel.getAnnotationsPanel()).y;
                                if (currentLabel == null) {
                                    currentLabel = new LinkLabel(true, true, true);
                                    summaryPanel.addAbsolute(summaryPanel.annotationsPanel, currentLabel, labelX, labelY,
                                            currentLabel.getImageIcon().getIconWidth(), currentLabel.getImageIcon().getIconHeight());
                                } else {
                                    currentLabel.setLocation(labelX - (int) (1f / 3 * currentLabel.getImageIcon().getIconWidth()), labelY);
                                    currentLabel.setVisible(true);
                                }
                                summaryPanel.refreshAnnotationPanel();
                            }
                        } else {
                            removeLabel();
                        }
                    } else {
                        removeLabel();
                    }

                } else {
                    removeLabel();
                }
            } else {
                removeLabel();
            }

        }

        private void removeLabel() {
            if (currentLabel != null) {
                currentLabel.setVisible(false);
                c1 = null;
                c2 = null;
            }
        }

        private AnnotationSummaryComponent getComponentForY(final Color color, final int y) {
            if (color != null) {
                final List<Integer> ycoords = yCoordinates.get(color);
                int yIdx = Collections.binarySearch(ycoords, y);
                if (yIdx < 0) {
                    yIdx = -1 * (yIdx + 1);
                    if (yIdx > 0 && yIdx < ycoords.size() && yIdx % 2 == 1) {
                        final int cIdx = yIdx / 2;
                        final DraggableAnnotationPanel annotationPanel = getColorPanelFor(color).getDraggableAnnotationPanel();
                        return annotationPanel.getComponentCount() > cIdx ?
                                (AnnotationSummaryComponent) annotationPanel.getComponents()[cIdx] : null;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Moves a component to a given y position
     *
     * @param comp The component to move
     * @param y    The y coordinate
     * @param uuid The uuid of the operation
     */
    public void moveComponentToY(final AnnotationSummaryComponent comp, final int y, final UUID uuid) {
        final DraggableAnnotationPanel panel = getColorPanelFor(comp).getDraggableAnnotationPanel();
        panel.moveComponentToY(comp.asComponent(), y, false, uuid);
    }

    private class LinkMouseListener extends MouseAdapter {

        private final LinkLabel label;
        private final AnnotationSummaryComponent key;
        private final AnnotationSummaryComponent value;

        public LinkMouseListener(final LinkLabel label, final AnnotationSummaryComponent key,
                                 final AnnotationSummaryComponent value) {
            this.label = label;
            this.key = key;
            this.value = value;
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            dragManager.unlinkComponents(key, value);
        }

        @Override
        public void mouseEntered(final MouseEvent e) {
            label.setDeleteIcon(true);
            if (siblingLabels.containsKey(label)) {
                siblingLabels.get(label).setDeleteIcon(true);
            }
        }

        @Override
        public void mouseExited(final MouseEvent e) {
            label.setDeleteIcon(false);
            if (siblingLabels.containsKey(label)) {
                siblingLabels.get(label).setDeleteIcon(false);
            }
        }
    }

    /**
     * Class representing a label with a Link icon
     */
    static class LinkLabel extends JLabel {
        private final boolean isBottom;
        private final boolean isFull;
        private final boolean isLeft;
        private boolean isDelete = false;
        private static ImageIcon FULL;
        private static ImageIcon FULL_DELETE;
        private static ImageIcon HALF_RIGHT;
        private static ImageIcon HALF_RIGHT_DELETE;
        private static ImageIcon HALF_LEFT;
        private static ImageIcon HALF_LEFT_DELETE;

        public LinkLabel(final boolean isLeft, final boolean isFull, final boolean isBottom) {
            super();
            this.isBottom = isBottom;
            this.isFull = isFull;
            this.isLeft = isLeft;
            setCorrectIcon();
        }


        /**
         * @return if the label is to be at the bottom of the component
         */
        public boolean isBottom() {
            return isBottom;
        }

        /**
         * @return if the label is to be the full image
         */
        public boolean isFull() {
            return isFull;
        }

        /**
         * @return if the label is to be to the left
         */
        public boolean isLeft() {
            return isLeft;
        }

        public void toggleIcon() {
            isDelete = !isDelete;
            setCorrectIcon();
        }

        /**
         * Sets the icon to be displayed
         *
         * @param delete If the icon to be displayed is the delete one
         */
        public void setDeleteIcon(final boolean delete) {
            isDelete = delete;
            setCorrectIcon();
        }

        /**
         * @return The current imageicon
         */
        public ImageIcon getImageIcon() {
            return (ImageIcon) getIcon();
        }

        private void setCorrectIcon() {
            if (!isDelete) {
                setIcon(isFull ? FULL : (isLeft ? HALF_LEFT : HALF_RIGHT));
            } else {
                setIcon(isFull ? FULL_DELETE : (isLeft ? HALF_LEFT_DELETE : HALF_RIGHT_DELETE));
            }
        }

        /**
         * Rescales all images by the given factor
         *
         * @param factor The factor
         */
        public static void rescaleAll(final int factor) {
            FULL = rescale(new ImageIcon(Images.get("link.png")), factor);
            FULL_DELETE = rescale(new ImageIcon(Images.get("unlink.png")), factor);
            HALF_RIGHT = rescale(new ImageIcon(Images.get("link1.png")), factor);
            HALF_RIGHT_DELETE = rescale(new ImageIcon(Images.get("unlink1.png")), factor);
            HALF_LEFT = rescale(new ImageIcon(Images.get("link2.png")), factor);
            HALF_LEFT_DELETE = rescale(new ImageIcon(Images.get("unlink2.png")), factor);
        }

        private static ImageIcon rescale(final ImageIcon icon) {
            return rescale(icon, 8);
        }

        private static ImageIcon rescale(final ImageIcon icon, final int factor) {
            return new ImageIcon(icon.getImage().getScaledInstance(icon.getIconWidth() / factor,
                    icon.getIconHeight() / factor, Image.SCALE_SMOOTH));
        }
    }


    private class PropertiesListener implements PropertyChangeListener {
        protected MarkupAnnotation lastSelectedMarkupAnnotation;

        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
            final Object newValue = evt.getNewValue();
            final Object oldValue = evt.getOldValue();
            final String propertyName = evt.getPropertyName();
            switch (propertyName) {
                case PropertyConstants.ANNOTATION_DELETED:
                    if (oldValue instanceof MarkupAnnotationComponent) {
                        // find an remove the markup annotation node.
                        hasAutomaticallyChanged = true;
                        final MarkupAnnotationComponent comp = (MarkupAnnotationComponent) oldValue;
                        final MarkupAnnotation markupAnnotation = (MarkupAnnotation) comp.getAnnotation();
                        final Reference ref = markupAnnotation.getPObjectReference();
                        if (ref != null) {
                            AnnotationSummaryBox box = annotationToBox.get(ref);
                            if (box == null) {
                                if (comp.getPopupAnnotationComponent() != null &&
                                        comp.getPopupAnnotationComponent().getAnnotation() != null) {
                                    box = annotationToBox.get(comp.getPopupAnnotationComponent()
                                            .getAnnotation().getPObjectReference());
                                }
                            }
                            if (box == null) {
                                if (comp.getPopupAnnotationComponent() != null &&
                                        comp.getPopupAnnotationComponent().getAnnotationParentComponent() != null &&
                                        comp.getPopupAnnotationComponent().getAnnotationParentComponent().getAnnotation() != null) {
                                    box =
                                            annotationToBox.get(comp.getPopupAnnotationComponent()
                                                    .getAnnotationParentComponent().getAnnotation().getPObjectReference());
                                }
                            }
                            if (box == null) {
                                final ColorLabelPanel panel = getColorPanelFor(markupAnnotation, true);
                                if (panel != null) {
                                    panel.removeAnnotation(markupAnnotation);
                                }
                            } else {
                                box.delete();
                                dragManager.unlinkComponent(box, false);
                            }
                            annotationToBox.remove(ref);
                            annotationToColorPanel.remove(ref);
                        }
                    }
                    break;
                case PropertyConstants.ANNOTATION_UPDATED:
                    final MarkupAnnotation annotation = newValue instanceof PopupAnnotationComponent ?
                            ((PopupAnnotationComponent) newValue).getAnnotation().getParent() :
                            newValue instanceof MarkupAnnotationComponent ?
                                    (MarkupAnnotation) ((MarkupAnnotationComponent) newValue).getAnnotation() : null;
                    if (annotation != null) {
                        hasAutomaticallyChanged = true;
                        final ColorLabelPanel oldPanel = getColorPanelFor(annotation, true);
                        final ColorLabelPanel newPanel = getColorPanelFor(annotation, false);
                        final AnnotationSummaryBox box = annotationToBox.get(annotation.getPObjectReference());
                        if (box != null) {
                            if (!oldPanel.equals(newPanel)) {
                                dragManager.unlinkComponent(box, false);
                                box.moveToCorrectPanel();
                            }
                            box.refresh();
                        } else {
                            if (oldPanel != newPanel || (oldPanel != null && !oldPanel.contains(annotation))) {
                                if (oldPanel != null) oldPanel.removeAnnotation(annotation);
                                if (newPanel != null) {
                                    final AnnotationSummaryBox newBox = newPanel.addAnnotation(annotation);
                                    if (newBox != null) {
                                        annotationToBox.put(annotation.getPObjectReference(), newBox);
                                    }
                                }
                                annotationToColorPanel.put(annotation.getPObjectReference(), newPanel);
                            } else {
                                if (newPanel != null) newPanel.updateAnnotation(annotation);
                            }
                        }
                        refreshPanelLayout();
                    }
                    break;
                case PropertyConstants.ANNOTATION_ADDED:
                    // rebuild the tree so we get a good sort etc and do  worker thread setup.
                    if (newValue instanceof PopupAnnotationComponent) {
                        hasAutomaticallyChanged = true;
                        // find an remove the markup annotation node.
                        if (annotationNamedColorPanels != null) {
                            final PopupAnnotationComponent comp = (PopupAnnotationComponent) newValue;
                            final MarkupAnnotation markupAnnotation = comp.getAnnotation().getParent();
                            if (markupAnnotation != null) {
                                final ColorLabelPanel panel = getColorPanelFor(markupAnnotation, false);
                                if (panel != null) {
                                    final AnnotationSummaryBox box = panel.addAnnotation(markupAnnotation);
                                    if (box != null) {
                                        annotationToBox.put(markupAnnotation.getPObjectReference(), box);
                                    }
                                    annotationToColorPanel.put(markupAnnotation.getPObjectReference(), panel);
                                    refreshPanelLayout();
                                }
                            }
                        }
                    }
                    break;
                case PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE:
                    if (lastSelectedMarkupAnnotation != null) {
                        // find and remove,
                        hasAutomaticallyChanged = true;
                        final AnnotationSummaryBox box = annotationToBox.get(lastSelectedMarkupAnnotation.getPObjectReference());
                        if (box != null) {
                            dragManager.unlinkComponent(box, false);
                            box.moveToCorrectPanel();
                        } else if (annotationNamedColorPanels != null) {
                            for (final ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                                annotationColumnPanel.removeAnnotation(lastSelectedMarkupAnnotation);
                                refreshPanelLayout();
                            }
                            // and then add back in.
                            final ColorLabelPanel panel = getColorPanelFor(lastSelectedMarkupAnnotation, false);
                            if (panel != null) {
                                annotationToColorPanel.put(lastSelectedMarkupAnnotation.getPObjectReference(), panel);
                                final AnnotationSummaryBox newBox = panel.addAnnotation(lastSelectedMarkupAnnotation);
                                if (newBox != null) {
                                    annotationToBox.put(lastSelectedMarkupAnnotation.getPObjectReference(), newBox);
                                }
                            }
                        }
                        refreshPanelLayout();
                    }
                    break;
                case PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE:
                    // no choice but to do a full refresh, order will be lost.
                    refreshDocumentInstance();
                    hasAutomaticallyChanged = true;
                    break;
                case PropertyConstants.ANNOTATION_SELECTED:
                case PropertyConstants.ANNOTATION_FOCUS_GAINED:
                    if (newValue instanceof MarkupAnnotationComponent) {
                        lastSelectedMarkupAnnotation = (MarkupAnnotation) ((MarkupAnnotationComponent) newValue).getAnnotation();
                    }
                    break;
                case PropertyConstants.ANNOTATION_SUMMARY_UPDATED:
                    hasAutomaticallyChanged = true;
                    break;
                default:

            }
        }
    }

    private class SummaryMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() == 2) {
                final Component comp = (Component) e.getSource();
                if (annotationNamedColorPanels != null) {
                    final double weightX = 1.0 / (float) annotationNamedColorPanels.size();
                    final GridBagLayout gridBagLayout = (GridBagLayout) summaryPanel.getAnnotationsPanel().getLayout();
                    for (final ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                        final GridBagConstraints constraints = gridBagLayout.getConstraints(colorLabelPanel);
                        constraints.weightx = colorLabelPanel.equals(comp) ? 0.9 : weightX;
                        gridBagLayout.setConstraints(colorLabelPanel, constraints);
                        colorLabelPanel.invalidate();
                    }
                    summaryPanel.revalidate();
                }
            }
        }
    }

    private class SummaryComponentListener extends ComponentAdapter {
        @Override
        public void componentResized(final ComponentEvent e) {
            // reset the constraint back to an even division of
            if (annotationNamedColorPanels != null) {
                final double weightX = 1.0 / (float) annotationNamedColorPanels.size();
                final GridBagLayout gridBagLayout = (GridBagLayout) summaryPanel.getAnnotationsPanel().getLayout();
                for (final ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                    final GridBagConstraints constraints = gridBagLayout.getConstraints(colorLabelPanel);
                    constraints.weightx = weightX;
                    gridBagLayout.setConstraints(colorLabelPanel, constraints);
                }
                summaryPanel.invalidate();
                summaryPanel.revalidate();
            }
        }
    }
}
