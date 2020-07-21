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
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.ri.common.ViewModel;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;

/**
 * HighLightAnnotationHandler tool extends TextSelectionPageHandler which
 * takes care visually selected text as the mouse is dragged across text on the
 * current page.
 * <br>
 * Once the mouseReleased event is fired this handler will create new
 * HighLightAnnotation and respective AnnotationComponent.  The addition of the
 * Annotation object to the page is handled by the annotation callback. Once
 * create the handler will deselect the text and the newly created annotation
 * will be displayed.
 *
 * @since 5.0
 */
public class HighLightAnnotationHandler extends TextSelectionPageHandler implements ActionListener {

    /**
     * Property when enabled will set the /contents key value to the selected text of the markup annotation.
     */
    private static boolean enableHighlightContents;

    private static final int MULTI_CLICK_INTERVAL =
            (int) Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    private MouseEvent lastMouseClickEvent;
    private Timer mouseClickTimer;

    static {
        try {
            enableHighlightContents = Defs.booleanProperty(
                    "org.icepdf.core.views.page.annotation.highlightContent.enabled", true);
        } catch (NumberFormatException e) {
            logger.warning("Error reading highlight selection content enabled property.");
        }
    }

    protected Name highLightType;
    protected TextMarkupAnnotation annotation;

    public HighLightAnnotationHandler(DocumentViewController documentViewController,
                                      AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        // default type
        highLightType = Annotation.SUBTYPE_HIGHLIGHT;
        mouseClickTimer = new Timer(MULTI_CLICK_INTERVAL, this);
    }

    public void actionPerformed(ActionEvent e) {
        mouseClickTimer.stop();
        createMarkupAnnotationFromTextSelection(lastMouseClickEvent);
    }

    /**
     * Check for double and triple click word and line selection
     *
     * @param evt mouse event
     */
    public void mouseClicked(MouseEvent evt) {
        super.mouseClicked(evt);
        int clickCount = evt.getClickCount();
        if (clickCount == 1 || evt.getClickCount() > 3) {
            mouseClickTimer.stop();
            return;
        }
        lastMouseClickEvent = evt;
        // 2 comes before 3 so we do all this lifting to avoid double processing 2 and 3 clicks.
        if (clickCount == 2 && !mouseClickTimer.isRunning()) {
            mouseClickTimer.restart();
        } else if (clickCount == 3) {
            mouseClickTimer.stop();
            createMarkupAnnotationFromTextSelection(evt);
        }
    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) {
        if (isMouseDrag) {
            createMarkupAnnotationFromTextSelection(e);
        }
        super.mouseReleased(e);
    }

    public void createTextMarkupAnnotation(ArrayList<Shape> highlightBounds) {
        // mke sure we don't create a highlight annotation for every word in the
        // document when first selecting the tool for highlighted next. .
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (documentViewModel.isSelectAll()) {
            documentViewController.clearSelectedText();
        }

        // get the geometric path of the selected text
        if (highlightBounds == null) {
            highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());
        }
        // grab the selected text
        String contents = enableHighlightContents && highlightBounds != null ? getSelectedText() : "";

        // clear the selected text
        documentViewController.clearSelectedText();

        if (highlightBounds != null && highlightBounds.size() > 0) {

            // bound of the selected text
            GeneralPath highlightPath = new GeneralPath();
            for (Shape bounds : highlightBounds) {
                highlightPath.append(bounds, false);
            }
            // get the bounds before convert to page space
            Rectangle bounds = highlightPath.getBounds();

            Rectangle tBbox = convertToPageSpace(highlightBounds, highlightPath);

            AffineTransform pageTransform = getToPageSpaceTransform();

            // create annotations types that that are rectangle based;
            // which is actually just link annotations
            annotation = (TextMarkupAnnotation)
                    AnnotationFactory.buildAnnotation(
                            documentViewModel.getDocument().getPageTree().getLibrary(),
                            highLightType,
                            tBbox);
            // set the private contents flag.
            ViewModel viewModel = documentViewController.getParentController().getViewModel();
            annotation.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, !viewModel.getAnnotationPrivacy());

            // pass outline shapes and bounds to create the highlight shapes
            annotation.setContents(contents != null && enableHighlightContents ? contents : highLightType.toString());
            // before assigning the default colour check to see if there is an entry in the properties manager
            checkAndApplyPreferences();
            annotation.setCreationDate(PDate.formatDateTime(new Date()));
            annotation.setTitleText(SystemProperties.USER_NAME);
            annotation.setMarkupBounds(highlightBounds);
            annotation.setMarkupPath(highlightPath);
            annotation.setBBox(tBbox);
            // finalized the appearance properties.
            annotation.resetAppearanceStream(pageTransform);

