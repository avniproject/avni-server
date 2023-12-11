package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
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

    @Test
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
}
