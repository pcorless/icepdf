/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.acroform.signature.utils;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;

import javax.imageio.ImageIO;
import javax.security.auth.x500.X500Principal;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility of commonly used signature related algorithms.
 */
public class SignatureUtilities {

    private static final Logger logger =
            Logger.getLogger(SignatureUtilities.class.toString());

    /**
     * Parse out a known data element from an X500Name.
     *
     * @param rdName     name to parse value from.
     * @param commonCode BCStyle name .
     * @return BCStyle name value,  null if the BCStyle name was not found.
     */
    public static String parseRelativeDistinguishedName(X500Name rdName, ASN1ObjectIdentifier commonCode) {
        RDN[] rdns = rdName.getRDNs(commonCode);
        if (rdns != null && rdns.length > 0 && rdns[0].getFirst() != null) {
            return rdns[0].getFirst().getValue().toString();
        }
        return null;
    }

    /**
     * Populate signature dictionary with values from the certificate
     *
     * @param signatureDictionary dictionary to populate
     * @param certificate         cert to extract values from
     */
    public static void updateSignatureDictionary(SignatureDictionary signatureDictionary, X509Certificate certificate) {
        X500Principal principal = certificate.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        // Set up dictionary using certificate values.
        // https://javadoc.io/static/org.bouncycastle/bcprov-jdk15on/1.70/org/bouncycastle/asn1/x500/style/BCStyle.html
        if (x500name.getRDNs() != null) {
            String commonName = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN);
            if (commonName != null) {
                signatureDictionary.setName(commonName);
            }
            String email = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.EmailAddress);
            if (email != null) {
                signatureDictionary.setContactInfo(email);
            }
            ArrayList<String> location = new ArrayList<>(2);
            String state = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.ST);
            if (state != null) {
                location.add(state);
            }
            String country = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.C);
            if (country != null) {
                location.add(country);
            }
            if (!location.isEmpty()) {
                signatureDictionary.setLocation(String.join(", ", location));
            }
        } else {
            throw new IllegalStateException("Certificate has no DRNs data");
        }
//        signatureDictionary.setReason("Approval"); // Approval or certification but technically can be anything
//        signatureDictionary.setDate(PDate.formatDateTime(new Date()));
    }

    public static BufferedImage loadSignatureImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }
        try {
            return ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading signature image", e);
        }
        return null;
    }
}
