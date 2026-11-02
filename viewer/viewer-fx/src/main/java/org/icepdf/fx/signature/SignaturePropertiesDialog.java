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

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.icepdf.fx.view.PdfView;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * The details of one signature: the verdict, who signed and when, each check behind the verdict,
 * and the certificate chain with each certificate's details.
 */
public class SignaturePropertiesDialog extends Dialog<Void> {

    private static final String[] KEY_USAGES = {"digital signature", "non-repudiation", "key encipherment",
            "data encipherment", "key agreement", "certificate signing", "CRL signing", "encipher only",
            "decipher only"};

    private final PdfView view;
    private SignatureStatus status;
    private final ListChangeListener<SignatureStatus> follow = change -> follow();

    /**
     * Shows the details of a signature, modal to the window of {@code owner} (may be null); does
     * nothing for a status that isn't signed.  When the owner is the {@link PdfView} the status came
     * from, the dialog follows it: a signature still being checked shows progress until its result
     * arrives, and one whose revocation went unchecked can be checked online.
     */
    public static void show(javafx.scene.Node owner, SignatureStatus status) {
        if (status == null || !status.isSigned()) return;
        Window window = owner != null && owner.getScene() != null ? owner.getScene().getWindow() : null;
        new SignaturePropertiesDialog(window, owner instanceof PdfView view ? view : null, status).showAndWait();
    }

    public SignaturePropertiesDialog(Window owner, SignatureStatus status) {
        this(owner, null, status);
    }

    /**
     * @param owner  window to be modal to, or null
     * @param view   the view the status came from, to follow its updates; or null for a fixed status
     * @param status the signature
     */
    public SignaturePropertiesDialog(Window owner, PdfView view, SignatureStatus status) {
        this.view = view;
        if (owner != null) initOwner(owner);
        setTitle(Messages.get("signatureProperties.title"));
        setResizable(true);
        getDialogPane().getStyleClass().add("pdf-signature-dialog");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefWidth(660);
        getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        setStatus(status);
        if (view != null) {
            view.getSignatures().addListener(follow);
            setOnHidden(e -> view.getSignatures().removeListener(follow));
        }
    }

    // the view's entry for this signature changed: a check finished or started.
    private void follow() {
        for (SignatureStatus listed : view.getSignatures()) {
            if (listed.widget() == status.widget()) {
                if (listed != status) setStatus(listed);
                return;
            }
        }
    }

