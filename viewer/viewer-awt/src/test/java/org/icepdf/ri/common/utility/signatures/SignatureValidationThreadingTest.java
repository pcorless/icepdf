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
package org.icepdf.ri.common.utility.signatures;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.signatures.SignaturePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationProgressPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GH-643: the Swing viewer must never validate a signature on the AWT event thread.  Validation can
 * wait on a revocation server for as long as its timeouts allow, and used to run on the event
 * thread when the signatures panel built its nodes, when Validate was chosen and when a properties
 * dialog opened.
 * <p>
 * The validator here is a stub that fails the test if it is called on the event thread, and waits
 * on a latch otherwise, so the "validating" state can be seen before the result arrives.
 */
public class SignatureValidationThreadingTest {

    private static final ResourceBundle messages =
            ResourceBundle.getBundle("org.icepdf.ri.resources.MessageBundle");

    /** A validator that records how it was called and refuses the event thread. */
    private static final class StubValidator {
        final SignatureValidator validator = mock(SignatureValidator.class);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean validated = new AtomicBoolean();
        final AtomicInteger runs = new AtomicInteger();
        final AtomicBoolean onEventThread = new AtomicBoolean();

        StubValidator() throws Exception {
            X509Certificate certificate = mock(X509Certificate.class);
            when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=Test Signer"));
            when(validator.getSignerCertificate()).thenReturn(certificate);
            when(validator.isSignedDataModified()).thenReturn(false);
            when(validator.isCertificateChainTrusted()).thenReturn(true);
            when(validator.isRevocationChecked()).thenReturn(true);
            when(validator.isValidated()).thenAnswer(i -> validated.get());
            when(validator.getLastValidated()).thenAnswer(i -> validated.get() ? new Date() : null);
            doAnswer(i -> run()).when(validator).ensureValidated(any());
            doAnswer(i -> run()).when(validator).validate(any(SignatureValidationOptions.class));
        }

        private Object run() throws InterruptedException {
            if (SwingUtilities.isEventDispatchThread()) {
                onEventThread.set(true);
                throw new AssertionError("validated on the event thread");
            }
            runs.incrementAndGet();
            assertTrue(release.await(10, TimeUnit.SECONDS), "test never released the validation");
            validated.set(true);
            return null;
        }
    }

    private static SignatureWidgetAnnotation signedField(SignatureValidator validator) {
        SignatureWidgetAnnotation annotation = mock(SignatureWidgetAnnotation.class);
        SignatureDictionary dictionary = mock(SignatureDictionary.class);
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Contents"), "x");
        when(dictionary.getEntries()).thenReturn(entries);
        when(dictionary.getDate()).thenReturn("D:20260101120000Z");
        when(annotation.getSignatureDictionary()).thenReturn(dictionary);
        when(annotation.getFieldDictionary()).thenReturn(mock(SignatureFieldDictionary.class));
        when(annotation.getSignatureValidator()).thenReturn(validator);
        return annotation;
    }

