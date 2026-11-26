/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.fx.watermark;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import org.icepdf.core.util.watermark.Watermark;
import org.icepdf.fx.i18n.Messages;

/**
 * Asks for a watermark: its text, font, size, colour, opacity and position, with a sketch of where
 * it lands on the page. Gives the {@link Watermark} to apply to every page, or nothing on Cancel.
 * <p>
 * The sketch is drawn, not rendered from the document, so it follows every change at once; the
 * page's shape is the current page as shown, rotation included.
 */
public class WatermarkDialog extends Dialog<Watermark> {

    static final double PREVIEW_SIZE = 220;
    /** Same as the core's edge margin, in points. */
    private static final double EDGE_MARGIN = 18;

    private final TextField text = new TextField();
    private final ComboBox<Watermark.StandardFont> font = new ComboBox<>();
    private final Spinner<Integer> fontSize = new Spinner<>(4, 288, 12);
    private final CheckBox fitToPage = new CheckBox(Messages.get("watermarkDialog.fit"));
    private final ToggleGroup position = new ToggleGroup();
    private final RadioButton header = new RadioButton(Messages.get("watermarkDialog.position.header"));
    private final RadioButton footer = new RadioButton(Messages.get("watermarkDialog.position.footer"));
    private final RadioButton overlay = new RadioButton(Messages.get("watermarkDialog.position.overlay"));
    private final ColorPicker color = new ColorPicker(Color.GRAY);
    private final Slider opacity = new Slider(0.05, 1, 0.5);
    private final Canvas preview = new Canvas(PREVIEW_SIZE, PREVIEW_SIZE);
    private final double pageWidth;
    private final double pageHeight;
    private final boolean documentText;

