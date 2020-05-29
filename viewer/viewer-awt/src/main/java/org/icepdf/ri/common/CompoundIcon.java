package org.icepdf.ri.common;

import javax.swing.*;
import java.awt.*;

/**
 * The CompoundIcon will paint two, or more, Icons as a single Icon. The
 * Icons are painted in the order in which they are added.
 * <p>
 * The Icons are layed out on the specified axis:
 * <ul>
 * <li>X-Axis (horizontally)
 * <li>Y-Axis (vertically)
 * <li>Z-Axis (stacked)
 * </ul>
 */
//Taken from https://tips4java.wordpress.com/2009/03/29/compound-icon/
public class CompoundIcon implements Icon {
    public enum Axis {
        X_AXIS,
        Y_AXIS,
        Z_AXIS
    }

    public static final float TOP = 0.0f;
    public static final float LEFT = 0.0f;
    public static final float CENTER = 0.5f;
    public static final float BOTTOM = 1.0f;
    public static final float RIGHT = 1.0f;

    private final Icon[] icons;

    private final Axis axis;

    private final int gap;

    private float alignmentX = CENTER;
    private float alignmentY = CENTER;

    /**
     * Convenience constructor for creating a CompoundIcon where the
     * icons are laid out on the X-AXIS, the gap is 0 and the
     * X/Y alignments will default to CENTER.
     *
     * @param icons the Icons to be painted as part of the CompoundIcon
     */
    public CompoundIcon(final Icon... icons) {
        this(Axis.X_AXIS, icons);
    }

    /**
     * Convenience constructor for creating a CompoundIcon where the
     * gap is 0 and the X/Y alignments will default to CENTER.
     *
     * @param axis  the axis used to lay out the icons for painting.
     *              Must be one of the Axis enums: X_AXIS, Y_AXIS, Z_Axis.
     * @param icons the Icons to be painted as part of the CompoundIcon
     */
    public CompoundIcon(final Axis axis, final Icon... icons) {
        this(axis, 0, icons);
    }

    /**
     * Convenience contrusctor for creating a CompoundIcon where the
     * X/Y alignments will default to CENTER.
     *
     * @param axis  the axis used to lay out the icons for painting
     *              Must be one of the Axis enums: X_AXIS, Y_AXIS, Z_Axis.
     * @param gap   the gap between the icons
     * @param icons the Icons to be painted as part of the CompoundIcon
     */
    public CompoundIcon(final Axis axis, final int gap, final Icon... icons) {
        this(axis, gap, CENTER, CENTER, icons);
    }

    /**
     * Create a CompoundIcon specifying all the properties.
     *
     * @param axis       the axis used to lay out the icons for painting
     *                   Must be one of the Axis enums: X_AXIS, Y_AXIS, Z_Axis.
     * @param gap        the gap between the icons
     * @param alignmentX the X alignment of the icons. Common values are
     *                   LEFT, CENTER, RIGHT. Can be any value between 0.0 and 1.0
     * @param alignmentY the Y alignment of the icons. Common values are
     *                   TOP, CENTER, BOTTOM. Can be any value between 0.0 and 1.0
     * @param icons      the Icons to be painted as part of the CompoundIcon
     */
    public CompoundIcon(final Axis axis, final int gap, final float alignmentX, final float alignmentY, final Icon... icons) {
        this.axis = axis;
        this.gap = gap;
        this.alignmentX = alignmentX > 1.0f ? 1.0f : Math.max(alignmentX, 0.0f);
        this.alignmentY = alignmentY > 1.0f ? 1.0f : Math.max(alignmentY, 0.0f);

        for (int i = 0; i < icons.length; i++) {
            if (icons[i] == null) {
                final String message = "Icon (" + i + ") cannot be null";
                throw new IllegalArgumentException(message);
            }
        }

        this.icons = icons;
    }

    /**
     * Get the Axis along which each icon is painted.
     *
     * @return the Axis
     */
    public Axis getAxis() {
        return axis;
    }

    /**
     * Get the gap between each icon
     *
     * @return the gap in pixels
     */
    public int getGap() {
        return gap;
    }

    /**
     * Get the alignment of the icon on the x-axis
     *
     * @return the alignment
     */
    public float getAlignmentX() {
        return alignmentX;
    }

    /**
     * Get the alignment of the icon on the y-axis
     *
     * @return the alignment
     */
    public float getAlignmentY() {
        return alignmentY;
    }

    /**
     * Get the number of Icons contained in this CompoundIcon.
     *
     * @return the total number of Icons
     */
    public int getIconCount() {
        return icons.length;
    }

    /**
     * Get the Icon at the specified index.
     *
     * @param index the index of the Icon to be returned
     * @return the Icon at the specifed index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Icon getIcon(final int index) {
        return icons[index];
    }

    /**
     * Gets the width of this icon.
     *
     * @return the width of the icon in pixels.
     */
    @Override
    public int getIconWidth() {
        int width = 0;

        //  Add the width of all Icons while also including the gap

        if (axis == Axis.X_AXIS) {
            width += (icons.length - 1) * gap;

            for (final Icon icon : icons)
                width += icon.getIconWidth();
        } else  //  Just find the maximum width
        {
            for (final Icon icon : icons)
                width = Math.max(width, icon.getIconWidth());
        }

        return width;
    }

    /**
     * Gets the height of this icon.
     *
     * @return the height of the icon in pixels.
     */
    @Override
    public int getIconHeight() {
        int height = 0;

        //  Add the height of all Icons while also including the gap

        if (axis == Axis.Y_AXIS) {
            height += (icons.length - 1) * gap;

            for (final Icon icon : icons)
                height += icon.getIconHeight();
        } else  //  Just find the maximum height
        {
            for (final Icon icon : icons)
                height = Math.max(height, icon.getIconHeight());
        }

        return height;
    }

    /**
     * Paint the icons of this compound icon at the specified location
     *
     * @param c The component on which the icon is painted
     * @param g the graphics context
     * @param x the X coordinate of the icon's top-left corner
     * @param y the Y coordinate of the icon's top-left corner
     */
    @Override
    public void paintIcon(final Component c, final Graphics g, int x, int y) {
        if (axis == Axis.X_AXIS) {
            final int height = getIconHeight();

            for (final Icon icon : icons) {
                final int iconY = getOffset(height, icon.getIconHeight(), alignmentY);
                icon.paintIcon(c, g, x, y + iconY);
                x += icon.getIconWidth() + gap;
            }
        } else if (axis == Axis.Y_AXIS) {
            final int width = getIconWidth();

            for (final Icon icon : icons) {
                final int iconX = getOffset(width, icon.getIconWidth(), alignmentX);
                icon.paintIcon(c, g, x + iconX, y);
                y += icon.getIconHeight() + gap;
            }
        } else // must be Z_AXIS
        {
            final int width = getIconWidth();
            final int height = getIconHeight();

            for (final Icon icon : icons) {
                final int iconX = getOffset(width, icon.getIconWidth(), alignmentX);
                final int iconY = getOffset(height, icon.getIconHeight(), alignmentY);
                icon.paintIcon(c, g, x + iconX, y + iconY);
            }
        }
    }

    /*
     *  When the icon value is smaller than the maximum value of all icons the
     *  icon needs to be aligned appropriately. Calculate the offset to be used
     *  when painting the icon to achieve the proper alignment.
     */
    private static int getOffset(final int maxValue, final int iconValue, final float alignment) {
        final float offset = (maxValue - iconValue) * alignment;
        return Math.round(offset);
    }
}