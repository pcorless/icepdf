package com.icepdf.fx.scene.control;

import com.icepdf.fx.scene.control.skin.PageViewSkin;
import javafx.beans.property.*;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

import java.util.Random;

/**
 *
 */
public class PageView extends Control {

    private DoubleProperty scale;
    private DoubleProperty rotation;
    private ObjectProperty<DocumentView> documentView;
    private ObjectProperty<Bounds> clipBounds;
    private ObjectProperty<Bounds> pageBounds;

    private ReadOnlyIntegerWrapper index = new ReadOnlyIntegerWrapper(this, "index", -1);

    private ObjectProperty<Color> backgroundFill;

    private static Random random = new Random();

    public PageView() {
        scale = new SimpleDoubleProperty(1);
        rotation = new SimpleDoubleProperty(0);
        documentView = new SimpleObjectProperty<>();
        backgroundFill = new SimpleObjectProperty<>(Color.WHITE);
        clipBounds = new SimpleObjectProperty<>(new BoundingBox(0, 0, 0, 0));
        pageBounds = new SimpleObjectProperty<>(new BoundingBox(0, 0, 0, 0));

    }


    @Override
    protected Skin<?> createDefaultSkin() {
        return new PageViewSkin(this);
    }

    public double getScale() {
        return scale.get();
    }

    public DoubleProperty scaleProperty() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale.set(scale);
    }

    public void setScale(float scale) {
        this.scale.set(scale);
    }

    public double getRotation() {
        return rotation.get();
    }

    public DoubleProperty rotationProperty() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation.set(rotation);
    }


    public Color getBackgroundFill() {
        return backgroundFill.get();
    }

    public ObjectProperty<Color> backgroundFillProperty() {
        return backgroundFill;
    }

    public void setBackgroundFill(Color backgroundFill) {
        this.backgroundFill.set(backgroundFill);
    }


    public Bounds getClipBounds() {
        return clipBounds.get();
    }

    public ObjectProperty<Bounds> clipBoundsProperty() {
        return clipBounds;
    }

    public Bounds getPageBounds() {
        return pageBounds.get();
    }

    public ObjectProperty<Bounds> pageBoundsProperty() {
        return pageBounds;
    }

    public final int getIndex() {
        return index.get();
    }

    public ReadOnlyIntegerWrapper indexProperty() {
        return index;
    }

    public void updateIndex(int i) {
        final int oldIndex = index.get();
        index.set(i);
//        indexChanged(oldIndex, i);
    }

    public DocumentView getDocumentView() {
        return documentView.get();
    }


    public ObjectProperty<DocumentView> documentViewProperty() {
        return documentView;
    }

    public void setDocumentView(DocumentView documentView) {
        this.documentView.set(documentView);
    }
}
