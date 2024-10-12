package com.icepdf.fx.scene.control.skin;

import com.icepdf.fx.scene.control.DocumentView;
import com.icepdf.fx.scene.control.PageView;
import com.icepdf.fx.scene.control.behavior.DocumentViewBehavior;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;

import java.lang.ref.SoftReference;
import java.util.HashMap;

/**
 *
 */
public class DocumentViewSkin extends VirtualContainerBase<DocumentView, DocumentViewBehavior, PageView> {

    private HashMap<Integer, SoftReference<Image>> imageCaptureCache = new HashMap<>(25);

    private StackPane placeholderRegion;
    private Node placeholderNode;

    private int itemCount = -1;

    private boolean needCellsRebuilt = true;
    private boolean needCellsReconfigured = false;

    private Dimension2D defaultDimension;

    private DropShadow dropShadow;

    public DocumentViewSkin(DocumentView documentView) {
        super(documentView, new DocumentViewBehavior(documentView));

        updateListViewItems();

        flow.setId("virtual-flow");
        flow.setPannable(true);
        flow.setVertical(true);
        flow.setCreateCell(flow1 -> DocumentViewSkin.this.createCell());
        getChildren().add(flow);

        updatePageCount();

        // page shadow reuse.
        dropShadow = new DropShadow();
        dropShadow.setRadius(5.0);
        dropShadow.setOffsetX(3.0);
        dropShadow.setOffsetY(3.0);
        dropShadow.setColor(Color.color(0.4, 0.5, 0.5));


        if (getSkinnable().getNumberOfPages() > 0) {
            // find page size so we can apply a default size.
            Document document = getSkinnable().getDocument();
            PDimension dim = document.getPageDimension(0, (float) getSkinnable().getRotation(),
                    (float) getSkinnable().getScale());
            defaultDimension = new Dimension2D(dim.getWidth(), dim.getHeight());
        }
//        flow.setOnScroll(t -> {
//            // todo centering point
//            double x = t.getX();
//            double y = t.getY();
//            if (t.isControlDown()) {
//                if (t.getDeltaY() > 0) {
//                    incrementScale();
//                } else {
//                    decrementScale();
//                }
//                t.consume();
//            }
//        });

        // init the behavior 'closures'
//        getBehavior().setOnFocusPreviousRow(() -> { onFocusPreviousCell(); });
//        getBehavior().setOnFocusNextRow(() -> { onFocusNextCell(); });
//        getBehavior().setOnMoveToFirstCell(() -> { onMoveToFirstCell(); });
//        getBehavior().setOnMoveToLastCell(() -> { onMoveToLastCell(); });
//        getBehavior().setOnScrollPageDown(isFocusDriven -> onScrollPageDown(isFocusDriven));
//        getBehavior().setOnScrollPageUp(isFocusDriven -> onScrollPageUp(isFocusDriven));
//        getBehavior().setOnSelectPreviousRow(() -> { onSelectPreviousCell(); });
//        getBehavior().setOnSelectNextRow(() -> { onSelectNextCell(); });

//        documentView.itemsProperty().addListener(new WeakInvalidationListener(itemsChangeListener));
    }

    private void incrementScale() {
        DocumentView documentView = getSkinnable();
        double scale = documentView.getScale() + documentView.getScaleIncrementValue();
        if (scale <= documentView.getScaleMaxValue()) {
            documentView.setScale(scale);
        }
    }

    private void decrementScale() {
        DocumentView documentView = getSkinnable();
        double scale = documentView.getScale() - documentView.getScaleIncrementValue();
        if (scale >= documentView.getScaleMinValue()) {
            documentView.setScale(scale);
        }
    }

