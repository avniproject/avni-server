package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.UserSubjectAssignmentContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
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
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private IndividualService individualService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;

    @Test
    public void assignAllMemberSubjectsOnGroupAssignment___disallow_Member_Subject_To_Be_Assigned_To_User_Other_Than_One_Assigned_To_Group() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).setGroup(true).build());
        SubjectType memberSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_DirectAssignment").setName("st_DirectAssignment").setDirectlyAssignable(true).build());
        SubjectType memberSubjectTypeButNotDirectlyAssignable = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_NoDirectAssignment").setName("st_NoDirectAssignment").build());
        GroupRole groupRoleInvolvingDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectType).build());
        GroupRole groupRoleWithoutDirectAssignment = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(groupSubjectType).withMemberSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());

        Individual group1 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        Individual directlyAssignableMember1 = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectType).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleInvolvingDirectAssignment).withMember(directlyAssignableMember1).withGroup(group1).build());
        Individual groupNotDirectlyAssignable = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withLocation(testCatchmentData.getAddressLevel1()).withSubjectType(memberSubjectTypeButNotDirectlyAssignable).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRoleWithoutDirectAssignment).withMember(directlyAssignableMember1).withGroup(groupNotDirectlyAssignable).build());
        User user1 = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());
        User user2 = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user2@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());

        userSubjectAssignmentService.assignSubjects(createContract(user1, group1, false));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubject(user1, group1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubject(user1, directlyAssignableMember1));

        try {
            userSubjectAssignmentService.assignSubjects(createContract(user2, directlyAssignableMember1, false));
            fail();
        } catch (ValidationException ignored) {
        }

        userSubjectAssignmentService.assignSubjects(createContract(user2, group1, false));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubject(user1, group1));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubject(user1, directlyAssignableMember1));
    }

    @Test
    public void saveShouldUseTheSameEntityAndNotThrowUniqueConstrantException() throws ValidationException {
        TestDataSetupService.TestOrganisationData testOrganisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData testCatchmentData = testDataSetupService.setupACatchment();

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st_GroupForDirectAssignment").setName("st_GroupForDirectAssignment").setDirectlyAssignable(true).build());
        User user1 = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user1@example").withAuditUser(testOrganisationData.getUser()).organisationId(testOrganisationData.getOrganisationId()).build());
        Individual subject = individualService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(testCatchmentData.getAddressLevel1()).build());
        userSubjectAssignmentService.assignSubjects(createContract(user1, subject, false));
        userSubjectAssignmentService.assignSubjects(createContract(user1, subject, true));
        userSubjectAssignmentService.assignSubjects(createContract(user1, subject, false));
    }

    private UserSubjectAssignmentContract createContract(User user1, Individual subject, boolean voided) {
        UserSubjectAssignmentContract userSubjectAssignmentContract = new UserSubjectAssignmentContract();
        userSubjectAssignmentContract.setUserId(user1.getId());
        userSubjectAssignmentContract.setSubjectIds(Collections.singletonList(subject.getId()));
        userSubjectAssignmentContract.setVoided(voided);
        return userSubjectAssignmentContract;
    }
}
