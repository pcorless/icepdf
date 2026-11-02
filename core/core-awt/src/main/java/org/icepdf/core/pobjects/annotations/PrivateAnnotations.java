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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.util.SystemProperties;

/**
 * The private annotation rule.  ICEpdf's own /F bit {@link Annotation#FLAG_PRIVATE_CONTENTS}
 * (0x400, reserved by the PDF specification) marks a markup annotation as private to its author.
 * With {@code org.icepdf.core.page.annotation.privateProperty.enabled} set, a page leaves out
 * the private markup annotations of every other author (/T other than {@code user.name}), with
 * their popups.
 * <p>
 * This is a viewer convention, not protection: the annotations stay in the file, saving keeps
 * them, and other viewers show them.
 */
public final class PrivateAnnotations {

    private static volatile boolean enabled = SystemProperties.PRIVATE_PROPERTY_ENABLED;
    private static volatile String currentUser = SystemProperties.USER_NAME;

    private PrivateAnnotations() {
    }

    /** True when private annotations are in use ({@code privateProperty.enabled}). */
    public static boolean isEnabled() {
        return enabled;
    }

    /** The author whose private annotations are shown: {@code user.name}. */
    public static String currentUser() {
        return currentUser;
    }

    /**
     * True if a page leaves this annotation out: private annotations are in use, and it is a
     * private markup annotation by another author (one without an author included), or the popup
     * of one.
     */
    public static boolean hides(Annotation annotation) {
        if (!enabled) return false;
        if (annotation instanceof PopupAnnotation) {
            MarkupAnnotation parent = ((PopupAnnotation) annotation).getParent();
            return parent != null && hides(parent);
        }
        if (!(annotation instanceof MarkupAnnotation) || !annotation.getFlagPrivateContents()) return false;
        String author = ((MarkupAnnotation) annotation).getTitleText();
        return author == null || !author.equals(currentUser);
    }

    /**
     * Sets the rule for this JVM, in place of the system properties read at start up; for tests,
     * and applications that know the user by another name.  Affects pages loaded afterwards.
     *
     * @param enabled     whether private annotations are in use
     * @param currentUser whose private annotations are shown
     */
    public static void configure(boolean enabled, String currentUser) {
        PrivateAnnotations.enabled = enabled;
        PrivateAnnotations.currentUser = currentUser;
    }

    /** Goes back to the system properties. */
    public static void reset() {
        configure(SystemProperties.PRIVATE_PROPERTY_ENABLED, SystemProperties.USER_NAME);
    }
}
