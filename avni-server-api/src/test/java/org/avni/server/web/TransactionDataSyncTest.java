package org.avni.server.web;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
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
        Individual hasMatchingObs = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 1").getUuid())).build());
        Individual obsNotMatching = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).withObservations(ObservationCollectionBuilder.withOneObservation(concept, concept.getAnswerConcept("Answer 2").getUuid())).build());
        Individual obsNotPresent = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).build());
        Individual assigned = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).build());
        Individual notAssigned = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st_SyncAttributes).withLocation(addressLevel2).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(user).build());

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(st_SyncAttributes.getUuid()).setSyncConcept1(concept.getUuid()).setSyncConcept1Values(Collections.singletonList(concept.getAnswerConcept("Answer 1").getUuid())).build();
        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user.getUsername());

        List syncDetails = getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st_CatchmentBasedSync.getUuid())));
        assertEquals(1, getNumberOfSubjects(st_CatchmentBasedSync));
//        assertEquals(1, getNumberOfSubjects(st_SyncAttributes));
//        assertEquals(1, getNumberOfSubjects(st_DirectAssignment));
    }

    private List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getEntitiesWithoutSubEntity().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
        return ((JsonObject) response.getBody()).getList("syncDetails");
    }

    private long getNumberOfSubjects(SubjectType st1) {
        PagedResources<Resource<Individual>> individuals = individualController.getIndividualsByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), st1.getUuid(), PageRequest.of(0, 10));
        return individuals.getContent().size();
    }
}
