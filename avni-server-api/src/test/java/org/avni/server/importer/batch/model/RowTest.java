package org.avni.server.importer.batch.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RowTest {
    @Test
    public void toStringShouldSerialiseProperly() throws Exception {
        String[] headers = {"A", "B"};
        assertEquals("\"AA\",\"\"", new Row(headers, new String[]{"AA"}).toString());
        assertEquals("\"AA\",\"BB\"", new Row(headers, new String[]{"AA", "BB"}).toString());
        assertEquals("\"AB, CD\",\"BB, EE\"", new Row(headers, new String[]{"AB, CD", "BB, EE"}).toString());
    }

    @Test
    public void trimHeadersAndValues() {
        String[] headers = {"A", "B"};
        Row row = new Row(headers, new String[]{" AA ", " BB"});
        assertEquals("A", row.getHeaders()[0]);
        assertEquals("B", row.getHeaders()[1]);
        assertEquals("AA", row.get("A"));
        assertEquals("BB", row.get("B"));
        assertEquals("AA", row.get("A "));
        assertEquals("BB", row.get(" B"));
    }
}
