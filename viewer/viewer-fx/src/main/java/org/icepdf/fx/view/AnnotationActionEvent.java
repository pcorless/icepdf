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
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * An action the view doesn't perform itself, for the application ({@link PdfView#setOnAnnotationAction}):
 * a URI to open, a file to launch, a remote GoTo, SubmitForm, JavaScript...  In-document navigation
 * (GoTo, the page named actions) is handled by the view and never arrives here.
 */
public class AnnotationActionEvent extends PdfViewEvent {

    public static final EventType<AnnotationActionEvent> ANNOTATION_ACTION =
            new EventType<>(PdfViewEvent.ANY, "ANNOTATION_ACTION");

    private final transient Annotation annotation;
    private final transient Action action;

    /**
     * @param annotation the annotation whose action it is; null for an action from elsewhere (a
     *                   bookmark, {@link PdfView#performAction})
     */
    public AnnotationActionEvent(Object source, EventTarget target, Annotation annotation, Action action) {
        super(source, target, ANNOTATION_ACTION);
        this.annotation = annotation;
        this.action = action;
    }

    /** The annotation clicked, or null when the action didn't come from one (a bookmark). */
    public Annotation getAnnotation() {
        return annotation;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "AnnotationActionEvent[annotation=" + annotation + ", action=" + action + "]";
    }
}