    /**
     * @param pageWidth   the current page's width as shown, in points (height for a quarter-turned page)
     * @param pageHeight  its height as shown
     * @param initialText what to start with: the document's current watermark text, or null
     */
    public WatermarkDialog(Window owner, double pageWidth, double pageHeight, String initialText) {
        this.pageWidth = pageWidth > 0 ? pageWidth : 612;
        this.pageHeight = pageHeight > 0 ? pageHeight : 792;
        if (owner != null) initOwner(owner);
        setTitle(Messages.get("watermarkDialog.title"));
        getDialogPane().getStyleClass().add("pdf-watermark-dialog");

        ButtonType apply = new ButtonType(Messages.get("watermarkDialog.apply"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
        getDialogPane().lookupButton(apply).disableProperty().bind(
                javafx.beans.binding.Bindings.createBooleanBinding(() -> text.getText().isBlank(), text.textProperty()));

        documentText = initialText != null && !initialText.isBlank();
        text.setText(documentText ? initialText : Messages.get("watermarkDialog.defaultText"));
        text.setPrefColumnCount(24);
        font.getItems().addAll(Watermark.StandardFont.values());
        font.setValue(Watermark.StandardFont.HELVETICA_BOLD);
        font.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Watermark.StandardFont f) {
                return f == null ? "" : f.getBaseFont().replace('-', ' ');
            }

            @Override
            public Watermark.StandardFont fromString(String s) {
                return null;
            }
        });
        fontSize.setEditable(true);
        fontSize.setPrefWidth(80);
        header.setToggleGroup(position);
        footer.setToggleGroup(position);
        overlay.setToggleGroup(position);
        overlay.setSelected(true);
        fitToPage.setSelected(true);
        fitToPage.disableProperty().bind(overlay.selectedProperty().not());
        fontSize.disableProperty().bind(fitToPage.selectedProperty().and(overlay.selectedProperty()));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(Messages.get("watermarkDialog.text")), text);
        form.addRow(row++, new Label(Messages.get("watermarkDialog.font")), font);
        form.addRow(row++, new Label(Messages.get("watermarkDialog.size")), new HBox(8, fontSize, fitToPage));
        form.addRow(row++, new Label(Messages.get("watermarkDialog.position")), new HBox(12, header, footer, overlay));
        form.addRow(row++, new Label(Messages.get("watermarkDialog.color")), color);
        form.addRow(row, new Label(Messages.get("watermarkDialog.opacity")), opacity);

        Label previewLabel = new Label(Messages.get("watermarkDialog.preview"));
        previewLabel.setStyle("-fx-font-weight: bold;");
        Label note = new Label(Messages.get("watermarkDialog.allPages"));
        note.setWrapText(true);
        note.setMaxWidth(PREVIEW_SIZE + 280);
        VBox content = new VBox(10, new HBox(16, form, new VBox(6, previewLabel, preview)), note);
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);

        text.textProperty().addListener((o, a, b) -> drawPreview());
        font.valueProperty().addListener((o, a, b) -> drawPreview());
        fontSize.valueProperty().addListener((o, a, b) -> drawPreview());
        fitToPage.selectedProperty().addListener((o, a, b) -> drawPreview());
        position.selectedToggleProperty().addListener((o, a, b) -> drawPreview());
        color.valueProperty().addListener((o, a, b) -> drawPreview());
        opacity.valueProperty().addListener((o, a, b) -> drawPreview());

        setResultConverter(button -> button == apply && !text.getText().isBlank() ? watermark() : null);
        drawPreview();
    }

    /**
     * Starts from the last watermark applied: its font, size, position, colour and opacity, and its
     * text unless the document already has a watermark (whose text the dialog keeps).
     *
     * @param last the last watermark applied, or null to keep the defaults
     */
    public void setDefaults(Watermark last) {
        if (last == null) return;
        if (!documentText) text.setText(last.getText());
        font.setValue(last.getFont());
        switch (last.getPosition()) {
            case HEADER -> header.setSelected(true);
            case FOOTER -> footer.setSelected(true);
            default -> overlay.setSelected(true);
        }
        boolean fit = last.getPosition() == Watermark.Position.OVERLAY && last.getFontSize() == 0;
        fitToPage.setSelected(fit);
        if (last.getFontSize() > 0) {
            SpinnerValueFactory.IntegerSpinnerValueFactory sizes =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) fontSize.getValueFactory();
            int size = Math.round(last.getFontSize());
            sizes.setValue(Math.max(sizes.getMin(), Math.min(sizes.getMax(), size)));
        }
        java.awt.Color c = last.getColor();
        color.setValue(Color.rgb(c.getRed(), c.getGreen(), c.getBlue()));
        opacity.setValue(Math.max(opacity.getMin(), Math.min(opacity.getMax(), last.getOpacity())));
        drawPreview();
    }

    /** The watermark as the controls stand. */
    Watermark watermark() {
        Color c = color.getValue();
        Watermark.Position where = header.isSelected() ? Watermark.Position.HEADER
                : footer.isSelected() ? Watermark.Position.FOOTER : Watermark.Position.OVERLAY;
        float size = where == Watermark.Position.OVERLAY && fitToPage.isSelected() ? 0 : fontSize.getValue();
        return Watermark.of(text.getText())
                .withFont(font.getValue())
                .withFontSize(size)
                .withColor(new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue()))
                .withOpacity((float) opacity.getValue())
                .withPosition(where);
    }

    private void drawPreview() {
        GraphicsContext g = preview.getGraphicsContext2D();
        g.clearRect(0, 0, PREVIEW_SIZE, PREVIEW_SIZE);
        double scale = (PREVIEW_SIZE - 8) / Math.max(pageWidth, pageHeight);
        double w = pageWidth * scale, h = pageHeight * scale;
        double left = (PREVIEW_SIZE - w) / 2, top = (PREVIEW_SIZE - h) / 2;
        g.setFill(Color.WHITE);
        g.fillRect(left, top, w, h);
        g.setStroke(Color.web("#999"));
        g.strokeRect(left, top, w, h);
        if (text.getText().isBlank()) return;

        Watermark mark = watermark();
        double size = mark.getFontSize();
        double unitWidth = measure(mark, 1);
        if (mark.getPosition() == Watermark.Position.OVERLAY && size == 0) {
            size = Math.min(0.7 * Math.hypot(pageWidth, pageHeight) / unitWidth, Math.min(pageWidth, pageHeight) / 4);
        } else if (mark.getPosition() != Watermark.Position.OVERLAY && unitWidth * size > pageWidth - 2 * EDGE_MARGIN) {
            size = (pageWidth - 2 * EDGE_MARGIN) / unitWidth;
        }
        Color c = color.getValue();
        g.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), opacity.getValue()));
        g.setFont(fxFont(mark, size * scale));
        g.setTextAlign(TextAlignment.CENTER);
        g.save();
        switch (mark.getPosition()) {
            case HEADER -> {
                g.setTextBaseline(VPos.TOP);
                g.fillText(mark.getText(), left + w / 2, top + EDGE_MARGIN * scale);
            }
            case FOOTER -> {
                g.setTextBaseline(VPos.BASELINE);
                g.fillText(mark.getText(), left + w / 2, top + h - EDGE_MARGIN * scale);
            }
            default -> {
                g.translate(left + w / 2, top + h / 2);
                g.rotate(-Math.toDegrees(Math.atan2(pageHeight, pageWidth)));
                g.setTextBaseline(VPos.CENTER);
                g.fillText(mark.getText(), 0, 0);
            }
        }
        g.restore();
    }

    /** Text width in points at the given size, measured with the nearest screen font. */
    private static double measure(Watermark mark, double size) {
        Text probe = new Text(mark.getText());
        probe.setFont(fxFont(mark, 100));
        return probe.getLayoutBounds().getWidth() / 100 * size;
    }

    private static Font fxFont(Watermark mark, double size) {
        Watermark.StandardFont f = mark.getFont();
        String family = switch (f) {
            case TIMES_ROMAN, TIMES_BOLD -> "Serif";
            case COURIER, COURIER_BOLD -> "Monospaced";
            default -> "SansSerif";
        };
        boolean bold = f == Watermark.StandardFont.HELVETICA_BOLD || f == Watermark.StandardFont.TIMES_BOLD
                || f == Watermark.StandardFont.COURIER_BOLD;
        return Font.font(family, bold ? FontWeight.BOLD : FontWeight.NORMAL, FontPosture.REGULAR, Math.max(1, size));
    }

    // for tests
    TextField textField() {
        return text;
    }

    RadioButton headerButton() {
        return header;
    }

    RadioButton footerButton() {
        return footer;
    }

    CheckBox fitToPageBox() {
        return fitToPage;
    }

    Spinner<Integer> fontSizeSpinner() {
        return fontSize;
    }

    ComboBox<Watermark.StandardFont> fontBox() {
        return font;
    }
}
