package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.ProgramEncounter;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramEncounterControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;

    private final String PROGRAM_ENCOUNTER_UUID = "fbfd4ce8-b03b-45b9-b869-1ef8a0d9651e";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void createNewEncounter() {
        try {
            Object json = mapper.readValue(this.getClass().getResource("/ref/programEncounters/newProgramEncounter.json"), Object.class);
            post("/programEncounters", json);

            ProgramEncounter newEncounter = programEncounterRepository.findByUuid(PROGRAM_ENCOUNTER_UUID);
            assertThat(newEncounter).isNotNull();

        } catch (IOException e) {
            Assert.fail();
        }
    }
}