package org.icepdf.core.util.parser.content;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.filters.ASCIIHexDecode;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class LexerTest
{

    @DisplayName("Lexer - read escaped characters")
    @Test
    public void read_escaped_characters() throws IOException
    {
        Lexer lexer = new Lexer();
        byte [] expectedBytes = new byte[]{0x00,'8',0x00,'0', 0x00,'4',0x00,'6',0x00,'6',0x00,'M',0x00, (byte) 0xfc,0x00,'n',0x00,'c',0x00,'h',0x00,'e',0x00,'n'};
        String expectedString = new String(expectedBytes,StandardCharsets.UTF_16);

        lexer.setContentStream(Stream.fromByteArray("BT\n(\\0008\\0000\\0004\\0006\\0006\\0M\\0\\374\\0n\\0c\\0h\\0e\\0n) Tj\nET"
                .getBytes(UTF_8),new Dictionary(new Library(),new DictionaryEntries())),null);

        assertEquals(Operands.BT,lexer.next());

        Object nextOjbect = lexer.next();
        assertInstanceOf(StringObject.class,nextOjbect);

        String lexerString = new String(new ASCIIHexDecode(new ByteArrayInputStream(((StringObject) nextOjbect).getHexString().getBytes(UTF_8))).readAllBytes(), StandardCharsets.UTF_16);
        assertEquals(expectedString,lexerString);

        assertEquals(Operands.Tj,lexer.next());
        assertEquals(Operands.ET,lexer.next());
    }
}