package org.avni.server.service;

import org.springframework.transaction.annotation.Transactional;
import org.avni.server.application.SubjectTypeSettingKey;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.web.GroupSubjectController;
import org.avni.server.web.request.GroupSubjectContract;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
public class GroupSubjectServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private IndividualService individualService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private GroupSubjectController groupSubjectController;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;

    @Test
    @Transactional
    public void on_addition_of_member_to_a_group__assign_member_to_the_user_assigned_to_the_group() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        SubjectType memberSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        SubjectType memberSubjectTypeButNotDirectlyAssignable = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_NoDirectAssignment").setName("st_NoDirectAssignment").build());
        GroupRole groupRoleInvolvingDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectType).build());
        GroupRole groupRoleWithoutDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());

        Individual group = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        Individual groupNotDirectlyAssignable = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());

        User user1 = userRepository.save(new UserBuilder().withCatchment(testCatchmentData.getCatchment()).withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());
        User user3 = userRepository.save(new UserBuilder().withCatchment(testCatchmentData.getCatchment()).withDefaultValuesForNewEntity().userName("user3@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());

        userSubjectAssignmentService.assignSubjects(user1, Collections.singletonList(group), false);
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember).withGroup(group).build());
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember));


        Individual group2 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember2 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        //previously voided entry of member assignment exists
        userSubjectAssignmentService.assignSubjects(user1, Collections.singletonList(group2), false);
        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(directlyAssignableMember2).withUser(user1).setVoided(true).build());

        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember).withGroup(group2).build());
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember));
    }

    @Test
    public void on_removal_of_a_group__retain_members_assigned_to_the_user() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        SubjectType memberSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        GroupRole groupRoleInvolvingDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectType).build());

        Individual group = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember1 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        Individual directlyAssignableMember2 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());

        User user1 = userRepository.save(new UserBuilder().withCatchment(testCatchmentData.getCatchment()).withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());

        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember1).withGroup(group).build());
        userSubjectAssignmentService.assignSubjects(user1, Collections.singletonList(group), false);
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember2).withGroup(group).build());

        //      Confirm that User has the 2 members as well as the group assigned
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, group));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember2));

        //      Remove group from user
        userSubjectAssignmentService.assignSubjects(user1, Collections.singletonList(group), true);

        //      Confirm that User still has the 2 members assigned, but not the group
        assertNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, group));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember2));

    }

    @Test
    public void on_removal_of_members_whose_group_is_still_assigned_to_user__retain_members_and_group_and_throw_error() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        SubjectType memberSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        GroupRole groupRoleInvolvingDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectType).build());

        Individual group = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember1 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        Individual directlyAssignableMember2 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());

        User user1 = userRepository.save(new UserBuilder().withCatchment(testCatchmentData.getCatchment()).withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());

        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember1).withGroup(group).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember2).withGroup(group).build());
        userSubjectAssignmentService.assignSubjects(user1, Collections.singletonList(group), false);

        //      Confirm that User has the 2 members as well as the group assigned
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, group));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember2));

        //      Remove member1 from user
        try {
            userSubjectAssignmentService.assignSubjects(user1, Arrays.asList(directlyAssignableMember1, directlyAssignableMember2),
                    true);
            fail("Members should not have got removed from group");
        } catch (Exception e) {
        }

        //      Confirm that User still has the 2 members assigned, along with the group
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, group));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user1, directlyAssignableMember2));

    }

    private SubjectType configureGroupTypeWithRemovalReason(String groupTypeUuid, Concept reasonParentConcept) {
        SubjectType groupType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(groupTypeUuid).setName(groupTypeUuid).setGroup(true).build());
        JsonObject settings = groupType.getSettings() == null ? new JsonObject() : groupType.getSettings();
        settings.put(String.valueOf(SubjectTypeSettingKey.removalReasonConceptUuid), reasonParentConcept.getUuid());
        groupType.setSettings(settings);
        return subjectTypeRepository.save(groupType);
    }

    private SubjectType configureGroupTypeWithoutRemovalReason(String groupTypeUuid) {
        return testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(groupTypeUuid).setName(groupTypeUuid).setGroup(true).build());
    }

    private GroupSubject buildMembershipFor(SubjectType groupType, SubjectType memberType, TestDataSetupService.TestCatchmentData catchment) {
        GroupRole groupRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupType).withMemberSubjectType(memberType).build());
        Individual group = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupType).withLocation(catchment.getAddressLevel1()).build());
        Individual member = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(memberType).withLocation(catchment.getAddressLevel1()).build());
        return new TestGroupSubjectBuilder().withGroupRole(groupRole).withGroup(group).withMember(member).build();
    }

    @Test
    @Transactional
    public void removalReason_featureOff_savesWithEndDateAndNoReason() throws ValidationException {
        testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        SubjectType groupType = configureGroupTypeWithoutRemovalReason("st_RemovalReason_Off");
        SubjectType memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_RR_Member_Off").setName("st_RR_Member_Off").build());

        GroupSubject groupSubject = buildMembershipFor(groupType, memberType, catchment);
        groupSubject.setMembershipEndDate(DateTime.now());

        testGroupSubjectService.save(groupSubject);
        assertNotNull(groupSubject.getId());
    }

    @Test
    @Transactional
    public void removalReason_featureOn_endDateNull_savesWithoutValidation() throws ValidationException {
        testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        Concept reasonParent = testConceptService.createCodedConcept("RR Reason Parent A", "Lost contact", "Moved away");
        SubjectType groupType = configureGroupTypeWithRemovalReason("st_RR_EndDateNull", reasonParent);
        SubjectType memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_RR_Member_EndDateNull").setName("st_RR_Member_EndDateNull").build());

        GroupSubject groupSubject = buildMembershipFor(groupType, memberType, catchment);

        testGroupSubjectService.save(groupSubject);
        assertNotNull(groupSubject.getId());
    }

    @Test
    @Transactional
    public void removalReason_featureOn_endDateSet_reasonSet_persists() throws ValidationException {
        testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        Concept reasonParent = testConceptService.createCodedConcept("RR Reason Parent C", "Lost contact", "Moved away");
        Concept reasonAnswer = testConceptService.createConcept("RR Picked Reason", ConceptDataType.Coded);
        SubjectType groupType = configureGroupTypeWithRemovalReason("st_RR_WithReason", reasonParent);
        SubjectType memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_RR_Member_WithReason").setName("st_RR_Member_WithReason").build());

        GroupSubject groupSubject = buildMembershipFor(groupType, memberType, catchment);
        groupSubject.setMembershipEndDate(DateTime.now());
        groupSubject.setRemovalReasonConceptUUID(reasonAnswer.getUuid());

        testGroupSubjectService.save(groupSubject);
        assertNotNull(groupSubject.getId());
        assertEquals(reasonAnswer.getUuid(), groupSubject.getRemovalReasonConceptUUID());
    }

    private GroupSubjectContract buildContractFor(GroupSubject template) {
        GroupSubjectContract contract = new GroupSubjectContract();
        contract.setUuid(template.getUuid());
        contract.setGroupSubjectUUID(template.getGroupSubjectUUID());
        contract.setMemberSubjectUUID(template.getMemberSubjectUUID());
        contract.setGroupRoleUUID(template.getGroupRoleUUID());
        contract.setMembershipStartDate(template.getMembershipStartDate());
        contract.setMembershipEndDate(template.getMembershipEndDate());
        return contract;
    }

    private void assignCatchmentToContextUser(TestDataSetupService.TestOrganisationData orgData, TestDataSetupService.TestCatchmentData catchmentData) {
        userRepository.save(new UserBuilder(orgData.getUser())
                .withCatchment(catchmentData.getCatchment())
                .withOperatingIndividualScope(OperatingIndividualScope.ByCatchment)
                .build());
    }

    @Test
    @Transactional
    public void controller_setsRemovalReasonConceptUUID_andPersistsItVerbatim() {
        TestDataSetupService.TestOrganisationData orgData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        assignCatchmentToContextUser(orgData, catchment);
        SubjectType groupType = configureGroupTypeWithoutRemovalReason("st_Ctlr_Round_Group");
        SubjectType memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_Ctlr_Round_Member").setName("st_Ctlr_Round_Member").build());

        GroupSubject groupSubject = buildMembershipFor(groupType, memberType, catchment);
        groupSubject.setUuid(java.util.UUID.randomUUID().toString());
        groupSubject.setMembershipEndDate(DateTime.now());

        GroupSubjectContract contract = buildContractFor(groupSubject);
        String reasonUuid = java.util.UUID.randomUUID().toString();
        contract.setRemovalReasonConceptUUID(reasonUuid);
        contract.setVoided(true);

        try {
            groupSubjectController.save(contract);
        } catch (ValidationException e) {
            fail("controller.save threw: " + e.getMessage());
        }
        GroupSubject persisted = groupSubjectRepository.findByUuid(contract.getUuid());
        assertNotNull(persisted);
        assertEquals(reasonUuid, persisted.getRemovalReasonConceptUUID());
    }

    @Test
    @Transactional
    public void controller_omitsRemovalReasonOnUpdate_preservesPreviouslySavedValue() {
        TestDataSetupService.TestOrganisationData orgData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        assignCatchmentToContextUser(orgData, catchment);
        SubjectType groupType = configureGroupTypeWithoutRemovalReason("st_Ctlr_Wipe_Group");
        SubjectType memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_Ctlr_Wipe_Member").setName("st_Ctlr_Wipe_Member").build());

        GroupSubject groupSubject = buildMembershipFor(groupType, memberType, catchment);
        groupSubject.setUuid(java.util.UUID.randomUUID().toString());
        groupSubject.setMembershipEndDate(DateTime.now());

        // First call from a new-version client carries the field.
        GroupSubjectContract initial = buildContractFor(groupSubject);
        String reasonUuid = java.util.UUID.randomUUID().toString();
        initial.setRemovalReasonConceptUUID(reasonUuid);
        initial.setVoided(true);
        try {
            groupSubjectController.save(initial);
        } catch (ValidationException e) {
            fail("initial controller.save threw: " + e.getMessage());
        }
        assertEquals(reasonUuid, groupSubjectRepository.findByUuid(initial.getUuid()).getRemovalReasonConceptUUID());

        // Second call from a pre-feature client omits the field. The reason must not be wiped.
        GroupSubjectContract followUp = buildContractFor(groupSubject);
        followUp.setRemovalReasonConceptUUID(null);
        followUp.setVoided(true);
        try {
            groupSubjectController.save(followUp);
        } catch (ValidationException e) {
            fail("follow-up controller.save threw: " + e.getMessage());
        }
        assertEquals("Older client must not wipe a previously-saved removal reason",
                reasonUuid,
                groupSubjectRepository.findByUuid(followUp.getUuid()).getRemovalReasonConceptUUID());
    }
}
