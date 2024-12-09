package org.avni.server.service.accessControl;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.SubjectPartitionCheckStatus;
import org.avni.server.domain.accessControl.SubjectPartitionData;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.TestUserSyncSettingsBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class AccessControlServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;

    private TestDataSetupService.TestOrganisationData organisationData;
    private TestDataSetupService.TestCatchmentData catchmentData;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();
    }

    @Test
    public void checkSubjectAccessForCatchmentTypePartition() {
        User user = organisationData.getUser();
        user.setCatchment(catchmentData.getCatchment());
        userRepository.save(user);
        setUser(user);

        AddressLevel outsideCatchment = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).build());
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setShouldSyncByLocation(true)
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType")
                        .setName("subjectType")
                        .build());

        // new subject
        Individual subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1()).build();
        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());

        // existing subject
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1()).build();
        subject = testSubjectService.save(subject);
        SubjectPartitionData previousPartitionState = SubjectPartitionData.create(subject);
        subject.setAddressLevel(catchmentData.getAddressLevel2());
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());

        // new subject, outside catchment
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(outsideCatchment).build();
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.NotInThisUsersCatchment, subjectPartitionCheckStatus.getMessage());


        // existing subject, outside catchment
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(outsideCatchment).build();
        subject = testSubjectService.save(subject);
        previousPartitionState = SubjectPartitionData.create(subject);

        subject.setAddressLevel(catchmentData.getAddressLevel2());
        subject = testSubjectService.save(subject);
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.NotInThisUsersCatchment, subjectPartitionCheckStatus.getMessage());
        //
    }

    @Test
    public void checkSubjectAccessForSyncAttributes() {
        User user = organisationData.getUser();
        TestDataSetupService.TestSyncAttributeBasedSubjectTypeData subjectTypeData = testDataSetupService.setupSubjectTypeWithSyncAttributes();
        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder()
                .setSubjectTypeUUID(subjectTypeData.getSubjectType().getUuid())
                .setSyncConcept1(subjectTypeData.getSyncConcept().getUuid())
                .setSyncConcept1Values(Collections.singletonList(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid()))
                .build();
        userRepository.save(new UserBuilder(user)
                .withCatchment(catchmentData.getCatchment())
                .withOperatingIndividualScope(OperatingIndividualScope.ByCatchment)
                .withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user);

        // new subject
        Individual subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid())
                .build();
        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());

        // new subject, with not matching sync attribute value
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid())
                .build();
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.SyncAttributeForUserNotValidForUpdate, subjectPartitionCheckStatus.getMessage());


        // existing subject with matching sync attribute value
        ObservationCollection observationCollection = new ObservationCollection();
        observationCollection.put(subjectTypeData.getSyncConcept().getUuid(), subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid());
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withObservations(observationCollection)
                .build();
        subject = testSubjectService.save(subject);
        SubjectPartitionData previousPartitionState = SubjectPartitionData.create(subject);

        subject.setSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid());
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());


        // existing subject with not matching sync attribute value
        observationCollection = new ObservationCollection();
        observationCollection.put(subjectTypeData.getSyncConcept().getUuid(), subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid());
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .build();
        subject = testSubjectService.save(subject);
        previousPartitionState = SubjectPartitionData.create(subject);

        subject.setSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid());
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.SyncAttributeForUserNotValidForUpdate, subjectPartitionCheckStatus.getMessage());
    }

    @Test
    public void checkSubjectAccessForUserIgnoringSyncAttributes() {
        User user = organisationData.getUser();
        user.setIgnoreSyncSettingsInDEA(true);
        TestDataSetupService.TestSyncAttributeBasedSubjectTypeData subjectTypeData = testDataSetupService.setupSubjectTypeWithSyncAttributes();
        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder()
                .setSubjectTypeUUID(subjectTypeData.getSubjectType().getUuid())
                .setSyncConcept1(subjectTypeData.getSyncConcept().getUuid())
                .setSyncConcept1Values(Collections.singletonList(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid()))
                .build();
        userRepository.save(new UserBuilder(user)
                .withCatchment(catchmentData.getCatchment())
                .withOperatingIndividualScope(OperatingIndividualScope.ByCatchment)
                .withSubjectTypeSyncSettings(userSyncSettings).build());
        setUser(user);

        // new subject
        Individual subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid())
                .build();
        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());

        // new subject, with not matching sync attribute value
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid())
                .build();
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertTrue(subjectPartitionCheckStatus.isPassed());


        // existing subject with matching sync attribute value
        ObservationCollection observationCollection = new ObservationCollection();
        observationCollection.put(subjectTypeData.getSyncConcept().getUuid(), subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid());
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .withObservations(observationCollection)
                .build();
        subject = testSubjectService.save(subject);
        SubjectPartitionData previousPartitionState = SubjectPartitionData.create(subject);

        subject.setSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid());
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());


        // existing subject with not matching sync attribute value
        observationCollection = new ObservationCollection();
        observationCollection.put(subjectTypeData.getSyncConcept().getUuid(), subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid());
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(catchmentData.getAddressLevel1())
                .build();
        subject = testSubjectService.save(subject);
        previousPartitionState = SubjectPartitionData.create(subject);

        subject.setSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 1").getUuid());
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertTrue(subjectPartitionCheckStatus.isPassed());


        AddressLevel outsideCatchment = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).build());
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setShouldSyncByLocation(true)
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType")
                        .setName("subjectType")
                        .build());

        // new subject, with sync attribute value, outside catchment
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(outsideCatchment)
                .withSyncConcept1Value(subjectTypeData.getSyncConcept().getAnswerConcept("Answer 2").getUuid())
                .build();
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, null);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.NotInThisUsersCatchment, subjectPartitionCheckStatus.getMessage());

        // existing subject, without sync attribute value, outside catchment
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectTypeData.getSubjectType())
                .withLocation(outsideCatchment).build();
        subject = testSubjectService.save(subject);
        previousPartitionState = SubjectPartitionData.create(subject);

        subject.setAddressLevel(catchmentData.getAddressLevel2());
        subject = testSubjectService.save(subject);
        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.NotInThisUsersCatchment, subjectPartitionCheckStatus.getMessage());
        //
    }

    @Test
    public void checkForDirectAssignment() throws ValidationException {
        User user = organisationData.getUser();
        userRepository.save(new UserBuilder(user)
                .withCatchment(catchmentData.getCatchment())
                .withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        setUser(user);
        userRepository.save(new UserBuilder(organisationData.getUser2())
                .withCatchment(catchmentData.getCatchment())
                .withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType")
                        .setName("subjectType")
                        .setDirectlyAssignable(true)
                        .build());


        // existing subject assigned to current user
        Individual subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1()).build();
        subject = testSubjectService.save(subject);
        userSubjectAssignmentService.assignSubjects(user, Collections.singletonList(subject), false);
        SubjectPartitionData previousPartitionState = SubjectPartitionData.create(subject);

        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertTrue(subjectPartitionCheckStatus.getMessage(), subjectPartitionCheckStatus.isPassed());


        // existing subject, outside catchment
        subject = new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1()).build();
        subject = testSubjectService.save(subject);
        userSubjectAssignmentService.assignSubjects(organisationData.getUser2(), Collections.singletonList(subject), false);
        previousPartitionState = SubjectPartitionData.create(subject);

        subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, previousPartitionState);
        assertFalse(subjectPartitionCheckStatus.isPassed());
        assertEquals(SubjectPartitionCheckStatus.NotDirectlyAssignedToThisUser, subjectPartitionCheckStatus.getMessage());
    }
}
