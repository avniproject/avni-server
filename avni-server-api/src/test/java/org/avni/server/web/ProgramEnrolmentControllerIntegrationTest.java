package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramEnrolmentControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;

    @Autowired
    private IndividualRepository individualRepository;

    @Autowired
    private ProgramRepository programRepository;

    private final String PROGRAM_ENROL_UUID = "0a1bf764-4576-4d71-a9de-25895a113e81";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void createNewProgramEnrolment() {
        try {
            Object json = mapper.readValue(this.getClass().getResource("/ref/enrolment/newProgEnrol.json"), Object.class);
            post("/programEnrolments", json);

            ProgramEnrolment newProgEnrolment = programEnrolmentRepository.findByUuid(PROGRAM_ENROL_UUID);
            assertThat(newProgEnrolment).isNotNull();

        } catch (IOException e) {
            Assert.fail();
        }
    }

    /**
     * TODO Remove this after finding root cause of overwrite of observations to empty
     */
    @Test
    public void ensureUpdateExistingIndividualWithEmptyObsFails() {
        try {
            Object json = mapper.readValue(this.getClass().getResource("/ref/enrolment/NonEmptyObsProgEnrolment.json"), Object.class);
            post("/programEnrolments", json);

            ProgramEnrolment newProgEnrolment = programEnrolmentRepository.findByUuid(PROGRAM_ENROL_UUID);
            assertThat(newProgEnrolment).isNotNull();

            json = mapper.readValue(this.getClass().getResource("/ref/enrolment/newProgEnrol.json"), Object.class);
            post("/programEnrolments", json);

            ProgramEnrolment updatedProgEnrolment = programEnrolmentRepository.findByUuid(PROGRAM_ENROL_UUID);
            assertThat(updatedProgEnrolment).isNotNull();
            assertThat(updatedProgEnrolment.getObservations().size()).isGreaterThan(0);
            assertThat(updatedProgEnrolment.getObservations().size()).isEqualTo(newProgEnrolment.getObservations().size());
            assertThat(updatedProgEnrolment.getLastModifiedDateTime()).isNotEqualTo(newProgEnrolment.getLastModifiedDateTime());

        } catch (IOException e) {
            Assert.fail();
        }
    }

    @Test
    public void getProgramEnrolmentShouldNotUpdateIndividualLastModified() {
        ProgramEnrolment enrolment = programEnrolmentRepository.findByUuid("ba0a3b91-2d4d-446b-a3ee-d56e7edaf3d3");
        assertThat(enrolment).isNotNull();

        Individual individual = enrolment.getIndividual();
        assertThat(individual.getSubjectLocation()).isNotNull();
        DateTime lastModifiedBefore = individual.getLastModifiedDateTime();

        Program program = programRepository.findByName("Diabetes");
        assertThat(program).isNotNull();

        String url = base.toString() + "/programEnrolment?lastModifiedDateTime=1900-01-01T00:00:00.000Z"
                + "&now=" + DateTime.now().toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                + "&programUuid=" + program.getUuid()
                + "&size=100&page=0";
        ResponseEntity<String> response = template.getForEntity(url, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Individual individualAfter = individualRepository.findByUuid(individual.getUuid());
        assertThat(individualAfter.getLastModifiedDateTime()).isEqualTo(lastModifiedBefore);
    }
}
