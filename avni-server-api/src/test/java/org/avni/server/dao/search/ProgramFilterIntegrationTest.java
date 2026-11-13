package org.avni.server.dao.search;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.IndividualSearchService;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.EnrolmentContract;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramFilterIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private IndividualSearchService individualSearchService;

    private SubjectType subjectType;
    private Program programA;
    private Program programB;
    private TestDataSetupService.TestCatchmentData catchment;

    private void setUpOrganisation(String suffix) {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation(suffix);
        setUser(org.getUser());
        catchment = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Person " + suffix).build());
        programA = testProgramService.addProgram(new ProgramBuilder().withName("Programme A " + suffix).build(), subjectType);
        programB = testProgramService.addProgram(new ProgramBuilder().withName("Programme B " + suffix).build(), subjectType);
    }

    private Individual subject(String firstName) {
        return testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(firstName).build());
    }

    private void enrol(Individual individual, Program program, DateTime exitDateTime, boolean voided) {
        ProgramEnrolment enrolment = new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(individual)
                .setProgramExitDateTime(exitDateTime).build();
        enrolment.setVoided(voided);
        testProgramEnrolmentService.save(enrolment);
    }

    private List<Map<String, Object>> search(List<String> programs, boolean includeExited, boolean notEnrolled) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setPrograms(programs);
        request.setIncludeExitedEnrolments(includeExited);
        request.setNotEnrolledInAnyProgram(notEnrolled);
        request.setIncludeVoided(false);
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(100);
        request.setPageElement(page);
        return (List<Map<String, Object>>) individualSearchService.search(request).get("listOfRecords");
    }

    private List<String> uuids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get("uuid")).collect(Collectors.toList());
    }

    private List<String> enrolmentsOf(List<Map<String, Object>> rows, Individual individual) {
        Map<String, Object> row = rows.stream().filter(r -> individual.getUuid().equals(r.get("uuid"))).findFirst().orElseThrow();
        return ((List<EnrolmentContract>) row.get("enrolments")).stream()
                .map(e -> e.getOperationalProgramName() + (e.getProgramExitDateTime() == null ? "" : " exited"))
                .collect(Collectors.toList());
    }

    @Test
    public void notEnrolledIsIgnoredWhenIncludeExitedIsAlsoSet() {
        setUpOrganisation("pfa");
        Individual neverEnrolled = subject("Never");
        Individual activeInA = subject("ActiveA");
        enrol(activeInA, programA, null, false);
        Individual exitedFromA = subject("ExitedA");
        enrol(exitedFromA, programA, DateTime.now().minusDays(1), false);
        Individual exitedFromBActiveInA = subject("ExitedBActiveA");
        enrol(exitedFromBActiveInA, programB, DateTime.now().minusDays(1), false);
        enrol(exitedFromBActiveInA, programA, null, false);

        assertThat(uuids(search(null, true, true))).containsExactlyInAnyOrder(exitedFromA.getUuid(), exitedFromBActiveInA.getUuid());
        assertThat(uuids(search(null, true, false))).containsExactlyInAnyOrder(exitedFromA.getUuid(), exitedFromBActiveInA.getUuid());
        assertThat(uuids(search(Collections.singletonList(programA.getUuid()), true, true)))
                .containsExactlyInAnyOrder(activeInA.getUuid(), exitedFromA.getUuid(), exitedFromBActiveInA.getUuid());
        assertThat(uuids(search(null, false, true))).containsExactly(neverEnrolled.getUuid());
    }

    @Test
    public void resultsListExitedEnrolmentsWithTheirExitDateOnlyWithIncludeExited() {
        setUpOrganisation("pfb");
        Individual activeInA = subject("ActiveA");
        enrol(activeInA, programA, null, false);
        Individual exitedFromA = subject("ExitedA");
        enrol(exitedFromA, programA, DateTime.now().minusDays(1), false);
        enrol(exitedFromA, programB, DateTime.now().minusDays(1), true);
        Individual reEnrolledInA = subject("ReEnrolledA");
        enrol(reEnrolledInA, programA, DateTime.now().minusDays(5), false);
        enrol(reEnrolledInA, programA, null, false);
        Individual deletedFromA = subject("DeletedA");
        enrol(deletedFromA, programA, null, true);
        String a = subjectType.getName();

        List<Map<String, Object>> withoutIncludeExited = search(null, false, false);
        assertThat(enrolmentsOf(withoutIncludeExited, activeInA)).containsExactly(a);
        assertThat(enrolmentsOf(withoutIncludeExited, exitedFromA)).isEmpty();
        assertThat(enrolmentsOf(withoutIncludeExited, reEnrolledInA)).containsExactly(a);
        assertThat(enrolmentsOf(withoutIncludeExited, deletedFromA)).isEmpty();

        List<Map<String, Object>> withIncludeExited = search(Collections.singletonList(programA.getUuid()), true, false);
        assertThat(uuids(withIncludeExited)).containsExactlyInAnyOrder(activeInA.getUuid(), exitedFromA.getUuid(), reEnrolledInA.getUuid());
        assertThat(enrolmentsOf(withIncludeExited, activeInA)).containsExactly(a);
        assertThat(enrolmentsOf(withIncludeExited, exitedFromA)).containsExactly(a + " exited");
        assertThat(enrolmentsOf(withIncludeExited, reEnrolledInA)).containsExactlyInAnyOrder(a, a + " exited");
    }
}
