package org.avni.server.service;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class IndividualServiceIntegration2Test extends AbstractControllerIntegrationTest {
    @Autowired
    private IndividualService individualService;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;
    @Autowired
    private TestDataSetupService testDataSetupService;

    @Test
    public void voidSubjectItemsAtLocation() throws ValidationException {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();

        // Metadata for Catchment based sync
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .build());
        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setGroup(true)
                        .setMandatoryFieldsForNewEntity()
                        .build());

        Program program = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("p1")
                        .build(),
                subjectType);

        GroupRole groupRole = groupRoleRepository.save(
                new TestGroupRoleBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withGroupSubjectType(groupSubjectType)
                        .withMemberSubjectType(subjectType)
                        .build());

        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType, groupSubjectType);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectType, program);

        Individual subject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        ProgramEnrolment enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(subject).build());
        Individual groupTypeSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        GroupSubject groupSubject = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRole).withMember(subject).withGroup(groupTypeSubject).build());

        individualService.voidSubjectsTree(catchmentData.getAddressLevel1());
        assertThat(testSubjectService.reload(subject).isVoided()).isTrue();
        assertThat(testProgramEnrolmentService.reload(enrolment).isVoided()).isTrue();
        assertThat(testSubjectService.reload(groupTypeSubject).isVoided()).isTrue();
        assertThat(testGroupSubjectService.reload(groupSubject).isVoided()).isTrue();
    }
}
