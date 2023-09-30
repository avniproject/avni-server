package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.TestUserSyncSettingsBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.service.builder.*;
import org.avni.server.web.SubjectMigrationController;
import org.avni.server.web.SyncController;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.joda.time.DateTime;
import org.junit.Before;
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
public class SubjectMigrationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private SubjectMigrationService subjectMigrationService;
    @Autowired
    private SubjectMigrationController subjectMigrationController;
    @Autowired
    private SyncController syncController;
    @Autowired
    private TestGroupService testGroupService;

    private Concept concept1;
    private Concept concept2;
    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private TestDataSetupService.TestOrganisationData organisationData;

    private List<SubjectMigration> getMigrations(SubjectType subjectType, DateTime lastModifiedDateTime, DateTime now) {
        PagedResources<Resource<SubjectMigration>> migrations = subjectMigrationController.getMigrationsByCatchmentAndLastModified(lastModifiedDateTime, now, subjectType.getUuid(), PageRequest.of(0, 10));
        return migrations.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }

    private List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getNonTransactionalEntities().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
        return ((JsonObject) response.getBody()).getList("syncDetails");
    }

    @Before
    public void setup() {
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();

        concept1 = testConceptService.createCodedConcept("Concept Name 1", "Answer 11", "Answer 12", "Answer 13");
        concept2 = testConceptService.createCodedConcept("Concept Name 2", "Answer 21", "Answer 22");
        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType")
                        .setName("subjectType")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(concept1.getUuid())
                        .setSyncRegistrationConcept2(concept2.getUuid())
                        .build());
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectType.getUuid()).setSyncConcept1(concept1.getUuid()).setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 11").getUuid())).build();
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(organisationData.getUser().getUsername());
    }

    @Test
    public void checkSyncStrategy() {
        // Subjects not migrated
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).build()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build()).build());
        assertFalse(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(0, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject with one concept attribute, location migrated
        ObservationCollection observations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid());
        Individual s1 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s1.getUuid(), catchmentData.getAddressLevel2(), observations);
        List syncDetails = getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject with two concept attributes, location migrated
        observations = new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build();
        Individual s2 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s2.getUuid(), catchmentData.getAddressLevel2(), observations);
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(2, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject with one concept attribute, attribute migrated
        Individual s3 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s3.getUuid(), catchmentData.getAddressLevel1(), ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 12").getUuid()));
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(3, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject migrated but is old and new attributes are not assigned to user
        observations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 12").getUuid());
        ObservationCollection newObservations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 13").getUuid());
        Individual s7 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s7.getUuid(), catchmentData.getAddressLevel1(), newObservations);
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(3, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        //Setup two concept attributes on user
        UserSyncSettings userSyncSettingsWithTwoConcepts = new TestUserSyncSettingsBuilder()
                .setSubjectTypeUUID(subjectType.getUuid())
                .setSyncConcept1(concept1.getUuid())
                .setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 11").getUuid()))
                .setSyncConcept2Values(Collections.singletonList(concept2.getAnswerConcept("Answer 21").getUuid()))
                .build();
        userRepository.save(new UserBuilder(userRepository.findOne(organisationData.getUser().getId())).withSubjectTypeSyncSettings(userSyncSettingsWithTwoConcepts).build());

        // Subject with two concept attributes, first attribute migrated
        Individual s4 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s4.getUuid(), catchmentData.getAddressLevel1(), new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build());
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(4, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject with two concept attributes, second attribute migrated
        Individual s5 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s5.getUuid(), catchmentData.getAddressLevel1(), new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build());
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(5, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject with two concept attributes, both attributes migrated
        Individual s6 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s6.getUuid(), catchmentData.getAddressLevel1(), new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build());
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(6, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());
    }

    @Test
    public void migrations_created_by_one_user_is_returned_for_another_user_even_when_concept_attributes_dont_match() {
        ObservationCollection observations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid());
        ObservationCollection newObservations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid());
        Individual s = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s.getUuid(), catchmentData.getAddressLevel2(), newObservations);
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        UserSyncSettings user2SyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectType.getUuid()).setSyncConcept1(concept1.getUuid()).setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 13").getUuid())).build();
        User user2 = userRepository.save(new UserBuilder(organisationData.getUser2()).withSubjectTypeSyncSettings(user2SyncSettings).withCatchment(catchmentData.getCatchment()).build());
        setUser(user2);
        //This should have failed. Check comment on SubjectMigrationService. org.avni.server.service.SubjectMigrationService.markSubjectMigrationIfRequired
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());
    }
}
