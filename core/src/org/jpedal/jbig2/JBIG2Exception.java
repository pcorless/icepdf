/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
package org.jpedal.jbig2;

public class JBIG2Exception extends Exception {
    public JBIG2Exception(Exception ex) {
        super(ex);
    }

    /**
     * Constructs a <CODE>JBIGException</CODE> whithout a message.
     */

    public JBIG2Exception() {
        super();
    }

    /**
     * Constructs a <code>JBIGException</code> with a message.
     *
     * @param message a message describing the exception
     */

    public JBIG2Exception(String message) {
        super(message);
    }
}
