package com.flowledger.migration.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {
    private final CsvParser parser = new CsvParser();

    @Test
    void parsesHeadersAndQuotedValues() {
        String csv = "sku,name,qty\n" + "A1,\"Widget, Pro\",10\n" + "B2,Gadget,3\n";
        ParsedSheet sheet = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "data");
        assertEquals(List.of("sku", "name", "qty"), sheet.columns());
        assertEquals(2, sheet.rows().size());
        assertEquals("Widget, Pro", sheet.rows().get(0).get("name"));
        assertEquals("10", sheet.rows().get(0).get("qty"));
    }

    @Test
    void emptyFileReturnsEmptySheet() {
        ParsedSheet sheet = parser.parse(new ByteArrayInputStream(new byte[0]), "data");
        assertTrue(sheet.columns().isEmpty());
        assertTrue(sheet.rows().isEmpty());
    }
}
