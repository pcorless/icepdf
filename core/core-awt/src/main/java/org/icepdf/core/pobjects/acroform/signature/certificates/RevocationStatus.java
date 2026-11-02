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
package org.icepdf.core.pobjects.acroform.signature.certificates;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one revocation pass over a certificate chain was able to establish.  A certificate whose
 * status could not be fetched - offline, a responder that timed out, a list that would not download,
 * or no OCSP or CRL url to ask - is recorded here rather than failing the chain: not knowing is not
 * the same as revoked, nor as untrusted.  A revoked certificate still throws
 * {@link org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException}.
 * <p>
 * One per validation; not thread safe.
 */
public final class RevocationStatus {

    private final List<String> unchecked = new ArrayList<>();

    /**
     * Notes a certificate whose revocation status could not be determined.
     *
     * @param certificate the certificate
     * @param reason      why, for the log and the caller
     */
    public void markUnchecked(X509Certificate certificate, String reason) {
        unchecked.add(certificate.getSubjectX500Principal().getName() + ": " + reason);
    }

    /**
     * @return true if every certificate checked had its revocation status determined
     */
    public boolean isComplete() {
        return unchecked.isEmpty();
    }

    /**
     * @return a line per certificate left unchecked, with the reason
     */
    public List<String> getUnchecked() {
        return Collections.unmodifiableList(unchecked);
    }
}
