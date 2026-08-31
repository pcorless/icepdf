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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a "view mode" {@link Stream} (backed by a {@link ByteBuffer} view into the shared document buffer,
 * as produced by the object parser) decodes identically to the equivalent byte[]-backed stream, materializes its
 * raw bytes lazily, and round-trips through {@link Stream#disposeDecompressed()}.
 */
public class StreamViewModeTest {

    /** Deflate (FlateDecode) some bytes so the stream has a real filter to run on decode. */
    private static byte[] flate(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION))) {
            deflater.write(raw);
        }
        return out.toByteArray();
    }

    private static DictionaryEntries flateEntries() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Stream.FILTER_KEY, new Name("FlateDecode"));
        return entries;
    }

    /** Wrap {@code compressed} in a window inside a larger buffer, exactly as ByteBufferUtil.sliceObjectStream does. */
    private static ByteBuffer slicedView(byte[] compressed) {
        byte[] prefix = "JUNK-PREFIX".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "JUNK-SUFFIX".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer big = ByteBuffer.allocate(prefix.length + compressed.length + suffix.length);
        big.put(prefix);
        big.put(compressed);
        big.put(suffix);
        big.position(prefix.length);
        big.limit(prefix.length + compressed.length);
        return big.slice(); // position 0, limit == compressed.length
    }

    private static final byte[] PAYLOAD =
            ("BT /F1 12 Tf 72 720 Td (the quick brown fox jumps over the lazy dog) Tj ET\n"
                    + "0 0 1 rg 100 100 200 200 re f\n").repeat(50).getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("view-mode stream decodes identically to a byte[]-backed stream")
    public void viewModeDecodesIdenticallyToArrayMode() throws Exception {
        Library library = new Library();
        byte[] compressed = flate(PAYLOAD);

        Stream arrayMode = new Stream(library, flateEntries(), compressed);
        Stream viewMode = new Stream(library, flateEntries(), slicedView(compressed));

        byte[] arrayDecoded = arrayMode.getDecodedStreamBytes(0);
        byte[] viewDecoded = viewMode.getDecodedStreamBytes(0);

        assertArrayEquals(PAYLOAD, arrayDecoded, "control: array-mode decode must reproduce the payload");
        assertArrayEquals(arrayDecoded, viewDecoded, "view-mode decode must match array-mode decode");
    }

    @Test
    @DisplayName("getRawBytesLength() reports the compressed length and getRawBytes() materializes the exact window")
    public void viewModeMaterializesRawBytesLazilyAndExactly() throws Exception {
        Library library = new Library();
        byte[] compressed = flate(PAYLOAD);

        Stream viewMode = new Stream(library, flateEntries(), slicedView(compressed));

        // length is available without forcing a copy...
        assertEquals(compressed.length, viewMode.getRawBytesLength(), "length must match the compressed slice");
        // ...and decoding never needs the raw byte[] copy, only the view.
        assertArrayEquals(PAYLOAD, viewMode.getDecodedStreamBytes(0));
        // the cold path materializes exactly the slice window (no prefix/suffix bleed).
        assertArrayEquals(compressed, viewMode.getRawBytes(), "materialized raw bytes must equal the slice contents");
    }

    @Test
    @DisplayName("disposeDecompressed() drops the cache of a compressed stream and re-decode reproduces the bytes")
    public void disposeDecompressedAllowsReDecode() throws Exception {
        Library library = new Library();
        Stream viewMode = new Stream(library, flateEntries(), slicedView(flate(PAYLOAD)));

        byte[] first = viewMode.getDecodedStreamBytes(0);     // populates the decompressed cache
        viewMode.disposeDecompressed();                       // compressed == true, so the cache is released
        byte[] second = viewMode.getDecodedStreamBytes(0);    // must re-inflate from the view

        assertArrayEquals(PAYLOAD, first);
        assertArrayEquals(first, second, "re-decode after disposeDecompressed() must reproduce the same bytes");
    }

    @Test
    @DisplayName("disposeDecompressed() preserves an edited (setRawBytes) stream's content")
    public void disposeDecompressedPreservesEdits() {
        Library library = new Library();
        Stream edited = new Stream(library, new DictionaryEntries(), (byte[]) null);
        byte[] newContent = "edited, not-yet-recompressed content".getBytes(StandardCharsets.US_ASCII);
        edited.setRawBytes(newContent); // compressed == false, content lives in decompressedBytes

        edited.disposeDecompressed(); // must be a no-op for an edited stream

        assertArrayEquals(newContent, edited.getDecodedStreamBytes(0),
                "an edited stream's content must survive disposeDecompressed()");
    }
}