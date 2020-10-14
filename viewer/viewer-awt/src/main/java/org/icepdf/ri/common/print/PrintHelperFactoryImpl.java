package org.icepdf.ri.common.print;

import org.icepdf.core.pobjects.PageTree;

import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.*;

public class PrintHelperFactoryImpl implements PrintHelperFactory {

    public static final PrintHelperFactoryImpl INSTANCE = new PrintHelperFactoryImpl();

    @Override
    public PrintHelper createPrintHelper(final Container container, final PageTree pageTree, final float userRotation, final DocAttributeSet docAttributeSet, final PrintRequestAttributeSet printRequestAttributeSet) {
        return new PrintHelperImpl(container, pageTree, userRotation, docAttributeSet, printRequestAttributeSet);
    }


}
