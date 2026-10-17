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
package org.icepdf.fx.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The translations stay in step with the English bundle. */
class MessagesTest {

    private static final List<String> LOCALES = List.of("da", "de", "de_CH", "es", "fi", "fr", "it", "nl", "no",
            "pt", "sv", "zh_CN", "zh_TW");

    @DisplayName("every translated key exists in English, with the same arguments, and every pattern parses")
    @Test
    void translationsMatchEnglish() throws Exception {
        Properties english = load("messages.properties");
        assertFalse(english.isEmpty());
        for (String locale : LOCALES) {
            Properties translated = load("messages_" + locale + ".properties");
            for (String key : translated.stringPropertyNames()) {
                assertTrue(english.containsKey(key), locale + ": " + key + " is not in the English bundle");
                String en = english.getProperty(key), tr = translated.getProperty(key);
                assertEquals(arguments(en), arguments(tr), locale + ": " + key + " has other arguments");
                if (!arguments(en).isEmpty()) {
                    assertDoesNotThrow(() -> new MessageFormat(tr), locale + ": " + key);
                }
            }
        }
    }

    @DisplayName("every pattern formats, fills its arguments, and keeps its apostrophes (also inside plurals)")
    @Test
    void patternsFormat() throws Exception {
        Properties english = load("messages.properties");
        List<String> files = new ArrayList<>(List.of("messages.properties"));
        LOCALES.forEach(l -> files.add("messages_" + l + ".properties"));
        for (String file : files) {
            Properties bundle = load(file);
            for (String key : bundle.stringPropertyNames()) {
                if (arguments(english.getProperty(key, "")).isEmpty()) continue;
                String pattern = bundle.getProperty(key);
                // plural arguments, so a choice picks its "many" form
                String out = new MessageFormat(pattern, Locale.ROOT).format(new Object[]{3, 5, 7});
                assertFalse(out.matches("(?s).*\\{\\d.*"), file + ": " + key + " left an argument: " + out);
                if (pattern.contains("''")) {
                    assertTrue(out.contains("'"), file + ": " + key + " lost its apostrophe (write '''' inside a plural): " + out);
                }
            }
        }
    }

    @DisplayName("a missing key reads as the key, with a fallback when one is given")
    @Test
    void missingKeys() {
        assertEquals("no.such.key", Messages.get("no.such.key"));
        assertEquals("fallback", Messages.get("no.such.key", "fallback"));
        assertEquals("Page 3 of 12", Messages.format("pdfView.printingPage", 3, 12).replace("Printing page", "Page"));
    }

    private static Set<String> arguments(String pattern) {
        Set<String> found = new TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(\\d)").matcher(pattern);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    private static Properties load(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @DisplayName("reason is the exception's message, else its cause's, else its kind - never null")
    @Test
    void reasonNeverNull() {
        assertEquals("disk full", Messages.reason(new java.io.IOException("disk full")));
        assertEquals("NullPointerException", Messages.reason(new NullPointerException()));
        assertEquals("inner", Messages.reason(new RuntimeException(null, new IllegalStateException("inner"))));
        assertEquals("IllegalStateException", Messages.reason(new IllegalStateException("  ")));
        assertEquals("", Messages.reason(null));
        // a wrapper that only repeats its cause
        assertEquals("inner", Messages.reason(new RuntimeException(new IllegalStateException("inner"))));
    }

    @DisplayName("a file error's reason says what went wrong, not the path again")
    @Test
    void fileReasons() {
        Locale was = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            assertEquals("the file doesn't exist", Messages.reason(new java.nio.file.NoSuchFileException("/a/b.pdf")));
            assertEquals("permission denied", Messages.reason(new java.nio.file.AccessDeniedException("/a/b.pdf")));
            assertEquals("Read-only file system",
                    Messages.reason(new java.nio.file.FileSystemException("/a/b.pdf", null, "Read-only file system")));
            assertEquals("No such file or directory",
                    Messages.reason(new java.io.FileNotFoundException("/a/b.pdf (No such file or directory)")));
            assertEquals("plain", Messages.reason(new java.io.FileNotFoundException("plain")));
        } finally {
            Locale.setDefault(was);
        }
    }
}
