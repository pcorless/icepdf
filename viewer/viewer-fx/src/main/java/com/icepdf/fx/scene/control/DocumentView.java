package com.icepdf.fx.scene.control;

import com.icepdf.fx.scene.control.skin.DocumentViewSkin;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import org.icepdf.core.pobjects.Document;

/**
 *
 */
public class DocumentView extends Control {

    private enum PageViewMode {SINGLE_PAGE, SINGLE_COLUMN, DOUBLE_PAGE, DOUBLE_COLUMN}

    ;

    private DoubleProperty scaleIncrementValue;
    private DoubleProperty scaleMaxValue;
    private DoubleProperty scaleMinValue;

    private int currentPageIndex;

    private DoubleProperty scale;
    private DoubleProperty rotation;
    private ObjectProperty<Document> document;

    // viewport
    private ScrollBar hbar;

    private ObjectProperty<Color> backgroundFill;

    private int numberOfPages;

    // todo Document insertion.
    public DocumentView(Document document) {
        this.numberOfPages = document.getNumberOfPages();
        this.document = new SimpleObjectProperty<>(document);
        scale = new SimpleDoubleProperty(1);
        rotation = new SimpleDoubleProperty(0);
        backgroundFill = new SimpleObjectProperty<>(Color.LIGHTGRAY);

        // mouse wheel and touch zoom level
        scaleIncrementValue = new SimpleDoubleProperty(0.05);
        scaleMaxValue = new SimpleDoubleProperty(8);
        scaleMinValue = new SimpleDoubleProperty(0.05);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DocumentViewSkin(this);
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

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public double getScaleIncrementValue() {
        return scaleIncrementValue.get();
    }

    public DoubleProperty scaleIncrementValueProperty() {
        return scaleIncrementValue;
    }

    public void setScaleIncrementValue(double scaleIncrementValue) {
        this.scaleIncrementValue.set(scaleIncrementValue);
    }

    public double getScaleMaxValue() {
        return scaleMaxValue.get();
    }

    public DoubleProperty scaleMaxValueProperty() {
        return scaleMaxValue;
    }

    public void setScaleMaxValue(double scaleMaxValue) {
        this.scaleMaxValue.set(scaleMaxValue);
    }

    public double getScaleMinValue() {
        return scaleMinValue.get();
    }

    public DoubleProperty scaleMinValueProperty() {
        return scaleMinValue;
    }

    public void setScaleMinValue(double scaleMinValue) {
        this.scaleMinValue.set(scaleMinValue);
    }

    public Document getDocument() {
        return document.get();
    }

    public ObjectProperty<Document> documentProperty() {
        return document;
    }

    public void setDocument(Document document) {
        this.document.set(document);
    }

    public ScrollBar getHbar() {
        return hbar;
    }

    public void setHbar(ScrollBar hbar) {
        this.hbar = hbar;
    }
}
