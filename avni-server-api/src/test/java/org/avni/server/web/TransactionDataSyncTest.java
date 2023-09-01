package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.TestUserSyncSettingsBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.*;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.IndividualService;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class TransactionDataSyncTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;
    @Autowired
    private IndividualController individualController;
    @Autowired
    private ProgramEnrolmentController programEnrolmentController;
    @Autowired
    private GroupSubjectController groupSubjectController;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private IndividualService subjectService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;

    @Test
    public void sync() throws Exception {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        // Metadata for Catchment based sync
        SubjectType st_CatchmentBasedSync = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_CatchmentBasedSync").setName("st_CatchmentBasedSync").build());
        SubjectType st_group_CatchmentBasedSync = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setGroup(true).setMandatoryFieldsForNewEntity().setUuid("st_group_CatchmentBasedSync").setName("st_group_CatchmentBasedSync").build());
        Program p_CatchmentBasedSync = testProgramService.addProgram(new ProgramBuilder().withName("st_CatchmentBasedSync").build(), st_CatchmentBasedSync);
        GroupRole groupRoleForCatchment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(st_group_CatchmentBasedSync).withMemberSubjectType(st_CatchmentBasedSync).build());

        // Metadata for no access
        SubjectType st_NoAccess = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_NoAccess").setName("st_NoAccess").build());
        Program p_NoAccess = testProgramService.addProgram(new ProgramBuilder().withName("st_NoAccess").build(), st_NoAccess);

        // Metadata for sync attribute based sync
        Concept concept = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        SubjectType st_SyncAttributes = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_SyncAttributes").setName("st_SyncAttributes")
                        .setSyncRegistrationConcept1Usable(true).setSyncRegistrationConcept1(concept.getUuid()).build());
        SubjectType st_group_SyncAttributes = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_group_SyncAttributes").setName("st_group_SyncAttributes").setSyncRegistrationConcept1Usable(true).setSyncRegistrationConcept1(concept.getUuid()).build());
        Program p_SyncAttributes = testProgramService.addProgram(new ProgramBuilder().withName("st_SyncAttributes").build(), st_SyncAttributes);
        GroupRole groupRoleForSyncAttribute = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(st_group_SyncAttributes).withMemberSubjectType(st_SyncAttributes).build());

        // Metadata for direct assignment based sync
        SubjectType st_DirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        SubjectType st_group_DirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        Program p_DirectAssignment = testProgramService.addProgram(new ProgramBuilder().withName("st_DirectAssignment").build(), st_DirectAssignment);
        GroupRole groupRoleForDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(st_group_DirectAssignment).withMemberSubjectType(st_DirectAssignment).build());

        testGroupService.giveViewSubjectPrivilegeTo(testOrganisationData.getGroup(), st_CatchmentBasedSync, st_DirectAssignment, st_SyncAttributes, st_group_SyncAttributes, st_group_CatchmentBasedSync, st_group_DirectAssignment);
        testGroupService.giveViewProgramPrivilegeTo(testOrganisationData.getGroup(), st_CatchmentBasedSync, p_CatchmentBasedSync);
        testGroupService.giveViewProgramPrivilegeTo(testOrganisationData.getGroup(), st_DirectAssignment, p_DirectAssignment);
        testGroupService.giveViewProgramPrivilegeTo(testOrganisationData.getGroup(), st_SyncAttributes, p_SyncAttributes);

        // Catchment tx entities
        Individual inTheCatchment = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(testCatchmentData.getAddressLevel1()).build());
        subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_group_CatchmentBasedSync).withLocation(testCatchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentInTheCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_CatchmentBasedSync).setIndividual(inTheCatchment).build());
        Individual groupInCatchment = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(testCatchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInCatchment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForCatchment).withMember(inTheCatchment).withGroup(groupInCatchment).build());

        // Outside catchment tx entities
        Individual outsideCatchment = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(testCatchmentData.getAddressLevel2()).build());
        ProgramEnrolment enrolmentOutsideCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_CatchmentBasedSync).setIndividual(outsideCatchment).build());
        Individual noAccessToSubjectType = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_NoAccess).withLocation(testCatchmentData.getAddressLevel1()).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_NoAccess).setIndividual(noAccessToSubjectType).build());

        // Sync attributes tx entities
        Individual hasMatchingObs = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(testCatchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(hasMatchingObs).build());
        Individual obsNotMatching = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(testCatchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 2").getUuid())).build());
        ProgramEnrolment enrolmentObsNotMatching = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(obsNotMatching).build());
        Individual obsNotPresent = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(testCatchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentObsNotPresent = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(obsNotPresent).build());

        // Direct assignment tx entities
        Individual assigned = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_DirectAssignment).withLocation(testCatchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_DirectAssignment).setIndividual(assigned).build());
        Individual notAssigned = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_DirectAssignment).withLocation(testCatchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentNotAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_DirectAssignment).setIndividual(notAssigned).build());

        userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(testOrganisationData.getUser()).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(st_SyncAttributes.getUuid()).setSyncConcept1(concept.getUuid()).setSyncConcept1Values(Collections.singletonList(concept.getAnswerConcept("Answer 1").getUuid())).build();
        User user = userRepository.save(new UserBuilder(testOrganisationData.getUser()).withCatchment(testCatchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());

        List syncDetails = getSyncDetails();

        // Check catchment based sync strategy
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_NoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_CatchmentBasedSync.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.GroupSubject.name(), st_group_CatchmentBasedSync.getUuid())));
        List<Individual> subjects = getSubjects(st_CatchmentBasedSync);
        assertTrue(hasEntity(inTheCatchment, subjects));
        assertFalse(hasEntity(outsideCatchment, subjects));
        List<ProgramEnrolment> enrolments = getEnrolments(p_CatchmentBasedSync);
        assertTrue(hasEntity(enrolmentInTheCatchment, enrolments));
        assertFalse(hasEntity(enrolmentOutsideCatchment, enrolments));
        List<GroupSubject> groupSubjects = getGroupSubjects(st_group_CatchmentBasedSync);
        assertTrue(hasEntity(groupSubjectInCatchment, groupSubjects));

        // Check for no access
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), p_NoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), p_CatchmentBasedSync.getUuid())));

        // CHECK FOR SYNC ATTRIBUTES BASED SYNC STRATEGY
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_SyncAttributes.getUuid())));
        subjects = getSubjects(st_SyncAttributes);
        assertTrue(hasEntity(hasMatchingObs, subjects));
        assertFalse(hasEntity(obsNotMatching, subjects));
        assertFalse(hasEntity(obsNotPresent, subjects));
        enrolments = getEnrolments(p_SyncAttributes);
        assertTrue(hasEntity(enrolmentHasMatchingObs, enrolments));
        assertFalse(hasEntity(enrolmentObsNotMatching, enrolments));
        assertFalse(hasEntity(enrolmentObsNotPresent, enrolments));
        // Group Subject
        Individual groupHasMatchingObs = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_group_SyncAttributes).withLocation(testCatchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 1").getUuid())).build());
        GroupSubject groupSubjectInObsMatching = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForSyncAttribute).withMember(hasMatchingObs).withGroup(groupHasMatchingObs).build());
        userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(st_group_SyncAttributes.getUuid()).setSyncConcept1(concept.getUuid()).setSyncConcept1Values(Collections.singletonList(concept.getAnswerConcept("Answer 1").getUuid())).build();
        user = userRepository.save(new UserBuilder(user).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());
        groupSubjects = getGroupSubjects(st_group_SyncAttributes);
        assertTrue(hasEntity(groupSubjectInObsMatching, groupSubjects));

        // CHECK FOR DIRECT ASSIGNMENT BASED SYNC STRATEGY
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_DirectAssignment.getUuid())));
        subjects = getSubjects(st_DirectAssignment);
        assertTrue(hasEntity(assigned, subjects));
        assertFalse(hasEntity(notAssigned, subjects));
        enrolments = getEnrolments(p_DirectAssignment);
        assertTrue(hasEntity(enrolmentAssigned, enrolments));
        assertFalse(hasEntity(enrolmentNotAssigned, enrolments));
        // Standalone Subject
        DateTime beforeSave = takeTimeBeforeOperation();
        userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(notAssigned).withUser(user).build());
        subjectService.getIndividualRepository().save(notAssigned); // required because test is not running in transaction to modified individual is not saved yet
        Individual assignedNow = notAssigned;
        assertTrue(hasEntity(assignedNow, getSubjects(st_DirectAssignment, beforeSave)));
        ProgramEnrolment enrolmentAssignedNow = enrolmentNotAssigned;
        assertTrue(hasEntity(enrolmentAssignedNow, getEnrolments(p_DirectAssignment, beforeSave)));
        //// modify after assignment
        assignedNow.setFirstName(UUID.randomUUID().toString());
        beforeSave = takeTimeBeforeOperation();
        subjectService.getIndividualRepository().save(assignedNow);
        assertTrue(hasEntity(assignedNow, getSubjects(st_DirectAssignment, beforeSave)));
        // Group Subject
        Individual assignedGroupSubject = subjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_group_DirectAssignment).withLocation(testCatchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInDirectAssignment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForCatchment).withMember(assigned).withGroup(assignedGroupSubject).build());
        userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assignedGroupSubject).withUser(user).build());
        groupSubjects = getGroupSubjects(st_group_CatchmentBasedSync);
        assertTrue(hasEntity(groupSubjectInDirectAssignment, groupSubjects));
    }

    private DateTime takeTimeBeforeOperation() throws InterruptedException {
        DateTime beforeSave;
        beforeSave = DateTime.now();
        Thread.sleep(1);
        return beforeSave;
    }

    private boolean hasEntity(CHSEntity entity, List<? extends CHSEntity> entities) {
        return entities.stream().anyMatch(individual -> individual.getUuid().equals(entity.getUuid()));
    }

    private List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getNonTransactionalEntities().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
        return ((JsonObject) response.getBody()).getList("syncDetails");
    }

    private List<Individual> getSubjects(SubjectType subjectType, DateTime lastModifiedDateTime) {
        PagedResources<Resource<Individual>> individuals = individualController.getIndividualsByOperatingIndividualScope(lastModifiedDateTime, DateTime.now(), subjectType.getUuid(), PageRequest.of(0, 10));
        return individuals.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }

    private List<Individual> getSubjects(SubjectType subjectType) {
        return this.getSubjects(subjectType, DateTime.now().minusDays(1));
    }

    private List<ProgramEnrolment> getEnrolments(Program program, DateTime lastModifiedDateTime) throws Exception {
        PagedResources<Resource<ProgramEnrolment>> enrolments = programEnrolmentController.getProgramEnrolmentsByOperatingIndividualScope(lastModifiedDateTime, DateTime.now(), program.getUuid(), PageRequest.of(0, 10));
        return enrolments.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }

    private List<ProgramEnrolment> getEnrolments(Program program) throws Exception {
        return this.getEnrolments(program, DateTime.now().minusDays(1));
    }

    private List<GroupSubject> getGroupSubjects(SubjectType groupSubjectType) {
        return this.getGroupSubjects(DateTime.now().minusDays(1), groupSubjectType);
    }

    private List<GroupSubject> getGroupSubjects(DateTime lastModifiedDateTime, SubjectType groupSubjectType) {
        PagedResources<Resource<GroupSubject>> enrolments = groupSubjectController.getGroupSubjectsByOperatingIndividualScope(lastModifiedDateTime, DateTime.now(),
                groupSubjectType.getUuid(),
                PageRequest.of(0, 10));
        return enrolments.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }
}
