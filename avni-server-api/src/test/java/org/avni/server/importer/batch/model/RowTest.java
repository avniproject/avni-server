package org.avni.server.importer.batch.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RowTest {
    @Test
    public void toStringShouldSerialiseProperly() {
        String[] headers = {"A", "B"};
        assertEquals("\"AA\",\"\"", new Row(headers, new String[]{"AA"}).toString());
        assertEquals("\"AA\",\"BB\"", new Row(headers, new String[]{"AA", "BB"}).toString());
        assertEquals("\"AB, CD\",\"BB, EE\"", new Row(headers, new String[]{"AB, CD", "BB, EE"}).toString());
    }

    @Test
    public void allowExtraColumnsInData() {
        String[] headers = {"A", "B"};
        new Row(headers, new String[]{"AA", "BB", "CC"});
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

    @Test
    public void dropBlankHeaderColumns() {
        String[] paddedHeaders = {"A", "B", "", "  ", null, ""};
        Row row = new Row(paddedHeaders, new String[]{"AA", "BB", "junk", "", "", ""});
        assertArrayEquals(new String[]{"A", "B"}, row.getHeaders());
        assertEquals("AA", row.get("A"));
        assertEquals("BB", row.get("B"));
        assertEquals("\"AA\",\"BB\",\"junk\",\"\",\"\",\"\"", row.toString());
    }

    @Test
    public void allBlankHeaderRowYieldsNoHeadersButStillSerialises() {
        Row row = new Row(new String[]{"", "  "}, new String[]{"v1", "v2"});
        assertArrayEquals(new String[]{}, row.getHeaders());
        assertEquals("\"v1\",\"v2\"", row.toString());
    }

    @Test
    public void dropBlankHeaderColumnsKeepsHeaderValuePairing() {
        // a blank header mid-row must drop its own value, not shift its neighbour's
        String[] paddedHeaders = {"A", "", "B"};
        Row row = new Row(paddedHeaders, new String[]{"AA", "orphan", "BB"});
        assertArrayEquals(new String[]{"A", "B"}, row.getHeaders());
        assertEquals("AA", row.get("A"));
        assertEquals("BB", row.get("B"));
    }

    @Test
    public void distinguishPaddingFromClearedHeaders() {
        // padding: blank headers over blank values - no orphans
        Row padded = new Row(new String[]{"A", "", "  "}, new String[]{"AA", "", ""});
        assertTrue(padded.getOrphanedValueColumns().isEmpty());
        // cleared header: blank header over real values - reported by original column number
        Row cleared = new Row(new String[]{"A", "", "B"}, new String[]{"AA", "orphan", "BB"});
        assertEquals(List.of(2), cleared.getOrphanedValueColumns());
    }
}
