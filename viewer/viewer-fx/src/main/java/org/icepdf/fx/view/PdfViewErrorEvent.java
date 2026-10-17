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

/**
 * Something the user asked the view to do failed - an undo, marking search hits for redaction - and
 * the user should be told.  {@link #getMessage()} is ready to show (translated); the cause is for
 * the log.  The view has already put itself back into a usable state.
 */
public class PdfViewErrorEvent extends PdfViewEvent {

    /** An operation in the view failed. */
    public static final EventType<PdfViewErrorEvent> ERROR = new EventType<>(PdfViewEvent.ANY, "PDF_VIEW_ERROR");

    private final String message;
    private final transient Throwable cause;

    public PdfViewErrorEvent(Object source, EventTarget target, String message, Throwable cause) {
        super(source, target, ERROR);
        this.message = message;
        this.cause = cause;
    }

    /** What went wrong, for the user. */
    public String getMessage() {
        return message;
    }

    /** The exception behind it, or null. */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public PdfViewErrorEvent copyFor(Object newSource, EventTarget newTarget) {
        return (PdfViewErrorEvent) super.copyFor(newSource, newTarget);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EventType<? extends PdfViewErrorEvent> getEventType() {
        return (EventType<? extends PdfViewErrorEvent>) super.getEventType();
    }
}
