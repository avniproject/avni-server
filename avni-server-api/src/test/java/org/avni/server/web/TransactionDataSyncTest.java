package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
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
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private IndividualRepository subjectRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;
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
    public void sync() {
        Group group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().build();
        User user = setupOrganisation(group);

        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        AddressLevel addressLevel2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = new TestCatchmentBuilder().withDefaultValuesForNewEntity().build();
        testCatchmentService.createCatchment(catchment, addressLevel1);

        SubjectType st_CatchmentBasedSync = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());
        SubjectType st_NoAccess = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());
        Concept concept = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        SubjectType st_SyncAttributes = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity()
                        .setSyncRegistrationConcept1Usable(true).setSyncRegistrationConcept1(concept.getUuid()).build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());
        SubjectType st_DirectAssignment = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());

        testGroupService.giveViewSubjectPrivilegeTo(group, st_CatchmentBasedSync, st_DirectAssignment, st_SyncAttributes);

        Individual inTheCatchment = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(addressLevel1).build());
        Individual outsideCatchment = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_CatchmentBasedSync).withLocation(addressLevel2).build());
        Individual noAccessToSubjectType = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_NoAccess).withLocation(addressLevel1).build());
        Individual hasMatchingObs = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 1").getUuid())).build());
        Individual obsNotMatching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 2").getUuid())).build());
        Individual obsNotPresent = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel1).build());
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).build());
        Individual notAssigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(user).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(st_SyncAttributes.getUuid()).setSyncConcept1(concept.getUuid()).setSyncConcept1Values(Collections.singletonList(concept.getAnswerConcept("Answer 1").getUuid())).build();
        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());

        List syncDetails = getSyncDetails();

        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_NoAccess.getUuid())));

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_CatchmentBasedSync.getUuid())));
        List<Individual> subjects = getSubjects(st_CatchmentBasedSync);
        assertTrue(hasSubject(inTheCatchment, subjects));
        assertFalse(hasSubject(outsideCatchment, subjects));

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_SyncAttributes.getUuid())));
        subjects = getSubjects(st_SyncAttributes);
        assertTrue(hasSubject(hasMatchingObs, subjects));
        assertFalse(hasSubject(obsNotMatching, subjects));
        assertFalse(hasSubject(obsNotPresent, subjects));

//        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_DirectAssignment.getUuid())));
//        subjects = getSubjects(st_DirectAssignment);
//        assertTrue(hasSubject(assigned, subjects));
//        assertFalse(hasSubject(notAssigned, subjects));
    }

    private boolean hasSubject(Individual hasMatchingObs, List<Individual> subjects) {
        return subjects.stream().anyMatch(individual -> individual.getUuid().equals(hasMatchingObs.getUuid()));
    }

    private List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getNonTransactionalEntities().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
        return ((JsonObject) response.getBody()).getList("syncDetails");
    }

    private List<Individual> getSubjects(SubjectType st1) {
        PagedResources<Resource<Individual>> individuals = individualController.getIndividualsByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), st1.getUuid(), PageRequest.of(0, 10));
        return individuals.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }
}
