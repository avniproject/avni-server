package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormRepository;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * avni-client#1635: names of subject types, programmes and encounter types are saved without leading or trailing
 * spaces, whichever way they reach the server, and " X " counts as a duplicate of an existing "X".
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = {"/test-data.sql"})
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class EntityTypeNameTrimIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private EncounterTypeRepository encounterTypeRepository;
    @Autowired
    private OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    @Autowired
    private FormRepository formRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void webAppSaveTrimsTheNamesAndTreatsAPaddedNameAsADuplicate() {
        String subjectTypeUuid = UUID.randomUUID().toString();
        post("/web/subjectType", Map.of("uuid", subjectTypeUuid, "name", "  Trim Person  ", "type", "Person", "active", true, "locationTypeUUIDs", List.of()));
        assertEquals("Trim Person", subjectTypeRepository.findByUuid(subjectTypeUuid).getName());
        assertNotNull(operationalSubjectTypeRepository.findByNameIgnoreCase("Trim Person"));
        assertNotNull(formRepository.findByName("Trim Person Registration"));

        String programUuid = UUID.randomUUID().toString();
        post("/web/program", Map.of("uuid", programUuid, "name", " Trim Malaria ", "colour", "#ff0000", "active", true, "subjectTypeUuid", subjectTypeUuid));
        assertEquals("Trim Malaria", programRepository.findByUuid(programUuid).getName());
        assertNotNull(operationalProgramRepository.findByNameIgnoreCase("Trim Malaria"));

        String encounterTypeUuid = UUID.randomUUID().toString();
        post("/web/encounterType", Map.of("uuid", encounterTypeUuid, "name", "Trim Visit  ", "active", true, "subjectTypeUuid", subjectTypeUuid));
        assertEquals("Trim Visit", encounterTypeRepository.findByUuid(encounterTypeUuid).getName());
        assertNotNull(operationalEncounterTypeRepository.findByNameIgnoreCase("Trim Visit"));

        assertRefusedAsDuplicate("/web/subjectType", Map.of("name", " trim person", "type", "Person", "active", true, "locationTypeUUIDs", List.of()));
        assertRefusedAsDuplicate("/web/program", Map.of("name", "Trim Malaria ", "colour", "#ff0000", "active", true, "subjectTypeUuid", subjectTypeUuid));
        assertRefusedAsDuplicate("/web/encounterType", Map.of("name", " Trim Visit", "active", true, "subjectTypeUuid", subjectTypeUuid));
    }

    private void assertRefusedAsDuplicate(String path, Object json) {
        ResponseEntity<String> response = template.postForEntity(path, json, String.class);
        assertEquals(response.getBody(), 400, response.getStatusCode().value());
        assertTrue(response.getBody(), response.getBody().contains("already exists"));
    }

    @Test
    public void bundleUploadTrimsTheNames() {
        String subjectTypeUuid = UUID.randomUUID().toString();
        post("/subjectTypes", List.of(Map.of("uuid", subjectTypeUuid, "name", " Bundle Person ", "type", "Person", "active", true)));
        assertEquals("Bundle Person", subjectTypeRepository.findByUuid(subjectTypeUuid).getName());

        String programUuid = UUID.randomUUID().toString();
        post("/programs", List.of(Map.of("uuid", programUuid, "name", "Bundle Malaria  ", "colour", "#ff0000", "active", true)));
        assertEquals("Bundle Malaria", programRepository.findByUuid(programUuid).getName());

        String encounterTypeUuid = UUID.randomUUID().toString();
        post("/encounterTypes", List.of(Map.of("uuid", encounterTypeUuid, "name", "  Bundle Visit", "immutable", false)));
        assertEquals("Bundle Visit", encounterTypeRepository.findByUuid(encounterTypeUuid).getName());

        post("/operationalPrograms", Map.of("operationalPrograms", List.of(Map.of("uuid", UUID.randomUUID().toString(), "name", " Bundle Malaria Op ", "program", Map.of("uuid", programUuid)))));
        assertNotNull(operationalProgramRepository.findByNameIgnoreCase("Bundle Malaria Op"));
    }
}
