package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Page;

public class ModifierFactory {

    public static Modifier getModifier(Dictionary modify) {
        if (modify instanceof Page) {
            return new PageRemovalModifier();
        }
        return null;
    }
}