            // create new annotation given the general path
            MarkupAnnotationComponent comp = (MarkupAnnotationComponent)
                    AnnotationComponentFactory.buildAnnotationComponent(
                            annotation, documentViewController, pageViewComponent);

            // add the main highlight annotation
            documentViewController.addNewAnnotation(comp);

            // convert to user rect to page space along with the bounds.
            comp.setBounds(bounds);
            // avoid a potential rounding error in comp.refreshAnnotationRect(), stead we simply
            // set the bbox to the rect which is just fine for highlight annotations.
            Rectangle2D rect = annotation.getUserSpaceRectangle();
            annotation.syncBBoxToUserSpaceRectangle(rect);

            // associate popup to location
            PopupAnnotationComponent popupAnnotationComponent = comp.getPopupAnnotationComponent();
            popupAnnotationComponent.setBoundsRelativeToParent(
                    bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, pageTransform);
            popupAnnotationComponent.setVisible(false);
            popupAnnotationComponent.getAnnotation().setOpen(false);

        }
        pageViewComponent.repaint();
    }

    protected void checkAndApplyPreferences() {
        Color color = null;
        if (preferences.getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR, -1) != -1) {
            int rgb = preferences.getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR, 0);
            color = new Color(rgb);
        }
        // apply the settings or system property base colour for the given subtype.
        if (color == null) {
            annotation.setColor(annotation.getTextMarkupColor());
        } else {
            annotation.setColor(color);
            annotation.setTextMarkupColor(color);
        }
        annotation.setOpacity(preferences.getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_OPACITY,
                TextMarkupAnnotation.HIGHLIGHT_ALPHA));
    }

    protected String getSelectedText() {
        Page currentPage = pageViewComponent.getPage();
        String selectedText = null;
        try {
            selectedText = currentPage.getViewText().getSelected().toString();
            // remove line feeds and and 160 long breaking space
            selectedText = selectedText.replaceAll("[\\s\\p{Z}]", " ");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("HighLightAnnotation initialization interrupted.");
        }
        return selectedText;
    }

    protected void createMarkupAnnotationFromTextSelection(MouseEvent e) {
        // get the selection bounds
        ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());

        // clear the selection
        super.mouseReleased(e);

        // create the text markup annotation.
        createTextMarkupAnnotation(highlightBounds);

        // set the annotation tool to he select tool
        if (preferences.getBoolean(ViewerPropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_SELECTION_ENABLED, false)) {
            documentViewController.getParentController().setDocumentToolMode(
                    DocumentViewModel.DISPLAY_TOOL_SELECTION);
        }
    }

    public static ArrayList<Shape> getSelectedTextBounds(AbstractPageViewComponent pageViewComponent,
                                                         AffineTransform pageTransform) {
        Page currentPage = pageViewComponent.getPage();
        ArrayList<Shape> highlightBounds = null;
        if (currentPage != null && currentPage.isInitiated()) {
            try {
                PageText pageText = currentPage.getViewText();
                if (pageText != null) {
                    // paint the sprites
                    GeneralPath textPath;
                    ArrayList<LineText> pageLines = pageText.getPageLines();
                    if (pageLines != null) {
                        for (LineText lineText : pageLines) {
                            java.util.List<WordText> words = lineText.getWords();
                            Rectangle2D line = null;
                            if (highlightBounds == null) {
                                highlightBounds = new ArrayList<>();
                            }
                            if (words != null) {
                                for (WordText wordText : words) {
                                    // paint whole word
                                    if (wordText.isSelected() || wordText.isHighlighted()) {
                                        textPath = new GeneralPath(wordText.getBounds());
                                        textPath.transform(pageTransform);
                                        // paint highlight over any selected
                                        if (wordText.isSelected()) {
                                            if (line == null) {
                                                line = textPath.getBounds2D();
                                            } else {
                                                line.add(textPath.getBounds2D());
                                            }
                                        }
                                    }
                                    // check children
                                    else {
                                        for (GlyphText glyph : wordText.getGlyphs()) {
                                            if (glyph.isSelected()) {
                                                textPath = new GeneralPath(glyph.getBounds());
                                                textPath.transform(pageTransform);
                                                if (line == null) {
                                                    line = textPath.getBounds2D();
                                                } else {
                                                    line.add(textPath.getBounds2D());
                                                }
                                            }
                                        }
                                    }
                                }
                                if (line != null) {
                                    highlightBounds.add(line.getBounds2D());
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.fine("HighLightAnnotation selected text bounds calculation interrupted.");
            }
        }
        return highlightBounds;
    }
}
