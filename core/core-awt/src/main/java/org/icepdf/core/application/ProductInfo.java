/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Product and build identification for ICEpdf.  The values are populated at
 * build time from the {@code product-info.properties} classpath resource, which
 * both the Maven and Gradle builds filter with the current project version (see
 * the resource template in {@code src/main/resources}).  Loading the values from
 * a resource rather than generating a {@code .java} file avoids the duplicate
 * class that resulted when both build tools emitted their own ProductInfo, and
 * keeps this class as ordinary, version-controlled source.
 */
public class ProductInfo {

    private static final Logger logger =
            Logger.getLogger(ProductInfo.class.getName());

    private static final String RESOURCE = "product-info.properties";

    /**
     * The company that owns this product.
     */
    public static String COMPANY;

    /**
     * The name of the product.
     */
    public static String PRODUCT;

    /**
     * The 3 levels of version identification, e.g. 1.0.0.
     */
    public static String VERSION;

    /**
     * The release type of the product (alpha, beta, production).
     */
    public static String RELEASE_TYPE;

    /**
     * The build number.  Typically tracked and maintained by the build system.
     */
    public static String BUILD_NO;

    /**
     * The revision number retrieved from the repository for this build.
     */
    public static String REVISION;

    static {
        Properties props = new Properties();
        try (InputStream in = ProductInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            } else {
                logger.fine(RESOURCE + " not found on the classpath; using default product info.");
            }
        } catch (IOException e) {
            logger.fine("Could not read " + RESOURCE + "; using default product info.");
        }
        COMPANY = resolve(props, "company", "");
        PRODUCT = resolve(props, "product", "ICEpdf");
        VERSION = resolve(props, "version", "dev");
        RELEASE_TYPE = resolve(props, "releaseType", "");
        BUILD_NO = resolve(props, "build", "0");
        REVISION = resolve(props, "revision", "0");
    }

    /**
     * Returns the property value, falling back to {@code defaultValue} when the
     * property is absent or still holds an unfiltered {@code @token@} (i.e. the
     * resource was used without the build filtering step).  An empty filtered
     * value (e.g. an unset release type) is returned as-is.
     */
    private static String resolve(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value == null || (value.startsWith("@") && value.endsWith("@"))) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Convenience method to get all the relevant product information.
     *
     * @return formatted product information.
     */
    public String toString() {
        StringBuilder info = new StringBuilder();
        info.append("\n");
        info.append(COMPANY);
        info.append("\n");
        info.append(PRODUCT);
        info.append(" ");
        info.append(VERSION);
        info.append(" ");
        info.append(RELEASE_TYPE);
        info.append("\n");
        info.append("Build number: ");
        info.append(BUILD_NO);
        info.append("\n");
        info.append("Revision: ");
        info.append(REVISION);
        info.append("\n");
        return info.toString();
    }

    public String getVersion() {
        StringBuilder info = new StringBuilder();
        info.append(VERSION);
        info.append(" ");
        info.append(RELEASE_TYPE);
        return info.toString();
    }

}