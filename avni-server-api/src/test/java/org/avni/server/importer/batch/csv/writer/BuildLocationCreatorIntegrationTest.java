package org.avni.server.importer.batch.csv.writer;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.avni.server.importer.batch.csv.writer.BulkLocationCreator.LocationTypesHeaderError;
import static org.avni.server.importer.batch.csv.writer.BulkLocationCreator.UnknownHeadersErrorMessage;
import static org.junit.Assert.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BuildLocationCreatorIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private BulkLocationCreator bulkLocationCreator;
    @Autowired
    private LocationHierarchyService locationHierarchyService;
    @Autowired
    private LocationRepository addressLevelRepository;
    private String hierarchy;

    @Test
    @Ignore
    public void shouldCreate() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).withUuid(UUID.randomUUID()).build();
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(block, district, state));

        setUser(organisationData.getUser().getUsername());
        Map<String, String> hierarchies = locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        hierarchy = String.join(".", hierarchies.keySet());

        String[] validHeaders = new String[]{"State", "District", "Block", "GPS coordinates"};
        success(validHeaders, 3, "Bihar", "Vaishali", "Mahua", "23.45,43.85");
        success(validHeaders, 1, " Bihar", " Vaishali ", " Jamui ", "23.20,43.85");
        success(validHeaders, 1, " Bihar", " Darbhanga ");
        success(validHeaders, 1, " Bihar", " Aara ", " ", "24.20,43.85");
        success(new String[]{" State ", "District", " Block", " GPS coordinates"}, 1, " Bihar", " Chapra ", " ", "24.20,43.85");

        success(validHeaders, 1, " bihar", " VAISHALI ", " Tarora ", "23.20,43.85");

        failure(new String[]{"State1", "District", "Block", "GPS coordinates"}, LocationTypesHeaderError, "Bihar", "Vaishali", "Nijma", "23.45,43.85");
        failure(new String[]{"State", "District2", "Block", "GPS coordinates"}, LocationTypesHeaderError, "Bihar", "Vaishali", "Nijma", "23.45,43.85");
        failure(new String[]{"State", "District", "Block", "GPS"}, UnknownHeadersErrorMessage, "Bihar", "Vaishali", "Nijma", "23.45,43.85");
        failure(validHeaders, LocationTypesHeaderError, " ", " ", "Sori", "23.45,43.85");

        successNoOp(validHeaders, "Ex: state 1", "Ex: distr 1", "Ex: blo 1", "Ex. 23.45,43.85");
        successNoOp(validHeaders, " Ex: state 1", "Ex: distr 1 ", "Ex: blo 1", " Ex. 23.45,43.85 ");
    }

    private void successNoOp(String[] headers, String ... additionalHeaders) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, additionalHeaders)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }

    private void success(String[] headers, int numberOfNewLocations, String... cells) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before + numberOfNewLocations, after);
    }

    private void failure(String[] headers, String errorMessage, String... cells) {
        long before = addressLevelRepository.count();
        try {
            bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
        } catch (Exception e) {
            assertEquals(errorMessage, e.getMessage());
        }
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }
}
