package org.icepdf.core;

import org.icepdf.core.util.Defs;

public final class SystemProperties {
    //System properties
    public static final String OS_NAME = Defs.sysProperty("os.name");
    public static final String JAVA_HOME = Defs.sysProperty("java.home");
    public static final String USER_NAME = Defs.sysProperty("user.name");

    //ICEpdf-specifics
    public static final boolean PRIVATE_PROPERTY_ENABLED = Defs.booleanProperty(
            "org.icepdf.core.page.annotation.privateProperty.enabled", false);
    public static boolean USE_NFONT = Defs.sysPropertyBoolean("org.icepdf.core.useNFont", true);

    private SystemProperties() {
    }

    public static void setUseNFont(boolean useNFont) {
        USE_NFONT = useNFont;
    }
}
