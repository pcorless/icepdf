package org.icepdf.ri.common.views.annotations.signing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;

import javax.security.auth.x500.X500Principal;
import javax.swing.table.AbstractTableModel;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

public class CertificateTableModel extends AbstractTableModel {

    private String[] columnNames;
    private String[][] data = new String[][]{};
    private static SimpleDateFormat validityDateFormat = new SimpleDateFormat("dd/MM/yyyy");

    public CertificateTableModel(SignerHandler signerHandler, Enumeration<String> aliases,
                                 ResourceBundle messageBundle) throws KeyStoreException {
        columnNames = new String[]{
                messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.table.name.label"),
                messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.table.author.label"),
                messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.table.validity.label"),
                messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.table.description.label")};

        // build data from aliases in keystore.
        List<String[]> rows = new ArrayList<>();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate cert = signerHandler.getCertificate(alias);
            rows.add(createCertSummaryData(cert));
        }
        data = new String[rows.size()][columnNames.length];
        rows.toArray(data);
    }

    private static String[] createCertSummaryData(X509Certificate certificate) {
        X500Principal principal = certificate.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        // Set up dictionary using certificate values.
        // https://javadoc.io/static/org.bouncycastle/bcprov-jdk15on/1.70/org/bouncycastle/asn1/x500/style/BCStyle.html
        if (x500name.getRDNs() != null) {
            String commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
            String email = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            String validity = validityDateFormat.format(certificate.getNotAfter());
            String description = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.DESCRIPTION);
            return new String[]{commonName, email, validity, description};
        } else {
            throw new IllegalStateException("Certificate has no DRNs data");
        }
    }

    @Override
    public int getRowCount() {
        return data.length;
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Object getValueAt(int row, int col) {
        return data[row][col];
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return false;
    }
}
