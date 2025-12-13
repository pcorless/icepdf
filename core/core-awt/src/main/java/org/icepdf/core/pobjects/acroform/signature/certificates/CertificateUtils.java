/*
 * Copyright 2025 Patrick Corless
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
package org.icepdf.core.pobjects.acroform.signature.certificates;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.logging.Logger;

public class CertificateUtils {

    private static final Logger logger =
            Logger.getLogger(CertificateUtils.class.toString());

    /**
     * Checks whether given X.509 certificate is self-signed.
     *
     * @param cert cert to test for self signing.
     * @return true if self-signed, otherwise false.
     * @throws CertificateException     certificate exception.
     * @throws NoSuchAlgorithmException not such algorithm exception.
     * @throws NoSuchProviderException  no such provider exception.
     */
    public static boolean isSelfSigned(X509Certificate cert)
            throws CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException {
        try {
            // Try to verify certificate signature with its own public key
            PublicKey key = cert.getPublicKey();
            cert.verify(key);
            return true;
        } catch (SignatureException | InvalidKeyException sigEx) {
            // Invalid signature, not self-signed
            return false;
        }
    }

    /**
     * Extract the OCSP URL from an X.509 certificate if available.
     *
     * @param cert X.509 certificate
     * @return the URL of the OCSP validation service
     * @throws IOException
     */
    public static String extractOCSPURL(X509Certificate cert) throws IOException {
        byte[] authorityExtensionValue = cert.getExtensionValue(Extension.authorityInfoAccess.getId());
        if (authorityExtensionValue != null) {
            // copied from CertInformationHelper.getAuthorityInfoExtensionValue()
            // DRY refactor should be done some day
            ASN1Sequence asn1Seq = (ASN1Sequence) JcaX509ExtensionUtils.parseExtensionValue(authorityExtensionValue);
            Enumeration<?> objects = asn1Seq.getObjects();
            while (objects.hasMoreElements()) {
                // AccessDescription
                ASN1Sequence obj = (ASN1Sequence) objects.nextElement();
                ASN1Encodable oid = obj.getObjectAt(0);
                // accessLocation
                ASN1TaggedObject location = (ASN1TaggedObject) obj.getObjectAt(1);
                if (X509ObjectIdentifiers.id_ad_ocsp.equals(oid)
                        && location.getTagNo() == GeneralName.uniformResourceIdentifier) {
                    ASN1OctetString url = (ASN1OctetString) location.getBaseObject();
                    String ocspURL = new String(url.getOctets());
                    logger.finer("OCSP URL:" + ocspURL);
                    return ocspURL;
                }
            }
        }
        return null;
    }

}
