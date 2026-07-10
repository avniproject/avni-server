package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.ProgramEncounterContract;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ProgramCompletedEncountersFilterIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private ProgramEnrolmentService programEnrolmentService;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;
    @Autowired
    private GroupRepository groupRepository;

    private static final DateTime VISIT_DATE = new DateTime(2024, 4, 1, 0, 0);

    private TestDataSetupService.TestOrganisationData organisationData;
    private User user;
    private Long addressId;
    private Individual subject;
    private ProgramEnrolment enrolment;
    private EncounterType typeA;
    private EncounterType typeB;
    private EncounterType typeC;

    @Before
    public void setUp() {
        organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        user = organisationData.getUser();
        addressId = catchmentData.getAddressLevel1().getId();

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setType(Subject.Person).setName("Person").build());

        Program program = new Program();
        program.setName("Program");
        program.assignUUID();
        program = testProgramService.addProgram(program, subjectType);

        typeA = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Visit A", subjectType, program).getEncounterType();
        typeB = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Visit B", subjectType, program).getEncounterType();
        typeC = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Visit C", subjectType, program).getEncounterType();

        setUser(organisationData.getUser().getUsername());

        subject = testSubjectService.save(new SubjectBuilder()
                .withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1())
                .build());

        enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder()
                .withMandatoryFieldsForNewEntity()
                .setProgram(program)
                .setIndividual(subject)
                .setObservations(new ObservationCollection())
                .build());

        // Two visits of type A, one each of B and C — four completed visits in total.
        saveProgramEncounter(typeA);
        saveProgramEncounter(typeA);
        saveProgramEncounter(typeB);
        saveProgramEncounter(typeC);
    }

    private void saveProgramEncounter(EncounterType encounterType) {
        ProgramEncounter e = new ProgramEncounter();
        e.assignUUID();
        e.setEncounterType(encounterType);
        e.setProgramEnrolment(enrolment);
        e.setIndividual(subject);
        e.setAddressId(addressId);
        e.setTiming(VISIT_DATE, null, user);
        e.setObservations(new ObservationCollection());
        e.setCancelObservations(new ObservationCollection());
        programEncounterRepository.save(e);
    }

    private void giveUserAllPrivileges() {
        Group group = organisationData.getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);
    }

    private List<String> completedEncounterTypeUuids(String encounterTypeUuids) {
        Page<ProgramEncounterContract> page = programEnrolmentService.getAllCompletedEncounters(
                enrolment.getUuid(), encounterTypeUuids, null, null, PageRequest.of(0, 50));
        return page.getContent().stream()
                .map(c -> c.getEncounterType().getUuid())
                .collect(Collectors.toList());
    }

    @Test
    public void noEncounterTypeUuidsReturnsAllCompletedVisits() {
        assertEquals(4, completedEncounterTypeUuids(null).size());
    }

    @Test
    public void singleEncounterTypeNarrowsToThatTypeForOrgWithoutGroupPrivileges() {
        // No group privileges configured (the previous boolean privilege filter returned false here,
        // which silently widened the result to all types).
        List<String> types = completedEncounterTypeUuids(typeA.getUuid());
        assertEquals(2, types.size());
        assertTrue(types.stream().allMatch(uuid -> uuid.equals(typeA.getUuid())));
    }

    @Test
    public void multipleEncounterTypesNarrowToThoseTypes() {
        List<String> types = completedEncounterTypeUuids(typeA.getUuid() + "," + typeB.getUuid());
        assertEquals(3, types.size());
        assertTrue(types.stream().noneMatch(uuid -> uuid.equals(typeC.getUuid())));
    }

    @Test
    public void singleEncounterTypeNarrowsForFullPrivilegeUser() {
        giveUserAllPrivileges();
        List<String> types = completedEncounterTypeUuids(typeA.getUuid());
        assertEquals(2, types.size());
        assertTrue(types.stream().allMatch(uuid -> uuid.equals(typeA.getUuid())));
    }
}
