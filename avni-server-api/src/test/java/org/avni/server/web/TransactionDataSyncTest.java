package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.backgroundJob.StorageManagementJob;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.StorageManagementConfigRepository;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.TestUserSyncSettingsBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.*;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.builder.*;
import org.avni.server.service.sync.TestSyncService;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private TestSyncService testSyncService;
    @Autowired
    private GroupSubjectController groupSubjectController;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestSubjectService testSubjectService;
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

    @Autowired
    private StorageManagementJob storageManagementJob;
    @Autowired
    private StorageManagementConfigRepository storageManagementConfigRepository;

    private TestDataSetupService.TestOrganisationData organisationData;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private SubjectType subjectTypeWithCatchmentBasedSync;
    private SubjectType groupSubjectTypeForCatchmentBasedSync;
    private Program programWithCatchmentBasedSync;
    private SubjectType subjectTypeWithNoAccess;
    private Program programWithNoAccess;
    private GroupRole groupRoleForGroupSubjectTypeWithCatchmentBasedSync;
    private GroupRole groupRoleForGroupSubjectTypeWithDirectAssignment;
    private Concept conceptForAttributeBasedSync;
    private SubjectType subjectTypeWithSyncAttributeBasedSync;
    private SubjectType groupSubjectTypeWithSyncAttributeBasedSync;
    private Program programForSyncAttributeBasedSync;
    private GroupRole groupRoleForGroupSubjectTypeWithSyncAttributeBasedSync;
    private SubjectType subjectTypeForDirectAssignment;
    private SubjectType groupSubjectTypeForDirectAssignment;
    private Program programForDirectAssignment;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setup() {
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();

        // Metadata for Catchment based sync
        subjectTypeWithCatchmentBasedSync = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithCatchmentBasedSync")
                        .setName("subjectTypeWithCatchmentBasedSync")
                        .build());
        groupSubjectTypeForCatchmentBasedSync = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setGroup(true)
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("groupSubjectTypeForCatchmentBasedSync")
                        .setName("groupSubjectTypeForCatchmentBasedSync").build());

        programWithCatchmentBasedSync = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("subjectTypeWithCatchmentBasedSync")
                        .build(),
                subjectTypeWithCatchmentBasedSync);

        groupRoleForGroupSubjectTypeWithCatchmentBasedSync = groupRoleRepository.save(
                new TestGroupRoleBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withGroupSubjectType(groupSubjectTypeForCatchmentBasedSync)
                        .withMemberSubjectType(subjectTypeWithCatchmentBasedSync)
                        .build());

        // Metadata for no access
        subjectTypeWithNoAccess = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithNoAccess")
                        .setName("subjectTypeWithNoAccess")
                        .build());

        programWithNoAccess = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("subjectTypeWithNoAccess")
                        .build(),
                subjectTypeWithNoAccess);

        // Metadata for sync attribute based sync
        conceptForAttributeBasedSync = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        subjectTypeWithSyncAttributeBasedSync = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithSyncAttributeBasedSync")
                        .setName("subjectTypeWithSyncAttributeBasedSync")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(conceptForAttributeBasedSync.getUuid()).build());

        groupSubjectTypeWithSyncAttributeBasedSync = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity()
                        .setUuid("groupSubjectTypeWithSyncAttributeBasedSync")
                        .setName("groupSubjectTypeWithSyncAttributeBasedSync")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(conceptForAttributeBasedSync.getUuid())
                        .build());

        programForSyncAttributeBasedSync = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("programForSyncAttributeBasedSync")
                        .build(),
                subjectTypeWithSyncAttributeBasedSync);

        groupRoleForGroupSubjectTypeWithSyncAttributeBasedSync = groupRoleRepository.save(
                new TestGroupRoleBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withGroupSubjectType(groupSubjectTypeWithSyncAttributeBasedSync)
                        .withMemberSubjectType(subjectTypeWithSyncAttributeBasedSync)
                        .build());

        // Metadata for direct assignment based sync
        subjectTypeForDirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("subjectTypeForDirectAssignment").setName("subjectTypeForDirectAssignment").setDirectlyAssignable(true).build());
        groupSubjectTypeForDirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        programForDirectAssignment = testProgramService.addProgram(new ProgramBuilder().withName("subjectTypeForDirectAssignment").build(), subjectTypeForDirectAssignment);

        groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectTypeForDirectAssignment).withMemberSubjectType(subjectTypeForDirectAssignment).build());

        groupRoleForGroupSubjectTypeWithDirectAssignment = groupRoleRepository.save(
                new TestGroupRoleBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withGroupSubjectType(groupSubjectTypeForDirectAssignment)
                        .withMemberSubjectType(subjectTypeForDirectAssignment)
                        .build());

        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectTypeWithCatchmentBasedSync, subjectTypeForDirectAssignment, subjectTypeWithSyncAttributeBasedSync, groupSubjectTypeWithSyncAttributeBasedSync, groupSubjectTypeForCatchmentBasedSync, groupSubjectTypeForDirectAssignment);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectTypeWithCatchmentBasedSync, programWithCatchmentBasedSync);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectTypeForDirectAssignment, programForDirectAssignment);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectTypeWithSyncAttributeBasedSync, programForSyncAttributeBasedSync);
    }

    @Test
    @Transactional
    public void sync() throws Exception {
        // Catchment tx entities
        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectTypeForCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentInTheCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithCatchmentBasedSync).setIndividual(inTheCatchment).build());
        Individual groupInCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInCatchment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForGroupSubjectTypeWithCatchmentBasedSync).withMember(inTheCatchment).withGroup(groupInCatchment).build());

        // Outside catchment tx entities
        Individual outsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel2()).build());
        ProgramEnrolment enrolmentOutsideCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithCatchmentBasedSync).setIndividual(outsideCatchment).build());
        Individual noAccessToSubjectType = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithNoAccess).withLocation(catchmentData.getAddressLevel1()).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithNoAccess).setIndividual(noAccessToSubjectType).build());

        // Sync attributes tx entities
        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(hasMatchingObs).build());
        Individual obsNotMatching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 2").getUuid())).build());
        ProgramEnrolment enrolmentObsNotMatching = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(obsNotMatching).build());
        Individual obsNotPresent = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentObsNotPresent = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(obsNotPresent).build());

        // Direct assignment tx entities
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForDirectAssignment).setIndividual(assigned).build());
        Individual notAssigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentNotAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForDirectAssignment).setIndividual(notAssigned).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(organisationData.getUser()).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());

        setUser(organisationData.getUser().getUsername());

        List syncDetails = testSyncService.getSyncDetails();

        // Check catchment based sync strategy
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithNoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithCatchmentBasedSync.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.GroupSubject.name(), groupSubjectTypeForCatchmentBasedSync.getUuid())));
        List<Individual> subjects = testSyncService.getSubjects(subjectTypeWithCatchmentBasedSync);
        assertTrue(hasEntity(inTheCatchment, subjects));
        assertFalse(hasEntity(outsideCatchment, subjects));
        List<ProgramEnrolment> enrolments = testSyncService.getEnrolments(programWithCatchmentBasedSync);
        assertTrue(hasEntity(enrolmentInTheCatchment, enrolments));
        assertFalse(hasEntity(enrolmentOutsideCatchment, enrolments));
        List<GroupSubject> groupSubjects = getGroupSubjects(groupSubjectTypeForCatchmentBasedSync);
        assertTrue(hasEntity(groupSubjectInCatchment, groupSubjects));

        // Check for no access
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), programWithNoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), programWithCatchmentBasedSync.getUuid())));

        // CHECK FOR SYNC ATTRIBUTES BASED SYNC STRATEGY
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithSyncAttributeBasedSync.getUuid())));
        subjects = testSyncService.getSubjects(subjectTypeWithSyncAttributeBasedSync);
        assertTrue(hasEntity(hasMatchingObs, subjects));
        assertFalse(hasEntity(obsNotMatching, subjects));
        assertFalse(hasEntity(obsNotPresent, subjects));
        enrolments = testSyncService.getEnrolments(programForSyncAttributeBasedSync);
        assertTrue(hasEntity(enrolmentHasMatchingObs, enrolments));
        assertFalse(hasEntity(enrolmentObsNotMatching, enrolments));
        assertFalse(hasEntity(enrolmentObsNotPresent, enrolments));

        // Group Subject
        Individual groupHasMatchingObs = testSubjectService.save(
                new SubjectBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withSubjectType(groupSubjectTypeWithSyncAttributeBasedSync)
                        .withLocation(catchmentData.getAddressLevel1())
                        .withObservations(ObservationCollectionBuilder
                                .withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid()))
                        .build());
        GroupSubject groupSubjectInObsMatching = testGroupSubjectService.save(
                new TestGroupSubjectBuilder()
                        .withGroupRole(groupRoleForGroupSubjectTypeWithSyncAttributeBasedSync)
                        .withMember(hasMatchingObs)
                        .withGroup(groupHasMatchingObs)
                        .build());
        userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(groupSubjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        User user = userRepository.save(new UserBuilder(organisationData.getUser()).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());
        groupSubjects = getGroupSubjects(groupSubjectTypeWithSyncAttributeBasedSync);
        assertTrue(hasEntity(groupSubjectInObsMatching, groupSubjects));

        // CHECK FOR DIRECT ASSIGNMENT BASED SYNC STRATEGY
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeForDirectAssignment.getUuid())));
        subjects = testSyncService.getSubjects(subjectTypeForDirectAssignment);
        assertTrue(hasEntity(assigned, subjects));
        assertFalse(hasEntity(notAssigned, subjects));
        enrolments = testSyncService.getEnrolments(programForDirectAssignment);
        assertTrue(hasEntity(enrolmentAssigned, enrolments));
        assertFalse(hasEntity(enrolmentNotAssigned, enrolments));

        // Standalone Subject
        UserSubjectAssignment userSubjectAssignment = userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(notAssigned).withUser(organisationData.getUser()).build());
        Thread.sleep(1);
        Individual assignedNow = notAssigned;
        subjects = testSyncService.getSubjects(subjectTypeForDirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertTrue(hasEntity(assignedNow, subjects));
        ProgramEnrolment enrolmentAssignedNow = enrolmentNotAssigned;
        enrolments = testSyncService.getEnrolments(programForDirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertTrue(hasEntity(enrolmentAssignedNow, enrolments));
        // Group Subject
        Individual assignedGroupSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInDirectAssignment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForGroupSubjectTypeWithDirectAssignment).withMember(assigned).withGroup(assignedGroupSubject).build());
        userSubjectAssignmentService.assignSubjects(organisationData.getUser(), Collections.singletonList(assignedGroupSubject), false);

        groupSubjects = getGroupSubjects(groupSubjectTypeForDirectAssignment);
        assertTrue(hasEntity(groupSubjectInDirectAssignment, groupSubjects));
    }

    @Test
    @Transactional
    public void syncShouldSyncEverythingBeforeNow() throws Exception {
        setUser(organisationData.getUser().getUsername());

        // Catchment tx entities
        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectTypeForCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentInTheCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithCatchmentBasedSync).setIndividual(inTheCatchment).build());
        Individual groupInCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInCatchment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForGroupSubjectTypeWithCatchmentBasedSync).withMember(inTheCatchment).withGroup(groupInCatchment).build());

        // Sync attributes tx entities
        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(hasMatchingObs).build());

        // Direct assignment tx entities
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForDirectAssignment).setIndividual(assigned).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(organisationData.getUser()).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());

        setUser(organisationData.getUser().getUsername());

        // Check catchment based sync strategy
        List<Individual> subjects = testSyncService.getSubjects(subjectTypeWithCatchmentBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(inTheCatchment, subjects));
        List<ProgramEnrolment> enrolments = testSyncService.getEnrolments(programWithCatchmentBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(enrolmentInTheCatchment, enrolments));
        List<GroupSubject> groupSubjects = getGroupSubjects(groupSubjectTypeForCatchmentBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(groupSubjectInCatchment, groupSubjects));

        subjects = testSyncService.getSubjects(subjectTypeWithSyncAttributeBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(hasMatchingObs, subjects));

        enrolments = testSyncService.getEnrolments(programForSyncAttributeBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(enrolmentHasMatchingObs, enrolments));

        // Group Subject
        Individual groupHasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build());
        GroupSubject groupSubjectInObsMatching = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForGroupSubjectTypeWithSyncAttributeBasedSync).withMember(hasMatchingObs).withGroup(groupHasMatchingObs).build());
        userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        User user = userRepository.save(new UserBuilder(organisationData.getUser()).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());
        groupSubjects = getGroupSubjects(groupSubjectTypeWithSyncAttributeBasedSync, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(groupSubjectInObsMatching, groupSubjects));

        // CHECK FOR DIRECT ASSIGNMENT BASED SYNC STRATEGY
        subjects = testSyncService.getSubjects(subjectTypeForDirectAssignment, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(assigned, subjects));
        enrolments = testSyncService.getEnrolments(programForDirectAssignment, DateTime.now().minusDays(1), DateTime.now().minusMinutes(5));
        assertFalse(hasEntity(enrolmentAssigned, enrolments));
    }

    private void saveStorageManagementConfig(String query) {
        StorageManagementConfig storageManagementConfig = new StorageManagementConfig();
        storageManagementConfig.setSqlQuery(query);
        storageManagementConfig.setUuid(UUID.randomUUID().toString());
        storageManagementConfigRepository.save(storageManagementConfig);
    }

    @Test
    @Transactional
    public void doNotSyncDisabledEntities() throws Exception {
        // Catchment tx entities
        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectTypeForCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentInTheCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithCatchmentBasedSync).setIndividual(inTheCatchment).build());
        Individual groupInCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        GroupSubject groupSubjectInCatchment = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleForGroupSubjectTypeWithCatchmentBasedSync).withMember(inTheCatchment).withGroup(groupInCatchment).build());

        // Outside catchment tx entities
        Individual outsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithCatchmentBasedSync).withLocation(catchmentData.getAddressLevel2()).build());
        ProgramEnrolment enrolmentOutsideCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithCatchmentBasedSync).setIndividual(outsideCatchment).build());
        Individual noAccessToSubjectType = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithNoAccess).withLocation(catchmentData.getAddressLevel1()).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programWithNoAccess).setIndividual(noAccessToSubjectType).build());

        // Sync attributes tx entities
        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(hasMatchingObs).build());
        Individual obsNotMatching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 2").getUuid())).build());
        ProgramEnrolment enrolmentObsNotMatching = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(obsNotMatching).build());
        Individual obsNotPresent = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttributeBasedSync).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentObsNotPresent = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForSyncAttributeBasedSync).setIndividual(obsNotPresent).build());

        // Direct assignment tx entities
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForDirectAssignment).setIndividual(assigned).build());
        Individual notAssigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeForDirectAssignment).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolmentNotAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(programForDirectAssignment).setIndividual(notAssigned).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(organisationData.getUser()).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());

        setUser(organisationData.getUser().getUsername());

        // Disable syncs
        saveStorageManagementConfig("select id from public.individual");
        storageManagementJob.manage();

        List syncDetails = testSyncService.getSyncDetails();

        // Check catchment based sync strategy
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithNoAccess.getUuid())));
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithCatchmentBasedSync.getUuid())));
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.GroupSubject.name(), groupSubjectTypeForCatchmentBasedSync.getUuid())));
        List<Individual> subjects = testSyncService.getSubjects(subjectTypeWithCatchmentBasedSync);
        assertFalse(hasEntity(inTheCatchment, subjects));
        assertFalse(hasEntity(outsideCatchment, subjects));
        List<ProgramEnrolment> enrolments = testSyncService.getEnrolments(programWithCatchmentBasedSync);
        assertFalse(hasEntity(enrolmentInTheCatchment, enrolments));
        assertFalse(hasEntity(enrolmentOutsideCatchment, enrolments));
        List<GroupSubject> groupSubjects = getGroupSubjects(groupSubjectTypeForCatchmentBasedSync);
        assertFalse(hasEntity(groupSubjectInCatchment, groupSubjects));

        // Check for no access
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), programWithNoAccess.getUuid())));
        // we by-pass in program enrolment
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), programWithCatchmentBasedSync.getUuid())));

        // CHECK FOR SYNC ATTRIBUTES BASED SYNC STRATEGY
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeWithSyncAttributeBasedSync.getUuid())));
        subjects = testSyncService.getSubjects(subjectTypeWithSyncAttributeBasedSync);
        assertFalse(hasEntity(hasMatchingObs, subjects));
        assertFalse(hasEntity(obsNotMatching, subjects));
        assertFalse(hasEntity(obsNotPresent, subjects));
        enrolments = testSyncService.getEnrolments(programForSyncAttributeBasedSync);
        assertFalse(hasEntity(enrolmentHasMatchingObs, enrolments));
        assertFalse(hasEntity(enrolmentObsNotMatching, enrolments));
        assertFalse(hasEntity(enrolmentObsNotPresent, enrolments));

        // Group Subject
        jdbcTemplate.execute("update individual set sync_disabled = false where 1 = 1");
        Individual groupHasMatchingObs = testSubjectService.save(
                new SubjectBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withSubjectType(groupSubjectTypeWithSyncAttributeBasedSync)
                        .withLocation(catchmentData.getAddressLevel1())
                        .withObservations(ObservationCollectionBuilder
                                .withOneObservation(conceptForAttributeBasedSync, conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid()))
                        .build());
        GroupSubject groupSubjectInObsMatching = testGroupSubjectService.save(
                new TestGroupSubjectBuilder()
                        .withGroupRole(groupRoleForGroupSubjectTypeWithSyncAttributeBasedSync)
                        .withMember(hasMatchingObs)
                        .withGroup(groupHasMatchingObs)
                        .build());

        storageManagementJob.manage();
        userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(groupSubjectTypeWithSyncAttributeBasedSync.getUuid()).setSyncConcept1(conceptForAttributeBasedSync.getUuid()).setSyncConcept1Values(Collections.singletonList(conceptForAttributeBasedSync.getAnswerConcept("Answer 1").getUuid())).build();
        User user = userRepository.save(new UserBuilder(organisationData.getUser()).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());
        groupSubjects = getGroupSubjects(groupSubjectTypeWithSyncAttributeBasedSync);
        assertFalse(hasEntity(groupSubjectInObsMatching, groupSubjects));

        // CHECK FOR DIRECT ASSIGNMENT BASED SYNC STRATEGY
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectTypeForDirectAssignment.getUuid())));
        subjects = testSyncService.getSubjects(subjectTypeForDirectAssignment);
        assertFalse(hasEntity(assigned, subjects));
        assertFalse(hasEntity(notAssigned, subjects));
        enrolments = testSyncService.getEnrolments(programForDirectAssignment);
        assertFalse(hasEntity(enrolmentAssigned, enrolments));
        assertFalse(hasEntity(enrolmentNotAssigned, enrolments));

        // Standalone Subject
        enableSyncOnAll();
        UserSubjectAssignment userSubjectAssignment = userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(notAssigned).withUser(organisationData.getUser()).build());
        storageManagementJob.manage();

        Thread.sleep(1);
        Individual assignedNow = notAssigned;
        subjects = testSyncService.getSubjects(subjectTypeForDirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertFalse(hasEntity(assignedNow, subjects));
        ProgramEnrolment enrolmentAssignedNow = enrolmentNotAssigned;
        enrolments = testSyncService.getEnrolments(programForDirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertFalse(hasEntity(enrolmentAssignedNow, enrolments));
    }

    private void enableSyncOnAll() {
        jdbcTemplate.execute("update individual set sync_disabled = false, sync_disabled_date_time = null where 1 = 1");
    }

    private boolean hasEntity(CHSEntity entity, List<? extends CHSEntity> entities) {
        return entities.stream().anyMatch(x -> x.getUuid().equals(entity.getUuid()));
    }

    private List<GroupSubject> getGroupSubjects(SubjectType groupSubjectType) {
        return this.getGroupSubjects(groupSubjectType, DateTime.now().minusDays(1));
    }

    private List<GroupSubject> getGroupSubjects(SubjectType groupSubjectType, DateTime lastModifiedDateTime) {
        return getGroupSubjects(groupSubjectType, lastModifiedDateTime, DateTime.now());
    }

    private List<GroupSubject> getGroupSubjects(SubjectType groupSubjectType, DateTime lastModifiedDateTime, DateTime now) {
        CollectionModel<EntityModel<GroupSubject>> enrolments = groupSubjectController.getGroupSubjectsByOperatingIndividualScope(lastModifiedDateTime, now,
                groupSubjectType.getUuid(),
                PageRequest.of(0, 10));
        return enrolments.getContent().stream().map(EntityModel::getContent).collect(Collectors.toList());
    }
}
