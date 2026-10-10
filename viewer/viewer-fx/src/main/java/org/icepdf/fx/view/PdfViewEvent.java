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

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
 * Events a {@link PdfView} fires.  They bubble from the view through its parents, so an application
 * can handle them on the view ({@code setOnXxx} or {@code addEventHandler}) or on any container
 * above it.  Fired on the FX thread.
 * <ul>
 *     <li>{@link AnnotationActionEvent#ANNOTATION_ACTION}: an action the view leaves to the application;</li>
 *     <li>{@link FormFieldChangeEvent#FIELD_CHANGED}: a form field's value changed;</li>
 *     <li>{@link SignatureEvent#SIGNATURE_CLICKED}: a signature field or badge was clicked;</li>
 *     <li>{@link #ANNOTATIONS_CHANGED}: an annotation or field was added, removed or edited through
 *     the view (including undo and redo).</li>
 * </ul>
 */
public class PdfViewEvent extends Event {

    /** Every PdfView event. */
    public static final EventType<PdfViewEvent> ANY = new EventType<>(Event.ANY, "PDF_VIEW");

    /**
     * Annotations or form fields were added, removed or edited through the view, undo and redo
     * included.  Edits made directly on the core objects aren't seen.
     */
    public static final EventType<PdfViewEvent> ANNOTATIONS_CHANGED = new EventType<>(ANY, "ANNOTATIONS_CHANGED");

    public PdfViewEvent(Object source, EventTarget target, EventType<? extends PdfViewEvent> eventType) {
        super(source, target, eventType);
    }

    @Override
    public PdfViewEvent copyFor(Object newSource, EventTarget newTarget) {
        return (PdfViewEvent) super.copyFor(newSource, newTarget);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EventType<? extends PdfViewEvent> getEventType() {
        return (EventType<? extends PdfViewEvent>) super.getEventType();
    }
}
