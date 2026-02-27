/*
 * Copyright 2026 Patrick Corless
 *
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
