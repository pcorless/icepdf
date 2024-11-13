package org.icepdf.fx.ri.views;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.FontSmoothingType;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.jfree.fx.FXGraphics2D;
import org.jfree.fx.FXHints;

import java.awt.*;

public class PageViewWidget extends Region {

    private FloatProperty scale;
    private FloatProperty rotation;
    private IntegerProperty pageIndex;

    public ObjectProperty<Bounds> viewportBounds;

    private DoubleProperty width;
    private DoubleProperty height;
    private double pageWidth;
    private double pageHeight;

    private Label pageIndexLabel;
    private Label scaleLabel;
    private Label rotationLabel;

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

//                System.out.println("Page " + this.getPageIndex() + " is in the viewport");
            scrollPause.playFromStart();
            // todo trigger a page capture
            //  - page should be doing intersection check
            //  - any size change would trigger a repaint
            //  - eventually bring clipped painting
            //  - try painting to a buffer and then paint that buffer to the screen (from prevoius work
            //  - try painting to graphics contet too,  maybe it's fast/optimized for reactive painting.
        });

        pageIndexLabel = new Label("Page " + pageIndex);
        scaleLabel = new Label();
        rotationLabel = new Label();
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
        if (canvas == null && isNodeIntersectingViewport(scrollPane, this)) {
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
//
//                            WritableImage image = new WritableImage((int) width.get(), (int) height.get());
//                            image.getPixelWriter().

                            return null;
                        }
                    };
                    Thread pageInitThread = new Thread(pageCaptureTask);
                    pageInitThread.start();
                    return;
                }

                canvas = new Canvas(pageWidth, pageHeight);

                Group root = new Group();
                GraphicsContext gc = canvas.getGraphicsContext2D();
                root.getChildren().add(canvas);
                getChildren().add(root);

                // try and get this working with
                canvas.scaleXProperty().bind(scale);
                canvas.scaleYProperty().bind(scale);

                gc.setStroke(Color.RED);
                gc.setLineWidth(5);
                calculateAndDrawClip(gc);

                gc.setFontSmoothingType(FontSmoothingType.LCD);
                FXGraphics2D fxg2 = new FXGraphics2D(gc);
                fxg2.setRenderingHint(FXHints.KEY_USE_FX_FONT_METRICS, true);
                fxg2.setZeroStrokeWidth(0.1);
                fxg2.setRenderingHint(
                        RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                fxg2.setClip(0, 0, (int) width.get(), (int) height.get());
                fxg2.scale(1, -1);
                fxg2.translate(0, -height.get());


                long start = System.currentTimeMillis();
                page.paintPageContent(fxg2, GraphicsRenderingHints.PRINT, rotation.get(), scale.get(), true, true);
                long end = System.currentTimeMillis();
                System.out.println("Page paint time: " + (end - start) + "ms");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void calculateAndDrawClip(GraphicsContext gc) {
        Bounds viewportBounds = scrollPane.getViewportBounds();
        Bounds nodeBounds = localToScene(getBoundsInLocal());
        Bounds scrollPaneBounds = scrollPane.localToScene(scrollPane.getBoundsInLocal());
        double x = scrollPaneBounds.getMinX() + viewportBounds.getMinX();
        double y = scrollPaneBounds.getMinY() + viewportBounds.getMinY();
        double width = viewportBounds.getWidth();
        double height = viewportBounds.getHeight();
        gc.strokeRect(x, y, width, height);
    }

    private void drawShapes(GraphicsContext gc) {
        gc.setFill(Color.GREEN);
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(5);
        gc.strokeLine(40, 10, 10, 40);
        gc.fillOval(10, 60, 30, 30);
        gc.strokeOval(60, 60, 30, 30);
        gc.fillRoundRect(110, 60, 30, 30, 10, 10);
        gc.strokeRoundRect(160, 60, 30, 30, 10, 10);
        gc.fillArc(10, 110, 30, 30, 45, 240, ArcType.OPEN);
        gc.fillArc(60, 110, 30, 30, 45, 240, ArcType.CHORD);
        gc.fillArc(110, 110, 30, 30, 45, 240, ArcType.ROUND);
        gc.strokeArc(10, 160, 30, 30, 45, 240, ArcType.OPEN);
        gc.strokeArc(60, 160, 30, 30, 45, 240, ArcType.CHORD);
        gc.strokeArc(110, 160, 30, 30, 45, 240, ArcType.ROUND);
        gc.fillPolygon(new double[]{10, 40, 10, 40},
                new double[]{210, 210, 240, 240}, 4);
        gc.strokePolygon(new double[]{60, 90, 60, 90},
                new double[]{210, 210, 240, 240}, 4);
        gc.strokePolyline(new double[]{110, 140, 110, 140},
                new double[]{210, 210, 240, 240}, 4);
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

    private Bounds intersectionClip(ScrollPane scrollPane, Node node) {

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

        // todo calculate intersection, so we can define a small canvas to draw to on high zoom levels

        return null;
    }

    private void createLayout() {
        minWidthProperty().bind(width);
        minHeightProperty().bind(height);

//        VBox vBox = new VBox();
//        scaleLabel.textProperty().bind(scale.asString());
//        rotationLabel.textProperty().bind(width.asString());
//        vBox.getChildren().addAll(pageIndexLabel, scaleLabel, rotationLabel);
//        getChildren().add(vBox);

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
