package org.avni.server.importer.batch.csv.writer.header;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The sample file's three rows are read by position: the server appends sync attribute columns to all three, so each
 * row must be as wide as the header row or those cells land under the wrong column.
 */
public class UsersAndCatchmentsSampleFileTest {
    private List<CSVRecord> sampleRows() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/bulkuploads/sample/usersAndCatchments.csv");
             CSVParser parser = CSVFormat.DEFAULT.parse(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parser.getRecords();
        }
    }

    @Test
    public void headerRowCarriesTheAbhaHealthFacilityColumn() throws Exception {
        CSVRecord header = sampleRows().get(0);
        assertEquals(Arrays.asList(new UsersAndCatchmentsHeaders().getAllHeaders()), header.toList());
        assertTrue(header.toList().contains(UsersAndCatchmentsHeaders.ABHA_HEALTH_FACILITY_ID));
    }

    @Test
    public void everyRowIsAsWideAsTheHeaderRow() throws Exception {
        List<CSVRecord> rows = sampleRows();
        int width = rows.get(0).size();
        for (CSVRecord row : rows) {
            assertEquals("row " + row.getRecordNumber(), width, row.size());
        }
    }
}
