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
package org.icepdf.signing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strings behind the signature layout control, in every language that has them.
 * <p>
 * Read out of each locale's own properties file rather than through a {@link java.util.ResourceBundle},
 * which is the whole point: a bundle falls back to its parent, so a key missing from the Danish file
 * comes back as the English text and every assertion about it passes.  Reading the file says whether
 * the translation is actually there.
 * <p>
 * Going through the bundle would also mean asserting the text differs from English to prove anything,
 * and that is not a thing that can be asserted - "Layout:" is the right answer in Danish, German,
 * Italian and Swedish alike.
 */
public class SignatureLayoutBundleTest {

    private static final String PREFIX =
            "viewer.annotation.signature.creation.dialog.signature.appearance.layout";

    private static final String[] KEYS = {PREFIX + ".label",
            PREFIX + ".sideBySide.label", PREFIX + ".overlay.label"};

    private static Properties bundleFor(String tag) throws Exception {
        String resource = "/org/icepdf/ri/resources/MessageBundle"
                + (tag.isEmpty() ? "" : "_" + tag) + ".properties";
        Properties properties = new Properties();
        try (InputStream stream = SignatureLayoutBundleTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "no bundle on the classpath at " + resource);
            // Reads ISO-8859-1 with \\uXXXX escapes, exactly as the bundle itself is read, so a
            // mangled escape shows up here as it would in the dialog.
            properties.load(stream);
        }
        return properties;
    }

    @DisplayName("every translated bundle carries the layout control's own strings")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"da", "de", "es", "fi", "fr", "it", "nl", "no", "pt", "sv",
            "zh_CN", "zh_TW"})
    public void bundleCarriesLayoutStrings(String tag) throws Exception {
        Properties translated = bundleFor(tag);
        for (String key : KEYS) {
            // A key only in the English parent is a label in the wrong language for the users
            // running in that locale, and on no other machine.
            assertTrue(translated.containsKey(key), tag + " is missing " + key);
            String value = translated.getProperty(key);
            assertFalse(value.trim().isEmpty(), tag + " has nothing for " + key);
            assertFalse(value.contains("\\u"), tag + " has an unread escape in " + key + ": " + value);
        }
    }

    @DisplayName("the English bundle has them too, since it is what every other one falls back to")
    @Test
    public void englishBundleCarriesLayoutStrings() throws Exception {
        Properties english = bundleFor("");
        for (String key : KEYS) {
            assertTrue(english.containsKey(key), "the English bundle is missing " + key);
        }
    }
}
