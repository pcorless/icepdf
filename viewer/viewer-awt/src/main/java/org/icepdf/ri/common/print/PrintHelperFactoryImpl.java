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
