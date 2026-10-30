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
package org.icepdf.core.pobjects.acroform.signature;

import org.icepdf.core.util.Defs;

import java.util.Objects;

/**
 * How a signature check may use the network.  Validation can fetch a certificate's revocation
 * status (OCSP, CRL) and missing issuer certificates (the AIA caIssuers url); offline it does none
 * of that, and the result says revocation was not checked
 * ({@link SignatureValidator#isRevocationChecked()}).  Immutable.
 * <p>
 * The defaults come from system properties, read once:
 * <ul>
 * <li>{@value #ONLINE_PROPERTY} - fetch over the network, default true</li>
 * <li>{@value #CONNECT_TIMEOUT_PROPERTY} - milliseconds to wait for a connection, default
 * {@value #DEFAULT_CONNECT_TIMEOUT}</li>
 * <li>{@value #READ_TIMEOUT_PROPERTY} - milliseconds to wait for data on an open connection,
 * default {@value #DEFAULT_READ_TIMEOUT}</li>
 * </ul>
 * A timeout of 0 waits forever, as the JDK does when none is set.
 */
public final class SignatureValidationOptions {

    public static final String ONLINE_PROPERTY = "org.icepdf.core.signatures.online";
    public static final String CONNECT_TIMEOUT_PROPERTY = "org.icepdf.core.signatures.network.connectTimeout";
    public static final String READ_TIMEOUT_PROPERTY = "org.icepdf.core.signatures.network.readTimeout";

    public static final int DEFAULT_CONNECT_TIMEOUT = 10_000;
    public static final int DEFAULT_READ_TIMEOUT = 15_000;

    private static final SignatureValidationOptions DEFAULTS = new SignatureValidationOptions(
            Defs.booleanProperty(ONLINE_PROPERTY, true),
            Defs.intProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT),
            Defs.intProperty(READ_TIMEOUT_PROPERTY, DEFAULT_READ_TIMEOUT));

    private final boolean online;
    private final int connectTimeout;
    private final int readTimeout;

    private SignatureValidationOptions(boolean online, int connectTimeout, int readTimeout) {
        this.online = online;
        this.connectTimeout = Math.max(0, connectTimeout);
        this.readTimeout = Math.max(0, readTimeout);
    }

    /**
     * The options set by the system properties.
     *
     * @return the defaults
     */
    public static SignatureValidationOptions defaults() {
        return DEFAULTS;
    }

    /**
     * @param online true to fetch revocation data and issuer certificates over the network
     * @return these options with that setting
     */
    public SignatureValidationOptions withOnline(boolean online) {
        return online == this.online ? this : new SignatureValidationOptions(online, connectTimeout, readTimeout);
    }

    /**
     * @param connectTimeout milliseconds to wait for a connection, 0 for no limit
     * @param readTimeout    milliseconds to wait for data, 0 for no limit
     * @return these options with those timeouts
     */
    public SignatureValidationOptions withTimeouts(int connectTimeout, int readTimeout) {
        return new SignatureValidationOptions(online, connectTimeout, readTimeout);
    }

    public boolean isOnline() {
        return online;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignatureValidationOptions)) return false;
        SignatureValidationOptions that = (SignatureValidationOptions) o;
        return online == that.online && connectTimeout == that.connectTimeout && readTimeout == that.readTimeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(online, connectTimeout, readTimeout);
    }

    @Override
    public String toString() {
        return "SignatureValidationOptions{online=" + online + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + '}';
    }
}
