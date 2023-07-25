package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Collection;
import java.util.UUID;

/**
 * Interface of a visible component in the summary panel
 */
public interface AnnotationSummaryComponent {
    Color SELECTED_COLOR = new Color(20, 20, 150);
    Border SELECTED_BORDER = BorderFactory.createLineBorder(SELECTED_COLOR, 2, true);

    /**
     * Toggles the text block visibility
     */
    void toggleTextBlockVisibility();

    /**
     * Sets the text block visibility
     *
     * @param visible true or false
     */
    void setTextBlockVisibility(boolean visible);

    /**
     * @return if the text block is visible
     */
    boolean isShowTextBlockVisible();

    /**
     * Toggles the header visibility
     */
    void toggleHeaderVisibility();

    /**
     * Sets the header visibility
     *
     * @param visible true or false
     */
    void setHeaderVisibility(boolean visible);

    /**
     * @return if the header is visible
     */
    boolean isHeaderVisible();

    /**
     * Returns the context menu (right-click) for this component
     *
     * @param frame The parent frame
     * @param panel The Panel
     * @return a JPopupMenu
     */
    JPopupMenu getContextMenu(Frame frame, DraggablePanelController panel);

    /**
     * @return This component's color
     */
    Color getColor();

    /**
     * Moves this component to another color
     *
     * @param c              The new color
     * @param isTopComponent If the method has been called on this component explicitly (false if it was called on its parent)
     */
    void moveTo(Color c, boolean isTopComponent);

    /**
     * Sets if the component is selected
     *
     * @param b true or false
     */
    void setComponentSelected(boolean b);

    /**
     * @return if the component is selected
     */
    boolean isComponentSelected();

    /**
     * Deletes the component (and its annotations)
     *
     * @return if the component has been deleted
     */
    boolean delete();

    /**
     * Refreshes the component
     */
    void refresh();

    /**
     * @return the annotations contained by this component
     */
    Collection<Annotation> getAnnotations();

    /**
     * @return the annotation components contained by this component
     */
    Collection<AnnotationComponent> getAnnotationComponents();

    @Override
    int hashCode();

    @Override
    boolean equals(Object that);

    /**
     * @return a debuggable string to more easily recognize this component
     */
    String getDebuggable();

    /**
     * Sets the font size
     *
     * @param size The new font size
     */
    void setFontSize(int size);

    /**
     * Sets the font family
     *
     * @param family The font
     */
    void setFontFamily(String family);

    void fireComponentMoved(boolean snap, boolean check, UUID uuid);

    /**
     * Casts this object to Component
     *
     * @return this as a Component
     */
    Component asComponent();

    /**
     * Casts this object to Container
     *
     * @return this as a Container
     */
    Container asContainer();

    /**
     * @return If the component can be edited by the current user (i. e. the user is the creator of all the components in the component)
     */
    boolean canEdit();
}
