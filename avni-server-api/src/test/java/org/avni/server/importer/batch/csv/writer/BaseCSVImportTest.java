package org.avni.server.importer.batch.csv.writer;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.util.S;

import java.util.Arrays;

public abstract class BaseCSVImportTest extends AbstractControllerIntegrationTest {
    protected String[] header(String... cells) {
        // Apply trim and unDoubleQuote to simulate what DelimitedLineTokenizer does in production
        return Arrays.stream(cells)
                .map(String::trim)
                .map(S::unDoubleQuote)
                .toArray(String[]::new);
    }

    protected String[] dataRow(String... cells) {
        return cells;
    }

    protected String[] lineage(String ... lineage) {
        return lineage;
    }

    protected String error(String message) {
        return message;
    }

    protected String hasError(String s) {
        return s;
    }

    protected String doesntHaveError(String s) {
        return s;
    }
}
