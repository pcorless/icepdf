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
import java.awt.*;

/**
 * A factory of PrintHelperImpl
 */
public final class PrintHelperFactoryImpl implements PrintHelperFactory {

    private static final PrintHelperFactoryImpl INSTANCE = new PrintHelperFactoryImpl();

    private PrintHelperFactoryImpl() {
    }

    /**
     * @return The factory instance
     */
    public static PrintHelperFactoryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public PrintHelper createPrintHelper(final Container container, final PageTree pageTree, final float userRotation,
                                         final DocAttributeSet docAttributeSet, final PrintRequestAttributeSet printRequestAttributeSet) {
        return new PrintHelperImpl(container, pageTree, userRotation, docAttributeSet, printRequestAttributeSet);
    }


}
