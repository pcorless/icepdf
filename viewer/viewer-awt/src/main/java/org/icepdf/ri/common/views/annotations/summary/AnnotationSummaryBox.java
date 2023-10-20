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
package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.ri.common.views.*;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;
import org.icepdf.ri.common.views.annotations.summary.menu.BoxMenuFactory;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.logging.Level;


/**
 * Class representing an annotation in the summary panel
 */
public class AnnotationSummaryBox extends PopupAnnotationComponent implements FocusListener, AnnotationSummaryComponent {

    private Border unselectedHeaderBorder = BorderFactory.createLineBorder(PopupAnnotation.BORDER_COLOR);
    private Border unselectedNoHeaderBorder = BorderFactory.createMatteBorder(10, 1, 1, 1, getColor());
    private Border selectedNoHeaderBorder = BorderFactory.createCompoundBorder(SELECTED_BORDER, unselectedNoHeaderBorder);
    protected boolean showHeader = true;
    protected boolean showTextBlock = true;
    protected final SummaryController summaryController;
    private boolean isComponentSelected;
    private final Reference reference;
    private final Annotation componentAnnotation;


    public AnnotationSummaryBox(final PopupAnnotation annotation, final DocumentViewController documentViewController,
                                final AbstractPageViewComponent pageViewComponent, final SummaryController summaryController) {
        super(annotation, documentViewController, pageViewComponent, true);
        Arrays.stream(commentTree.getMouseListeners()).skip(1).forEach(ml -> commentTree.removeMouseListener(ml));
        this.summaryController = summaryController;

        removeFocusListener(this);

        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        commentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        // hides a bunch of the controls.
        commentPanel.removeMouseListener(popupListener);
        commentPanel.removeMouseListener(this);
        commentPanel.removeMouseMotionListener(this);

        privateToggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        minimizeButton.setVisible(false);
        textArea.setEditable(isCurrentUserOwner(selectedMarkupAnnotation));
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        commentPanel.getInsets().set(10, 10, 10, 10);
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        final ViewerPropertiesManager propertiesManager = documentViewController.getParentController().getPropertiesManager();
        setFontSize(propertiesManager.getPreferences().getInt(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE, new JLabel().getFont().getSize()));
        // remove super mouse listener as it interferes with the drag and drop.
        removeMouseWheelListener(this);
        this.reference = getAnnotationComponent().getAnnotation().getPObjectReference();
        this.componentAnnotation = getAnnotationComponent().getAnnotation();
        setRequestFocusEnabled(true);
        setFocusable(true);
        privateToggleButton.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(final MouseEvent e) {
                repaint();
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                repaint();
            }

        });
        resetComponentColors();
    }


    public void setTextBlockVisibility(final boolean visible) {
        showTextBlock = visible;
        textArea.setVisible(showTextBlock);
        validate();
    }

    public void toggleTextBlockVisibility() {
        showTextBlock = !showTextBlock;
        setTextBlockVisibility(showTextBlock);
    }

    @Override
    public boolean isShowTextBlockVisible() {
        return showTextBlock;
    }

    @Override
    public void setBounds(final int x, final int y, final int width, final int height) {
        final Rectangle boundRectangle = limitAnnotationPosition(x, y, width, height);
        super.setBounds(boundRectangle.x, boundRectangle.y, boundRectangle.width, boundRectangle.height);
    }

    @Override
    public void toggleHeaderVisibility() {
        showHeader = !showHeader;
        setHeaderVisibility(showHeader);
    }

    @Override
    public void setHeaderVisibility(final boolean visible) {
        showHeader = visible;
        titleLabel.setVisible(showHeader);
        creationLabel.setVisible(showHeader);
        privateToggleButton.setVisible(SystemProperties.PRIVATE_PROPERTY_ENABLED && showHeader);
        setCorrectBorder();
        validate();
    }

    @Override
    public boolean isHeaderVisible() {
        return showHeader;
    }

    @Override
    protected void resetComponentColors() {
        final Color color = getColor();
        popupBackgroundColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), TextMarkupAnnotation.HIGHLIGHT_ALPHA);
        super.resetComponentColors();
        commentPanel.setBackground(popupBackgroundColor);
    }

    protected void updateContent(final DocumentEvent e) {
        // get the next text and save it to the selected markup annotation.
        final Document document = e.getDocument();
        try {
            if (document.getLength() > 0) {
                selectedMarkupAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
                selectedMarkupAnnotation.setContents(
                        document.getText(0, document.getLength()));
                // add them to the container, using absolute positioning.
                documentViewController.updatedSummaryAnnotation(this);
            }
        } catch (final BadLocationException ex) {
            logger.log(Level.FINE, "Error updating markup annotation content", ex);
        }
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source == null)
            return;
        if (source == privateToggleButton) {
            final boolean selected = privateToggleButton.isSelected();
            if (selectedMarkupAnnotation != null) {
                selectedMarkupAnnotation.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, selected);
                selectedMarkupAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
                documentViewController.updatedSummaryAnnotation(this);
            }
            repaint();
        }
    }

    public Color getColor() {
        if (getAnnotationComponent() != null && getAnnotationComponent().getAnnotation() != null) {
            return getAnnotationComponent().getAnnotation().getColor();
        } else {
            return popupBackgroundColor;
        }
    }

    @Override
    public void moveTo(final Color c, final boolean isTopComponent) {
        final Annotation annot = getAnnotationComponent().getAnnotation();
        final Color oldColor = annot.getColor();
        annot.setColor(c);
        annot.getPage().updateAnnotation(annot);
        resetComponentColors();
        summaryController.getController().getDocumentViewController().updateAnnotation(getAnnotationComponent());
        if (isTopComponent) {
            summaryController.moveTo(this, c, oldColor);
        }
        getAnnotationComponent().resetAppearanceShapes();
        getAnnotationComponent().repaint();
        refreshBorders();
        repaint();
        summaryController.getController().getDocumentViewController().updatedSummaryAnnotation(this);
    }

    /**
     * Updates the column of the component
     */
    public void moveToCorrectPanel() {
        final Color newColor = getColor();
        if (!popupBackgroundColor.equals(newColor)) {
            summaryController.moveTo(this, newColor, popupBackgroundColor);
            resetComponentColors();
        }
        refreshBorders();
        repaint();
    }

    public Controller getController() {
        return documentViewController.getParentController();
    }

    public JPopupMenu getContextMenu(final Frame frame, final DraggablePanelController panel) {
        final MarkupAnnotationComponent comp = getAnnotationComponent();
        return BoxMenuFactory.createBoxPopupMenu(this, comp, frame, panel, summaryController);
    }

    private MarkupAnnotationComponent getAnnotationComponent() {
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent) getAnnotationParentComponent();
        // page may not have been initialized and thus we don't have a component
        if (comp == null) {
            final int pageIndex = annotation.getParent().getPageIndex();
            final PageViewComponentImpl pageViewComponent = (PageViewComponentImpl)
                    documentViewController.getParentController().getDocumentViewController()
                            .getDocumentViewModel().getPageComponents().get(pageIndex);
            pageViewComponent.refreshAnnotationComponents(pageViewComponent.getPage(), false);
            final ArrayList<AbstractAnnotationComponent> comps = pageViewComponent.getAnnotationComponents();
            for (final AbstractAnnotationComponent abstractComp : comps) {
                final Annotation pageAnnotation = abstractComp.getAnnotation();
                final Annotation parent = annotation.getParent();
                if (pageAnnotation != null && parent != null &&
                        pageAnnotation.getPObjectReference().equals(parent.getPObjectReference())) {
                    comp = (MarkupAnnotationComponent) abstractComp;
                    break;
                }
            }
        }
        return comp;
    }

    @Override
    public boolean delete() {
        if (isCurrentUserOwner(annotation.getParent())) {
            documentViewController.deleteAnnotation(getAnnotationComponent());
            Arrays.stream(getPropertyChangeListeners()).forEach(this::removePropertyChangeListener);
            summaryController.getGroupManager().removeFromGroup(this);
            summaryController.getDragAndLinkManager().unlinkComponent(this, false);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void refresh() {
        refreshBorders();
        refreshPopupState();
        validate();
        repaint();
    }

    public void refreshBorders() {
        unselectedHeaderBorder = BorderFactory.createLineBorder(PopupAnnotation.BORDER_COLOR);
        unselectedNoHeaderBorder = BorderFactory.createMatteBorder(10, 1, 1, 1, getColor());
        selectedNoHeaderBorder = BorderFactory.createCompoundBorder(SELECTED_BORDER, unselectedNoHeaderBorder);
        setCorrectBorder();
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        return Collections.singletonList(componentAnnotation);
    }

    @Override
    public Collection<AnnotationComponent> getAnnotationComponents() {
        return Collections.singletonList(getAnnotationComponent());
    }

    @Override
    public void setComponentSelected(final boolean b) {
        isComponentSelected = b;
        setCorrectBorder();
    }

    private void setCorrectBorder() {
        if (showHeader) {
            if (isComponentSelected) {
                setBorder(SELECTED_BORDER);
            } else {
                setBorder(unselectedHeaderBorder);
            }
        } else {
            if (isComponentSelected) {
                setBorder(selectedNoHeaderBorder);
            } else {
                setBorder(unselectedNoHeaderBorder);
            }
        }
    }

    @Override
    public boolean isComponentSelected() {
        return isComponentSelected;
    }

    @Override
    public int hashCode() {
        return reference.hashCode();
    }

    @Override
    public boolean equals(final Object that) {
        return that instanceof AnnotationSummaryBox && reference.equals(((AnnotationSummaryBox) that).reference);
    }

    @Override
    public String getDebuggable() {
        return reference.toString();
    }

    @Override
    public void setFontSize(final float size) {
        titleLabel.setFont(titleLabel.getFont().deriveFont(size));
        creationLabel.setFont(titleLabel.getFont().deriveFont(size));
        textArea.setFont(titleLabel.getFont().deriveFont(size));
    }

    @Override
    public void fireComponentMoved(final boolean snap, final boolean check, final UUID uuid) {
        summaryController.getDragAndLinkManager().componentMoved(this, snap, check, uuid);
        repaint();
    }

    @Override
    public Component asComponent() {
        return this;
    }

    @Override
    public Container asContainer() {
        return this;
    }

    public boolean canEdit() {
        return isCurrentUserOwner();
    }

    private boolean isCurrentUserOwner() {
        return ((MarkupAnnotation) getMarkupAnnotationComponent().getAnnotation()).getTitleText().equals(SystemProperties.USER_NAME);
    }

    private static boolean isCurrentUserOwner(final MarkupAnnotation annotation) {
        return annotation.getTitleText().equals(SystemProperties.USER_NAME);
    }
}