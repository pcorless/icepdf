package org.icepdf.core.pobjects.fonts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.icepdf.core.pobjects.fonts.FontManager.FONT_FILE_ALLOW_LIST_PATTERN_PROPERTY;
import static org.icepdf.core.pobjects.fonts.FontManager.MOST_COMMON_FONTS_PATTERN;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FontManagerTest {
    private static final Set<String> SAMPLE_FILENAMES = Set.of("foo.ttf", "Arial Bold.ttf", "Arial Unicode.ttf",
            "Arial Narrow.ttf", "Foo Bold.ttf", "Foo Unicode.ttf", "Foo Narrow.ttf", "Times New Roman.ttf", "Apple " +
                    "Symbols.ttf");
    private static final Set<String> EXPECTED_FILTERED_FILES = Set.of("Arial Bold.ttf", "Arial Narrow.ttf", "Times " +
            "New Roman.ttf");
    private static final boolean EXPECTED_FALSE_AS_NO_FILE = false;

    private static Stream<Arguments> paramProvider() {
        return Stream.of(
                arguments(
                        null,
                        SAMPLE_FILENAMES),
                arguments(
                        MOST_COMMON_FONTS_PATTERN,
                        EXPECTED_FILTERED_FILES));
    }

    @ParameterizedTest
    @MethodSource("paramProvider")
    void testIsFontFileAllowedPattern(String patternString, Set<String> expectedFiles) throws IOException {
        // Given
        if (patternString != null) {
            System.setProperty(FONT_FILE_ALLOW_LIST_PATTERN_PROPERTY, patternString);
        }
        FontManager instance = new FontManager();
        Path tempDirectory = Files.createTempDirectory(FontManager.class.getSimpleName());
        for (String sampleFilename : SAMPLE_FILENAMES) {
            // Given
            File sampleFile = tempDirectory.resolve(sampleFilename).toFile();
            // When
            boolean actualAllowed = instance.isFontFileAllowed(sampleFile);
            // Then
            Assertions.assertEquals(EXPECTED_FALSE_AS_NO_FILE, actualAllowed);
            // Given
            sampleFile.createNewFile();
            // When
            actualAllowed = instance.isFontFileAllowed(sampleFile);
            // Then
            boolean expectedAllowed = expectedFiles.contains(sampleFilename);
            Assertions.assertEquals(expectedAllowed, actualAllowed);
        }
    }
}