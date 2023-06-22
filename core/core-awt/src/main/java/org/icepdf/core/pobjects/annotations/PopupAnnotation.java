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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.time.format.FormatStyle;
import java.util.logging.Logger;

/**
 * A pop-up annotation (PDF 1.3) displays text in a pop-up window for entry and
 * editing. It shall not appear alone but is associated with a markup annotation,
 * its parent annotation, and shall be used for editing the parent’s text. It
 * shall have no appearance stream or associated actions of its own and shall be
 * identified by the Popup entry in the parent’s annotation dictionary
 * (see Table 174). Table 183 shows the annotation dictionary entries specific to
 * this type of annotation.A pop-up annotation (PDF 1.3) displays text in a pop-up
 * window for entry and editing. It shall not appear alone but is associated
 * with a markup annotation, its parent annotation, and shall be used for editing
 * the parent’s text. It shall have no appearance stream or associated actions
 * of its own and shall be identified by the Popup entry in the parent’s annotation
 * dictionary (see Table 174). Table 183 shows the annotation dictionary entries
 * specific to this type of annotation.
 *
 * @since 5.0
 */
public class PopupAnnotation extends Annotation {

    private static final Logger logger =
            Logger.getLogger(PopupAnnotation.class.toString());

    public static final Color BORDER_COLOR = new Color(153, 153, 153);

    /**
     * (Optional; shall be an indirect reference) The parent annotation with
     * which this pop-up annotation shall be associated.
     * <br>
     * If this entry is present, the parent annotation’s Contents, M, C, and T
     * entries (see Table 168) shall override those of the pop-up annotation
     * itself.
     */
    public static final Name PARENT_KEY = new Name("Parent");
    /**
     * (Optional) A flag specifying whether the pop-up annotation shall
     * initially be displayed open. Default value: false (closed).
     */
    public static final Name OPEN_KEY = new Name("Open");

    protected MarkupAnnotation parent;

    protected final float fontSize = new JLabel().getFont().getSize();
    protected float textAreaFontsize = fontSize;
    protected float headerLabelsFontSize = fontSize;


    protected JPanel popupPaintablesPanel;
    protected JLabel creationLabel;
    protected JLabel titleLabel;
    protected JTextArea textArea;
    private boolean resetPopupPaintables = true;

    public PopupAnnotation(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
    }

    public synchronized void init() throws InterruptedException {
        super.init();
    }

    /**
     * Gets an instance of a PopupAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new PopupAnnotation Instance.
     */
    public static PopupAnnotation getInstance(Library library,
                                              Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        DictionaryEntries entries = new DictionaryEntries();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_POPUP);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        PopupAnnotation popupAnnotation = null;
        try {
            popupAnnotation = new PopupAnnotation(library, entries);
            popupAnnotation.init();
            popupAnnotation.setPObjectReference(stateManager.getNewReferenceNumber());
            popupAnnotation.setNew(true);

            // set default flags.
            popupAnnotation.setFlag(Annotation.FLAG_READ_ONLY, false);
            popupAnnotation.setFlag(Annotation.FLAG_NO_ROTATE, false);
            popupAnnotation.setFlag(Annotation.FLAG_NO_ZOOM, false);
            popupAnnotation.setFlag(Annotation.FLAG_PRINT, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.finer("Popup Annotation initialization was interrupted");
        }

        return popupAnnotation;
    }

    @Override
    protected void renderAppearanceStream(Graphics2D g2d, float rotation, float zoom) {
        GraphicsConfiguration graphicsConfiguration = g2d.getDeviceConfiguration();
        boolean isPrintingAllowed = getParent().getFlagPrint();
        if (graphicsConfiguration.getDevice().getType() == GraphicsDevice.TYPE_PRINTER &&
                isOpen() &&
                isPrintingAllowed) {
            if (resetPopupPaintables) {
                buildPopupPaintables();
            }
            applyFontScaling(zoom);
            paintPopupPaintables(g2d);
        }
    }

    public boolean allowPrintNormalMode() {
        boolean isParentPrintable = parent != null && parent.getFlagPrint();
        return allowScreenOrPrintRenderingOrInteraction() && isParentPrintable;
    }

    private void paintPopupPaintables(Graphics2D g2d) {

        Rectangle2D.Float popupBounds = getUserSpaceRectangle();
        Dimension popupSize = popupBounds.getBounds().getSize();

        AffineTransform oldTransform = g2d.getTransform();
        g2d.scale(1, -1);
        g2d.translate(0, -popupSize.height);

        popupPaintablesPanel.print(g2d);
        g2d.setColor(BORDER_COLOR);
        g2d.drawRect(0, 0, popupSize.width - 1, popupSize.height - 1);

        g2d.setTransform(oldTransform);
    }

