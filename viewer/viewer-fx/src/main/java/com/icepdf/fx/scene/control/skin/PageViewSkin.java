package com.icepdf.fx.scene.control.skin;

import com.icepdf.fx.scene.control.DocumentView;
import com.icepdf.fx.scene.control.PageView;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.util.Library;

import java.lang.ref.SoftReference;

/**
 *
 */
public class PageViewSkin extends SkinBase<PageView> {

    // default for a4 is  595 X 842, letter, 595 x 792
    public static final double WIDTH = 595;
    public static final double HEIGHT = 842;

    private double width = WIDTH;
    private double height = HEIGHT;

    private double xViewport = -1;
    private double yViewport = -1;

    private Path pageOutline;
    private Group pageGroup;
    private ImageView pageImageView;
    private Rectangle clipRectangle;
    private Point2D oldImageView;

    private PageCaptureTask pageCaptureTask;

    private boolean invalidPage = true;

    public PageViewSkin(PageView control) {
        super(control);
        // match min and max to preferred
        getSkinnable().setMaxWidth(Region.USE_PREF_SIZE);
        getSkinnable().setMaxHeight(Region.USE_PREF_SIZE);
        getSkinnable().setMinWidth(Region.USE_PREF_SIZE);
        getSkinnable().setMinHeight(Region.USE_PREF_SIZE);

        // todo remove.
        control.backgroundFillProperty().addListener(observable -> {
            updatePageColour();
        });

        control.scaleProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            scalePreviousRendering(oldValue.doubleValue(), newValue.doubleValue());
            updatePageZoom();
        });
        control.rotationProperty().addListener(observable -> {
            updateClipRectangle();
            updatePageRotation();
        });

        control.indexProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            updatePageRendering();
        });

        // listen for x:y changes so we can recalculate the page image clip
        getSkinnable().layoutYProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            updatePageRendering();
        });

        getSkinnable().getDocumentView().getHbar().valueProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            updatePageRendering();
        });

        getSkinnable().getDocumentView().widthProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            updatePageRendering();
        });

        getSkinnable().getDocumentView().heightProperty().addListener((observable, oldValue, newValue) -> {
            updateClipRectangle();
            updatePageRendering();
        });

    }

    private void initialize() {

        if (pageGroup != null) {
            pageGroup.getChildren().clear();
        } else {
            pageGroup = new Group();
            getChildren().add(pageGroup);
        }
        int index = getSkinnable().getIndex();
        Document document = getSkinnable().getDocumentView().getDocument();
        PDimension dimension = document.getPageDimension(index, 0,//(float) getSkinnable().getRotation(),
                (float) getSkinnable().getScale());
        pageOutline = new Path();
        pageOutline.getElements().add(new MoveTo(0, 0));
        pageOutline.getElements().add(new LineTo(dimension.getWidth(), 0));
        pageOutline.getElements().add(new LineTo(dimension.getWidth(), dimension.getHeight()));
        pageOutline.getElements().add(new LineTo(0, dimension.getHeight()));
        pageOutline.getElements().add(new ClosePath());
        pageOutline.setStroke(Color.BLACK);
        pageOutline.setFill(Color.WHITE);

        if (pageImageView == null) {
            pageImageView = new ImageView();
            pageImageView.setPreserveRatio(true);
        }
        oldImageView = new Point2D(pageImageView.getLayoutX(), pageImageView.getLayoutY());

        if (clipRectangle == null) {
            clipRectangle = new Rectangle(0, 0, dimension.getWidth(), dimension.getHeight());
        }
        // trim rectangle to view port size.
        updateClipRectangle();
        clipRectangle.setStroke(Color.RED);
        clipRectangle.setFill(null);
        updatePageRendering();

        pageGroup.getChildren().addAll(pageOutline, pageImageView, clipRectangle);
    }

    public void updatePageColour() {
        if (pageOutline != null) {
            pageOutline.setFill(getSkinnable().getBackgroundFill());
        }
    }

    public void updatePageRotation() {
        if (pageGroup != null) {
            pageGroup.setRotate(getSkinnable().getRotation());
            getSkinnable().requestLayout();
        }
    }

    public void updatePageZoom() {
        if (pageGroup != null) {
            invalidPage = true;
            getSkinnable().requestLayout();
        }
    }

    public void updateClipRectangle() {
        DocumentView documentView = getSkinnable().getDocumentView();
        int index = getSkinnable().getIndex();

        // if page index -1, avoid doing clip work?
        if (index == -1) {
            return;
        }

        Bounds viewport = getNode().getParent().getParent().getLayoutBounds();//getSkinnable().getDocumentView().getBoundsInLocal();
        double scrollBarWidth = getSkinnable().getDocumentView().getHbar().getHeight();
        double x = getSkinnable().getDocumentView().getHbar().getValue();
        double y = getSkinnable().getLayoutY(); // subtract inset.

//        System.out.println(viewport);

        PDimension pDimension = documentView.getDocument().getPageDimension(index, 0,//(float) getSkinnable().getRotation(),
                (float) getSkinnable().getScale());
        double pageWidth = pDimension.getWidth();
        double pageHeight = pDimension.getHeight();
        double insetTop = getSkinnable().getInsets().getTop();
        double insetLeft = getSkinnable().getInsets().getLeft();
        double viewportWidth = viewport.getWidth();
        double viewportHeight = viewport.getHeight();
        double xClip = 0;
        double yClip = 0;
        double clipWidth = pageWidth;
        double clipHeight = pageHeight;

        // todo further work is needed here to tighten up the bounds as well as support side by side page views
        if (pageWidth >= viewportWidth) {
            double max = (pageWidth + insetLeft + scrollBarWidth) - viewportWidth;
            if (x > insetLeft) {
                xClip = x - insetLeft;
            }
            if (x >= max) {
                clipWidth = viewportWidth - (x - max) - scrollBarWidth;
            } else {
                clipWidth = viewportWidth;//- scrollBarWidth;
            }

        }
        if (pageHeight >= viewportHeight) {
            if (y < 0) {
                yClip = -y - insetTop;
                if (yClip < 0) yClip = 0;
                clipHeight = pageHeight - yClip;
                if (clipHeight > viewportHeight) {
                    clipHeight = viewportHeight;
                }
            } else {
                clipHeight = viewportHeight - y;
            }
        }
        // update our clip outline.
        if (clipRectangle != null) {
            clipRectangle.setX(xClip);
            clipRectangle.setY(yClip);
            clipRectangle.setWidth(clipWidth);
            clipRectangle.setHeight(clipHeight);
//            System.out.println(clipRectangle);
        }
    }

    private void scalePreviousRendering(double previousScale, double newScale) {
//        pageImageView.setVisible(false);
        if (getSkinnable().getIndex() < 0) {
            return;
        }

        double scaleIncrement = newScale / previousScale; //newScale - previousScale;


//        double diff = oldImageView.getX()  / oldClipRectangle.getWidth();

//        System.out.println(clipRectangle.getX() + " " +clipRectangle.getY());
//        System.out.println(oldImageView.getX() + " " +oldImageView.getY());
//        System.out.println();

//        pageImageView.relocate(clipRectangle.getX() * scaleIncrement, clipRectangle.getY() * scaleIncrement);
        pageImageView.setFitWidth(clipRectangle.getWidth());
    }


    /**
     * Index has changed and we need to update the contents fo the cell with the new page data.
     */
    public void updatePageRendering() {

        int index = getSkinnable().getIndex();

        stopTask();
        if (index >= 0 && pageImageView != null) {
//            pageImageView.setVisible(false);
            DocumentView documentView = getSkinnable().getDocumentView();
            // todo cache needs to store previous x,y position, so we can make the repaint smarter
            DocumentViewSkin documentViewSkin = (DocumentViewSkin) documentView.getSkin();
            SoftReference<Image> cachedImageReference = documentViewSkin.getImageCaptureCache().get(index);
            if (cachedImageReference != null) {
                Image cachedImage = cachedImageReference.get();
                if (cachedImage != null) {
                    pageImageView.setImage(cachedImage);
                    pageImageView.setVisible(true);
//                    pageImageView.setFitWidth(clipRectangle.getWidth());
                }
            }

            if ((pageCaptureTask == null || !pageCaptureTask.isRunning())) {
                PDimension pDimension = documentView.getDocument().getPageDimension(index, 0, (float) 1.0f);
                width = pDimension.getWidth();
                height = pDimension.getHeight();

                pageImageView.setClip(new Rectangle(0, 0, clipRectangle.getWidth(), clipRectangle.getHeight()));

                oldImageView = new Point2D(pageImageView.getLayoutX(), pageImageView.getLayoutY());

                // we have a new page size so we can setup a paint
                pageCaptureTask = new PageCaptureTask(index, pageImageView, clipRectangle, documentView);
                Library.execute(pageCaptureTask);

                pageCaptureTask.setOnCancelled(t -> {
                    pageImageView.setVisible(false);
                });
            }
        }
        invalidPage = true;
        getSkinnable().requestLayout();
    }

    private boolean stopTask() {
        if (pageCaptureTask != null) {
            pageCaptureTask.cancel();
            boolean cancelled = pageCaptureTask.isCancelled() || pageCaptureTask.getState() == Task.State.READY;
            pageCaptureTask = null;
            return cancelled;
        }
        return false;
    }


    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        if (invalidPage) {
            initialize();
            invalidPage = false;
        }
        layoutInArea(pageGroup, contentX, contentY, contentWidth, contentHeight, -1,
                HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + rightInset + (width * getSkinnable().getScale());
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + bottomInset + (height * getSkinnable().getScale());
    }
}
