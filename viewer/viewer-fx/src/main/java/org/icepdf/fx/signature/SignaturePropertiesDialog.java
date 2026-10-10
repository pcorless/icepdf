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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

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

    public SignaturePropertiesDialog(Window owner, SignatureStatus status) {
        if (owner != null) initOwner(owner);
        setTitle("Signature properties");
        setResizable(true);
        getDialogPane().getStyleClass().add("pdf-signature-dialog");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

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
            content.getChildren().addAll(details(status), section("Checks", checks(status)));
            if (!status.chain().isEmpty()) content.getChildren().add(section("Certificate chain", chain(status)));
        }
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(660);
        getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
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
        row = add(grid, row, "Signed by", signer);
        if (status.signingTime() != null) {
            row = add(grid, row, "Signed", DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
                    .format(status.signingTime()) + (status.timestamped() ? "  (timestamp authority)"
                    : "  (signer's clock)"));
        }
        row = add(grid, row, "Reason", status.reason());
        row = add(grid, row, "Location", status.location());
        row = add(grid, row, "Contact", status.contact());
        row = add(grid, row, "Type", status.certification() ? "Certification signature" : "Approval signature");
        add(grid, row, "Field", status.fieldName());
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
            out.add(new Check(SignatureStatus.Verdict.ERROR, "The signature couldn't be checked: " + s.problem()));
            return out;
        }
        if (s.signedDataModified()) {
            out.add(new Check(SignatureStatus.Verdict.INVALID,
                    "The signed content has been altered or corrupted since it was signed."));
        } else {
            out.add(new Check(SignatureStatus.Verdict.VALID, "The document hasn't been altered since it was signed."));
        }
        if (s.modifiedAfterSigning() && !s.signedDataModified()) {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN, "The document has changed since it was signed "
                    + "(later revisions: form fill-in, comments or more signatures)."));
        }
        if (s.revoked()) {
            out.add(new Check(SignatureStatus.Verdict.INVALID, "The signer's certificate has been revoked."));
        } else if (s.identityTrusted()) {
            out.add(new Check(SignatureStatus.Verdict.VALID, "The signer's identity is valid."));
        } else if (s.selfSigned()) {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN,
                    "The signer's certificate is self-signed, so its identity can't be verified."));
        } else {
            out.add(new Check(SignatureStatus.Verdict.UNKNOWN,
                    "The signer's identity is unknown: the certificate doesn't chain to a trusted root."));
        }
        out.add(s.certificateDateValid()
                ? new Check(SignatureStatus.Verdict.VALID, "The certificate was valid at the signing time.")
                : new Check(SignatureStatus.Verdict.UNKNOWN,
                "The certificate wasn't valid at the signing time (expired, or not yet valid)."));
        out.add(new Check(s.timestamped() ? SignatureStatus.Verdict.VALID : SignatureStatus.Verdict.ERROR,
                s.timestamped() ? "The signing time comes from an embedded timestamp."
                        : "The signing time comes from the signer's computer clock."));
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
