package org.icepdf.fx.ri.views;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.fx.ri.viewer.ViewerModel;

public class PageViewWidget extends Region {

    private FloatProperty scale;
    private FloatProperty rotation;
    private IntegerProperty pageIndex;

    public ObjectProperty<Bounds> viewportBounds;

    private DoubleProperty width;
    private DoubleProperty height;
    private double pageWidth;
    private double pageHeight;

    private ViewerModel model;
    private ScrollPane scrollPane;

    private Canvas canvas;

    private Task<Void> pageCaptureTask;

    private static final Duration SCROLL_PAUSE_DURATION = Duration.millis(200);
    private PauseTransition scrollPause;

    public PageViewWidget(ViewerModel model, int pageIndex, FloatProperty scale, FloatProperty rotation, ScrollPane scrollPane) {
        this.pageIndex = new SimpleIntegerProperty(pageIndex);
        this.scale = new SimpleFloatProperty();
        this.scale.bind(scale);
        this.rotation = new SimpleFloatProperty();
        this.rotation.bind(rotation);
        this.scrollPane = scrollPane;
        this.model = model;
        scrollPause = new PauseTransition(SCROLL_PAUSE_DURATION);
        scrollPause.setOnFinished(event -> {
            draw();
        });


        this.viewportBounds = new SimpleObjectProperty<>();


        Document document = model.document.get();
        PDimension pageSize = document.getPageDimension(pageIndex, rotation.get(), scale.get());
        pageWidth = pageSize.getWidth();
        pageHeight = pageSize.getHeight();
        width = new SimpleDoubleProperty(pageWidth);
        height = new SimpleDoubleProperty(pageHeight);
        setWidth(pageWidth);
        setHeight(pageHeight);

        scale.addListener((observable, oldValue, newValue) -> {
            width.set(pageWidth * newValue.floatValue());
            height.set(pageHeight * newValue.floatValue());
        });

        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            scrollPause.playFromStart();
        });

        createLayout();

        setOnMouseClicked(event -> {
            System.out.println("Page " + pageIndex + " " + isNodeIntersectingViewport(scrollPane, this));
        });

        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> {
            if (isNodeIntersectingViewport(scrollPane, this)) {
                draw();
            }
        });

        // Add listener to detect when the node has valid bounds and is laid out
        boundsInParentProperty().addListener(new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> observable, Bounds oldBounds, Bounds newBounds) {
                if (newBounds.getWidth() == width.get() && newBounds.getHeight() == height.get()) {
                    draw();
                }
            }
        });
    }

    public void draw() {
        if (isNodeIntersectingViewport(scrollPane, this)) {
            System.out.println("drawing page " + this.pageIndex);

            try {
                Page page = model.document.get().getPageTree().getPage(pageIndex.get());
                if (!page.isInitiated() && (pageCaptureTask == null || pageCaptureTask.isDone())) {
                    pageCaptureTask = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            long start = System.currentTimeMillis();
                            page.init();
                            long end = System.currentTimeMillis();
                            System.out.println("Page init time: " + (end - start) + "ms");
                            Platform.runLater(() -> draw());
                            return null;
                        }
                    };
                    Thread pageInitThread = new Thread(pageCaptureTask);
                    pageInitThread.start();
                    return;
                }

                if (canvas == null) {
                    canvas = new Canvas(pageWidth, pageHeight);
                    Group root = new Group();
                    root.getChildren().add(canvas);
                    getChildren().add(root);
//                    canvas.scaleXProperty().bind(scale);
//                    canvas.scaleYProperty().bind(scale);
                }
                GraphicsContext gc = canvas.getGraphicsContext2D();

                gc.clearRect(0, 0, width.get(), height.get());
                gc.save();
                // clip testing
                gc.setFill(Color.BLUE);
                gc.fillRect(0, 0, 50, 50);
                calculateAndDrawClip(gc);

                // paint page to canvas
                // todo: likely fall back on paint directly to image and pain this image as node at the
                //  correct location.  Canvas is a buffer but a lot of work would be need to convert the
                //  rendering core to full javafx draw ops.  Not ruling it out as there are some benefits to
                //  some of the blending effects that are build into javafx.
//                gc.save();
//                Affine prePaintTransform = gc.getTransform();
//                gc.setFontSmoothingType(FontSmoothingType.LCD);
//                FXGraphics2D fxg2 = new FXGraphics2D(gc);
//                AffineTransform test = fxg2.getTransform();
//                fxg2.setRenderingHint(FXHints.KEY_USE_FX_FONT_METRICS, true);
//                fxg2.setZeroStrokeWidth(0.1);
//                fxg2.setRenderingHint(
//                        RenderingHints.KEY_FRACTIONALMETRICS,
//                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
//                fxg2.setClip(0, 0, (int) width.get(), (int) height.get());
//                fxg2.scale(1, -1);
//                fxg2.translate(0, -height.get());

//                long start = System.currentTimeMillis();
//                page.paintPageContent(fxg2, GraphicsRenderingHints.PRINT, rotation.get(), scale.get(), true, true);
//                long end = System.currentTimeMillis();
//                fxg2.transform(test);
//
//                System.out.println("Page paint time: " + (end - start) + "ms");
//                gc.restore();
//                fxg2.scale(1, -1);
//                gc.transform(prePaintTransform);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void calculateAndDrawClip(GraphicsContext gc) {
        Rectangle2D rect = intersectionClip(scrollPane, this);
        gc.setStroke(Color.RED);
        gc.setLineWidth(5);
        System.out.println("Drawing clip: " + rect);
        gc.strokeRect(rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight());
        ;
    }


    private boolean isNodeIntersectingViewport(ScrollPane scrollPane, Node node) {

        Bounds nodeBounds = node.getBoundsInParent();
//        if (nodeBounds.getWidth() != width.get() || nodeBounds.getHeight() != height.get()) {
//            return false;
//        }
        if (nodeBounds.getMinY() == 0 && pageIndex.get() > 0) {
            return false;
        }
        Bounds viewportBounds = scrollPane.getViewportBounds();

        double hmin = scrollPane.getHmin();
        double hmax = scrollPane.getHmax();
        double hvalue = scrollPane.getHvalue();
        double contentWidth = scrollPane.getContent().getLayoutBounds().getWidth();
        double viewportWidth = scrollPane.getViewportBounds().getWidth();

        double hoffset =
                Math.max(0, contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin);

        double vmin = scrollPane.getVmin();
        double vmax = scrollPane.getVmax();
        double vvalue = scrollPane.getVvalue();
        double contentHeight = scrollPane.getContent().getLayoutBounds().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();

        double voffset =
                Math.max(0, contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin);

//        System.out.printf("Offset: [%.1f, %.1f] width: %.1f height: %.1f %n",
//                hoffset, voffset, viewportWidth, viewportHeight);

        return nodeBounds.intersects(
                hoffset,
                voffset,
                viewportBounds.getWidth(),
                viewportBounds.getHeight()
        );
    }

    private Rectangle2D intersectionClip(ScrollPane scrollPane, Node node) {

        Bounds nodeBounds = node.getBoundsInParent();

        Bounds viewportBounds = scrollPane.getViewportBounds();

        double hmin = scrollPane.getHmin();
        double hmax = scrollPane.getHmax();
        double hvalue = scrollPane.getHvalue();
        double contentWidth = scrollPane.getContent().getLayoutBounds().getWidth();
        double viewportWidth = scrollPane.getViewportBounds().getWidth();

        double hoffset =
                Math.max(0, contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin);

        double vmin = scrollPane.getVmin();
        double vmax = scrollPane.getVmax();
        double vvalue = scrollPane.getVvalue();
        double contentHeight = scrollPane.getContent().getLayoutBounds().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();

        double voffset =
                Math.max(0, contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin);

        Rectangle2D intersection = new Rectangle2D(
                Math.max(hoffset, nodeBounds.getMinX()),
                Math.max(voffset, nodeBounds.getMinY()),
                Math.min(viewportWidth, nodeBounds.getWidth()),
                Math.min(viewportHeight, nodeBounds.getHeight())
        );
//        Rectangle2D intersection = findIntersection(
//                new Rectangle2D(hoffset, voffset, viewportWidth, viewportHeight),
//                new Rectangle2D(nodeBounds.getMinX(), nodeBounds.getMinY(), nodeBounds.getWidth(), nodeBounds.getHeight())
//        );
        Rectangle2D normalizedToNode = new Rectangle2D(
                intersection.getMinX() - nodeBounds.getMinX(),
                intersection.getMinY() - nodeBounds.getMinY(),
                intersection.getWidth(),
                intersection.getHeight()
        );


        return normalizedToNode;
    }

    // second method to calculate intersection
    public static Rectangle2D findIntersection(Rectangle2D rect1, Rectangle2D rect2) {
        double x1 = Math.max(rect1.getMinX(), rect2.getMinX());
        double y1 = Math.max(rect1.getMinY(), rect2.getMinY());
        double x2 = Math.min(rect1.getMinX() + rect1.getWidth(), rect2.getMinX() + rect2.getWidth());
        double y2 = Math.min(rect1.getMinY() + rect1.getHeight(), rect2.getMinY() + rect2.getWidth());

        if (x1 < x2 && y1 < y2) {
            return new Rectangle2D(x1, y1, x2 - x1, y2 - y1);
        }
        // No intersection
        return null;
    }

    private void createLayout() {
        minWidthProperty().bind(width);
        minHeightProperty().bind(height);
    }

    public int getPageIndex() {
        return pageIndex.get();
    }

    public float getScale() {
        return scale.get();
    }

    public FloatProperty scaleProperty() {
        return scale;
    }

    public float getRotation() {
        return rotation.get();
    }

    public FloatProperty rotationProperty() {
        return rotation;
    }
}
