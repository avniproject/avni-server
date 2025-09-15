package org.avni.server.backgroundJob;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.StorageManagementConfigRepository;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
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
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class StorageManagementConfigJobIntegrationTest extends AbstractControllerIntegrationTest {
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
    private StorageManagementConfigRepository storageManagementConfigRepository;
    @Autowired
    private StorageManagementJob storageManagementJob;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Individual memberSubject;
    private Individual groupSubject;
    private GroupSubject group;

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

        memberSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        groupSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
        testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(memberSubject).build());
        group = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRole).withMember(memberSubject).withGroup(groupSubject).build());
    }

    @Test(expected = UncategorizedSQLException.class)
    public void doNotAllowEnrolmentsSyncDisabledToGoOutOfSyncWithSubjectOnSingleRowUpdate() {
        jdbcTemplate.execute("update individual set sync_disabled = false, sync_disabled_date_time = null where id = "
                + memberSubject.getId());
        jdbcTemplate.execute("update program_enrolment set sync_disabled = true, sync_disabled_date_time = now() where individual_id = " + memberSubject.getId());
    }

    @Test(expected = UncategorizedSQLException.class)
    public void doNotAllowEnrolmentsSyncDisabledToGoOutOfSyncWithSubject() {
        jdbcTemplate.execute("update individual set sync_disabled = false, sync_disabled_date_time = null where 1 = 1");
        jdbcTemplate.execute("update program_enrolment set sync_disabled = true, sync_disabled_date_time = now() where 1 = 1");
    }

    @Test
    public void allowGroupSubjectSyncToBeDisabledEvenIfOneSubjectInDisable() {
        jdbcTemplate.execute("update individual set sync_disabled = false, sync_disabled_date_time = null where id = "
                + memberSubject.getId());
        jdbcTemplate.execute("update group_subject set sync_disabled = true, sync_disabled_date_time = now() where member_subject_id = " + memberSubject.getId());
    }

    @Test(expected = UncategorizedSQLException.class)
    public void doNotAllowGroupSubjectSyncToBeEnabledIfEitherOfSubjectsSyncIsEnabled() {
        jdbcTemplate.execute("update individual set sync_disabled = true, sync_disabled_date_time = null where id = "
                + memberSubject.getId());
        jdbcTemplate.execute("update group_subject set sync_disabled = false, sync_disabled_date_time = null where id = " + group.getId());
    }

    @Test
    public void markSyncDisabledInOneGo() {
        saveStorageManagementConfig("select id from individual");
        int count = individualRepository.countBySyncDisabled(false);
        assertEquals(3, count);
        assertNotEquals(0, programEnrolmentRepository.countBySyncDisabled(false));
        storageManagementJob.manage();
        assertEquals(count, individualRepository.countBySyncDisabled(true));
        assertEquals(0, individualRepository.countBySyncDisabled(false));
        assertEquals(0, programEnrolmentRepository.countBySyncDisabled(false));
    }

    @Test
    public void markSyncDisabledInLoops() {
        saveStorageManagementConfig("select id from individual limit 1");
        int count = individualRepository.countBySyncDisabled(false);
        assertEquals(3, count);
        int enrolmentCount = programEnrolmentRepository.countBySyncDisabled(false);
        assertNotEquals(0, enrolmentCount);
        storageManagementJob.manage();
        assertEquals(count, individualRepository.countBySyncDisabled(true));
        assertEquals(0, individualRepository.countBySyncDisabled(false));
        assertEquals(0, programEnrolmentRepository.countBySyncDisabled(false));
    }

    private void saveStorageManagementConfig(String query) {
        StorageManagementConfig storageManagementConfig = new StorageManagementConfig();
        storageManagementConfig.setSqlQuery(query);
        storageManagementConfig.setUuid(UUID.randomUUID().toString());
        storageManagementConfigRepository.save(storageManagementConfig);
    }
}
