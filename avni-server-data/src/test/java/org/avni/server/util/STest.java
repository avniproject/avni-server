package org.avni.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class STest {
    @Test
    void testUnDoubleQuote_withDoubleQuotes() {
        // Test input with leading and trailing double quotes
        String input = "\"Hello World\"";
        String result = S.unDoubleQuote(input);
        assertEquals("Hello World", result);
    }

    @Test
    public void testUnDoubleQuote_withEscapedDoubleQuotes() {
        // Test input with escaped double quotes
        String input = "\"Hello \"World\"\"";
        String result = S.unDoubleQuote(input);
        assertEquals("Hello \"World\"", result);
    }

    @Test
    void testUnDoubleQuote_withoutDoubleQuotes() {
        // Test input without leading and trailing double quotes
        String input = "Hello World";
        String result = S.unDoubleQuote(input);
        assertEquals("Hello World", result);
    }

    @Test
    void testUnDoubleQuote_singleLeadingDoubleQuote() {
        // Test input with a single leading double quote
        String input = "\"Hello World";
        String result = S.unDoubleQuote(input);
        assertEquals("\"Hello World", result);
    }

    @Test
    void testUnDoubleQuote_singleTrailingDoubleQuote() {
        // Test input with a single trailing double quote
        String input = "Hello World\"";
        String result = S.unDoubleQuote(input);
        assertEquals("Hello World\"", result);
    }

    @Test
    void testUnDoubleQuote_emptyString() {
        // Test with an empty string
        String input = "";
        String result = S.unDoubleQuote(input);
        assertEquals("", result);
    }

    @Test
    void testUnDoubleQuote_nullInput() {
        // Test with a null input
        String input = null;
        String result = S.unDoubleQuote(input);
        assertNull(result);
    }
}
