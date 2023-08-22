package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
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
import java.util.stream.Collectors;

import static org.junit.Assert.*;

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
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestOrganisationService testOrganisationService;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private TestCatchmentService testCatchmentService;
    @Autowired
    private TestLocationService testLocationService;
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

    private User setupOrganisation(Group group) {
        User user = new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        Organisation organisation = new TestOrganisationBuilder().withMandatoryFields().withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user);
        userRepository.save(new UserBuilder(user).withAuditUser(user).build());
        setUser(user.getUsername());

        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        groupRepository.save(group);
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user).build());
        return user;
    }

    @Test
    public void sync() throws Exception {
        Group group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().build();
        User user = setupOrganisation(group);

        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        AddressLevel addressLevel2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = new TestCatchmentBuilder().withDefaultValuesForNewEntity().build();
        testCatchmentService.createCatchment(catchment, addressLevel1);

        SubjectType st_CatchmentBasedSync = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_CatchmentBasedSync").setName("st_CatchmentBasedSync").build());
        Program p_CatchmentBasedSync = testProgramService.addProgram(new ProgramBuilder().withName("st_CatchmentBasedSync").build(), st_CatchmentBasedSync);
        SubjectType st_NoAccess = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_NoAccess").setName("st_NoAccess").build());
        Program p_NoAccess = testProgramService.addProgram(new ProgramBuilder().withName("st_NoAccess").build(), st_NoAccess);
        Concept concept = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        SubjectType st_SyncAttributes = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_SyncAttributes").setName("st_SyncAttributes")
                        .setSyncRegistrationConcept1Usable(true).setSyncRegistrationConcept1(concept.getUuid()).build());
        Program p_SyncAttributes = testProgramService.addProgram(new ProgramBuilder().withName("st_SyncAttributes").build(), st_SyncAttributes);
        SubjectType st_DirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        Program p_DirectAssignment = testProgramService.addProgram(new ProgramBuilder().withName("st_DirectAssignment").build(), st_DirectAssignment);

        testGroupService.giveViewSubjectPrivilegeTo(group, st_CatchmentBasedSync, st_DirectAssignment, st_SyncAttributes);
        testGroupService.giveViewProgramPrivilegeTo(group, st_CatchmentBasedSync, p_CatchmentBasedSync);
        testGroupService.giveViewProgramPrivilegeTo(group, st_DirectAssignment, p_DirectAssignment);
        testGroupService.giveViewProgramPrivilegeTo(group, st_SyncAttributes, p_SyncAttributes);

        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(addressLevel1).build());
        ProgramEnrolment enrolmentInTheCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_CatchmentBasedSync).setIndividual(inTheCatchment).build());
        Individual outsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(addressLevel2).build());
        ProgramEnrolment enrolmentOutsideCatchment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_CatchmentBasedSync).setIndividual(outsideCatchment).build());
        Individual noAccessToSubjectType = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_NoAccess).withLocation(addressLevel1).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_NoAccess).setIndividual(noAccessToSubjectType).build());
        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 1").getUuid())).build());
        ProgramEnrolment enrolmentHasMatchingObs = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(hasMatchingObs).build());
        Individual obsNotMatching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 2").getUuid())).build());
        ProgramEnrolment enrolmentObsNotMatching = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(obsNotMatching).build());
        Individual obsNotPresent = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).build());
        ProgramEnrolment enrolmentObsNotPresent = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_SyncAttributes).setIndividual(obsNotPresent).build());
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_DirectAssignment).withLocation(addressLevel1).build());
        ProgramEnrolment enrolmentAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_DirectAssignment).setIndividual(assigned).build());
        Individual notAssigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_DirectAssignment).withLocation(addressLevel1).build());
        ProgramEnrolment enrolmentNotAssigned = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(p_DirectAssignment).setIndividual(notAssigned).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(user).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(st_SyncAttributes.getUuid()).setSyncConcept1(concept.getUuid()).setSyncConcept1Values(Collections.singletonList(concept.getAnswerConcept("Answer 1").getUuid())).build();
        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());

        List syncDetails = getSyncDetails();

        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_NoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_CatchmentBasedSync.getUuid())));

        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), p_NoAccess.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), p_CatchmentBasedSync.getUuid())));

        List<Individual> subjects = getSubjects(st_CatchmentBasedSync);
        assertTrue(hasEntity(inTheCatchment, subjects));
        assertFalse(hasEntity(outsideCatchment, subjects));

        List<ProgramEnrolment> enrolments = getEnrolments(p_CatchmentBasedSync);
        assertTrue(hasEntity(enrolmentInTheCatchment, enrolments));
        assertFalse(hasEntity(enrolmentOutsideCatchment, enrolments));

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_SyncAttributes.getUuid())));
        subjects = getSubjects(st_SyncAttributes);
        assertTrue(hasEntity(hasMatchingObs, subjects));
        assertFalse(hasEntity(obsNotMatching, subjects));
        assertFalse(hasEntity(obsNotPresent, subjects));
        enrolments = getEnrolments(p_SyncAttributes);
        assertTrue(hasEntity(enrolmentHasMatchingObs, enrolments));
        assertFalse(hasEntity(enrolmentObsNotMatching, enrolments));
        assertFalse(hasEntity(enrolmentObsNotPresent, enrolments));

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_DirectAssignment.getUuid())));
        subjects = getSubjects(st_DirectAssignment);
        assertTrue(hasEntity(assigned, subjects));
        assertFalse(hasEntity(notAssigned, subjects));

        enrolments = getEnrolments(p_DirectAssignment);
        assertTrue(hasEntity(enrolmentAssigned, enrolments));
        assertFalse(hasEntity(enrolmentNotAssigned, enrolments));

        UserSubjectAssignment userSubjectAssignment = new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(notAssigned).withUser(user).build();
        userSubjectAssignmentRepository.save(userSubjectAssignment);
        Thread.sleep(1);
        Individual assignedNow = notAssigned;
        subjects = getSubjects(st_DirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertTrue(hasEntity(assignedNow, subjects));
        ProgramEnrolment enrolmentAssignedNow = enrolmentNotAssigned;
        enrolments = getEnrolments(p_DirectAssignment, userSubjectAssignment.getLastModifiedDateTime());
        assertTrue(hasEntity(enrolmentAssignedNow, enrolments));
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
}
