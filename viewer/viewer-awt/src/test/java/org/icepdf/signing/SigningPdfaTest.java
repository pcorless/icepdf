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
package org.icepdf.signing;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.security.JceProvider;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.views.annotations.signing.BasicSignatureAppearanceCallback;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModelImpl;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.utils.PDFValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

/**
 * Signing a document that conforms to PDF/A-1 <em>Level A</em>.
 * <p>
 * Level A is where the rules about structure live: a tagged document has to keep its content
 * described by the structure tree, and signing adds a widget annotation to a page. Level B says
 * nothing about any of that, so a 1b-only check - which is all this project had - would never notice
 * a signature breaking the tagging.
 * <p>
 * The fixture is the 1b one with its XMP declaring conformance A. Its tagging was already complete;
 * it simply never claimed the level it met.
 */
public class SigningPdfaTest {

    private static final PDFAFlavour[] LEVELS = {
            PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B,
            PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U,
            PDFAFlavour.PDFA_3_A, PDFAFlavour.PDFA_3_B};

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("signing a PDF/A-1a document leaves it conforming")
    @Test
    public void signingKeepsLevelAConformance() throws Exception {
        File source = new File("src/test/resources/annotation/hello_pdfa1a.pdf");
        // the fixture is the ground truth for this test, so say so plainly if it ever stops being 1a
        PDFValidator.assertConformsTo(source, PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B);

        File signed = SigningFixture.of(source).withAppearance()
                .signTo(new File("./src/test/out/SigningPdfaTest_signed_1a.pdf"));

        PDFValidator.assertNoNewFailures(source, signed, LEVELS);
        // Stated outright as well: the point of the test is that a Level A document survives signing,
        // and "no new failures" says that only because the source conformed.
        PDFValidator.assertConformsTo(signed, PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B);
    }

}
