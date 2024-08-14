package org.avni.server.importer.batch.csv.writer;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ImportLocationsConstants;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class LocationWriterIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private LocationWriter locationWriter;
    @Autowired
    private ImportService importService;
    @Autowired
    private LocationHierarchyService locationHierarchyService;

    @Test
    @Ignore
    public void shouldCreate() throws IOException {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        setUser(organisationData.getUser().getUsername());

        String locationHierarchy = getLocationHierarchy();
        String[] headers = getHeader(locationHierarchy);

//        Row row = new Row();
        locationWriter.write(new ArrayList<>());
        locationWriter.write(new ArrayList<>());
    }

    private String[] getHeader(String locationHierarchy) throws IOException {
        String locationsSampleFile = importService.getLocationsSampleFile(LocationWriter.LocationUploadMode.CREATE, locationHierarchy);
        BufferedReader bufferedReader = new BufferedReader(new StringReader(locationsSampleFile));
        return new String[]{bufferedReader.readLine(), bufferedReader.readLine()};
    }

    private String getLocationHierarchy() {
        HashMap<String, String> locationHierarchyNames = locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        Set<Map.Entry<String, String>> entries = locationHierarchyNames.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            return entry.getKey();
        }
        return null;
    }
}
