package org.avni.server.util;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CsvCellTest {
    @Test
    public void quotedDoublesAQuoteInsideTheValue() {
        assertEquals("\"GHS \"\"Wadagera\"\"\"", CsvCell.quoted("GHS \"Wadagera\""));
    }

    @Test
    public void quotedLeavesACommaAloneBecauseTheQuotesAlreadyHoldIt() {
        assertEquals("\"Devi, Sunita\"", CsvCell.quoted("Devi, Sunita"));
    }

    @Test
    public void quotedWritesAnEmptyCellRatherThanTheWordNull() {
        assertEquals("\"\"", CsvCell.quoted(null));
    }

    @Test
    public void quoteIfNeededLeavesAnOrdinaryValueExactlyAsItWas() {
        assertEquals("ST1_uuid", CsvCell.quoteIfNeeded("ST1_uuid", false));
        assertEquals("WASH Audit_1.id", CsvCell.quoteIfNeeded("WASH Audit_1.id", false));
    }

    @Test
    public void quoteIfNeededQuotesWhatWouldOtherwiseSplitTheRow() {
        assertEquals("\"School, Govt_uuid\"", CsvCell.quoteIfNeeded("School, Govt_uuid", false));
        assertEquals("\"Child's \"\"nickname\"\"\"", CsvCell.quoteIfNeeded("Child's \"nickname\"", false));
        assertEquals("\"two\nlines\"", CsvCell.quoteIfNeeded("two\nlines", false));
    }

    @Test
    public void quoteIfNeededAlwaysQuotesWhenAskedSoExistingOutputIsUnchanged() {
        assertEquals("\"State\"", CsvCell.quoteIfNeeded("State", true));
        assertEquals("\"\"", CsvCell.quoteIfNeeded(null, true));
    }
}
