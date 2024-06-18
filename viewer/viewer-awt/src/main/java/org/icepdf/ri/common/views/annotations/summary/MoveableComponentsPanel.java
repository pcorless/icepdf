package org.icepdf.ri.common.views.annotations.summary;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Represents a JPanel whose components can be moved up or down (swapping each other)
 */
public class MoveableComponentsPanel extends JPanel {

    /**
     * Moves a component to a given index
     *
     * @param component The component to move
     * @param idx       The index to move the component to
     */
    protected void move(final Component component, final int idx) {
        if (Arrays.asList(getComponents()).contains(component)) {
            remove(component);
            add(component, idx);
        }
        revalidate();
        repaint();
    }

    /**
     * Moves a component up
     *
     * @param c The component
     */
    protected void moveUp(final Component c) {
        final List<Component> components = Arrays.asList(getComponents());
        final int idx = components.indexOf(c);
        if (idx > 0) {
            move(c, idx - 1);
        }
    }

    /**
     * Moves a component down
     *
     * @param c The component
     */
    protected void moveDown(final Component c) {
        final List<Component> components = Arrays.asList(getComponents());
        final int idx = components.indexOf(c);
        if (idx != -1 && idx < components.size() - 1) {
            move(c, idx + 1);
        }
    }

    /**
     * Moves all the components given either up or down
     *
     * @param toMove The components to move
     * @param up     Up or down (true or false)
     */
    public void moveAll(final Collection<Component> toMove, final boolean up) {
        final List<Component> components = Arrays.asList(getComponents());
        if (components.containsAll(toMove)) {
            final List<Component> sorted = getSortedList(toMove, up);
            sorted.forEach(c -> {
                if (up) {
                    moveUp(c);
                } else {
                    moveDown(c);
                }
            });
        }
    }

    /**
     * Returns a sorted list for a given collection of components to move and a direction
     *
     * @param toSort    The collection to sort
     * @param ascending Whether the list must be in ascending or descending order
     * @return The sorted list
     */
    public List<Component> getSortedList(final Collection<Component> toSort, final boolean ascending) {
        final List<Component> components = Arrays.asList(getComponents());
        if (components.containsAll(toSort)) {
            final List<Component> sorted = new ArrayList<>(toSort);
            sorted.sort((component, t1) -> {
                final int idx1 = components.indexOf(component);
                final int idx2 = components.indexOf(t1);
                return ascending ? Integer.compare(idx1, idx2) : Integer.compare(idx2, idx1);
            });
            return sorted;
        } else return null;
    }
}
