package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
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

import static org.junit.Assert.fail;

@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class UserSubjectAssignmentServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;
    @Autowired
    private IndividualService individualService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;

    @Test
    public void disallow_Member_Subject_To_Be_Assigned_To_User_Other_Than_One_Assigned_To_Group() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        SubjectType memberSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        SubjectType memberSubjectTypeButNotDirectlyAssignable = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_NoDirectAssignment").setName("st_NoDirectAssignment").build());
        GroupRole groupRoleInvolvingDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectType).build());
        GroupRole groupRoleWithoutDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());

        Individual group = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember).withGroup(group).build());
        Individual groupNotDirectlyAssignable = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleWithoutDirectAssignment).withMember(directlyAssignableMember).withGroup(groupNotDirectlyAssignable).build());
        User user1 = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());
        User user2 = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user2@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());

        userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(group).withUser(user1).build());
        try {
            userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(directlyAssignableMember).withUser(user2).build());
            fail();
        } catch (ValidationException ignored) {
        }
        userSubjectAssignmentService.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(directlyAssignableMember).withUser(user1).build());
    }
}
