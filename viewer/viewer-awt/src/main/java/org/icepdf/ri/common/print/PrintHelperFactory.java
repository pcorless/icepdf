/*
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
package org.icepdf.ri.common.print;

import org.icepdf.core.pobjects.PageTree;

import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.PrintQuality;
import java.awt.*;

import static org.icepdf.ri.common.print.PrintHelper.createDocAttributeSet;
import static org.icepdf.ri.common.print.PrintHelper.createPrintRequestAttributeSet;

/**
 * represents a factory of PrintHelper
 */
@FunctionalInterface
public interface PrintHelperFactory {
    /**
     * Creates a new <code>PrintHelper</code> instance using the specified
     * doc and print attribute sets.  This constructor offers the most flexibility
     * as it allows the attributes sets to be pre configured.  This method
     * should only be used by advanced users.
     *
     * @param container                parent container uses to center print dialog.
     * @param pageTree                 document page tree.
     * @param userRotation             rotation of view
     * @param docAttributeSet          MediaSizeName constant of paper size to print to.
     * @param printRequestAttributeSet quality of the print job, draft, quality etc.
     */
    PrintHelper createPrintHelper(Container container, PageTree pageTree,
                                  float userRotation,
                                  DocAttributeSet docAttributeSet,
                                  PrintRequestAttributeSet printRequestAttributeSet);

    /**
     * Creates a new <code>PrintHelper</code> instance using the specified
     * media sized and print quality.
     *
     * @param container     parent container uses to center print dialog.
     * @param pageTree      document page tree.
     * @param rotation      rotation at witch to paint document.
     * @param paperSizeName MediaSizeName constant of paper size to print to.
     * @param printQuality  quality of the print job, draft, quality etc.
     */
    default PrintHelper createPrintHelper(final Container container, final PageTree pageTree,
                                          final float rotation,
                                          final MediaSizeName paperSizeName,
                                          final PrintQuality printQuality) {
        return createPrintHelper(container, pageTree, rotation, createDocAttributeSet(paperSizeName),
                createPrintRequestAttributeSet(printQuality, paperSizeName));
    }

    /**
     * Creates a new <code>PrintHelper</code> instance defaulting the
     * paper size to Letter and the print quality to Draft.
     *
     * @param container parent container used to center print dialogs.
     * @param pageTree  document page tree.
     * @param rotation  rotation of page
     */
    default PrintHelper createPrintHelper(final Container container, final PageTree pageTree, final int rotation) {
        return createPrintHelper(container, pageTree, rotation, MediaSizeName.NA_LETTER, PrintQuality.DRAFT);
    }
}
