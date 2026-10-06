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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A JBIG2 page whose text regions draw symbols from a shared dictionary can only be decoded with the
 * /JBIG2Globals stream; without it the library throws and the image falls back to an all-black raw decode.
 */
public class JBig2DecoderTest {

    private static final Name DECODE_PARMS = new Name("DecodeParms");
    private static final Name JBIG2_GLOBALS = new Name("JBIG2Globals");

    /** Records the globals the decoder hands to the JBIG2 library instead of calling it. */
    private static class RecordingDecoder extends JBig2Decoder {
        Stream globals;

        RecordingDecoder(ImageStream imageStream) {
            super(imageStream, null);
        }

        @Override
        protected BufferedImage decodeJbig2(DictionaryEntries decodeParams, Stream globalsStream,
                                            ImageInputStream imageInputStream, String[] jbigClasses) {
            globals = globalsStream;
            return new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        }
    }

    private static Stream globalsFor(Object decodeParms) {
        return globalsFor(new Library(), decodeParms);
    }

    private static Stream globalsFor(Library library, Object decodeParms) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), 1);
        entries.put(new Name("Height"), 1);
        entries.put(new Name("BitsPerComponent"), 1);
        entries.put(new Name("ImageMask"), true);
        entries.put(DECODE_PARMS, decodeParms);
        ImageStream imageStream = new ImageStream(library, entries, new byte[]{0});
        RecordingDecoder decoder = new RecordingDecoder(imageStream);
        decoder.decode();
        return decoder.globals;
    }

    private static DictionaryEntries withGlobals(Stream globals) {
        DictionaryEntries decodeParms = new DictionaryEntries();
        decodeParms.put(JBIG2_GLOBALS, globals);
        return decodeParms;
    }

    @DisplayName("the globals are found in a /DecodeParms dictionary")
    @Test
    public void globalsInDictionary() {
        Stream globals = new Stream(new Library(), new DictionaryEntries(), new byte[]{1});
        assertSame(globals, globalsFor(withGlobals(globals)));
    }

    @DisplayName("the globals are found in a /DecodeParms array of inline dictionaries")
    @Test
    public void globalsInArray() {
        Stream globals = new Stream(new Library(), new DictionaryEntries(), new byte[]{1});
        List<Object> decodeParms = new ArrayList<>();
        decodeParms.add(withGlobals(globals));
        assertSame(globals, globalsFor(decodeParms));
    }

    @DisplayName("the globals are found in a /DecodeParms array of references, as written for /Filter [/JBIG2Decode]")
    @Test
    public void globalsInArrayOfReferences() {
        // DEG Stromabrechnung Dez._0013.pdf: /DecodeParms [15 0 R].  The dictionary lookup merges inline
        // dictionaries from an array but doesn't resolve references in one, so it found nothing; the scan's
        // text regions couldn't find their symbols and the page rendered solid black.
        Library library = new Library();
        Stream globals = new Stream(library, new DictionaryEntries(), new byte[]{1});
        DictionaryEntries decodeParmsDictionary = withGlobals(globals);
        Reference reference = new Reference(15, 0);
        library.addObject(decodeParmsDictionary, reference);
        List<Object> decodeParms = new ArrayList<>();
        decodeParms.add(reference);
        assertSame(globals, globalsFor(library, decodeParms));
    }

    @DisplayName("a null entry ahead of the dictionary in a /DecodeParms array is skipped")
    @Test
    public void globalsAfterNullEntry() {
        // /Filter [/FlateDecode /JBIG2Decode] /DecodeParms [null 15 0 R]
        Library library = new Library();
        Stream globals = new Stream(library, new DictionaryEntries(), new byte[]{1});
        DictionaryEntries decodeParmsDictionary = withGlobals(globals);
        Reference reference = new Reference(15, 0);
        library.addObject(decodeParmsDictionary, reference);
        List<Object> decodeParms = new ArrayList<>();
        decodeParms.add(null);
        decodeParms.add(reference);
        assertSame(globals, globalsFor(library, decodeParms));
    }
}
