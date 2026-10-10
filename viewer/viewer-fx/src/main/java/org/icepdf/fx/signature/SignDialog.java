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
package org.icepdf.fx.signature;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DateFormat;
import java.util.List;

/**
 * Collects what signing a field needs: a PKCS#12 keystore (.p12 / .pfx) and its password, the
 * certificate to sign with, approval or certification, the signer's details, the appearance and an
 * optional timestamp authority.  The field shows the appearance live while the signer chooses.
 * <p>
 * The result is what to pass to {@link DocumentSigning#prepare}; on cancel the field's preview is
 * removed.  Signing itself happens when the document is saved ({@link DocumentSigning#saveSigned}).
 */
public class SignDialog extends Dialog<SignDialog.Result> {

    /** What the signer chose. */
    public record Result(SignerHandler signer, DocumentSigning.Request request, SignatureAppearance appearance) {
    }

    private static final double PREVIEW_W = 320;
    private static final double PREVIEW_H = 120;

    private final SignatureWidgetAnnotation field;
    private final SignatureAppearance appearance;
    private final TextField keystore = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label keystoreStatus = new Label();
    private final ListView<DocumentSigning.KeyEntry> certificates = new ListView<>();
    private final RadioButton approval = new RadioButton("Approval signature");
    private final RadioButton certification = new RadioButton("Certification signature (locks the document)");
    private final TextField name = new TextField();
    private final TextField contact = new TextField();
    private final TextField location = new TextField();
    private final TextField reason = new TextField();
    private final CheckBox visible = new CheckBox("Visible signature");
    private final CheckBox showText = new CheckBox("Show text");
    private final CheckBox showImage = new CheckBox("Show image");
    private final TextField imagePath = new TextField();
    private final ComboBox<SignatureAppearance.Layout> layout = new ComboBox<>();
    private final Spinner<Integer> fontSize = new Spinner<>(4, 36, 7);
    private final TextField timestampAuthority = new TextField();
    private final ImageView preview = new ImageView();
    private File keystoreFile;

    /**
     * @param field    the (empty) signature field to sign
     * @param document its document, to tell whether it may still be certified
     */
    public SignDialog(Window owner, Document document, SignatureWidgetAnnotation field) {
        this.field = field;
        this.appearance = new SignatureAppearance(field.getLibrary());
        if (owner != null) initOwner(owner);
        setTitle("Sign document");
        setResizable(true);
        getDialogPane().getStyleClass().add("pdf-sign-dialog");

        ButtonType sign = new ButtonType("Sign", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(sign, ButtonType.CANCEL);
        getDialogPane().lookupButton(sign).disableProperty().bind(
                certificates.getSelectionModel().selectedItemProperty().isNull());

        ToggleGroup type = new ToggleGroup();
        approval.setToggleGroup(type);
        certification.setToggleGroup(type);
        approval.setSelected(true);
        certification.setDisable(!DocumentSigning.canCertify(document));
        if (certification.isDisable()) {
            certification.setTooltip(new Tooltip("The document is already certified."));
        }

        TabPane tabs = new TabPane(new Tab("Certificate", certificateTab()), new Tab("Appearance", appearanceTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label previewLabel = new Label("Preview");
        previewLabel.setStyle("-fx-font-weight: bold;");
        preview.setFitWidth(PREVIEW_W);
        preview.setPreserveRatio(true);
        VBox previewBox = new VBox(6, previewLabel, preview);
        previewBox.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-padding: 6;");
        previewBox.setMinHeight(PREVIEW_H);
        VBox content = new VBox(10, tabs, previewBox);
        content.setPadding(new Insets(10));
        content.setMinWidth(560);
        getDialogPane().setContent(content);

        type.selectedToggleProperty().addListener((o, a, b) -> refresh());
        for (TextField t : List.of(name, contact, location)) t.textProperty().addListener((o, a, b) -> refresh());
        for (CheckBox c : List.of(visible, showText, showImage)) c.selectedProperty().addListener((o, a, b) -> refresh());
        layout.valueProperty().addListener((o, a, b) -> refresh());
        fontSize.valueProperty().addListener((o, a, b) -> refresh());
        certificates.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now == null) return;
            DocumentSigning.Request from = DocumentSigning.requestFrom(now.certificate(), SignatureType.SIGNER);
            name.setText(from.name() != null ? from.name() : "");
            contact.setText(from.contact() != null ? from.contact() : "");
            location.setText(from.location() != null ? from.location() : "");
            refresh();
        });

        setResultConverter(button -> {
            if (button != sign || certificates.getSelectionModel().getSelectedItem() == null) {
                DocumentSigning.cancel(field);
                return null;
            }
            String tsa = timestampAuthority.getText().isBlank() ? null : timestampAuthority.getText().strip();
            SignerHandler signer = new Pkcs12SignerHandler(tsa, keystoreFile,
                    certificates.getSelectionModel().getSelectedItem().alias(),
                    new SimplePasswordCallbackHandler(password.getText()));
            return new Result(signer, request(), appearance);
        });
        refresh();
    }

