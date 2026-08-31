/*
 * Copyright 2026 Patrick Corless
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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the GH-501 follow-up soft-mask resource-inheritance fix.  On the "Java
 * Magazine" cover (page 1) the drop shadows behind the "Devices and IoT" title
 * and the "14/24/32" callouts are luminosity soft masks whose group form draws
 * a pre-rendered grayscale shadow image via {@code Do}.  Those mask group forms
 * carry an empty {@code /Resources} dict and resolve the image through the
 * enclosing content stream's resources (PDF 32000-1 §7.8.3).
 * <p>
 * The mask group form is only <i>weakly</i> cached by the library, so parse-time
 * wiring can reach a different instance than the one finally rasterised.  The fix
 * wires the parent resources onto the {@link SoftMask} at the point of use and
 * applies them inside {@link SoftMask#getG()} just before the form initialises,
 * so the resources always reach the instance actually rendered.  Without it the
 * mask's {@code Do} operands don't resolve, the mask rasterises to nothing, and
 * the soft shadow collapses to a hard unmasked box.
 */
public class SoftMaskParentResourcesTest {

    private static Form emptyForm(Library library) {
        // A group form with no /Resources of its own -- the case that must
        // inherit the enclosing stream's resources.
        return new Form(library, new DictionaryEntries(), new byte[0]);
    }

    private static SoftMask luminosityMask(Library library, Form g) {
        DictionaryEntries smaskDict = new DictionaryEntries();
        smaskDict.put(SoftMask.S_KEY, new Name(SoftMask.SOFT_MASK_TYPE_LUMINOSITY));
        smaskDict.put(SoftMask.G_KEY, g);
        return new SoftMask(library, smaskDict);
    }

    /**
     * getG() must apply the resources supplied via setParentResources to the
     * mask group form before it initialises, so a form with no own /Resources
     * resolves its XObjects through the enclosing stream.
     */
    @Test
    public void getGAppliesParentResourcesToMaskForm() throws Exception {
        Library library = new Library();
        Form g = emptyForm(library);
        SoftMask softMask = luminosityMask(library, g);
        Resources enclosing = new Resources(library, new DictionaryEntries());

        softMask.setParentResources(enclosing);
        Form resolved = softMask.getG();

        assertSame(g, resolved, "getG must resolve the group form set on G");
        assertSame(enclosing, resolved.getLeafResources(),
                "an empty-resources mask form must inherit the enclosing stream's resources");
    }

    /**
     * Without wiring, an empty-resources mask form has no resources at all --
     * the pre-fix state where the mask cannot resolve its shadow image.
     */
    @Test
    public void unwiredMaskFormHasNoResources() {
        Library library = new Library();
        Form g = emptyForm(library);

        assertNull(g.getLeafResources(),
                "an un-wired empty-resources form must report no leaf resources");
    }

    /**
     * A mask form that carries its own /Resources keeps them: the parent is only
     * a fallback, so wiring must not override a form's own dictionary.
     */
    @Test
    public void ownResourcesTakePrecedenceOverParent() throws Exception {
        Library library = new Library();
        DictionaryEntries ownDict = new DictionaryEntries();
        DictionaryEntries formDict = new DictionaryEntries();
        formDict.put(Form.RESOURCES_KEY, ownDict);
        Form g = new Form(library, formDict, new byte[0]);
        SoftMask softMask = luminosityMask(library, g);
        Resources enclosing = new Resources(library, new DictionaryEntries());

        softMask.setParentResources(enclosing);
        Form resolved = softMask.getG();

        assertSame(ownDict, resolved.getLeafResources().getEntries(),
                "a mask form with its own /Resources must not be overridden by the parent");
    }
}