    @Override
    protected void handleControlPropertyChanged(String p) {
        super.handleControlPropertyChanged(p);
        if ("ITEMS".equals(p)) {
            updateListViewItems();
        } else if ("ORIENTATION".equals(p)) {
//            flow.setVertical(getSkinnable().getOrientation() == Orientation.VERTICAL);
        } else if ("CELL_FACTORY".equals(p)) {
            flow.recreateCells();
        } else if ("PARENT".equals(p)) {
            if (getSkinnable().getParent() != null && getSkinnable().isVisible()) {
                getSkinnable().requestLayout();
            }
        } else if ("PLACEHOLDER".equals(p)) {
            updatePlaceholderRegionVisibility();
        }
//        else if ("FIXED_CELL_SIZE".equals(p)) {
//            flow.setFixedCellSize(getSkinnable().getFixedCellSize());
//        }
    }
//
//    private MapChangeListener<Object, Object> propertiesMapListener = c -> {
//        if (! c.wasAdded()) return;
//        if (RECREATE.equals(c.getKey())) {
//            needCellsRebuilt = true;
//            getSkinnable().requestLayout();
//            getSkinnable().getProperties().remove(RECREATE);
//        }
//    };


    public void updateListViewItems() {

        pageCountDirty = true;
        getSkinnable().requestLayout();
    }

    @Override
    public int getPageCount() {
        return itemCount;
    }

    @Override
    protected void updatePageCount() {
        if (flow == null) return;

        Document document = getSkinnable().getDocument();

        int oldCount = itemCount;
        int newCount = document == null ? 0 : document.getNumberOfPages();

        itemCount = newCount;

        flow.setCellCount(newCount);

        getSkinnable().setHbar(flow.getHbar());

        updatePlaceholderRegionVisibility();
        if (newCount != oldCount) {
            needCellsRebuilt = true;
        } else {
            needCellsReconfigured = true;
        }
    }

    protected final void updatePlaceholderRegionVisibility() {
        boolean visible = getPageCount() == 0;

        if (visible) {
            placeholderNode = null;//getSkinnable().getPlaceholder();
            if (placeholderNode == null) {
                placeholderNode = new Label();
                ((Label) placeholderNode).setText("empty document");
            }
            if (placeholderNode != null) {
                if (placeholderRegion == null) {
                    placeholderRegion = new StackPane();
                    placeholderRegion.getStyleClass().setAll("placeholder");
                    getChildren().add(placeholderRegion);
                }
                placeholderRegion.getChildren().setAll(placeholderNode);
            }
        }
        flow.setVisible(!visible);
        if (placeholderRegion != null) {
            placeholderRegion.setVisible(visible);
        }
    }

    @Override
    public PageView createCell() {
        PageView cell = createDefaultCellImpl();
        cell.setLayoutX(-1);
        cell.setLayoutY(-1);
        cell.setDocumentView(getSkinnable());
        cell.scaleProperty().bind(getSkinnable().scaleProperty());
        cell.rotationProperty().bind(getSkinnable().rotationProperty());
        cell.setPadding(new Insets(10, 20, 10, 20));
        cell.setEffect(dropShadow);

        return cell;
    }

    private static PageView createDefaultCellImpl() {
        // todo add back factory to build pageView or documentView
        return new PageView();
    }

    @Override
    protected void layoutChildren(final double x, final double y,
                                  final double w, final double h) {
        super.layoutChildren(x, y, w, h);

        if (needCellsRebuilt) {
            flow.rebuildCells();
        } else if (needCellsReconfigured) {
            flow.reconfigureCells();
        }

        needCellsRebuilt = false;
        needCellsReconfigured = false;

        if (getPageCount() == 0) {
            // show message overlay instead of empty listview
            if (placeholderRegion != null) {
                placeholderRegion.setVisible(w > 0 && h > 0);
                placeholderRegion.resizeRelocate(x, y, w, h);
            }
        } else {
            flow.resizeRelocate(x, y, w, h);
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        checkState();

//        if (getPageCount() == 0) {
//            if (placeholderRegion == null) {
//                updatePlaceholderRegionVisibility();
//            }
//            if (placeholderRegion != null) {
//                return placeholderRegion.prefWidth(height) + leftInset + rightInset;
//            }
//        }
//
//        return computePrefHeight(-1, topInset, rightInset, bottomInset, leftInset) * 0.618033987;
        return defaultDimension.getWidth();
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return defaultDimension.getHeight();
    }

    public HashMap<Integer, SoftReference<Image>> getImageCaptureCache() {
        return imageCaptureCache;
    }

}
