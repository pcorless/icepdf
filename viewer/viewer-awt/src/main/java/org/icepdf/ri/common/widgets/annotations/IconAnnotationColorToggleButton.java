package org.icepdf.ri.common.widgets.annotations;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.images.IconPack;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;

/**
 * AnnotationColorToggleButton using an icon/image with white pixels to dynamically change the icon color.
 */
public class IconAnnotationColorToggleButton extends AnnotationColorToggleButton {

    private final BufferedImage baseImage;
    private final float alpha;

    public IconAnnotationColorToggleButton(final Controller controller, final ResourceBundle messageBundle,
                                           final String title, final String toolTip, final String colorProperty,
                                           final String imageName, final Images.IconSize imageSize, final Font font, final float alpha) {
        this(controller, messageBundle, title, toolTip, colorProperty, imageName, imageSize, font, alpha, Color.YELLOW);
    }

    /**
     * Instantiates a button
     *
     * @param controller    The controller
     * @param messageBundle The resource bundle
     * @param title         The button title
     * @param toolTip       The button tooltip
     * @param colorProperty The color preferences property
     * @param imageName     The base image filename
     * @param imageSize     The image size
     * @param font          The font
     * @param alpha         The added color transparency
     * @param defaultColor  The default added color
     */
    public IconAnnotationColorToggleButton(final Controller controller, final ResourceBundle messageBundle,
                                           final String title, final String toolTip, final String colorProperty,
                                           final String imageName, final Images.IconSize imageSize, final Font font,
                                           final float alpha, final Color defaultColor) {
        super(controller, messageBundle, title, toolTip, colorProperty, imageName, imageSize, font);
        this.alpha = alpha;
        //final ImageIcon icon = new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png"));
        final Icon icon = Images.getSingleIcon (imageName, IconPack.Variant.NORMAL, imageSize);
        baseImage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = baseImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        setupLayout();
        final Color defColor = new Color(ViewerPropertiesManager.getInstance().checkAndStoreIntProperty(colorProperty, defaultColor.getRGB()));
        setColor(defColor, false);
    }

    @Override
    public void setColor(final Color newColor, final boolean fireChangeEvent) {
        final BufferedImage newImage = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < baseImage.getWidth(); ++x) {
            for (int y = 0; y < baseImage.getHeight(); ++y) {
                final Color orig = new Color(baseImage.getRGB(x, y), true);
                if (orig.getRed() == 255 && orig.getGreen() == 255 && orig.getBlue() == 255) {
                    newImage.setRGB(x, y, multiply(orig, newColor).getRGB());
                } else {
                    newImage.setRGB(x, y, orig.getRGB());
                }
            }
        }

        colorButton.setIcon(new ImageIcon(newImage));
        super.setColor(newColor, fireChangeEvent);
    }

    private Color multiply(final Color color1, final Color color2) {
        final float[] color1Components = color1.getRGBComponents(null);
        final float[] color2Components = color2.getRGBColorComponents(null);
        final float[] newComponents = new float[3];

        for (int i = 0; i < 3; ++i) {
            newComponents[i] = color1Components[i] * color2Components[i];
        }
        return new Color(newComponents[0], newComponents[1], newComponents[2], alpha);
    }
}
