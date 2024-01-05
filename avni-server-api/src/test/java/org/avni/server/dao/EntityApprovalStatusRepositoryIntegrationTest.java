package org.avni.server.dao;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.domain.txn.EntityApprovalStatusBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.fail;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class EntityApprovalStatusRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private EntityApprovalStatusRepository entityApprovalStatusRepository;
    @Autowired
    private ApprovalStatusRepository approvalStatusRepository;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    private SubjectType st1;
    private ApprovalStatus pending;
    private Individual subject;

    @Before
    public void setUp() throws Exception {
        testDataSetupService.setupOrganisation();
        st1 = new SubjectTypeBuilder().setType(Subject.Individual).setName("ST1").build();
        testSubjectTypeService.createWithDefaults(st1);
        subject = new SubjectBuilder().withSubjectType(st1).withFirstName("foo").withRegistrationDate(new LocalDate()).build();
        testSubjectService.save(subject);
        pending = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Pending);
        EntityApprovalStatus entityApprovalStatus1 = new EntityApprovalStatusBuilder()
                .setEntityType(EntityApprovalStatus.EntityType.Subject)
                .setApprovalStatus(pending).setEntityTypeUuid(st1.getUuid())
                .setStatusDateTime(new DateTime().minusDays(1))
                .setEntityId(subject.getId())
                .setIndividual(subject).build();
        entityApprovalStatusRepository.saveEAS(entityApprovalStatus1);
    }

    @Test
    public void doNotAllowDuplicateEAS() {
        EntityApprovalStatus entityApprovalStatus2 = new EntityApprovalStatusBuilder()
                .setEntityType(EntityApprovalStatus.EntityType.Subject)
                .setApprovalStatus(pending).setEntityTypeUuid(st1.getUuid())
                .setStatusDateTime(new DateTime())
                .setEntityId(subject.getId())
                .setIndividual(subject).build();
        try {
            entityApprovalStatusRepository.saveEAS(entityApprovalStatus2);
            fail();
        } catch (RuntimeException re) {
        }
    }

    @Test
    public void allow_Same_EAS_If_There_Is_Intermediate_EAS_With_Different_Status() {
        ApprovalStatus rejected = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Rejected);
        EntityApprovalStatus entityApprovalStatus2 = new EntityApprovalStatusBuilder()
                .setEntityType(EntityApprovalStatus.EntityType.Subject)
                .setApprovalStatus(rejected).setEntityTypeUuid(st1.getUuid())
                .setStatusDateTime(new DateTime())
                .setEntityId(subject.getId())
                .setIndividual(subject).build();
        entityApprovalStatusRepository.saveEAS(entityApprovalStatus2);

        EntityApprovalStatus entityApprovalStatus3 = new EntityApprovalStatusBuilder()
                .setEntityType(EntityApprovalStatus.EntityType.Subject)
                .setApprovalStatus(pending).setEntityTypeUuid(st1.getUuid())
                .setStatusDateTime(new DateTime())
                .setEntityId(subject.getId())
                .setIndividual(subject).build();
        entityApprovalStatusRepository.saveEAS(entityApprovalStatus3);
    }
}
