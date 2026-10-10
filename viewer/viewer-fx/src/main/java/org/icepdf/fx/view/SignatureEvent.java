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
package org.icepdf.fx.view;

import javafx.event.EventTarget;
import javafx.event.EventType;
import org.icepdf.fx.signature.SignatureStatus;

/**
 * A signature field or its badge was clicked, or the signature tool drew a new field
 * ({@link PdfView#setOnSignatureClicked}).  With no {@code onSignatureClicked} handler a signed field
 * opens the signature properties dialog and unsigned fields aren't offered.
 */
public class SignatureEvent extends PdfViewEvent {

    public static final EventType<SignatureEvent> SIGNATURE_CLICKED =
            new EventType<>(PdfViewEvent.ANY, "SIGNATURE_CLICKED");

    private final transient SignatureStatus status;

    public SignatureEvent(Object source, EventTarget target, SignatureStatus status) {
        super(source, target, SIGNATURE_CLICKED);
        this.status = status;
    }

    /** The field's signature status (unsigned fields too). */
    public SignatureStatus getStatus() {
        return status;
    }
}
