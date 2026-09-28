/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.ri.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-532 brought every translation up to the English key set.  This keeps it there, and checks the one
 * thing a translator can break without a missing key: the {@code {n}} placeholders a message is
 * formatted with.
 */
public class MessageBundleParityTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[^}]*}");

    private static Properties bundle(String tag) throws Exception {
        String resource = "/org/icepdf/ri/resources/MessageBundle" + (tag.isEmpty() ? "" : "_" + tag) + ".properties";
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleParityTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "no bundle at " + resource);
            properties.load(stream);
        }
        return properties;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"da", "de", "es", "fi", "fr", "it", "nl", "no", "pt", "sv", "zh_CN", "zh_TW"})
    public void translationHasExactlyTheEnglishKeys(String tag) throws Exception {
        Set<String> english = bundle("").stringPropertyNames();
        Set<String> translated = bundle(tag).stringPropertyNames();

        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(translated);
        Set<String> extra = new TreeSet<>(translated);
        extra.removeAll(english);
        assertTrue(missing.isEmpty(), tag + " is missing " + missing);
        assertTrue(extra.isEmpty(), tag + " has keys English doesn't: " + extra);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"da", "de", "de_CH", "es", "fi", "fr", "it", "nl", "no", "pt", "sv", "zh_CN", "zh_TW"})
    public void translationKeepsTheEnglishPlaceholders(String tag) throws Exception {
        Properties english = bundle("");
        Properties translated = bundle(tag);
        for (String key : translated.stringPropertyNames()) {
            String source = english.getProperty(key);
            assertNotNull(source, tag + " has a key English doesn't: " + key);
            assertEquals(placeholders(source), placeholders(translated.getProperty(key)),
                    tag + " " + key + " drops or invents a placeholder");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"da", "de", "de_CH", "es", "fi", "fr", "it", "nl", "no", "pt", "sv", "zh_CN", "zh_TW"})
    public void placeholdersSubstituteOnceApostrophesAreEscaped(String tag) throws Exception {
        // bundles hold plain text; callers escape ' before MessageFormat, as Resources does.  Anything
        // still left as a literal {n} after that is a malformed pattern.
        Properties translated = bundle(tag);
        for (String key : translated.stringPropertyNames()) {
            String value = translated.getProperty(key);
            if (placeholders(value).isEmpty() || value.contains("{0,choice")) {
                continue;
            }
            MessageFormat format = new MessageFormat(value.replace("'", "''"));
            java.text.Format[] argFormats = format.getFormatsByArgumentIndex();
            Object[] args = new Object[Math.max(10, argFormats.length)];
            for (int i = 0; i < args.length; i++) {
                java.text.Format argFormat = i < argFormats.length ? argFormats[i] : null;
                args[i] = argFormat instanceof java.text.DateFormat ? new java.util.Date(0)
                        : argFormat instanceof java.text.NumberFormat ? (Object) 2 : "a" + i;
            }
            String formatted = format.format(args);
            assertFalse(PLACEHOLDER.matcher(formatted).find(), tag + " " + key + " -> " + formatted);
        }
    }

    @Test
    public void swissGermanOnlyOverridesExistingKeys() throws Exception {
        Set<String> english = bundle("").stringPropertyNames();
        for (String key : bundle("de_CH").stringPropertyNames()) {
            assertTrue(english.contains(key), "de_CH overrides a key that doesn't exist: " + key);
        }
    }

    @Test
    public void choiceStringsWithPlaceholdersHaveNoQuoteCharacter() throws Exception {
        // these are ChoiceFormat choices inside a MessageFormat: a choice holding {n} is re-parsed as a
        // pattern, so a plain ' would open a quote and swallow the rest of the text.
        String[] keys = {"viewer.exportText.fileStamp.progress.moreFile.msg",
                "viewer.utilityPane.search.searching1.moreFile.msg"};
        for (String tag : new String[]{"", "da", "de", "es", "fi", "fr", "it", "nl", "no", "pt", "sv", "zh_CN", "zh_TW"}) {
            Properties translated = bundle(tag);
            for (String key : keys) {
                assertFalse(translated.getProperty(key).contains("'"), tag + " " + key);
            }
        }
    }
}
