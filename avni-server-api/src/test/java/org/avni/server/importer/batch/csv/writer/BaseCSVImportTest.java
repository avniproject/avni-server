package org.avni.server.importer.batch.csv.writer;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.AddressLevel;

import static org.junit.Assert.assertNotNull;

public abstract class BaseCSVImportTest extends AbstractControllerIntegrationTest {
    protected String[] header(String... cells) {
        return cells;
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
}
