package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
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

import java.util.*;
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
    @Autowired
    private TestLocationService testLocationService;

    private Concept concept1;
    private Concept concept2;
    private SubjectType subjectType;
    private SubjectType subjectTypeWithBothSyncRegistrationConcepts;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private TestDataSetupService.TestOrganisationData organisationData;

    private List<SubjectMigration> getMigrations(SubjectType subjectType, DateTime lastModifiedDateTime, DateTime now) {
        PagedResources<Resource<SubjectMigration>> migrations = subjectMigrationController.getMigrationsByCatchmentAndLastModified(lastModifiedDateTime, now, subjectType.getUuid(), PageRequest.of(0, 10));
        return migrations.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }

    private boolean hasMigrationFor(SubjectType subjectType, DateTime lastModifiedDateTime, DateTime now, Individual subject) {
        List<SubjectMigration> migrations = getMigrations(subjectType, lastModifiedDateTime, now);
        return migrations.stream().anyMatch(subjectMigration -> subjectMigration.getIndividual().equals(subject));
    }

    private List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getNonTransactionalEntities().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false, true, null);
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
                        .build());
        subjectTypeWithBothSyncRegistrationConcepts = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithBothSyncRegistrationConcepts")
                        .setName("subjectTypeWithBothSyncRegistrationConcepts")
                        .setSyncRegistrationConcept1(concept1.getUuid())
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept2(concept2.getUuid())
                        .setSyncRegistrationConcept2Usable(true)
                        .build()
        );
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
        subjectMigrationService.markSubjectMigrationIfRequired(s1.getUuid(), null, catchmentData.getAddressLevel2(), null, observations, false);
        List syncDetails = getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s1));

        // Subject with one concept attribute, attribute migrated
        Individual s3 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s3.getUuid(), null, catchmentData.getAddressLevel1(), null, ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 12").getUuid()), false);
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s3));
        assertEquals(2, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Subject migrated but its old and new attributes are not assigned to user
        observations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 12").getUuid());
        ObservationCollection newObservations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 13").getUuid());
        Individual s7 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s7.getUuid(), null, catchmentData.getAddressLevel1(), null, newObservations, false);
        assertFalse(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s7));
        assertEquals(2, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        // Two concept attributes scenarios
        testSubjectTypeService.updateSubjectType(new SubjectTypeBuilder(subjectType).setSyncRegistrationConcept2Usable(true).setSyncRegistrationConcept2(concept2.getUuid()).build());
        UserSyncSettings userSyncSettingsWithTwoConcepts = new TestUserSyncSettingsBuilder()
                .setSubjectTypeUUID(subjectType.getUuid())
                .setSyncConcept1(concept1.getUuid())
                .setSyncConcept2(concept2.getUuid())
                .setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 11").getUuid()))
                .setSyncConcept2Values(Collections.singletonList(concept2.getAnswerConcept("Answer 21").getUuid()))
                .build();
        User user = userRepository.save(new UserBuilder(userRepository.findOne(organisationData.getUser().getId())).withSubjectTypeSyncSettings(userSyncSettingsWithTwoConcepts).build());
        setUser(user);

        // Subject with two concept attributes, location migrated
        observations = new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build();
        Individual s2 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s2.getUuid(), null, catchmentData.getAddressLevel2(), null, observations, false);
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s2));

        // Subject with two concept attributes, first attribute migrated
        Individual s4 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s4.getUuid(), null, catchmentData.getAddressLevel1(), null, new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build(), false);
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s4));

        // Subject with two concept attributes, second attribute migrated
        Individual s5 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s5.getUuid(), null, catchmentData.getAddressLevel1(), null, new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build(), false);
        boolean hasMigrationFor = hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s5);
        assertTrue(hasMigrationFor);

        // Subject with two concept attributes, both attributes migrated
        Individual s6 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 11")).addObservation(concept2, concept2.getAnswerConcept("Answer 21")).build()).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s6.getUuid(), null, catchmentData.getAddressLevel1(), null, new ObservationCollectionBuilder().addObservation(concept1, concept1.getAnswerConcept("Answer 12")).addObservation(concept2, concept2.getAnswerConcept("Answer 22")).build(), false);
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), s6));

        // User without sync attributes setup will not get any migration as that is more performance optimised. Setting
        UserSyncSettings userSyncSettingsWithNoConcepts = new TestUserSyncSettingsBuilder()
                .setSubjectTypeUUID(subjectType.getUuid())
                .setSyncConcept1(null)
                .setSyncConcept1Values(Collections.emptyList())
                .setSyncConcept2(null)
                .setSyncConcept2Values(Collections.emptyList())
                .build();
        user = userRepository.save(new UserBuilder(userRepository.findOne(organisationData.getUser().getId())).withSubjectTypeSyncSettings(userSyncSettingsWithNoConcepts).build());
        setUser(user);
        assertEquals(0, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());
        assertFalse(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
    }

    @Test
    public void migrations_created_by_one_user_is_returned_for_another_user_even_when_concept_attributes_dont_match() {
        ObservationCollection observations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid());
        ObservationCollection newObservations = ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid());
        Individual s = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(observations).build());
        subjectMigrationService.markSubjectMigrationIfRequired(s.getUuid(), null, catchmentData.getAddressLevel2(), null, newObservations, false);
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());

        UserSyncSettings user2SyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectType.getUuid()).setSyncConcept1(concept1.getUuid()).setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 13").getUuid())).build();
        User user2 = userRepository.save(new UserBuilder(organisationData.getUser2()).withSubjectTypeSyncSettings(user2SyncSettings).withCatchment(catchmentData.getCatchment()).build());
        setUser(user2);
        //This should have failed. Check comment on SubjectMigrationService. org.avni.server.service.SubjectMigrationService.markSubjectMigrationIfRequired
        assertTrue(getSyncDetails().contains(EntitySyncStatusContract.createForComparison(SyncEntityName.SubjectMigration.name(), subjectType.getUuid())));
        assertEquals(1, getMigrations(subjectType, DateTime.now().minusDays(1), DateTime.now()).size());
    }

    @Test
    public void bulkMigrateTracksFailures() {
        Individual i = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        AddressLevel voidedAddressLevel = new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).voided(true).build();
        testLocationService.save(voidedAddressLevel);
        testGroupService.giveEditSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        Map<String, String> destinationAddressLevels = new HashMap<>();
        destinationAddressLevels.put(String.valueOf(i.getAddressLevel().getId()), String.valueOf(voidedAddressLevel.getId()));
        Map<String, String> failures = subjectMigrationService.bulkMigrateByAddress(Collections.singletonList(i.getId()), destinationAddressLevels);
        assertEquals(failures.size(), 1);
    }

    @Test
    public void bulkMigrateByAddressLevelSucceedsForValidDestinationAddress() {
        Individual i = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        Map<String, String> destinationAddressLevels = new HashMap<>();
        destinationAddressLevels.put(i.getAddressLevel().getId().toString(), catchmentData.getAddressLevel2().getId().toString());
        testGroupService.giveEditSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        Map<String, String> failures = subjectMigrationService.bulkMigrateByAddress(Collections.singletonList(i.getId()), destinationAddressLevels);
        assertTrue(failures.isEmpty());
        i = testSubjectService.reload(i);
        assertEquals(i.getAddressLevel(), catchmentData.getAddressLevel2());
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), i));
    }

    @Test
    public void bulkMigrateProcessesAllRecordsEvenIfOneFails() {
        Individual i1 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        Individual i2 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel2()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 12").getUuid())).build());
        AddressLevel voidedAddressLevel = new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).voided(true).build();
        testLocationService.save(voidedAddressLevel);
        Map<String, String> destinationAddressLevels = new HashMap<>();
        destinationAddressLevels.put(catchmentData.getAddressLevel1().getId().toString(), voidedAddressLevel.getId().toString());
        destinationAddressLevels.put(catchmentData.getAddressLevel2().getId().toString(), catchmentData.getAddressLevel1().getId().toString());
        testGroupService.giveEditSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        Map<String, String> failures = subjectMigrationService.bulkMigrateByAddress(Arrays.asList(i1.getId(), i2.getId()), destinationAddressLevels);
        assertEquals(failures.size(), 1);
        i1 = testSubjectService.reload(i1);
        i2 = testSubjectService.reload(i2);
        assertEquals(i1.getAddressLevel(), catchmentData.getAddressLevel1());
        assertFalse(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), i1));
        assertEquals(i2.getAddressLevel(), catchmentData.getAddressLevel1());
        assertTrue(hasMigrationFor(subjectType, DateTime.now().minusDays(1), DateTime.now(), i2));
    }

    @Test
    public void bulkMigrateBySyncConceptMigratesSyncConcept1ValueIfSyncConcept2IsNotConfiguredForSubjectType() {
        Individual i = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())).build());
        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(subjectType.getSyncRegistrationConcept1(), concept1.getAnswerConcept("Answer 12").getUuid());
        testGroupService.giveEditSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        Map<String, String> failures = subjectMigrationService.bulkMigrateBySyncConcept(Collections.singletonList(i.getId()), destinationSyncConcepts);
        assertEquals(failures.size(), 0);
        i = testSubjectService.reload(i);
        assertEquals(i.getSyncConcept1Value(), concept1.getAnswerConcept("Answer 12").getUuid());
        assertNull(i.getSyncConcept2Value());
    }

    @Test
    public void bulkMigrateBySyncConceptMigratesSyncConcept2ValueEvenIfSyncConcept1ValueDoesNotNeedToBeMigrated() {
        Individual i1 = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeWithBothSyncRegistrationConcepts)
                .withLocation(catchmentData.getAddressLevel1())
                .withObservations(new ObservationCollectionBuilder()
                        .addObservation(concept1, concept1.getAnswerConcept("Answer 11").getUuid())
                        .addObservation(concept2, concept2.getAnswerConcept("Answer 21").getUuid())
                        .build())
                .build());
        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(subjectTypeWithBothSyncRegistrationConcepts.getSyncRegistrationConcept2(), concept2.getAnswerConcept("Answer 22").getUuid());
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectTypeWithBothSyncRegistrationConcepts);
        testGroupService.giveEditSubjectPrivilegeTo(organisationData.getGroup(), subjectTypeWithBothSyncRegistrationConcepts);
        Map<String, String> failures = subjectMigrationService.bulkMigrateBySyncConcept(Collections.singletonList(i1.getId()), destinationSyncConcepts);
        assertEquals(failures.size(), 0);
        i1 = testSubjectService.reload(i1);
        assertEquals(i1.getSyncConcept1Value(), concept1.getAnswerConcept("Answer 11").getUuid());
        assertEquals(i1.getSyncConcept2Value(), concept2.getAnswerConcept("Answer 22").getUuid());
        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithBothSyncRegistrationConcepts.getUuid())
                .setSyncConcept1(concept1.getUuid())
                .setSyncConcept1Values(Collections.singletonList(concept1.getAnswerConcept("Answer 11").getUuid()))
                .setSyncConcept2(concept2.getUuid())
                .setSyncConcept2Values(Collections.singletonList(concept2.getAnswerConcept("Answer 22").getUuid()))
                .build();
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(organisationData.getUser().getUsername());
        assertTrue(hasMigrationFor(subjectTypeWithBothSyncRegistrationConcepts, DateTime.now().minusDays(1), DateTime.now(), i1));
    }
}