    private void applyFontScaling(float zoom) {
        PopupAnnotation.updateTextAreaFontSize(titleLabel, headerLabelsFontSize, zoom);
        PopupAnnotation.updateTextAreaFontSize(creationLabel, headerLabelsFontSize, zoom);
        PopupAnnotation.updateTextAreaFontSize(textArea, textAreaFontsize, zoom);
    }

    /**
     * Builds a JPanel representing the popup annotation that can be printed.
     */
    private void buildPopupPaintables() {

        Rectangle2D.Float popupBounds = getUserSpaceRectangle();
        Rectangle popupBoundsNormalized = new Rectangle2D.Double(0, 0, popupBounds.width, popupBounds.height).getBounds();

        popupPaintablesPanel = new JPanel();

        Color color = getParent().getColor() != null ?
                getParent().getColor() :
                new Color(Integer.parseInt("ffff00", 16));
        Color contrastColor = calculateContrastHighLowColor(color.getRGB());
        popupPaintablesPanel.setBackground(color);
        popupPaintablesPanel.setBounds(popupBoundsNormalized);
        popupPaintablesPanel.setSize(popupBoundsNormalized.getSize());
        popupPaintablesPanel.setPreferredSize(popupBoundsNormalized.getSize());

        MarkupAnnotation markupAnnotation = getParent();
        // user
        String title = markupAnnotation.getFormattedTitleText();
        titleLabel = new JLabel(title);
        titleLabel.setForeground(contrastColor);
        popupPaintablesPanel.add(titleLabel);

        // creation date
        creationLabel = new JLabel();
        creationLabel.setText(markupAnnotation.getFormattedCreationDate(FormatStyle.MEDIUM));
        creationLabel.setForeground(contrastColor);
        popupPaintablesPanel.add(creationLabel);

        // text area
        String contents = getParent() != null ? getParent().getContents() : "";
        textArea = new JTextArea(contents);
        Font font = new JLabel().getFont();
        font = font.deriveFont(fontSize);
        textArea.setFont(font);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        popupPaintablesPanel.add(textArea);

        // basic layout
        Component[] components = popupPaintablesPanel.getComponents();
        int yOffset = 0;
        int padding = 2;
        for (Component comp : components) {
            Dimension size = comp.getPreferredSize();
            comp.setSize(size);
            comp.setPreferredSize(size);
            comp.setLocation(padding, yOffset);
            yOffset += comp.getHeight() + padding;
        }
        // stretch text area.
        textArea.setSize((int) (popupBounds.width - (padding * 2)), (int) (popupBounds.height + textArea.getHeight() - yOffset));
        resetPopupPaintables = false;
    }

    public Color calculateContrastHighLowColor(int rgb) {
        int tolerance = 120;
        if ((rgb & 0xFF) <= tolerance &&
                (rgb >> 8 & 0xFF) <= tolerance ||
                (rgb >> 16 & 0xFF) <= tolerance) {
            return Color.WHITE;
        } else {
            return Color.BLACK;
        }

    }

    public void updatePaintables() {
        resetPopupPaintables = true;
    }

    public void setUserSpaceRectangle(Rectangle2D.Float rect) {
        super.setUserSpaceRectangle(rect);
        resetPopupPaintables = true;
    }

    public float getTextAreaFontsize() {
        return textAreaFontsize;
    }

    public void setTextAreaFontsize(float textAreaFontsize) {
        this.textAreaFontsize = textAreaFontsize;
    }

    public float getHeaderLabelsFontSize() {
        return headerLabelsFontSize;
    }

    public void setHeaderLabelsFontSize(float headerLabelsFontSize) {
        this.headerLabelsFontSize = headerLabelsFontSize;
    }

    public static void updateTextAreaFontSize(Component component, float fontSize, float zoom) {
        final float scaledFontSize = fontSize * zoom;
        final Font font = component.getFont();
        component.setFont(font.deriveFont(scaledFontSize));
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform, boolean isNew) {

    }

    public boolean isOpen() {
        return library.getBoolean(entries, OPEN_KEY);
    }

    public void setOpen(boolean open) {
        entries.put(OPEN_KEY, open);
    }

    public MarkupAnnotation getParent() {
        Object tmp = library.getObject(entries, PARENT_KEY);
        // should normally be a text annotation type.
        if (tmp instanceof MarkupAnnotation) {
            parent = (MarkupAnnotation) tmp;
        }
        return parent;
    }

    public void setParent(MarkupAnnotation parent) {
        this.parent = parent;
        entries.put(PARENT_KEY, parent.getPObjectReference());
    }
}
