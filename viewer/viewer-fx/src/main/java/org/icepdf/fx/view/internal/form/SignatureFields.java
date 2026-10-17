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
package org.icepdf.fx.view.internal.form;

import javafx.application.Platform;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.signature.DocumentSigning;
import org.icepdf.fx.signature.SignaturePropertiesDialog;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.signature.SignatureVerifier;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.SignatureEvent;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.internal.ViewHost;
import org.icepdf.fx.view.internal.annot.AnnotationHit;

import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signature fields in the view: the checked status of each field (for badges and clicks), the
 * signature tool's new empty field, and where a click on one goes.
 */
public final class SignatureFields {

    private final ViewHost host;
    // checked signatures by their field's object reference: the form's widget objects need not be
    // the page's own annotation objects.
    private final Map<Reference, SignatureStatus> byReference = new HashMap<>();

    public SignatureFields(ViewHost host) {
        this.host = host;
    }

    /** The control's checked signatures changed. */
    public void statusesChanged(Collection<SignatureStatus> statuses) {
        byReference.clear();
        for (SignatureStatus status : statuses) {
            if (status.widget().getPObjectReference() != null) {
                byReference.put(status.widget().getPObjectReference(), status);
            }
        }
    }

    public SignatureStatus statusOf(Annotation annotation) {
        if (!(annotation instanceof SignatureWidgetAnnotation) || annotation.getPObjectReference() == null) return null;
        return byReference.get(annotation.getPObjectReference());
    }

    /** The signed fields of a rendered page and their status, for badges. */
    public Map<Annotation, SignatureStatus> badges(Page page) {
        if (byReference.isEmpty() || page == null || !page.isInitiated() || page.getAnnotations() == null) {
            return Map.of();
        }
        Map<Annotation, SignatureStatus> out = new HashMap<>();
        for (Annotation a : page.getAnnotations()) {
            SignatureStatus status = statusOf(a);
            // empty fields get a "sign here" badge when the application handles signing.
            boolean shown = status != null && (status.isSigned() || host.view().getOnSignatureClicked() != null);
            if (shown && !a.isDeleted() && a.allowScreenNormalMode()) out.put(a, status);
        }
        return out;
    }

    /**
     * The signature field under a viewport point with a checked status (signed, or an empty field
     * when the application handles clicks), or null.
     */
    public AnnotationHit fieldAtViewport(double vx, double vy) {
        if (byReference.isEmpty()) return null;
        PagePoint point = host.pageAtViewport(vx, vy);
        if (point == null) return null;
        Page page = host.document().getPageTree().getPage(point.pageIndex());
        if (page == null || !page.isInitiated() || page.getAnnotations() == null) return null;
        List<Annotation> annotations = page.getAnnotations();
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation a = annotations.get(i);
            SignatureStatus status = statusOf(a);
            if (status == null || a.isDeleted() || !a.allowScreenNormalMode()) continue;
            if (!status.isSigned() && host.view().getOnSignatureClicked() == null) continue;
            Rectangle2D.Float r = a.getUserSpaceRectangle();
            if (r != null && r.contains(point.x(), point.y())) return new AnnotationHit(point.pageIndex(), a);
        }
        return null;
    }

    /**
     * The signature tool: adds an empty signature field over a dragged view rectangle and reports it
     * to the application's onSignatureClicked, as a click on an unsigned field does.
     */
    public void createField(int pageIndex, Rectangle2D viewRect) {
        Page page = host.document().getPageTree().getPage(pageIndex);
        Rectangle bounds = Annotation.commonBoundsNormalization(new GeneralPath(viewRect), host.toPageSpace(page));
        SignatureWidgetAnnotation[] made = new SignatureWidgetAnnotation[1];
        host.annotationLocker().withAnnotationLock(pageIndex, () ->
                made[0] = DocumentSigning.addSignatureField(host.document(), pageIndex, bounds));
        SignatureStatus status = SignatureVerifier.verify(made[0]);
        host.addSignatureStatus(status);
        host.view().setToolMode(ToolMode.TEXT_SELECT);
        clicked(status);
    }

    /** A click on a signature field or badge: the application's handler, else the properties dialog. */
    public void clicked(SignatureStatus status) {
        PdfView view = host.view();
        boolean handled = view.getOnSignatureClicked() != null;
        // after the gesture: a modal dialog opened inside a mouse handler takes the release.
        Platform.runLater(() -> {
            if (handled) view.fireEvent(new SignatureEvent(view, view, status));
            else SignaturePropertiesDialog.show(view, status);
        });
    }
}
