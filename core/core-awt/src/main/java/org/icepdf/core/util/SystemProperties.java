package org.icepdf.core.util;

/**
 * All SystemProperties used in the core library.  This class provides better visibility when for configuration options
 * as well as way to easily set properties manually that maybe shared between class.
 */
public final class SystemProperties {

    public static final String OS_NAME = Defs.sysProperty("os.name");
    public static final String JAVA_HOME = Defs.sysProperty("java.home");
    public static final String USER_NAME = Defs.sysProperty("user.name");

    //  Shared system properties

    public static final boolean PRIVATE_PROPERTY_ENABLED = Defs.booleanProperty(
            "org.icepdf.core.page.annotation.privateProperty.enabled", false);

    public static final boolean INTERACTIVE_ANNOTATIONS =
            Defs.sysPropertyBoolean("org.icepdf.core.annotations.interactive.enabled", true);

    private SystemProperties() {
    }
}
