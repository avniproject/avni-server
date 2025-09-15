package org.avni.server.dao;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramEnrolmentRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;
    private TestDataSetupService.TestOrganisationData organisationData;
    private SubjectType subjectTypeWithSyncAttributeBasedSync;
    private Program programForSyncAttributeBasedSync;
    private Concept conceptForAttributeBasedSync;
    private TestDataSetupService.TestCatchmentData catchmentData;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();
        conceptForAttributeBasedSync = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");

        subjectTypeWithSyncAttributeBasedSync = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithSyncAttributeBasedSync")
                        .setName("subjectTypeWithSyncAttributeBasedSync")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(conceptForAttributeBasedSync.getUuid()).build());

        programForSyncAttributeBasedSync = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("programForSyncAttributeBasedSync")
                        .build(),
                subjectTypeWithSyncAttributeBasedSync);
    }

    @Test
    @Transactional
    public void updateSyncAttributesForIndividual_should_update_last_modified_datetime() throws Exception {
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withSubjectTypeSyncSettings().withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        userRepository.save(new UserBuilder(organisationData.getUser2()).withCatchment(catchmentData.getCatchment()).withSubjectTypeSyncSettings().withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        setUser(organisationData.getUser().getUsername());

        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectTypeWithSyncAttributeBasedSync);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectTypeWithSyncAttributeBasedSync, programForSyncAttributeBasedSync);

        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(hasMatchingObs).build());
        Thread.sleep(1);
        setUser(organisationData.getUser2().getUsername());
        programEnrolmentRepository.updateSyncAttributesForIndividual(hasMatchingObs.getId(), hasMatchingObs.getAddressLevel().getId(), conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid(), null);
        ProgramEnrolment reloaded = programEnrolmentRepository.findOne(enrolmentHasMatchingObs.getId());
        assertNotEquals(enrolmentHasMatchingObs.getLastModifiedDateTime(), reloaded.getLastModifiedDateTime());
        assertNotEquals(enrolmentHasMatchingObs.getLastModifiedBy(), reloaded.getLastModifiedBy());
    }

    @Test
    public void syncDisabledShouldBeCopiedFromIndividual() {
        Individual individual = testSubjectService.save(new SubjectBuilder()
                .withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeWithSyncAttributeBasedSync)
                .withLocation(catchmentData.getAddressLevel1())
                .withSyncDisabled(true)
                .build());
        ProgramEnrolment enrolment = new ProgramEnrolmentBuilder()
                .withMandatoryFieldsForNewEntity()
                .setProgram(programForSyncAttributeBasedSync)
                .setIndividual(individual).build();
        assertFalse(enrolment.isSyncDisabled());
        enrolment = programEnrolmentRepository.save(enrolment);
        assertTrue(enrolment.isSyncDisabled());
    }
}
