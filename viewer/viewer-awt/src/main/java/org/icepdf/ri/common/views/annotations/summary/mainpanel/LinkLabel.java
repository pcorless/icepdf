package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.awt.*;

/**
 * Class representing a label with a Link icon
 */
public class LinkLabel extends JLabel {
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
