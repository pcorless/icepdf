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

import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;

/**
 * Opens the connections signature checking and signing make, with the timeouts of the options.
 * Without them the JDK waits forever on a server that accepts a connection and never answers, and
 * the thread asking - often the one the user is waiting on - waits with it.
 */
final class NetworkSupport {

    private NetworkSupport() {
    }

    /**
     * Opens a connection with the connect and read timeouts set; works for http, https and ftp.
     */
    static URLConnection open(URL url, SignatureValidationOptions options) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(options.getConnectTimeout());
        connection.setReadTimeout(options.getReadTimeout());
        return connection;
    }

    /**
     * The JNDI environment entries that bound an LDAP lookup by the same timeouts.
     */
    static void ldapTimeouts(Hashtable<String, String> env, SignatureValidationOptions options) {
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(options.getConnectTimeout()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(options.getReadTimeout()));
    }
}