    private void setStatus(SignatureStatus status) {
        this.status = status;
        getDialogPane().setContent(status.isPending() ? pending(status) : content(status));
        if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null) {
            getDialogPane().getScene().getWindow().sizeToScene();
        }
    }

    /** While the signature is checked: a busy indicator and who signed it. */
    private static Node pending(SignatureStatus status) {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(40, 40);
        Label summary = new Label(status.summary());
        summary.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        VBox text = new VBox(4, summary);
        if (status.signerName() != null) {
            text.getChildren().add(new Label(Messages.get("signatureProperties.signedBy") + " " + status.signerName()));
        }
        HBox box = new HBox(12, progress, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12));
        box.setMinWidth(600);
        box.setPrefWidth(620);
        return box;
    }

    private Node content(SignatureStatus status) {
        Label summary = new Label(status.summary());
        summary.setWrapText(true);
        summary.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        HBox header = new HBox(12, SignatureIcons.icon(status.verdict(), 40), summary);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14, header);
        content.setPadding(new Insets(12));
        // wrapped labels report a tiny minimum width; without this the dialog opens a column wide.
        content.setMinWidth(600);
        content.setPrefWidth(620);
        if (status.isSigned()) {
            VBox checks = new VBox(8, checks(status));
            // revocation went unchecked (offline, or a server didn't answer): offer a one-off online check.
            if (view != null && status.isRevocationUnchecked()) {
                Button checkOnline = new Button(Messages.get("signatureProperties.checkOnline"));
                checkOnline.setOnAction(e -> view.verifySignature(status,
                        view.getSignatureValidationOptions().withOnline(true)));
                checks.getChildren().add(checkOnline);
            }
            content.getChildren().addAll(details(status), section(Messages.get("signatureProperties.checks"), checks));
            if (!status.chain().isEmpty()) content.getChildren().add(section(Messages.get("signatureProperties.chain"), chain(status)));
        }
        return content;
    }

    private static Node details(SignatureStatus status) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(90);
        grid.getColumnConstraints().addAll(labels, new ColumnConstraints());
        int row = 0;
        String signer = status.signerName() != null ? status.signerName() : "unknown";
        if (status.signerEmail() != null) signer += " <" + status.signerEmail() + ">";
        if (status.signerOrganization() != null && !status.signerOrganization().equals(status.signerName())) {
            signer += ", " + status.signerOrganization();
        }
        row = add(grid, row, Messages.get("signatureProperties.signedBy"), signer);
        if (status.signingTime() != null) {
            row = add(grid, row, Messages.get("signatureProperties.signed"), DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
                    .format(status.signingTime()) + "  " + Messages.get(status.timestamped() ? "signatureProperties.timestampAuthority"
                    : "signatureProperties.signersClock"));
        }
        row = add(grid, row, Messages.get("signatureProperties.reason"), status.reason());
        row = add(grid, row, Messages.get("signatureProperties.location"), status.location());
        row = add(grid, row, Messages.get("signatureProperties.contact"), status.contact());
        row = add(grid, row, Messages.get("signatureProperties.type"), status.certification() ? Messages.get("signatureProperties.certificationSignature") : Messages.get("signatureProperties.approvalSignature"));
        add(grid, row, Messages.get("signatureProperties.field"), status.fieldName());
        return grid;
    }

    private static int add(GridPane grid, int row, String label, String value) {
        if (value == null || value.isBlank()) return row;
        Label v = new Label(value);
        v.setWrapText(true);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: derive(-fx-text-base-color, 35%);");
        grid.addRow(row, l, v);
        return row + 1;
    }

    private static Node section(String title, Node body) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-weight: bold;");
        return new VBox(6, heading, body);
    }

    /** The checks behind the verdict, each with its own mark. */
    static List<Check> checkList(SignatureStatus s) {
        List<Check> out = new ArrayList<>();
        if (s.verdict() == SignatureStatus.Verdict.ERROR) {
            out.add(new Check(SignatureStatus.Verdict.ERROR, Messages.format("signatureProperties.check.error", s.problem())));
            return out;
        }
        if (s.signedDataModified()) {
            out.add(new Check(SignatureStatus.Verdict.INVALID,
                    Messages.get("signatureProperties.check.altered")));
        } else {
            out.add(new Check(SignatureStatus.Verdict.VALID, Messages.get("signatureProperties.check.unaltered")));
        }
        if (s.modifiedAfterSigning() && !s.signedDataModified()) {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN, Messages.get("signatureProperties.check.changed")));
        }
        if (s.revoked()) {
            out.add(new Check(SignatureStatus.Verdict.INVALID, Messages.get("signatureProperties.check.revoked")));
        } else if (s.identityTrusted()) {
            out.add(new Check(SignatureStatus.Verdict.VALID, Messages.get("signatureProperties.check.trusted")));
            if (!s.revocationChecked()) {
                out.add(new Check(SignatureStatus.Verdict.UNKNOWN,
                        Messages.get("signatureProperties.check.revocationUnchecked")));
            }
        } else if (s.selfSigned()) {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN,
                    Messages.get("signatureProperties.check.selfSigned")));
        } else {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN,
                    Messages.get("signatureProperties.check.untrusted")));
        }
        out.add(s.certificateDateValid()
                ? new Check(SignatureStatus.Verdict.VALID, Messages.get("signatureProperties.check.dateValid"))
                : new Check(SignatureStatus.Verdict.UNKNOWN,
                Messages.get("signatureProperties.check.dateInvalid")));
        out.add(new Check(s.timestamped() ? SignatureStatus.Verdict.VALID : SignatureStatus.Verdict.ERROR,
                s.timestamped() ? Messages.get("signatureProperties.check.timestamped")
                        : Messages.get("signatureProperties.check.clock")));
        return out;
    }

    record Check(SignatureStatus.Verdict mark, String text) {
    }

    private static Node checks(SignatureStatus status) {
        VBox box = new VBox(6);
        for (Check check : checkList(status)) {
            Label text = new Label(check.text());
            text.setWrapText(true);
            HBox line = new HBox(8, SignatureIcons.icon(check.mark(), 14), text);
            line.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(line);
        }
        return box;
    }

    private static Node chain(SignatureStatus status) {
        ListView<X509Certificate> list = new ListView<>();
        list.getItems().setAll(status.chain());
        list.setPrefSize(200, 170);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(X509Certificate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String cn = SignatureVerifier.part(item, "CN");
                setText((getIndex() > 0 ? "  ".repeat(getIndex()) + "↳ " : "")
                        + (cn != null ? cn : item.getSubjectX500Principal().getName()));
            }
        });
        TextArea details = new TextArea();
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefSize(380, 170);
        details.setStyle("-fx-font-family: monospace; -fx-font-size: 0.9em;");
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) ->
                details.setText(now != null ? describe(now) : ""));
        list.getSelectionModel().selectFirst();
        HBox box = new HBox(8, list, details);
        HBox.setHgrow(details, Priority.ALWAYS);
        return box;
    }

    /** A certificate's details as text. */
    static String describe(X509Certificate c) {
        StringBuilder out = new StringBuilder();
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        out.append("Subject:    ").append(readable(c.getSubjectX500Principal())).append('\n');
        out.append("Issuer:     ").append(readable(c.getIssuerX500Principal())).append('\n');
        out.append("Serial:     ").append(c.getSerialNumber().toString(16).toUpperCase()).append('\n');
        out.append("Valid from: ").append(dates.format(c.getNotBefore())).append('\n');
        out.append("Valid to:   ").append(dates.format(c.getNotAfter()));
        if (c.getNotAfter().before(new Date())) out.append("  (expired)");
        out.append('\n');
        out.append("Public key: ").append(c.getPublicKey().getAlgorithm()).append(keySize(c)).append('\n');
        out.append("Signature:  ").append(c.getSigAlgName()).append('\n');
        boolean[] usage = c.getKeyUsage();
        if (usage != null) {
            List<String> uses = new ArrayList<>();
            for (int i = 0; i < usage.length && i < KEY_USAGES.length; i++) if (usage[i]) uses.add(KEY_USAGES[i]);
            out.append("Key usage:  ").append(String.join(", ", uses)).append('\n');
        }
        try {
            byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            out.append("SHA-256:    ").append(HexFormat.ofDelimiter(":").withUpperCase().formatHex(sha256));
        } catch (Exception e) {
            // no fingerprint
        }
        return out.toString();
    }

    private static final java.util.Map<String, String> NAME_TYPES = java.util.Map.of(
            "2.5.4.13", "description", "2.5.4.5", "serialNumber", "2.5.4.42", "givenName", "2.5.4.4", "surname",
            "1.2.840.113549.1.9.1", "E", "2.5.4.97", "organizationIdentifier", "2.5.4.12", "title",
            "2.5.4.46", "dnQualifier");

    /** A distinguished name with attribute names in place of OIDs and DER-encoded values decoded. */
    static String readable(javax.security.auth.x500.X500Principal principal) {
        try {
            javax.naming.ldap.LdapName name = new javax.naming.ldap.LdapName(principal.getName());
            List<String> parts = new ArrayList<>();
            for (javax.naming.ldap.Rdn rdn : name.getRdns()) {
                String type = NAME_TYPES.getOrDefault(rdn.getType(), rdn.getType());
                Object value = rdn.getValue();
                if (value instanceof byte[] der) {
                    // RFC 2253 writes values of unknown types as #hex DER.
                    org.bouncycastle.asn1.ASN1Primitive primitive = org.bouncycastle.asn1.ASN1Primitive.fromByteArray(der);
                    value = primitive instanceof org.bouncycastle.asn1.ASN1String text ? text.getString() : primitive;
                }
                parts.add(0, type + "=" + value);
            }
            return String.join(", ", parts);
        } catch (Exception e) {
            return principal.getName();
        }
    }

    private static String keySize(X509Certificate c) {
        if (c.getPublicKey() instanceof RSAKey rsa) return " " + rsa.getModulus().bitLength() + " bits";
        if (c.getPublicKey() instanceof ECKey ec) return " " + ec.getParams().getCurve().getField().getFieldSize() + " bits";
        return "";
    }
}
