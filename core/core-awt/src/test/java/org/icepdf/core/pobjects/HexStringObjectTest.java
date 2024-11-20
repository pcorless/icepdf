package org.icepdf.core.pobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HexStringObjectTest {

    @DisplayName("HexStringObject - decode hex string")
    @Test
    public void decode_4_byte_hex_string() {
        HexStringObject hexStringObject = new HexStringObject(
                "FEFF004500780061006D0070006C00650020004F00700065006E004F0066006600690063006500200031002E0031002E003500200044006F00630075006D0065006E0074");
        String literalString = hexStringObject.getLiteralString();
        assertEquals("Example OpenOffice 1.1.5 Document", literalString);
        assertEquals(hexStringObject.getLength(), 138);
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

    @DisplayName("HexStringObject - get unsigned int from hex string")
    @Test
    public void get_unsigned_int_from_hex_string() {
        HexStringObject hexStringObject = new HexStringObject(
                "FEFF004500780061006D0070006C00650020004F00700065006E004F0066006600690063006500200031002E0031002E003500200044006F00630075006D0065006E0074");
        int unsignedInt = hexStringObject.getUnsignedInt(4, 4);
        assertEquals(69, unsignedInt);
    }
}
