package org.avni.server.importer.batch.csv;

import org.avni.server.importer.batch.model.Row;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CsvRowMapperTest {
    @Test
    public void paddedColumnsMapCleanly() {
        Row row = BatchConfiguration.mapRow(new String[]{"A", "B", "", ""}, new String[]{"AA", "BB", "", ""});
        assertArrayEquals(new String[]{"A", "B"}, row.getHeaders());
    }

    @Test
    public void clearedHeaderIsRejectedWithColumnNumbers() {
        try {
            BatchConfiguration.mapRow(new String[]{"A", "", "B"}, new String[]{"AA", "orphan", "BB"});
            fail();
        } catch (RuntimeException e) {
            assertEquals("Column(s) 2 have values but no header", e.getMessage());
        }
    }

    @Test
    public void valueBeyondHeaderRowIsRejected() {
        try {
            BatchConfiguration.mapRow(new String[]{"A", "B"}, new String[]{"AA", "BB", "stray"});
            fail();
        } catch (RuntimeException e) {
            assertEquals("Column(s) 3 have values but no header", e.getMessage());
        }
    }
}
