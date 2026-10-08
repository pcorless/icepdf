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
package org.icepdf.fx.print;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A JavaFX print dialog: printer, pages, copies, paper, sizing, orientation, two-sided and
 * annotations, with a preview of each page on its sheet.  Its result is the {@link PrintSettings}
 * to print with, or empty when cancelled:
 * <pre>{@code
 * new PdfPrintDialog(stage, document, view.getCurrentPageIndex()).showAndWait()
 *         .ifPresent(view::print);
 * }</pre>
 * Printers are looked up off the FX thread (a print server can be slow to answer).  The look is
 * the application's own rather than AWT's: {@code javafx.print}'s dialog is AWT's, which on Linux is
 * the old Swing one.
 */
public class PdfPrintDialog extends Dialog<PrintSettings> {

    private static final double PREVIEW_W = 220;
    private static final double PREVIEW_H = 260;
    private static final Map<String, String> PAPER_NAMES = Map.of(
            "na-letter", "Letter", "na-legal", "Legal", "iso-a4", "A4", "iso-a3", "A3", "iso-a5", "A5",
            "executive", "Executive", "tabloid", "Tabloid", "ledger", "Ledger", "iso-b5", "B5", "jis-b5", "B5 (JIS)");

    private final Document document;
    private final int pageCount;
    private final ComboBox<PrintService> printer = new ComboBox<>();
    private final Label printerStatus = new Label("Looking for printers…");
    private final RadioButton allPages = new RadioButton();
    private final RadioButton currentPage = new RadioButton();
    private final RadioButton rangePages = new RadioButton("Pages");
    private final TextField range = new TextField();
    private final Spinner<Integer> copies = new Spinner<>(1, 999, 1);
    private final CheckBox collate = new CheckBox("Collate");
    private final ComboBox<MediaSizeName> paper = new ComboBox<>();
    private final RadioButton fit = new RadioButton("Fit to paper");
    private final RadioButton shrink = new RadioButton("Shrink oversized pages");
    private final RadioButton actual = new RadioButton("Actual size");
    private final ComboBox<PrintSettings.Orientation> orientation = new ComboBox<>();
    private final ComboBox<Sides> sides = new ComboBox<>();
    private final Label sidesLabel = new Label("Two-sided");
    private final CheckBox annotations = new CheckBox("Print annotations and form fields");
    private final Label note = new Label();
    private final Canvas preview = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Label previewCaption = new Label();
    private final int current;
    private boolean lowResolutionOnly;
    private boolean printersGiven;
    private int previewIndex;
    private PageFormat sheet;
    // small page renders for the preview, by page index (null value: render in progress).
    private final Map<Integer, WritableImage> thumbnails = new HashMap<>();
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "print-preview");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param owner       window the dialog is modal to; may be null
     * @param document    the document to print
     * @param currentPage zero-based page offered as "Current page"
     */
    public PdfPrintDialog(Window owner, Document document, int currentPage) {
        this.document = document;
        this.pageCount = document.getNumberOfPages();
        this.current = Math.max(0, Math.min(currentPage, pageCount - 1));
        if (owner != null) initOwner(owner);
        setTitle("Print");
        setResizable(false);
        getDialogPane().getStyleClass().add("pdf-print-dialog");

        ButtonType print = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(print, ButtonType.CANCEL);
        getDialogPane().setContent(buildContent());
        Node printButton = getDialogPane().lookupButton(print);
        printButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> printer.getValue() == null || selectedPages() == null,
                printer.valueProperty(), range.textProperty(), rangePages.selectedProperty()));
        setResultConverter(button -> button == print ? settings() : null);
        setOnHidden(e -> renderer.shutdownNow());
        lookUpPrinters();
    }

    /**
     * Marks the document as permitting only low-quality printing: pages print as
     * {@value DocumentPrinter#LOW_RESOLUTION_DPI} dpi images, and the dialog says so.
     */
    public void setLowResolutionOnly(boolean lowResolutionOnly) {
        this.lowResolutionOnly = lowResolutionOnly;
        note.setText(lowResolutionOnly ? "This document permits only low-resolution printing." : "");
    }

    // ---- layout ---------------------------------------------------------------------------

    private Node buildContent() {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(80);
        form.getColumnConstraints().addAll(labels, new ColumnConstraints());
        int row = 0;

        printer.setPrefWidth(260);
        printer.setButtonCell(new PrinterCell());
        printer.setCellFactory(list -> new PrinterCell());
        printer.valueProperty().addListener((obs, was, now) -> printerChanged());
        form.addRow(row++, new Label("Printer"), new VBox(2, printer, printerStatus));

        ToggleGroup pages = new ToggleGroup();
        allPages.setText("All (" + pageCount + (pageCount == 1 ? " page)" : " pages)"));
        currentPage.setText("Current page (" + (current + 1) + ")");
        allPages.setToggleGroup(pages);
        currentPage.setToggleGroup(pages);
        rangePages.setToggleGroup(pages);
        allPages.setSelected(true);
        range.setPromptText("e.g. 1-3, 5, 8-");
        range.setPrefColumnCount(12);
        range.textProperty().addListener((obs, was, now) -> {
            if (!now.isBlank()) rangePages.setSelected(true);
            boolean bad = rangePages.isSelected() && selectedPages() == null;
            range.setStyle(bad ? "-fx-border-color: #d33; -fx-border-width: 1;" : "");
            pagesChanged();
        });
        pages.selectedToggleProperty().addListener((obs, was, now) -> pagesChanged());
        form.addRow(row++, new Label("Pages"), new VBox(4, allPages, currentPage,
                new HBox(6, rangePages, range)));

        copies.setEditable(true);
        copies.setPrefWidth(80);
        collate.setSelected(true);
        collate.disableProperty().bind(copies.valueProperty().isEqualTo(1));
        HBox copiesRow = new HBox(10, copies, collate);
        copiesRow.setAlignment(Pos.CENTER_LEFT);
        form.addRow(row++, new Label("Copies"), copiesRow);

        paper.setPrefWidth(260);
        paper.setButtonCell(new PaperCell());
        paper.setCellFactory(list -> new PaperCell());
        paper.valueProperty().addListener((obs, was, now) -> sheetChanged());
        form.addRow(row++, new Label("Paper"), paper);

        ToggleGroup sizing = new ToggleGroup();
        fit.setToggleGroup(sizing);
        shrink.setToggleGroup(sizing);
        actual.setToggleGroup(sizing);
        fit.setSelected(true);
        sizing.selectedToggleProperty().addListener((obs, was, now) -> drawPreview());
        form.addRow(row++, new Label("Size"), new VBox(4, fit, shrink, actual));

        orientation.getItems().setAll(PrintSettings.Orientation.values());
        orientation.setValue(PrintSettings.Orientation.AUTO);
        orientation.setButtonCell(new OrientationCell());
        orientation.setCellFactory(list -> new OrientationCell());
        orientation.valueProperty().addListener((obs, was, now) -> drawPreview());
        form.addRow(row++, new Label("Orientation"), orientation);

        sides.setButtonCell(new SidesCell());
        sides.setCellFactory(list -> new SidesCell());
        form.addRow(row++, sidesLabel, sides);
        sidesLabel.managedProperty().bind(sidesLabel.visibleProperty());
        sides.managedProperty().bind(sides.visibleProperty());
        sidesLabel.visibleProperty().bind(sides.visibleProperty());
        sides.setVisible(false);

        annotations.setSelected(true);
        annotations.selectedProperty().addListener((obs, was, now) -> {
            thumbnails.clear();
            drawPreview();
        });
        form.add(annotations, 1, row++);
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #a60;");
        note.managedProperty().bind(note.textProperty().isNotEmpty());
        form.add(note, 0, row, 2, 1);

        Button previous = new Button("◀");
        Button next = new Button("▶");
        previous.setOnAction(e -> stepPreview(-1));
        next.setOnAction(e -> stepPreview(1));
        HBox caption = new HBox(8, previous, previewCaption, next);
        caption.setAlignment(Pos.CENTER);
        VBox previewBox = new VBox(6, preview, caption);
        previewBox.setAlignment(Pos.TOP_CENTER);

        HBox content = new HBox(20, form, previewBox);
        HBox.setHgrow(form, Priority.ALWAYS);
        content.setPadding(new Insets(10));
        pagesChanged();
        return content;
    }

    // ---- printers and paper ---------------------------------------------------------------

    private void lookUpPrinters() {
        Thread lookup = new Thread(() -> {
            PrintService[] services = PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PAGEABLE, null);
            PrintService preferred = PrintServiceLookup.lookupDefaultPrintService();
            Platform.runLater(() -> {
                if (!printersGiven) showPrinters(Arrays.asList(services), preferred);
            });
        }, "printer-lookup");
        lookup.setDaemon(true);
        lookup.start();
    }

    /**
     * Offers these printers instead of the system's (a file printer, say, or a vetted subset).
     *
     * @param preferred the one selected; null selects the first
     */
    public void setPrinters(List<PrintService> services, PrintService preferred) {
        printersGiven = true;
        showPrinters(services, preferred);
    }

    private void showPrinters(List<PrintService> services, PrintService preferred) {
        printer.getItems().setAll(services);
        PrintService chosen = preferred != null && services.contains(preferred) ? preferred
                : services.isEmpty() ? null : services.get(0);
        printer.setValue(chosen);
        printerStatus.setText(services.isEmpty() ? "No printers found." : "");
        printerStatus.setManaged(services.isEmpty());
        printerStatus.setVisible(services.isEmpty());
    }

    private void printerChanged() {
        PrintService service = printer.getValue();
        MediaSizeName keep = paper.getValue();
        List<MediaSizeName> sizes = new ArrayList<>();
        MediaSizeName fallback = null;
        Sides[] sideValues = null;
        if (service != null) {
            Object media = service.getSupportedAttributeValues(Media.class, null, null);
            if (media instanceof Media[] all) {
                for (Media m : all) {
                    if (m instanceof MediaSizeName name && MediaSize.getMediaSizeForName(name) != null) sizes.add(name);
                }
            }
            if (service.getDefaultAttributeValue(Media.class) instanceof MediaSizeName name) fallback = name;
            if (service.getSupportedAttributeValues(Sides.class, null, null) instanceof Sides[] s) sideValues = s;
        }
        if (sizes.isEmpty()) {
            sizes.addAll(List.of(MediaSizeName.NA_LETTER, MediaSizeName.ISO_A4, MediaSizeName.NA_LEGAL));
        }
        if (fallback == null || !sizes.contains(fallback)) fallback = defaultPaper(sizes);
        paper.getItems().setAll(sizes);
        paper.setValue(keep != null && sizes.contains(keep) ? keep : fallback);

        Sides keepSides = sides.getValue();
        sides.getItems().clear();
        if (sideValues != null && sideValues.length > 1) sides.getItems().setAll(sideValues);
        sides.setVisible(!sides.getItems().isEmpty());
        sides.setValue(sides.getItems().contains(keepSides) ? keepSides
                : sides.getItems().contains(Sides.ONE_SIDED) ? Sides.ONE_SIDED : null);
        sheetChanged();
    }

    /** Letter in the US and Canada, A4 elsewhere, if the printer has it. */
    private static MediaSizeName defaultPaper(List<MediaSizeName> sizes) {
        String country = Locale.getDefault().getCountry();
        MediaSizeName wanted = "US".equals(country) || "CA".equals(country) ? MediaSizeName.NA_LETTER
                : MediaSizeName.ISO_A4;
        return sizes.contains(wanted) ? wanted : sizes.get(0);
    }

    private void sheetChanged() {
        PrintService service = printer.getValue();
        sheet = service != null ? DocumentPrinter.paperFormat(service, settings()) : null;
        drawPreview();
    }

    // ---- result ---------------------------------------------------------------------------

    /** Zero-based pages chosen, or null for an invalid range. */
    private int[] selectedPages() {
        if (currentPage.isSelected()) return new int[]{current};
        if (rangePages.isSelected()) return PrintSettings.parsePageRange(range.getText(), pageCount);
        int[] all = new int[pageCount];
        for (int i = 0; i < pageCount; i++) all[i] = i;
        return all;
    }

    private PrintSettings settings() {
        PrintSettings settings = new PrintSettings();
        settings.setPrinter(printer.getValue());
        int[] pages = selectedPages();
        settings.setPages(pages != null ? pages : new int[0]);
        settings.setCopies(copies.getValue() != null ? copies.getValue() : 1);
        settings.setCollate(collate.isSelected());
        settings.setPaper(paper.getValue());
        settings.setScaling(actual.isSelected() ? PrintSettings.Scaling.ACTUAL_SIZE
                : shrink.isSelected() ? PrintSettings.Scaling.SHRINK_LARGE : PrintSettings.Scaling.FIT);
        settings.setOrientation(orientation.getValue());
        settings.setSides(sides.isVisible() ? sides.getValue() : null);
        settings.setAnnotations(annotations.isSelected());
        settings.setLowResolution(lowResolutionOnly);
        return settings;
    }

    // ---- preview --------------------------------------------------------------------------

    private void pagesChanged() {
        previewIndex = 0;
        drawPreview();
    }

    private void stepPreview(int delta) {
        int[] pages = selectedPages();
        if (pages == null || pages.length == 0) return;
        previewIndex = Math.floorMod(previewIndex + delta, pages.length);
        drawPreview();
    }

    /** The current preview page on its sheet: paper, printable area (dashed) and the placed page. */
    private void drawPreview() {
        GraphicsContext g = preview.getGraphicsContext2D();
        g.setFill(Color.rgb(230, 230, 230));
        g.fillRect(0, 0, PREVIEW_W, PREVIEW_H);
        int[] pages = selectedPages();
        if (pages == null || pages.length == 0) {
            previewCaption.setText("No pages");
            return;
        }
        previewIndex = Math.min(previewIndex, pages.length - 1);
        int pageIndex = pages[previewIndex];
        previewCaption.setText("Page " + (pageIndex + 1) + "  (" + (previewIndex + 1) + " of " + pages.length + ")");
        Page page = document.getPageTree().getPage(pageIndex);
        PDimension size = page != null ? page.getSize(Page.BOUNDARY_CROPBOX, 0, 1f) : null;
        if (size == null) return;

        // the sheet as the reader holds it, and its printable area (a 0.25in margin until a printer answers).
        PageFormat format = sheet != null ? (PageFormat) sheet.clone() : letterFormat();
        boolean landscape = DocumentPrinter.landscape(size.getWidth(), size.getHeight(),
                format.getPaper().getWidth(), format.getPaper().getHeight(), orientation.getValue());
        format.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        double paperW = format.getWidth();
        double paperH = format.getHeight();
        double ix = format.getImageableX();
        double iy = format.getImageableY();
        double iw = format.getImageableWidth();
        double ih = format.getImageableHeight();
        double scale = Math.min((PREVIEW_W - 16) / paperW, (PREVIEW_H - 16) / paperH);
        double ox = (PREVIEW_W - paperW * scale) / 2;
        double oy = (PREVIEW_H - paperH * scale) / 2;
        g.setFill(Color.rgb(0, 0, 0, 0.18));
        g.fillRect(ox + 2, oy + 2, paperW * scale, paperH * scale);
        g.setFill(Color.WHITE);
        g.fillRect(ox, oy, paperW * scale, paperH * scale);

        DocumentPrinter.Placement at = DocumentPrinter.place(size.getWidth(), size.getHeight(), ix, iy, iw, ih,
                actual.isSelected() ? PrintSettings.Scaling.ACTUAL_SIZE
                        : shrink.isSelected() ? PrintSettings.Scaling.SHRINK_LARGE : PrintSettings.Scaling.FIT);
        double px = ox + at.x() * scale;
        double py = oy + at.y() * scale;
        double pw = size.getWidth() * at.scale() * scale;
        double ph = size.getHeight() * at.scale() * scale;
        g.save();
        g.beginPath();
        g.rect(ox + ix * scale, oy + iy * scale, iw * scale, ih * scale);
        g.clip();
        WritableImage thumbnail = thumbnails.get(pageIndex);
        if (thumbnail != null) {
            g.drawImage(thumbnail, px, py, pw, ph);
        } else {
            g.setFill(Color.rgb(245, 245, 245));
            g.fillRect(px, py, pw, ph);
            requestThumbnail(pageIndex, Math.max(pw, ph));
        }
        g.setStroke(Color.rgb(160, 160, 160));
        g.setLineWidth(0.5);
        g.strokeRect(px, py, pw, ph);
        g.restore();
        g.setStroke(Color.rgb(120, 160, 220));
        g.setLineDashes(3, 3);
        g.strokeRect(ox + ix * scale, oy + iy * scale, iw * scale, ih * scale);
        g.setLineDashes();
    }

    private static PageFormat letterFormat() {
        PageFormat format = new PageFormat();
        java.awt.print.Paper letter = new java.awt.print.Paper();
        letter.setSize(612, 792);
        letter.setImageableArea(18, 18, 612 - 36, 792 - 36);
        format.setPaper(letter);
        return format;
    }

    private void requestThumbnail(int pageIndex, double longSide) {
        if (thumbnails.containsKey(pageIndex)) return;
        thumbnails.put(pageIndex, null);
        boolean withAnnotations = annotations.isSelected();
        renderer.submit(() -> {
            WritableImage image = renderThumbnail(pageIndex, longSide * 2, withAnnotations);
            Platform.runLater(() -> {
                if (image == null || withAnnotations != annotations.isSelected()) {
                    thumbnails.remove(pageIndex);
                    return;
                }
                thumbnails.put(pageIndex, image);
                drawPreview();
            });
        });
    }

    /** A page rendered as it prints (PRINT hints: only printable annotations), long side in px. */
    private WritableImage renderThumbnail(int pageIndex, double longSide, boolean withAnnotations) {
        try {
            Page page = document.getPageTree().getPage(pageIndex);
            page.init();
            PDimension size = page.getSize(Page.BOUNDARY_CROPBOX, 0, 1f);
            float zoom = (float) (longSide / Math.max(size.getWidth(), size.getHeight()));
            int w = Math.max(1, (int) Math.ceil(size.getWidth() * zoom));
            int h = Math.max(1, (int) Math.ceil(size.getHeight() * zoom));
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.setClip(0, 0, w, h);
                DocumentPrinter.paintPage(g, page, Page.BOUNDARY_CROPBOX, zoom, withAnnotations);
            } finally {
                g.dispose();
            }
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            WritableImage fx = new WritableImage(w, h);
            fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            return fx;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- cells ----------------------------------------------------------------------------

    private static final class PrinterCell extends ListCell<PrintService> {
        @Override
        protected void updateItem(PrintService item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.getName());
        }
    }

    private static final class PaperCell extends ListCell<MediaSizeName> {
        @Override
        protected void updateItem(MediaSizeName item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : paperName(item));
        }
    }

    /** "A4 (210 x 297 mm)", "Letter (8.5 x 11 in)". */
    static String paperName(MediaSizeName name) {
        String label = PAPER_NAMES.getOrDefault(name.toString(), name.toString());
        MediaSize size = MediaSize.getMediaSizeForName(name);
        if (size == null) return label;
        boolean inches = name.toString().startsWith("na-") || "executive".equals(name.toString())
                || "tabloid".equals(name.toString()) || "ledger".equals(name.toString());
        int unit = inches ? MediaSize.INCH : MediaSize.MM;
        return String.format(Locale.ROOT, "%s (%s x %s %s)", label, trim(size.getX(unit)), trim(size.getY(unit)),
                inches ? "in" : "mm");
    }

    private static String trim(float v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? String.valueOf(Math.round(v))
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private static final class OrientationCell extends ListCell<PrintSettings.Orientation> {
        @Override
        protected void updateItem(PrintSettings.Orientation item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : switch (item) {
                case AUTO -> "Automatic (per page)";
                case PORTRAIT -> "Portrait";
                case LANDSCAPE -> "Landscape";
            });
        }
    }

    private static final class SidesCell extends ListCell<Sides> {
        @Override
        protected void updateItem(Sides item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null
                    : item == Sides.ONE_SIDED ? "Off"
                    : item == Sides.TWO_SIDED_LONG_EDGE ? "Flip on long edge"
                    : item == Sides.TWO_SIDED_SHORT_EDGE ? "Flip on short edge" : item.toString());
        }
    }
}
