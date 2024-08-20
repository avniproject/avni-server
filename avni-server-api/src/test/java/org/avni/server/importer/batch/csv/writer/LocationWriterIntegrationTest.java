package org.avni.server.importer.batch.csv.writer;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.HashMap;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class LocationWriterIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private LocationWriter locationWriter;

    @Test
    @Ignore
    public void shouldCreate() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();

        testDataSetupService.setupLocationHierarchy(
                new HashMap<Integer, String>() {
                    {
                        put(4, "State");
                        put(3, "District");
                        put(2, "Block");
                    }
                },
                new HashMap<String, String>() {{
                    put("State", "Bihar");
                    put("District", "Vaishali");
                    put("Block", "Mahua");
                }}
        );
        setUser(organisationData.getUser().getUsername());
        String[] validHeaders = new String[]{"State", "District", "Block", "GPS coordinates"};
        success(validHeaders, "Bihar", "Vaishali", "Aiana", "23.45,43.85");
    }

    private void success(String[] headers, String... cells) {
        locationWriter.write(Collections.singletonList(new Row(headers, cells)));
    }
}