    // ---- tabs ---------------------------------------------------------------------------

    private Node certificateTab() {
        keystore.setPromptText("Keystore file (.p12, .pfx)");
        HBox.setHgrow(keystore, Priority.ALWAYS);
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PKCS#12 keystore", "*.p12", "*.pfx"));
            File file = chooser.showOpenDialog(getOwner());
            if (file != null) keystore.setText(file.getAbsolutePath());
        });
        password.setPromptText("Keystore password");
        Button open = new Button("Open");
        open.setDefaultButton(false);
        open.setOnAction(e -> openKeystore());
        password.setOnAction(e -> openKeystore());
        HBox keystoreRow = new HBox(6, keystore, browse);
        HBox passwordRow = new HBox(6, password, open);
        HBox.setHgrow(password, Priority.ALWAYS);
        keystoreStatus.setStyle("-fx-text-fill: #b00;");
        keystoreStatus.managedProperty().bind(keystoreStatus.textProperty().isNotEmpty());

        certificates.setPrefHeight(110);
        certificates.setPlaceholder(new Label("Open a keystore to list its certificates"));
        certificates.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentSigning.KeyEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String issuer = SignatureVerifier.part(item.certificate(), "CN");
                String issuerCn = issuerName(item);
                setText(item.commonName() + "\n  issued by " + (issuerCn != null ? issuerCn : issuer)
                        + ", valid until " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(item.certificate().getNotAfter()));
            }
        });

        GridPane details = new GridPane();
        details.setHgap(8);
        details.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(80);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        details.getColumnConstraints().addAll(labels, fields);
        reason.setPromptText("optional");
        timestampAuthority.setPromptText("https://… (optional)");
        details.addRow(0, new Label("Name"), name);
        details.addRow(1, new Label("Contact"), contact);
        details.addRow(2, new Label("Location"), location);
        details.addRow(3, new Label("Reason"), reason);
        details.addRow(4, new Label("Timestamp"), timestampAuthority);

        VBox tab = new VBox(8, keystoreRow, passwordRow, keystoreStatus, certificates,
                new HBox(16, approval, certification), details);
        tab.setPadding(new Insets(10));
        return tab;
    }

    private static String issuerName(DocumentSigning.KeyEntry entry) {
        try {
            javax.naming.ldap.LdapName name = new javax.naming.ldap.LdapName(
                    entry.certificate().getIssuerX500Principal().getName());
            for (javax.naming.ldap.Rdn rdn : name.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) return String.valueOf(rdn.getValue());
            }
        } catch (javax.naming.InvalidNameException e) {
            // fall through
        }
        return null;
    }

    private Node appearanceTab() {
        visible.setSelected(true);
        showText.setSelected(true);
        showImage.setSelected(false);
        imagePath.setPromptText("Signature image (.png, .jpg)");
        HBox.setHgrow(imagePath, Priority.ALWAYS);
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image", "*.png", "*.jpg", "*.jpeg",
                    "*.gif"));
            File file = chooser.showOpenDialog(getOwner());
            if (file != null) {
                imagePath.setText(file.getAbsolutePath());
                showImage.setSelected(true);
            }
        });
        imagePath.setOnAction(e -> refresh());
        imagePath.focusedProperty().addListener((o, was, now) -> {
            if (!now) refresh();
        });
        layout.getItems().setAll(SignatureAppearance.Layout.values());
        layout.setValue(SignatureAppearance.Layout.SIDE_BY_SIDE);
        layout.setButtonCell(new LayoutCell());
        layout.setCellFactory(v -> new LayoutCell());
        fontSize.setEditable(true);
        fontSize.setPrefWidth(80);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, visible);
        grid.addRow(1, showText, new HBox(6, new Label("Font size"), fontSize));
        grid.addRow(2, showImage, new HBox(6, imagePath, browse));
        grid.addRow(3, new Label("Layout"), layout);
        GridPane.setHgrow(imagePath.getParent(), Priority.ALWAYS);
        grid.setPadding(new Insets(10));
        return grid;
    }

    private static final class LayoutCell extends ListCell<SignatureAppearance.Layout> {
        @Override
        protected void updateItem(SignatureAppearance.Layout item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item == SignatureAppearance.Layout.OVERLAY
                    ? "Text over the image" : "Image beside the text");
        }
    }

    // ---- behaviour ----------------------------------------------------------------------

    private void openKeystore() {
        keystoreStatus.setText("");
        certificates.getItems().clear();
        File file = new File(keystore.getText().strip());
        if (!file.isFile()) {
            keystoreStatus.setText("Choose a keystore file.");
            return;
        }
        try {
            List<DocumentSigning.KeyEntry> entries = DocumentSigning.keyEntries(new Pkcs12SignerHandler(null, file,
                    null, new SimplePasswordCallbackHandler(password.getText())));
            if (entries.isEmpty()) {
                keystoreStatus.setText("The keystore holds no certificate with a private key.");
                return;
            }
            keystoreFile = file;
            certificates.getItems().setAll(entries);
            certificates.getSelectionModel().selectFirst();
        } catch (Exception e) {
            keystoreStatus.setText("Couldn't open the keystore: wrong password, or not a PKCS#12 file.");
        }
    }

    private DocumentSigning.Request request() {
        return new DocumentSigning.Request(certification.isSelected() ? SignatureType.CERTIFIER : SignatureType.SIGNER,
                blankToNull(name.getText()), blankToNull(contact.getText()), blankToNull(location.getText()),
                blankToNull(reason.getText()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Applies the choices to the appearance and redraws the field and the preview. */
    private void refresh() {
        DocumentSigning.Request request = request();
        appearance.setSignatureType(request.type());
        appearance.setName(request.name());
        appearance.setContact(request.contact());
        appearance.setLocation(request.location());
        appearance.setSignatureVisible(visible.isSelected());
        appearance.setTextVisible(showText.isSelected());
        appearance.setImageVisible(showImage.isSelected());
        appearance.setLayout(layout.getValue());
        if (fontSize.getValue() != null) appearance.setFontSize(fontSize.getValue());
        String path = imagePath.getText().strip();
        appearance.setImage(path.isEmpty() ? null : SignatureUtilities.loadSignatureImage(path));
        try {
            DocumentSigning.previewAppearance(field, appearance);
            preview.setImage(render());
        } catch (RuntimeException e) {
            preview.setImage(null);
        }
    }

    /** The field's appearance as it will show, at twice the preview size for crispness. */
    private WritableImage render() {
        Rectangle2D r = field.getUserSpaceRectangle();
        double zoom = Math.min(PREVIEW_W * 2 / Math.max(1, r.getWidth()), PREVIEW_H * 2 / Math.max(1, r.getHeight()));
        int w = Math.max(1, (int) Math.ceil(r.getWidth() * zoom));
        int h = Math.max(1, (int) Math.ceil(r.getHeight() * zoom));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xC0C0C0));
            g.drawRect(0, 0, w - 1, h - 1);
            // page space (y up) to the image (y down), the field's corner at the origin.
            AffineTransform at = new AffineTransform(zoom, 0, 0, -zoom, -r.getX() * zoom, (r.getY() + r.getHeight()) * zoom);
            g.transform(at);
            field.render(g, GraphicsRenderingHints.SCREEN, 0f, (float) zoom, false);
        } finally {
            g.dispose();
        }
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        WritableImage fx = new WritableImage(w, h);
        fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return fx;
    }
}
