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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HexStringObjectTest {

    @DisplayName("HexStringObject - decode hex string")
    @Test
    public void decode_4_byte_hex_string() {
        HexStringObject hexStringObject = new HexStringObject(
                "FEFF004500780061006D0070006C00650020004F00700065006E004F0066006600690063006500200031002E0031002E003500200044006F00630075006D0065006E0074");
        String literalString = hexStringObject.getLiteralString();
        assertEquals("Example OpenOffice 1.1.5 Document", literalString);
        // 136 is the number of digits the string actually holds.  This asserted 138 because
        // decoding used to append a stray "00" to the object it was reading.
        assertEquals(136, hexStringObject.getLength());
    }

    @DisplayName("HexStringObject - decode hex string")
    @Test
    public void decode_2_byte_hex_string() {
        HexStringObject hexStringObject = new HexStringObject(
                "4578616D706C65204F70656E4F666669636520312E312E3520446F63756D656E74>");
        String literalString = hexStringObject.getLiteralString();
        assertEquals("Example OpenOffice 1.1.5 Document", literalString);
    }

    @DisplayName("HexStringObject - decode empty hex string")
    @Test
    public void decode_empty_hex_string() {
        HexStringObject hexStringObject = new HexStringObject("");
        String literalString = hexStringObject.getLiteralString();
        assertEquals("", literalString);
    }

    @DisplayName("HexStringObject - decode invalid hex string")
    @Test
    public void decode_invalid_hex_string() {
        HexStringObject hexStringObject = new HexStringObject("ZZZZ");
        String literalString = hexStringObject.getLiteralString();
        assertEquals("", literalString);
    }

    @DisplayName("HexStringObject - encode literal string to hex")
    @Test
    public void encode_literal_string_to_hex() {
        String literalString = "Example OpenOffice 1.1.5 Document";
        HexStringObject hexStringObject = HexStringObject.createHexString(literalString);
        assertEquals(
                "FEFF004500780061006D0070006C00650020004F00700065006E004F0066006600690063006500200031002E0031002E003500200044006F00630075006D0065006E0074",
                hexStringObject.getHexString());
    }

    @DisplayName("HexStringObject - a two digit string starting FE is not a byte order marker")
    @Test
    public void decode_short_hex_string_starting_like_the_marker() {
        // the marker test used to read four digits without checking there were four: <FE> matched
        // the first two and then threw indexing the third.
        assertEquals("\u00FE", new HexStringObject("FE").getLiteralString());
        assertEquals("\u00FE", new HexStringObject("fe").getLiteralString());
        assertEquals("\u00FE\u00F0", new HexStringObject("FEF").getLiteralString());
    }

    @DisplayName("HexStringObject - byte order marker is recognised in either case")
    @Test
    public void decode_lower_case_byte_order_marker() {
        assertEquals("Hi", new HexStringObject("feff00480069").getLiteralString());
        assertEquals("Hi", new HexStringObject("FeFf00480069").getLiteralString());
    }

    @DisplayName("HexStringObject - a marker on its own decodes to nothing")
    @Test
    public void decode_marker_only() {
        assertEquals("", new HexStringObject("FEFF").getLiteralString());
    }

    @DisplayName("StringObject - getHexString is upper case for both implementations")
    @Test
    public void hex_string_case_is_consistent() {
        String expected = "AB01";
        assertEquals(expected, new HexStringObject("ab01").getHexString());
        assertEquals(expected, new LiteralStringObject(new String(new char[]{0x00AB, 0x0001})).getHexString());
        assertEquals(expected, HexStringObject.encodeHexString(new byte[]{(byte) 0xAB, 0x01}));
    }

    @DisplayName("StringObject - getLength measures the data each implementation holds")
    @Test
    public void length_of_both_implementations() {
        assertEquals(4, new HexStringObject("4869").getLength());   // digits
        assertEquals(2, new LiteralStringObject("Hi").getLength()); // characters
    }

    @DisplayName("HexStringObject - decoding does not mutate the object it reads")
    @Test
    public void decode_is_side_effect_free() {
        // 15 four digit codes after the marker: the old padding test keyed off an odd GROUP count
        // rather than a leftover pair, so it appended "00" to well formed data - and did it again on
        // every call, growing the object and eventually decoding a trailing NUL.
        HexStringObject hex = new HexStringObject(
                "FEFF004800650078002000700072006F00620065002000760061006C00750065");
        String first = hex.getLiteralString();
        assertEquals("Hex probe value", first);
        assertEquals(64, hex.getLength());
        assertEquals(first, hex.getLiteralString());
        assertEquals(first, hex.getLiteralString());
        assertEquals(64, hex.getLength(), "decoding must not grow the stored digits");
    }

    @DisplayName("HexStringObject - a string authored for writing is marked modified")
    @Test
    public void authored_string_is_modified() {
        HexStringObject authored = new HexStringObject("Hi", new Reference(4, 0));
        assertTrue(authored.isModified());
        assertEquals("FEFF00480069", authored.getHexString());
        assertEquals("Hi", authored.getLiteralString());
    }
}
