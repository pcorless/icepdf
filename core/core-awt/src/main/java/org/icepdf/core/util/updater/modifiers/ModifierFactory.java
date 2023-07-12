package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;

import java.util.logging.Logger;

public class ModifierFactory {
    private static final Logger logger = Logger.getLogger(ModifierFactory.class.toString());

    public static Modifier getModifier(Dictionary parent, Dictionary modify) {
        if (modify instanceof Page) {
            return new PageRemovalModifier(parent);
        } else if (modify instanceof Annotation) {
            return new AnnotationRemovalModifier(parent);
        }
        // todo nametree
        // todo contentstream updater
        else {
            logger.warning("Could not find modifier for " + modify.getClass());
        }
        return null;
    }
}
