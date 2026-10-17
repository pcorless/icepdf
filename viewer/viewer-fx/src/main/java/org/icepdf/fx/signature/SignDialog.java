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

import org.icepdf.fx.i18n.Messages;

import javafx.application.Platform;
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
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs11SignerHandler;
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
import java.util.Objects;
import java.text.DateFormat;
import java.util.List;

/**
 * Collects what signing a field needs: the signer's key - a PKCS#12 file (.p12 / .pfx) or a PKCS#11
 * smart card or token - and its password or PIN, the certificate to sign with, approval or
 * certification, the signer's details, the appearance (with an optional image, drawn on the spot or
 * from a file) and an optional timestamp authority.  The field shows the appearance live while the
 * signer chooses.
 * <p>
 * The dialog starts from a {@link SigningSettings} and writes the choices back to it on Sign, so a
 * signer who keeps it only types the password next time.
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
    /** Pixels per pad unit for a drawn signature: crisp in print at the size a field usually is. */
    private static final double DRAWN_SCALE = 3;

    private final SignatureWidgetAnnotation field;
    private final SignatureAppearance appearance;
    private final SigningSettings settings;
    private final ComboBox<SigningSettings.KeystoreType> keystoreType = new ComboBox<>();
    private final TextField keystore = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label keystoreStatus = new Label();
    private final ListView<DocumentSigning.KeyEntry> certificates = new ListView<>();
    private final RadioButton approval = new RadioButton(Messages.get("signDialog.approval"));
    private final RadioButton certification = new RadioButton(Messages.get("signDialog.certification"));
    private final TextField name = new TextField();
    private final TextField contact = new TextField();
    private final TextField location = new TextField();
    private final TextField reason = new TextField();
    private final CheckBox visible = new CheckBox(Messages.get("signDialog.visible"));
    private final CheckBox showText = new CheckBox(Messages.get("signDialog.showText"));
    private final ToggleGroup imageSource = new ToggleGroup();
    private final RadioButton noImage = new RadioButton(Messages.get("signDialog.noImage"));
    private final RadioButton drawnImage = new RadioButton(Messages.get("signDialog.drawnImage"));
    private final RadioButton fileImage = new RadioButton(Messages.get("signDialog.fileImage"));
    private final TextField imagePath = new TextField();
    private final SignaturePad pad;
    private final ComboBox<SignatureAppearance.Layout> layout = new ComboBox<>();
    private final Spinner<Integer> fontSize = new Spinner<>(4, 36, 7);
    private final TextField timestampAuthority = new TextField();
    private final ImageView preview = new ImageView();
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SignDialog.class.getName());
    private SignerHandler signer;
    // a keystore or token being opened (off the FX thread: a token can take seconds); a newer open
    // or a change of keystore type makes an older one's answer stale.
    private final javafx.beans.property.BooleanProperty opening = new javafx.beans.property.SimpleBooleanProperty();
    private int keystoreGeneration;
    private BufferedImage drawn;
    private boolean drawnStale = true;
    private String loadedPath;
    private BufferedImage loaded;

    /**
     * @param field    the (empty) signature field to sign
     * @param document its document, to tell whether it may still be certified
     */
    public SignDialog(Window owner, Document document, SignatureWidgetAnnotation field) {
        this(owner, document, field, new SigningSettings());
    }

    /**
     * @param field    the (empty) signature field to sign
     * @param document its document, to tell whether it may still be certified
     * @param settings what to start from; the choices are written back to it on Sign
     */
    public SignDialog(Window owner, Document document, SignatureWidgetAnnotation field, SigningSettings settings) {
        this.field = field;
        this.settings = Objects.requireNonNull(settings);
        this.appearance = new SignatureAppearance(field.getLibrary());
        this.pad = new SignaturePad(settings.getInk());
        if (owner != null) initOwner(owner);
        setTitle(Messages.get("signDialog.title"));
        setResizable(true);
        getDialogPane().getStyleClass().add("pdf-sign-dialog");

        ButtonType sign = new ButtonType(Messages.get("signDialog.sign"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(sign, ButtonType.CANCEL);
        getDialogPane().lookupButton(sign).disableProperty().bind(
                certificates.getSelectionModel().selectedItemProperty().isNull());

        ToggleGroup type = new ToggleGroup();
        approval.setToggleGroup(type);
        certification.setToggleGroup(type);
        approval.setSelected(true);
        certification.setDisable(!DocumentSigning.canCertify(document));
        if (certification.isDisable()) {
            certification.setTooltip(new Tooltip(Messages.get("signDialog.alreadyCertified")));
        }

        TabPane tabs = new TabPane(new Tab(Messages.get("signDialog.tab.certificate"), certificateTab()), new Tab(Messages.get("signDialog.tab.appearance"), appearanceTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label previewLabel = new Label(Messages.get("signDialog.preview"));
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
        for (TextField t : List.of(name)) t.textProperty().addListener((o, a, b) -> refresh());
        for (TextField t : List.of(contact, location)) t.textProperty().addListener((o, a, b) -> refresh());
        for (CheckBox c : List.of(visible, showText)) c.selectedProperty().addListener((o, a, b) -> refresh());
        imageSource.selectedToggleProperty().addListener((o, a, b) -> refresh());
        pad.setOnChanged(() -> {
            drawnStale = true;
            if (!drawnImage.isSelected()) drawnImage.setSelected(true);
            else refresh();
        });
        layout.valueProperty().addListener((o, a, b) -> refresh());
        fontSize.valueProperty().addListener((o, a, b) -> refresh());
        certificates.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now == null) return;
            DocumentSigning.Request from = DocumentSigning.requestFrom(now.certificate(), SignatureType.SIGNER);
            name.setText(from.name() != null ? from.name() : "");
            // what the signer typed last time wins over what the certificate says.
            contact.setText(firstOf(settings.getContact(), from.contact()));
            location.setText(firstOf(settings.getLocation(), from.location()));
            refresh();
        });

        setResultConverter(button -> {
            if (button != sign || certificates.getSelectionModel().getSelectedItem() == null) {
                DocumentSigning.cancel(field);
                return null;
            }
            DocumentSigning.KeyEntry entry = certificates.getSelectionModel().getSelectedItem();
            String tsa = blankToNull(timestampAuthority.getText());
            // a fresh handler with the timestamp authority; the PIN or password is the one that opened it.
            SignerHandler handler = signer instanceof Pkcs11SignerHandler
                    ? new Pkcs11SignerHandler(tsa, settings.getPkcs11Config(), entry.certificate().getSerialNumber(),
                    new SimplePasswordCallbackHandler(password.getText()))
                    : new Pkcs12SignerHandler(tsa, new File(settings.getPkcs12Path()), entry.alias(),
                    new SimplePasswordCallbackHandler(password.getText()));
            remember(entry);
            return new Result(handler, request(), appearance);
        });
        restore();
        refresh();
        setOnShown(e -> Platform.runLater(() -> {
            if (!keystore.getText().isBlank()) password.requestFocus();
        }));
    }

    /** Puts the remembered choices in the controls. */
    private void restore() {
        keystoreType.setValue(settings.getKeystoreType());
        keystore.setText(Objects.toString(settings.getKeystorePath(), ""));
        timestampAuthority.setText(Objects.toString(settings.getTimestampAuthority(), ""));
        contact.setText(Objects.toString(settings.getContact(), ""));
        location.setText(Objects.toString(settings.getLocation(), ""));
        reason.setText(Objects.toString(settings.getReason(), ""));
        visible.setSelected(settings.isVisible());
        showText.setSelected(settings.isShowText());
        imagePath.setText(Objects.toString(settings.getImagePath(), ""));
        layout.setValue(settings.getLayout());
        fontSize.getValueFactory().setValue(settings.getFontSize());
        switch (settings.getImageSource()) {
            case DRAWN -> drawnImage.setSelected(true);
            case FILE -> fileImage.setSelected(true);
            default -> noImage.setSelected(true);
        }
    }

    /** Writes the choices back to the settings (never the password). */
    private void remember(DocumentSigning.KeyEntry entry) {
        settings.setKeystoreType(keystoreType.getValue());
        if (keystoreType.getValue() == SigningSettings.KeystoreType.PKCS11) settings.setPkcs11Config(keystore.getText().strip());
        else settings.setPkcs12Path(keystore.getText().strip());
        settings.setCertificateAlias(entry.alias());
        settings.setTimestampAuthority(blankToNull(timestampAuthority.getText()));
        settings.setContact(blankToNull(contact.getText()));
        settings.setLocation(blankToNull(location.getText()));
        settings.setReason(blankToNull(reason.getText()));
        settings.setVisible(visible.isSelected());
        settings.setShowText(showText.isSelected());
        settings.setImageSource(imageSourceChoice());
        settings.setImagePath(blankToNull(imagePath.getText()));
        settings.setLayout(layout.getValue());
        if (fontSize.getValue() != null) settings.setFontSize(fontSize.getValue());
    }

    private SigningSettings.ImageSource imageSourceChoice() {
        return drawnImage.isSelected() ? SigningSettings.ImageSource.DRAWN
                : fileImage.isSelected() ? SigningSettings.ImageSource.FILE : SigningSettings.ImageSource.NONE;
    }

    private static String firstOf(String a, String b) {
        return a != null && !a.isBlank() ? a : b != null ? b : "";
    }

    // ---- tabs ---------------------------------------------------------------------------

    private Node certificateTab() {
        keystoreType.getItems().setAll(SigningSettings.KeystoreType.values());
        keystoreType.setButtonCell(new KeystoreTypeCell());
        keystoreType.setCellFactory(v -> new KeystoreTypeCell());
        keystoreType.valueProperty().addListener((o, was, now) -> {
            boolean token = now == SigningSettings.KeystoreType.PKCS11;
            // each type keeps its own path, so switching back and forth loses neither.
            if (was == SigningSettings.KeystoreType.PKCS11) settings.setPkcs11Config(keystore.getText().strip());
            else if (was == SigningSettings.KeystoreType.PKCS12) settings.setPkcs12Path(keystore.getText().strip());
            keystore.setText(Objects.toString(token ? settings.getPkcs11Config() : settings.getPkcs12Path(), ""));
            keystore.setPromptText(token ? Messages.get("signDialog.tokenConfig") : Messages.get("signDialog.keystoreFile"));
            password.setPromptText(token ? Messages.get("signDialog.tokenPin") : Messages.get("signDialog.keystorePassword"));
            password.clear();
            certificates.getItems().clear();
            keystoreStatus.setText("");
            signer = null;
            keystoreGeneration++;
            opening.set(false);
        });
        HBox.setHgrow(keystore, Priority.ALWAYS);
        Button browse = new Button(Messages.get("signDialog.browse"));
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            if (keystoreType.getValue() == SigningSettings.KeystoreType.PKCS11) {
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(Messages.get("signDialog.pkcs11Filter"), "*.cfg", "*.conf"),
                        new FileChooser.ExtensionFilter(Messages.get("signDialog.allFilter"), "*.*"));
            } else {
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("signDialog.pkcs12Filter"), "*.p12", "*.pfx"));
            }
            File current = new File(keystore.getText().strip());
            if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
                chooser.setInitialDirectory(current.getParentFile());
            }
            File file = chooser.showOpenDialog(getOwner());
            if (file != null) {
                keystore.setText(file.getAbsolutePath());
                password.requestFocus();
            }
        });
        Button open = new Button(Messages.get("signDialog.open"));
        open.setDefaultButton(false);
        open.setOnAction(e -> openKeystore());
        open.disableProperty().bind(opening);
        password.setOnAction(e -> {
            if (!opening.get()) openKeystore();
        });
        HBox keystoreRow = new HBox(6, keystore, browse);
        HBox passwordRow = new HBox(6, password, open);
        HBox.setHgrow(password, Priority.ALWAYS);
        keystoreStatus.setStyle("-fx-text-fill: #b00;");
        keystoreStatus.managedProperty().bind(keystoreStatus.textProperty().isNotEmpty());

        certificates.setPrefHeight(110);
        certificates.setPlaceholder(new Label(Messages.get("signDialog.noCertificates")));
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
                setText(item.commonName() + "\n  " + Messages.format("signDialog.issuedBy", issuerCn != null ? issuerCn : issuer,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(item.certificate().getNotAfter())));
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
        timestampAuthority.setPromptText(Messages.get("signDialog.timestampPrompt"));
        details.addRow(0, new Label(Messages.get("signDialog.name")), name);
        details.addRow(1, new Label(Messages.get("signDialog.contact")), contact);
        details.addRow(2, new Label(Messages.get("signDialog.location")), location);
        details.addRow(3, new Label(Messages.get("signDialog.reason")), reason);
        details.addRow(4, new Label(Messages.get("signDialog.timestamp")), timestampAuthority);

        VBox tab = new VBox(8, keystoreType, keystoreRow, passwordRow, keystoreStatus, certificates,
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
        for (RadioButton r : List.of(noImage, drawnImage, fileImage)) r.setToggleGroup(imageSource);
        imagePath.setPromptText(Messages.get("signDialog.imagePrompt"));
        HBox.setHgrow(imagePath, Priority.ALWAYS);
        Button browse = new Button(Messages.get("signDialog.browse"));
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("signDialog.imageFilter"), "*.png", "*.jpg", "*.jpeg",
                    "*.gif"));
            File file = chooser.showOpenDialog(getOwner());
            if (file != null) {
                imagePath.setText(file.getAbsolutePath());
                fileImage.setSelected(true);
                refresh();
            }
        });
        imagePath.setOnAction(e -> refresh());
        imagePath.focusedProperty().addListener((o, was, now) -> {
            if (!now) refresh();
        });
        layout.getItems().setAll(SignatureAppearance.Layout.values());
        layout.setButtonCell(new LayoutCell());
        layout.setCellFactory(v -> new LayoutCell());
        fontSize.setEditable(true);
        fontSize.setPrefWidth(80);

        HBox fileRow = new HBox(6, imagePath, browse);
        fileRow.visibleProperty().bind(fileImage.selectedProperty());
        fileRow.managedProperty().bind(fileRow.visibleProperty());
        pad.visibleProperty().bind(drawnImage.selectedProperty());
        pad.managedProperty().bind(pad.visibleProperty());
        layout.disableProperty().bind(noImage.selectedProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, visible);
        grid.addRow(1, showText, new HBox(6, new Label(Messages.get("signDialog.fontSize")), fontSize));
        grid.addRow(2, new Label(Messages.get("signDialog.signature")), new HBox(12, noImage, drawnImage, fileImage));
        grid.addRow(3, new Label(Messages.get("signDialog.layout")), layout);
        VBox tab = new VBox(8, grid, pad, fileRow);
        tab.setPadding(new Insets(10));
        return tab;
    }

    private static final class KeystoreTypeCell extends ListCell<SigningSettings.KeystoreType> {
        @Override
        protected void updateItem(SigningSettings.KeystoreType item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item == SigningSettings.KeystoreType.PKCS11
                    ? Messages.get("signDialog.pkcs11") : Messages.get("signDialog.pkcs12"));
        }
    }

    private static final class LayoutCell extends ListCell<SignatureAppearance.Layout> {
        @Override
        protected void updateItem(SignatureAppearance.Layout item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item == SignatureAppearance.Layout.OVERLAY
                    ? Messages.get("signDialog.overlay") : Messages.get("signDialog.beside"));
        }
    }

    // ---- behaviour ----------------------------------------------------------------------

    void openKeystore() {
        keystoreStatus.setText("");
        certificates.getItems().clear();
        signer = null;
        boolean token = keystoreType.getValue() == SigningSettings.KeystoreType.PKCS11;
        File file = new File(keystore.getText().strip());
        if (!file.isFile()) {
            keystoreStatus.setText(token ? Messages.get("signDialog.chooseToken") : Messages.get("signDialog.chooseKeystore"));
            return;
        }
        SimplePasswordCallbackHandler secret = new SimplePasswordCallbackHandler(password.getText());
        SignerHandler handler = token ? new Pkcs11SignerHandler(null, file.getAbsolutePath(), null, secret)
                : new Pkcs12SignerHandler(null, file, null, secret);
        int mine = ++keystoreGeneration;
        opening.set(true);
        keystoreStatus.setText(Messages.get(token ? "signDialog.openingToken" : "signDialog.openingKeystore"));
        Thread worker = new Thread(() -> {
            List<DocumentSigning.KeyEntry> entries = null;
            Throwable failure = null;
            try {
                entries = DocumentSigning.keyEntries(handler);
            } catch (Exception | Error e) {
                // a missing PKCS#11 library is a ProviderException (or an Error from the native side).
                failure = e;
            }
            List<DocumentSigning.KeyEntry> found = entries;
            Throwable failed = failure;
            Platform.runLater(() -> {
                if (mine != keystoreGeneration) return;
                opening.set(false);
                keystoreOpened(token, file, handler, found, failed);
            });
        }, "icepdf-fx-keystore");
        worker.setDaemon(true);
        worker.start();
    }

    private void keystoreOpened(boolean token, File file, SignerHandler handler, List<DocumentSigning.KeyEntry> entries,
                                Throwable failure) {
        if (failure != null) {
            logger.log(java.util.logging.Level.WARNING, "Could not open " + file, failure);
            keystoreStatus.setText(Messages.get(token ? "signDialog.tokenFailed" : "signDialog.keystoreFailed"));
            keystoreStatus.setTooltip(new Tooltip(Messages.reason(failure)));
            return;
        }
        keystoreStatus.setTooltip(null);
        if (entries.isEmpty()) {
            keystoreStatus.setText(token ? Messages.get("signDialog.tokenEmpty")
                    : Messages.get("signDialog.keystoreEmpty"));
            return;
        }
        keystoreStatus.setText("");
        signer = handler;
        if (token) settings.setPkcs11Config(file.getAbsolutePath());
        else settings.setPkcs12Path(file.getAbsolutePath());
        certificates.getItems().setAll(entries);
        DocumentSigning.KeyEntry last = entries.stream()
                .filter(entry -> entry.alias().equals(settings.getCertificateAlias())).findFirst().orElse(null);
        if (last != null) certificates.getSelectionModel().select(last);
        else certificates.getSelectionModel().selectFirst();
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
        appearance.setImageVisible(!noImage.isSelected());
        appearance.setLayout(layout.getValue());
        if (fontSize.getValue() != null) appearance.setFontSize(fontSize.getValue());
        if (drawnImage.isSelected()) {
            if (drawnStale) {
                drawn = settings.getInk().toImage(DRAWN_SCALE);
                drawnStale = false;
            }
            appearance.setImage(drawn);
        } else if (fileImage.isSelected()) {
            String path = imagePath.getText().strip();
            // loaded once per path: each new image is written as a new object.
            if (!path.equals(loadedPath)) {
                loadedPath = path;
                loaded = path.isEmpty() ? null : SignatureUtilities.loadSignatureImage(path);
            }
            appearance.setImage(loaded);
        } else {
            appearance.setImage(null);
        }
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

    // ---- for tests ----------------------------------------------------------------------

    TextField keystoreField() {
        return keystore;
    }

    PasswordField passwordField() {
        return password;
    }

    Label keystoreStatus() {
        return keystoreStatus;
    }

    ListView<DocumentSigning.KeyEntry> certificateList() {
        return certificates;
    }
}
