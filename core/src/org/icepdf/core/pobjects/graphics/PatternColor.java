/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;

/**
 * <p>Pattern colour implements PColorSpace but is more of a parser placeholder
 * for dealing with 'cs' token which sets a pattern Colour space.  The pattern
 * color space can either define a Pattern dictionary which contains valid
 * pattern object which are then specified by the 'scn' or 'SCN' tokens.  The
 * pattern can also define straight up color space rgb, gray, N etc.</p>
 * <p>If the PatternColor contains dictionary of Pattern Object from the
 * pages resources then this object is created with the corrisponding
 * dictionary reference. </p>
 *
 * @since 1.0
 */
public class PatternColor extends PColorSpace {

    private Pattern pattern;

    private PColorSpace PColorSpace;

    /**
     * Creates a new instance of PatternColor.
     *
     * @param library document library.
     * @param entries dictionary entries.
     */
    public PatternColor(Library library, Hashtable entries) {
        super(library, entries);
    }

    /**
     * Not applicable to a Pattern Colour space.
     *
     * @return value of zero
     */
    public int getNumComponents() {
        if (PColorSpace != null) {
            return PColorSpace.getNumComponents();
        }
        return 0;
    }

    /**
     * Not applicable to a Pattern Colour space.
     *
     * @param f any value.
     * @return always returns null.
     */
    public Color getColor(float[] f) {
        if (PColorSpace != null) {
            return PColorSpace.getColor(f);
        }
        return Color.black;
    }

    public Pattern getPattern(Reference reference) {
        if (entries != null) {
            return (Pattern) entries.get(reference);
        }
        return null;
    }

    public PColorSpace getPColorSpace() {
        return PColorSpace;
    }

    public void setPColorSpace(PColorSpace PColorSpace) {
        this.PColorSpace = PColorSpace;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }
}
