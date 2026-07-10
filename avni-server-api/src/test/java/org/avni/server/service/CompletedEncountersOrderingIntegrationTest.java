package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.EncounterContract;
import org.avni.server.web.request.ProgramEncounterContract;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class CompletedEncountersOrderingIntegrationTest extends AbstractControllerIntegrationTest {
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
    private EncounterService encounterService;
    @Autowired
    private ProgramEnrolmentService programEnrolmentService;
    @Autowired
    private EncounterRepository encounterRepository;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;
    @Autowired
    private GroupRepository groupRepository;

    private static final DateTime JAN_10 = new DateTime(2024, 1, 10, 0, 0);
    private static final DateTime JAN_12 = new DateTime(2024, 1, 12, 0, 0);
    private static final DateTime FEB_01 = new DateTime(2024, 2, 1, 0, 0);
    private static final DateTime MAR_05 = new DateTime(2024, 3, 5, 0, 0);
    private static final DateTime APR_01 = new DateTime(2024, 4, 1, 0, 0);
    private static final DateTime MAY_01 = new DateTime(2024, 5, 1, 0, 0);
    private static final DateTime JUN_20 = new DateTime(2024, 6, 20, 0, 0);

    private User user;
    private Long addressId;
    private Individual subject;
    private EncounterType generalEncounterType;

    private ProgramEnrolment enrolment;
    private EncounterType programEncounterType;

    @Before
    public void setUp() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        user = organisationData.getUser();
        addressId = catchmentData.getAddressLevel1().getId();

        Group group = organisationData.getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setType(Subject.Person).setName("Person").build());
        generalEncounterType = testEncounterTypeService
                .addGeneralEncounterTypeAndGetFormMapping("General Visit", subjectType).getEncounterType();

        Program program = new Program();
        program.setName("Program");
        program.assignUUID();
        program = testProgramService.addProgram(program, subjectType);
        programEncounterType = testEncounterTypeService
                .addProgramEncounterTypeAndGetFormMapping("Program Visit", subjectType, program).getEncounterType();

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
    }

    private String saveGeneralEncounter(DateTime earliestVisit, DateTime encounter, DateTime cancel) {
        Encounter e = new Encounter();
        e.assignUUID();
        e.setEncounterType(generalEncounterType);
        e.setIndividual(subject);
        e.setAddressId(addressId);
        e.setEarliestVisitDateTime(earliestVisit);
        e.setTiming(encounter, cancel, user);
        e.setObservations(new ObservationCollection());
        e.setCancelObservations(new ObservationCollection());
        encounterRepository.save(e);
        return e.getUuid();
    }

    private String saveProgramEncounter(DateTime earliestVisit, DateTime encounter, DateTime cancel) {
        ProgramEncounter e = new ProgramEncounter();
        e.assignUUID();
        e.setEncounterType(programEncounterType);
        e.setProgramEnrolment(enrolment);
        e.setIndividual(subject);
        e.setAddressId(addressId);
        e.setEarliestVisitDateTime(earliestVisit);
        e.setTiming(encounter, cancel, user);
        e.setObservations(new ObservationCollection());
        e.setCancelObservations(new ObservationCollection());
        programEncounterRepository.save(e);
        return e.getUuid();
    }

    @Test
    public void subjectCompletedEncountersAreOrderedByVisitDateAndClientSortIsIgnored() {
        // Saved oldest-id first so the id-desc tiebreaker is exercised by the two APR_01 unplanned visits.
        String plannedCompleted = saveGeneralEncounter(JAN_10, JAN_12, null);
        String unplannedCancelled = saveGeneralEncounter(null, null, FEB_01);
        String plannedCancelledRecentCancel = saveGeneralEncounter(MAR_05, null, JUN_20);
        String unplannedCompletedAprLowerId = saveGeneralEncounter(null, APR_01, null);
        String unplannedCompletedAprHigherId = saveGeneralEncounter(null, APR_01, null);
        String unplannedCompletedMay = saveGeneralEncounter(null, MAY_01, null);

        // A client sort is deliberately supplied; the endpoint must discard it and apply the visit-date ordering.
        Page<EncounterContract> page = encounterService.getAllCompletedEncounters(subject.getUuid(), null, null, null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "encounterDateTime")));
        List<String> uuids = page.getContent().stream().map(EncounterContract::getUuid).collect(Collectors.toList());

        assertEquals(List.of(
                unplannedCompletedMay,           // COALESCE = May 1
                unplannedCompletedAprHigherId,   // COALESCE = Apr 1, higher id first
                unplannedCompletedAprLowerId,    // COALESCE = Apr 1
                plannedCancelledRecentCancel,    // COALESCE = earliest visit Mar 5, not the recent cancel date
                unplannedCancelled,              // COALESCE = cancel Feb 1
                plannedCompleted                 // COALESCE = earliest visit Jan 10
        ), uuids);

        // The recently-cancelled visit must sort at its old scheduled date, never floated to the top.
        assertNotEquals(plannedCancelledRecentCancel, uuids.get(0));
        assertEquals(3, uuids.indexOf(plannedCancelledRecentCancel));
    }

    @Test
    public void subjectCompletedEncountersPaginationIsStableAcrossPages() {
        String plannedCompleted = saveGeneralEncounter(JAN_10, JAN_12, null);
        String unplannedCancelled = saveGeneralEncounter(null, null, FEB_01);
        String plannedCancelledRecentCancel = saveGeneralEncounter(MAR_05, null, JUN_20);
        String unplannedCompletedAprLowerId = saveGeneralEncounter(null, APR_01, null);
        String unplannedCompletedAprHigherId = saveGeneralEncounter(null, APR_01, null);
        String unplannedCompletedMay = saveGeneralEncounter(null, MAY_01, null);

        List<String> expected = List.of(unplannedCompletedMay, unplannedCompletedAprHigherId, unplannedCompletedAprLowerId,
                plannedCancelledRecentCancel, unplannedCancelled, plannedCompleted);

        List<String> paged = new java.util.ArrayList<>();
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            Page<EncounterContract> page = encounterService.getAllCompletedEncounters(subject.getUuid(), null, null, null,
                    PageRequest.of(pageNumber, 2));
            assertEquals(6, page.getTotalElements());
            assertEquals(3, page.getTotalPages());
            page.getContent().forEach(e -> paged.add(e.getUuid()));
        }
        assertEquals(expected, paged);
    }

    @Test
    public void programCompletedEncountersAreOrderedByVisitDate() {
        String plannedCompleted = saveProgramEncounter(JAN_10, JAN_12, null);
        String unplannedCancelled = saveProgramEncounter(null, null, FEB_01);
        String plannedCancelledRecentCancel = saveProgramEncounter(MAR_05, null, JUN_20);
        String unplannedCompletedAprLowerId = saveProgramEncounter(null, APR_01, null);
        String unplannedCompletedAprHigherId = saveProgramEncounter(null, APR_01, null);
        String unplannedCompletedMay = saveProgramEncounter(null, MAY_01, null);

        Page<ProgramEncounterContract> page = programEnrolmentService.getAllCompletedEncounters(enrolment.getUuid(), null, null, null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "encounterDateTime")));
        List<String> uuids = page.getContent().stream().map(ProgramEncounterContract::getUuid).collect(Collectors.toList());

        assertEquals(List.of(
                unplannedCompletedMay,
                unplannedCompletedAprHigherId,
                unplannedCompletedAprLowerId,
                plannedCancelledRecentCancel,
                unplannedCancelled,
                plannedCompleted
        ), uuids);

        assertNotEquals(plannedCancelledRecentCancel, uuids.get(0));
        assertEquals(3, uuids.indexOf(plannedCancelledRecentCancel));
    }
}
