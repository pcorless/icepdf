package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;

import java.util.logging.Logger;

/**
 * Modifier factory gets a modifier for tasks that are rather involved.  Mainly used for removal but could
 * be used for other object operations.
 *
 * @since 7.2
 */
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