    private static Controller controller(SignatureWidgetAnnotation field) {
        Controller controller = mock(Controller.class);
        when(controller.getMessageBundle()).thenReturn(messages);
        Document document = mock(Document.class);
        Catalog catalog = mock(Catalog.class);
        InteractiveForm form = mock(InteractiveForm.class);
        ArrayList<SignatureWidgetAnnotation> fields = new ArrayList<>();
        fields.add(field);
        when(form.getSignatureFields()).thenReturn(fields);
        when(catalog.getInteractiveForm()).thenReturn(form);
        when(document.getCatalog()).thenReturn(catalog);
        when(controller.getDocument()).thenReturn(document);
        return controller;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static String label(SignatureTreeNode node) {
        return String.valueOf(node.getUserObject());
    }

    private static String format(String key) {
        return new java.text.MessageFormat(messages.getString(key)).format(new Object[]{"Test Signer ", ""});
    }

    @DisplayName("opening the signatures panel lists the signature as validating, then validates it off the event thread")
    @Test
    public void panelLoad() throws Exception {
        StubValidator stub = new StubValidator();
        SignatureWidgetAnnotation field = signedField(stub.validator);
        SignaturesHandlerPanel panel = onEdt(() -> new SignaturesHandlerPanel(controller(field)));
        onEdt(() -> {
            panel.refreshDocumentInstance();
            return null;
        });

        // the node arrives before its validation finishes, and says so.
        SignatureTreeNode node = null;
        for (int i = 0; i < 200 && node == null; i++) {
            node = onEdt(() -> panel.getSignatureTreeNode(field));
            if (node == null) Thread.sleep(25);
        }
        assertTrue(node != null, "the signature was never listed");
        SignatureTreeNode listed = node;
        assertEquals(format("viewer.utilityPane.signatures.tab.certTree.rootValidating.label"),
                onEdt(() -> label(listed)));
        assertNull(onEdt(listed::getRootNodeValidityIcon), "no verdict icon before there is a verdict");

        stub.release.countDown();
        panel.getWorkerTask().get(10, TimeUnit.SECONDS);
        // SwingWorker hands published chunks to the event thread on a timer, not at once.
        String signed = format("viewer.utilityPane.signatures.tab.certTree.rootSigned.label");
        for (int i = 0; i < 200 && !signed.equals(onEdt(() -> label(listed))); i++) {
            Thread.sleep(25);
        }
        assertEquals(signed, onEdt(() -> label(listed)));
        for (int i = 0; i < 200 && onEdt(() -> panel.getProgressBar().isVisible()); i++) {
            Thread.sleep(25);
        }
        assertTrue(onEdt(() -> listed.getChildCount() > 0), "the validated node shows its details");
        assertEquals(1, stub.runs.get(), "validated once, by the task");
        assertFalse(stub.onEventThread.get());
        assertFalse(onEdt(() -> panel.getProgressBar().isVisible()), "progress hidden when done");
    }

    @DisplayName("Validate re-runs the check off the event thread and the node shows progress meanwhile")
    @Test
    public void validateAction() throws Exception {
        StubValidator stub = new StubValidator();
        stub.validated.set(true);
        SignatureWidgetAnnotation field = signedField(stub.validator);
        SignaturesHandlerPanel panel = onEdt(() -> new SignaturesHandlerPanel(controller(field)));
        SignatureTreeNode node = onEdt(() -> {
            panel.addSignature(field);
            return panel.getSignatureTreeNode(field);
        });
        assertEquals(format("viewer.utilityPane.signatures.tab.certTree.rootSigned.label"), onEdt(() -> label(node)));

        CompletableFuture<SignatureValidator> validation = onEdt(() -> panel.updateSignature(field, node));
        assertEquals(format("viewer.utilityPane.signatures.tab.certTree.rootValidating.label"),
                onEdt(() -> label(node)));

        stub.release.countDown();
        validation.get(10, TimeUnit.SECONDS);
        onEdt(() -> null);
        assertEquals(format("viewer.utilityPane.signatures.tab.certTree.rootSigned.label"), onEdt(() -> label(node)));
        assertEquals(1, stub.runs.get());
        assertFalse(stub.onEventThread.get());
    }

    @DisplayName("a properties dialog on an unchecked signature shows progress, then the result")
    @Test
    public void propertiesDialog() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialogs need a display");
        StubValidator stub = new StubValidator();
        SignatureWidgetAnnotation field = signedField(stub.validator);
        SignaturePropertiesDialog dialog = onEdt(() -> new SignaturePropertiesDialog((Frame) null, messages, field));
        try {
            assertInstanceOf(SignatureValidationProgressPanel.class,
                    onEdt(() -> dialog.getContentPane().getComponent(0)));

            stub.release.countDown();
            Component content = null;
            for (int i = 0; i < 200; i++) {
                content = onEdt(() -> dialog.getContentPane().getComponent(0));
                if (!(content instanceof SignatureValidationProgressPanel)) break;
                Thread.sleep(25);
            }
            assertFalse(content instanceof SignatureValidationProgressPanel, "the result replaced the progress");
            assertEquals(1, stub.runs.get());
            assertFalse(stub.onEventThread.get());
        } finally {
            onEdt(() -> {
                dialog.dispose();
                return null;
            });
        }
    }
}
