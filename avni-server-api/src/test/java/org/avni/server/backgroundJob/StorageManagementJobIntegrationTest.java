package org.avni.server.backgroundJob;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ArchivalConfigRepository;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.IndividualRepository;
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

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class StorageManagementJobIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;
    @Autowired
    private ArchivalConfigRepository archivalConfigRepository;
    @Autowired
    private StorageManagementJob storageManagementJob;
    @Autowired
    private IndividualRepository individualRepository;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        SubjectType groupSubjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setGroup(true)
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("groupSubjectType")
                        .setName("groupSubjectType").build());

        Program program = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("program1")
                        .build(),
                subjectType);

        GroupRole groupRole = groupRoleRepository.save(
                new TestGroupRoleBuilder()
                        .withMandatoryFieldsForNewEntity()
                        .withGroupSubjectType(groupSubjectType)
                        .withMemberSubjectType(subjectType)
                        .build());

        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        Individual group = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(inTheCatchment).build());
        testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRole).withMember(inTheCatchment).withGroup(group).build());
    }

    @Test
    public void markSyncDisabledInOneGo() {
        saveArchivalConfig("select id from individual");
        int count = individualRepository.countBySyncDisabled(false);
        assertEquals(3, count);
        storageManagementJob.manage();
        assertEquals(count, individualRepository.countBySyncDisabled(true));
        assertEquals(0, individualRepository.countBySyncDisabled(false));
    }

    @Test
    public void markSyncDisabledInLoops() {
        saveArchivalConfig("select id from individual limit 1");
        int count = individualRepository.countBySyncDisabled(false);
        assertEquals(3, count);
        storageManagementJob.manage();
        assertEquals(count, individualRepository.countBySyncDisabled(true));
        assertEquals(0, individualRepository.countBySyncDisabled(false));
    }

    private void saveArchivalConfig(String query) {
        ArchivalConfig archivalConfig = new ArchivalConfig();
        archivalConfig.setSqlQuery(query);
        archivalConfig.setUuid(UUID.randomUUID().toString());
        archivalConfigRepository.save(archivalConfig);
    }
}
