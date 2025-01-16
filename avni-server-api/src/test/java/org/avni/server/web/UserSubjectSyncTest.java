package org.avni.server.web;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.UserSubjectRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.UserService;
import org.avni.server.service.builder.*;
import org.avni.server.service.sync.TestSyncService;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class UserSubjectSyncTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private UserSubjectRepository userSubjectRepository;
    @Autowired
    private UserService userService;

    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;

    @Autowired
    private TestSyncService testSyncService;

    private TestDataSetupService.TestOrganisationData organisationData;
    private SubjectType subjectType;
    private Program program;

    @Before
    public void setup() {
        organisationData = testDataSetupService.setupOrganisation();
        testDataSetupService.setupACatchment();

        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType")
                        .setName("subjectType")
                        .setType(Subject.User)
                        .build());
        program = testProgramService.addProgram(
                new ProgramBuilder()
                        .withName("p")
                        .build(),
                subjectType);

        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), subjectType, program);
    }

    @Test
    @Ignore
    public void syncSubjectAndEnrolment() {
        userService.ensureSubjectForUser(organisationData.getUser(), subjectType);
        userService.ensureSubjectForUser(organisationData.getUser2(), subjectType);

        UserSubject userSubject = userSubjectRepository.findByUser(organisationData.getUser());
        ProgramEnrolment enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(userSubject.getSubject()).build());
        userSubject.getSubject().addEnrolment(enrolment);

        List syncDetails = testSyncService.getSyncDetails();

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), subjectType.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Program.name(), program.getUuid())));

        List<Individual> subjects = testSyncService.getSubjects(subjectType);
        assertEquals(1, subjects.size());
    }
}
