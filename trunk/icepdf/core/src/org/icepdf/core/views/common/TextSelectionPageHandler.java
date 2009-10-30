package org.icepdf.core.views.common;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.ri.common.search.DocumentSearchControllerImpl;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles Paint and mouse/keyboard logic around text selection and search
 * highlighting.  there is on text handler isntance of each pageComponent
 * used to dispaly the document.
 * <p/>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.highlightColor"
 * <p/>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.selectionColor"
 *
 * // todo - push text selection box outinto the parent page view
 * // todo - use existing selection box as a crop tool.
 * // todo - push mouse events out to parent 
 *
 * @since 4.0
 */
public class TextSelectionPageHandler implements MouseInputListener {

    private static final Logger logger =
            Logger.getLogger(TextSelectionPageHandler.class.toString());

    // text selection colour
    public static Color selectionColor;
    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.text.selectionColor", "#0077FF");
            int colorValue = ColorUtil.convertColor(color);
            selectionColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("0077FF", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text selection colour");
            }
        }
    }
    // text highlight colour
    public static Color highlightColor;
    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.text.highlightColor", "#CC00FF");
            int colorValue = ColorUtil.convertColor(color);
            highlightColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("FFF600", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text highlight colour");
            }
        }
    }

    // dashed selection rectangle stroke
    private final static float dash1[] = {1.0f};
    private final static BasicStroke dashed = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            1.0f, dash1, 0.0f);

    // selection rectangle used for glyph intersection aka text selection
    private Rectangle currentRect = null;
    private Rectangle rectToDraw = null;
    private Rectangle previousRectDrawn = new Rectangle();

    // parent page component
    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;


    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewModel view model.
     */
    public TextSelectionPageHandler(AbstractPageViewComponent pageViewComponent,
                                    DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    /**
     * Document view controller callback setup.  Has to be done after the
     * contructor.
     * @param documentViewController  document controller callback.
     */
    public void setDocumentViewController(
            DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    /**
     * When mouse is double clicked we select the word the mouse if over.  When
     * the mouse is triple clicked we select the line of text that the mouse
     * is over.
     */
    public void mouseClicked(MouseEvent e) {
        // double click we select the whole line.
        if (e.getClickCount() == 3) {
            if (documentViewModel.getViewToolMode() ==
                    DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

                Page currentPage = pageViewComponent.getPageLock(this);
                // handle text selection mouse coordinates
                Point mouseLocation = (Point) e.getPoint().clone();
                lineSelectHandler(currentPage, mouseLocation);
                currentPage.getViewText().getSelected();
                pageViewComponent.releasePageLock(currentPage, this);
            }
        }
        // single click we select word that was clicked. 
        else if (e.getClickCount() == 2) {
            if (documentViewModel.getViewToolMode() ==
                    DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

                Page currentPage = pageViewComponent.getPageLock(this);

                // handle text selection mouse coordinates
                Point mouseLocation = (Point) e.getPoint().clone();
                wordSelectHandler(currentPage, mouseLocation);
                currentPage.getViewText().getSelected();
                pageViewComponent.releasePageLock(currentPage, this);
            }
        }
    }

    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent e) {

        // todo: this also has to listen for selection from parent view, so
        // we can deselect text from other pages....

        // on mouse click clear the currently selected sprints
        Page currentPage = pageViewComponent.getPageLock(this);
        // clear selected text.
        if (currentPage.getViewText() != null) {
            currentPage.getViewText().clearSelected();
        }
        // todo: repaint should be able to do a tight repaint clip...
        // likely have to calculate bounds of selected text.
        pageViewComponent.repaint();
        pageViewComponent.releasePageLock(currentPage, this);

        // text selection box.
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            int x = e.getX();
            int y = e.getY();
            currentRect = new Rectangle(x, y, 0, 0);
            updateDrawableRect(pageViewComponent.getWidth(), pageViewComponent.getHeight());
            pageViewComponent.repaint();
        }

    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            // update selection rectangle
            updateSize(e);

            // write out selected text.
            if (logger.isLoggable(Level.FINE)){
                Page currentPage = pageViewComponent.getPageLock(this);
                // handle text selection mouse coordinates
                currentPage.getViewText().getSelected();
                pageViewComponent.releasePageLock(currentPage, this);
            }

            // clear the rectangle
            currentRect = new Rectangle(0, 0, 0, 0);
            updateDrawableRect(pageViewComponent.getWidth(),
                    pageViewComponent.getHeight());
            pageViewComponent.repaint();
        }
    }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * Invoked when a mouse button is pressed on a component and then
     * dragged.  <code>MOUSE_DRAGGED</code> events will continue to be
     * delivered to the component where the drag originated until the
     * mouse button is released (regardless of whether the mouse position
     * is within the bounds of the component).
     * <p/>
     * Due to platform-dependent Drag&Drop implementations,
     * <code>MOUSE_DRAGGED</code> events may not be delivered during a native
     * Drag&Drop operation.
     */
    public void mouseDragged(MouseEvent e) {

        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

            // rectangle select tool
            updateSize(e);

            // lock and unlock content before iterating over the pageText tree.
            Page currentPage = pageViewComponent.getPageLock(this);
            multilineSelectHandler(currentPage, e.getPoint());
            pageViewComponent.releasePageLock(currentPage, this);
        }
    }

    /**
     * Invoked when the mouse cursor has been moved onto a component
     * but no buttons have been pushed.
     */
    public void mouseMoved(MouseEvent e) {
        // change state of mouse from pointer to text selection icon
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            Page currentPage = pageViewComponent.getPageLock(this);
            selectionMouseCursor(currentPage,  e.getPoint());
            pageViewComponent.releasePageLock(currentPage, this);
        }
    }

    /**
     * Utility for detecting and changing the cursor to the text selection tool
     * when over text in the doucument.
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void selectionMouseCursor(Page currentPage, Point mouseLocation){
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                ArrayList<LineText> pageLines = pageText.getPageLines();
                boolean found = false;
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.contains(pageTransform, mouseLocation)) {
                        found = true;
                        documentViewController.setViewCursor(
                                DocumentViewController.CURSOR_TEXT_SELECTION);
                        break;
                    }
                }
                if (!found){
                    documentViewController.setViewCursor(
                                DocumentViewController.CURSOR_SELECT);
                }
            }
        }
    }

    /**
     * Utility for selecting multiple lines via l-> right type select. This
     * method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void multilineSelectHandler(Page currentPage, Point mouseLocation) {

        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                LineText firstPageLine = null;
                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.intersects(pageTransform, rectToDraw)) {
                        pageLine.setHasSelected(true);
                        if (firstPageLine == null) {
                            firstPageLine = pageLine;
                        }
                        if (pageLine.contains(pageTransform, mouseLocation)) {

                            ArrayList<WordText> lineWords = pageLine.getWords();
                            for (WordText word : lineWords) {
                                if (word.intersects(pageTransform, rectToDraw)) {
                                    word.setHasHighlight(true);
                                    ArrayList<GlyphText> glyphs = word.getGlyphs();
                                    for (GlyphText glyph : glyphs) {
                                        if (glyph.intersects(pageTransform, rectToDraw)) {
                                            glyph.setSelected(true);
                                            pageViewComponent.repaint();
                                        }
                                    }
                                }
                            }
                        } else if (firstPageLine == pageLine) {
                            // left to right selection
//                            if (currentRect.width > 0 ){
                            selectLeftToRight(pageLine, pageTransform);
//                            }else{
//                                selectRightToLeft(pageLine, pageTransform););
//                            }
                        } else {
                            pageLine.selectAll();
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for right to left selection, NOT Correct
     * @param pageLine page line to select.
     * @param pageTransform page transform.
     */
    private void selectRightToLeft(LineText pageLine,
                                   AffineTransform pageTransform) {
        ArrayList<WordText> lineWords = pageLine.getWords();
        for (WordText word : lineWords) {
            if (word.intersects(pageTransform, rectToDraw)) {
                word.setHasHighlight(true);
                ArrayList<GlyphText> glyphs = word.getGlyphs();
                GlyphText glyph = null;
                for (int i = glyphs.size() - 1; i >= 0; i--) {
                    if (glyph.intersects(pageTransform, rectToDraw)) {
                        glyph.setSelected(true);
                        pageViewComponent.repaint();
                    }
                }
            }
        }
    }


    /**
     * Simple left to right, top down type selection model, not perfect.
     * @param pageLine page line to select.
     * @param pageTransform page transform.
     */
    private void selectLeftToRight(LineText pageLine,
                                   AffineTransform pageTransform) {
        GlyphText fistGlyph = null;
        ArrayList<WordText> lineWords = pageLine.getWords();
        for (WordText word : lineWords) {
            if (word.intersects(pageTransform, rectToDraw)) {
                word.setHasHighlight(true);
                ArrayList<GlyphText> glyphs = word.getGlyphs();
                for (GlyphText glyph : glyphs) {
                    if (glyph.intersects(pageTransform, rectToDraw)) {
                        if (fistGlyph == null) {
                            fistGlyph = glyph;
                        }
                        glyph.setSelected(true);
                    } else if (fistGlyph != null) {
                        glyph.setSelected(true);
                    }
                }
            }
            // select the rest
            else if (fistGlyph != null) {
                word.selectAll();
            }
        }
        pageViewComponent.repaint();
    }

    /**
     * Utility for selecting multiple lines via rectangle like tool. The
     * selection works based on the intersection of the rectangle and glyph
     * bounding box.
     * <p/>
     * This method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void rectangleSelectHandler(Page currentPage, Point mouseLocation) {
        // detect L->R or R->L
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.intersects(pageTransform, rectToDraw)) {
                        pageLine.setHasSelected(true);
                        ArrayList<WordText> lineWords = pageLine.getWords();
                        for (WordText word : lineWords) {
                            if (word.intersects(pageTransform, currentRect)) {
                                word.setHasHighlight(true);
                                ArrayList<GlyphText> glyphs = word.getGlyphs();
                                for (GlyphText glyph : glyphs) {
                                    if (glyph.intersects(pageTransform, currentRect)) {
                                        glyph.setSelected(true);
                                        pageViewComponent.repaint();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for selecting multiple lines via rectangle like tool. The
     * selection works based on the intersection of the rectangle and glyph
     * bounding box.
     * <p/>
     * This method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void wordSelectHandler(Page currentPage, Point mouseLocation) {

        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {

                    // check for containment, if so break into words.
                    if (pageLine.contains(pageTransform, mouseLocation)) {
                        pageLine.setHasSelected(true);
                        ArrayList<WordText> lineWords = pageLine.getWords();
                        for (WordText word : lineWords) {
                            if (word.contains(pageTransform, mouseLocation)) {
                                word.selectAll();
                                pageViewComponent.repaint();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for selecting a LineText which is usually a sentence in the
     * document.   This is usually triggered by a tripple click of the mouse
     * @param currentPage page to select
     * @param mouseLocation location of mouse
     */
    private void lineSelectHandler(Page currentPage, Point mouseLocation) {
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {

                    // check for containment, if so break into words.
                    if (pageLine.contains(pageTransform, mouseLocation)) {
                        pageLine.selectAll();
                        pageViewComponent.repaint();
                    }
                }
            }
        }
    }

    /**
     * Utility for painting the highlight and selected
     * @param g graphics to paint to.
     */
    public void paintSelectedText(Graphics g) {
        // ready outline paint
        Graphics2D gg = (Graphics2D) g;
        AffineTransform prePaintTransform = gg.getTransform();
        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        gg.setColor(new Color(0, 119, 255));
        gg.setStroke(new BasicStroke(1.0f));

        Page currentPage = pageViewComponent.getPageLock(this);
        if (currentPage != null && currentPage.isInitiated()) {
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {
                // get page transformation
                AffineTransform pageTransform = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                // paint the sprites
                GeneralPath textPath;
                for (LineText lineText : pageText.getPageLines()) {
                    // paint whole line
                    if (lineText.isSelected()) {
                        textPath = new GeneralPath(lineText.getGeneralPath());
                        textPath.transform(pageTransform);
                        gg.fill(textPath);
                    }
                    // check children for selection
                    else {
                        for (WordText wordText : lineText.getWords()) {
                            // paint whole word
                            if (wordText.isSelected() || wordText.isHighlighted()) {
                                textPath = new GeneralPath(wordText.getGeneralPath());
                                textPath.transform(pageTransform);
                                // tmp highlight hack.
                                if (wordText.isHighlighted()) {
                                    gg.setColor(highlightColor);
                                }
                                gg.fill(textPath);
                                if (wordText.isHighlighted()) {
                                    gg.setColor(selectionColor);
                                }
                            }
                            // check children
                            else {
                                for (GlyphText glyph : wordText.getGlyphs()) {
                                    if (glyph.isSelected()) {
                                        textPath = new GeneralPath(glyph.getGeneralPath());
                                        textPath.transform(pageTransform);
                                        gg.fill(textPath);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        pageViewComponent.releasePageLock(currentPage, this);

        // post paint clean up.
        gg.setColor(oldColor);
        gg.setStroke(oldStroke);
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        oldColor = gg.getColor();
        oldStroke = gg.getStroke();
        if (currentRect != null) {
            //Draw a rectangle on top of the image.
            oldColor = g.getColor();
            g.setColor(Color.gray);
            gg.setStroke(dashed);
            g.drawRect(rectToDraw.x, rectToDraw.y,
                    rectToDraw.width - 1, rectToDraw.height - 1);
            g.setColor(oldColor);
        }

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);

        // paint words for bounds test.
//        paintTextBounds(g);

    }


    /**
     * Utility for painting text bounds.
     * @param g graphics context to paint to.
     */
    private void paintTextBounds(Graphics g) {
        Page currentPage = pageViewComponent.getPageLock(this);
        // get page transformation
        AffineTransform pageTransform = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        Graphics2D gg = (Graphics2D) g;
        Color oldColor = g.getColor();
        g.setColor(Color.red);

        PageText pageText = currentPage.getViewText();
        ArrayList<LineText> pageLines = pageText.getPageLines();
        for (LineText lineText : pageLines) {

            for (WordText wordText : lineText.getWords()) {
//                for (GlyphText glyph : wordText.getGlyphs()) {
//                    g.setColor(Color.black);
//                    GeneralPath glyphSpritePath =
//                        new GeneralPath(glyph.getGeneralPath());
//                    glyphSpritePath.transform(pageTransform);
//                    gg.draw(glyphSpritePath);
//                }

//                if (!wordText.isWhiteSpace()) {
//                    g.setColor(Color.blue);
//                    GeneralPath glyphSpritePath =
//                            new GeneralPath(wordText.getGeneralPath());
//                    glyphSpritePath.transform(pageTransform);
//                    gg.draw(glyphSpritePath);
//                }
            }
            g.setColor(Color.red);
            GeneralPath glyphSpritePath =
                    new GeneralPath(lineText.getGeneralPath());
            glyphSpritePath.transform(pageTransform);
            gg.draw(glyphSpritePath);
        }
        g.setColor(oldColor);
        pageViewComponent.releasePageLock(currentPage, this);
    }

    /**
     * Update the size of the selection rectangle.
     * @param e
     */
    private void updateSize(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        currentRect.setSize(x - currentRect.x,
                y - currentRect.y);
        updateDrawableRect(pageViewComponent.getWidth(), pageViewComponent.getHeight());
        Rectangle totalRepaint = rectToDraw.union(previousRectDrawn);
        pageViewComponent.repaint(totalRepaint.x, totalRepaint.y,
                totalRepaint.width, totalRepaint.height);
    }

    /**
     * Udpate the drawable rectangle so that it does not extend bast the edge
     * of the page.
     *
     * @param compWidth  width of component being selected
     * @param compHeight height of component being selected.
     */
    private void updateDrawableRect(int compWidth, int compHeight) {
        int x = currentRect.x;
        int y = currentRect.y;
        int width = currentRect.width;
        int height = currentRect.height;

        //Make the width and height positive, if necessary.
        if (width < 0) {
            width = 0 - width;
            x = x - width + 1;
            if (x < 0) {
                width += x;
                x = 0;
            }
        }
        if (height < 0) {
            height = 0 - height;
            y = y - height + 1;
            if (y < 0) {
                height += y;
                y = 0;
            }
        }

        //The rectangle shouldn't extend past the drawing area.
        if ((x + width) > compWidth) {
            width = compWidth - x;
        }
        if ((y + height) > compHeight) {
            height = compHeight - y;
        }

        //Update rectToDraw after saving old value.
        if (rectToDraw != null) {
            previousRectDrawn.setBounds(
                    rectToDraw.x, rectToDraw.y,
                    rectToDraw.width, rectToDraw.height);
            rectToDraw.setBounds(x, y, width, height);
        } else {
            rectToDraw = new Rectangle(x, y, width, height);
        }
    }
}
