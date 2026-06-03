package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.ProgramEncounterRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class RegistrationDecisionSyncAttributePropagationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private ProgramEncounterService programEncounterService;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;

    private Concept syncConcept;
    private SubjectType subjectType;
    private Program program;
    private EncounterType encounterType;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private TestDataSetupService.TestOrganisationData organisationData;

    @Before
    public void setup() {
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();

        TestDataSetupService.TestSyncAttributeBasedSubjectTypeData syncData = testDataSetupService.setupSubjectTypeWithSyncAttributes();
        subjectType = syncData.getSubjectType();
        syncConcept = syncData.getSyncConcept();

        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType);

        program = new Program();
        program.setName("TestProgram");
        program.assignUUID();
        program = testProgramService.addProgram(program, subjectType);

        encounterType = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping(
                "TestProgramEncounter", subjectType, program).getEncounterType();

        setUser(organisationData.getUser().getUsername());
    }

    @Test
    public void registrationDecisions_should_propagate_sync_attributes_to_child_entities() throws Exception {
        String answer1Uuid = syncConcept.getAnswerConcept("Answer 1").getUuid();
        String answer2Uuid = syncConcept.getAnswerConcept("Answer 2").getUuid();

        Individual individual = testSubjectService.save(
                new SubjectBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withSubjectType(subjectType)
                        .withLocation(catchmentData.getAddressLevel1())
                        .withObservations(ObservationCollectionBuilder.withOneObservation(syncConcept, answer1Uuid))
                        .build());
        assertEquals(answer1Uuid, individual.getSyncConcept1Value());

        ProgramEnrolment enrolment = testProgramEnrolmentService.save(
                new ProgramEnrolmentBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .setProgram(program)
                        .setIndividual(individual)
                        .setObservations(new ObservationCollection())
                        .build());
        assertEquals(answer1Uuid, enrolment.getSyncConcept1Value());

        ProgramEncounter existingEncounter = new ProgramEncounter();
        existingEncounter.assignUUID();
        existingEncounter.setProgramEnrolment(enrolment);
        existingEncounter.setEncounterType(encounterType);
        existingEncounter.setEncounterDateTime(DateTime.now().minusDays(10), organisationData.getUser());
        existingEncounter.setObservations(new ObservationCollection());
        existingEncounter.setCancelObservations(new ObservationCollection());
        existingEncounter.setIndividual(individual);
        existingEncounter.setAddressId(catchmentData.getAddressLevel1().getId());
        existingEncounter.setSyncConcept1Value(answer1Uuid);
        programEncounterRepository.save(existingEncounter);
        assertEquals(answer1Uuid, existingEncounter.getSyncConcept1Value());

        ProgramEncounterRequest request = new ProgramEncounterRequest();
        request.setUuid(UUID.randomUUID().toString());
        request.setProgramEnrolmentUUID(enrolment.getUuid());
        request.setEncounterTypeUUID(encounterType.getUuid());
        request.setEncounterDateTime(DateTime.now());
        request.setObservations(Collections.emptyList());
        request.setCancelObservations(Collections.emptyList());

        Decision decision = new Decision();
        decision.setName(syncConcept.getName());
        decision.setValue("Answer 2");
        Decisions decisions = Decisions.nullObject();
        decisions.setRegistrationDecisions(Collections.singletonList(decision));
        request.setDecisions(decisions);

        programEncounterService.saveProgramEncounter(request);

        assertEquals(answer2Uuid, individual.getObservations().getObjectAsSingleStringValue(syncConcept.getUuid()));

        assertEquals("Existing ProgramEncounter sync_concept_1_value should be propagated",
                answer2Uuid, programEncounterRepository.findByUuid(existingEncounter.getUuid()).getSyncConcept1Value());

        assertEquals("ProgramEnrolment sync_concept_1_value should be propagated",
                answer2Uuid, programEnrolmentRepository.findByUuid(enrolment.getUuid()).getSyncConcept1Value());
    }
}
