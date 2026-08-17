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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;

/**
 * Builds the {@link TextSprite} runs a {@link org.icepdf.core.util.updater.callbacks.StringObjectWriter}
 * consumes, without a PDF, a page or a parser.
 * <p>
 * Glyph positions are accumulated from {@link StubFontFile}'s constant advance, so every offset a
 * writer emits is a small exact number that a test can state literally.
 * <pre>
 *     TextSprite sprite = GlyphRunBuilder.simpleFont(12f)
 *             .glyphs("Hello World")
 *             .flag(6, 11)              // flag "World"
 *             .build();
 * </pre>
 * Character codes come from the string's own chars for simple fonts. For CID runs, codes are given
 * explicitly - the byte-level encoding of a code is exactly what several of these tests are about,
 * so it must not be inferred.
 */
public class GlyphRunBuilder {

    private static final Name FONT_NAME = new Name("F1");

    private final byte subTypeFormat;
    private final StubFontFile font;
    private final ArrayList<char[]> pending = new ArrayList<>();
    private final ArrayList<Boolean> flags = new ArrayList<>();
    private float startX;

    private GlyphRunBuilder(byte subTypeFormat, StubFontFile font) {
        this.subTypeFormat = subTypeFormat;
        this.font = font;
    }

    /**
     * A run in a simple (single byte) font at the given size.
     *
     * @param size font size
     * @return new builder
     */
    public static GlyphRunBuilder simpleFont(float size) {
        return new GlyphRunBuilder(Font.SIMPLE_FORMAT,
                new StubFontFile(StubFontFile.DEFAULT_ADVANCE, size, "StubSimple"));
    }

    /**
     * A run in a composite (CID) font at the given size.
     *
     * @param size font size
     * @return new builder
     */
    public static GlyphRunBuilder cidFont(float size) {
        return new GlyphRunBuilder(Font.CID_FORMAT,
                new StubFontFile(StubFontFile.DEFAULT_ADVANCE, size, "StubCid"));
    }

    /**
     * Text space x of the first glyph. Non-zero exercises the writers' handling of a run that does
     * not start at the origin.
     *
     * @param startX x of the first glyph
     * @return this builder
     */
    public GlyphRunBuilder startingAt(float startX) {
        this.startX = startX;
        return this;
    }

    /**
     * Appends one glyph per character, using each character as its own code.
     *
     * @param text characters to append
     * @return this builder
     */
    public GlyphRunBuilder glyphs(String text) {
        for (int i = 0; i < text.length(); i++) {
            pending.add(new char[]{text.charAt(i)});
            flags.add(false);
        }
        return this;
    }

    /**
     * Appends glyphs with explicit character codes, for cases where the code is the point of the
     * test and must not be inferred from a string.
     *
     * @param codes character codes to append
     * @return this builder
     */
    public GlyphRunBuilder codes(int... codes) {
        for (int code : codes) {
            pending.add(new char[]{(char) code});
            flags.add(false);
        }
        return this;
    }

    /**
     * Flags glyphs in {@code [from, to)} as redacted.
     *
     * @param from first glyph index to flag, inclusive
     * @param to   last glyph index to flag, exclusive
     * @return this builder
     */
    public GlyphRunBuilder flag(int from, int to) {
        for (int i = from; i < to; i++) {
            flags.set(i, true);
        }
        return this;
    }

    /**
     * Flags every glyph, the "whole string is redacted" case.
     *
     * @return this builder
     */
    public GlyphRunBuilder flagAll() {
        return flag(0, pending.size());
    }

    /**
     * @return the advance every glyph of this run contributes, in text space
     */
    public float advance() {
        return font.advanceInTextSpace();
    }

    public TextSprite build() {
        TextSprite sprite = new TextSprite(font, subTypeFormat, pending.size(),
                new AffineTransform(), new AffineTransform());
        sprite.setFontSize(font.getSize());
        sprite.setFontName(font.getName());
        float advance = font.advanceInTextSpace();
        float x = startX;
        for (int i = 0; i < pending.size(); i++) {
            char code = pending.get(i)[0];
            GlyphText glyphText = sprite.addText(code, FONT_NAME, String.valueOf(code),
                    x, 0, advance, advance, 0);
            glyphText.setFontSubTypeFormat(subTypeFormat);
            if (flags.get(i)) {
                glyphText.flagged();
            }
            x += advance;
        }
        return sprite;
    }

    /**
     * Convenience for the common single-sprite case.
     *
     * @return the built sprite as the one-element operator list a writer takes
     */
    public ArrayList<TextSprite> buildOperators() {
        ArrayList<TextSprite> operators = new ArrayList<>(1);
        operators.add(build());
        return operators;
    }
}
