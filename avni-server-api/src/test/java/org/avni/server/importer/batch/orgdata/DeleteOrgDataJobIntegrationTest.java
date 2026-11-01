package org.avni.server.importer.batch.orgdata;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.OrganisationCategoryRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.*;

/**
 * avni-webapp#1639: deleting an organisation's data ran inside the HTTP request and timed out (504) at the gateway.
 * The deletion now runs as a background job: POST /implementation/delete/job returns 202 with the job uuid straight
 * away, and GET /implementation/delete/job/status reports how the latest deletion for the caller's organisation went.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class DeleteOrgDataJobIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String START = "/implementation/delete/job?deleteMetadata=%s&deleteAdminConfig=%s";
    private static final String STATUS = "/implementation/delete/job/status";

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private OrganisationCategoryRepository organisationCategoryRepository;
    @Autowired
    private DeleteOrgDataJobService deleteOrgDataJobService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Organisation organisation;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestDataSetupService.TestOrganisationData orgData = testDataSetupService.setupOrganisation();
        Group group = orgData.getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);
        organisation = organisationRepository.findOne(orgData.getOrganisationId());

        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("st-1639").setName("st-1639").build());
        testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());
    }

    private void setCategoryToPrototype() {
        // ids follow the insert order in V1_339_3__OrgCategoryTable.sql: 1 Production, 2 UAT, 3 Prototype
        organisation.setCategory(organisationCategoryRepository.findEntity(3L));
        organisationRepository.save(organisation);
    }

    private long subjectCount() {
        return jdbcTemplate.queryForObject("select count(*) from individual where organisation_id = ?", Long.class, organisation.getId());
    }

    private BatchJobStatus waitForJobToEnd() throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            ResponseEntity<BatchJobStatus> response = template.getForEntity(base + STATUS.substring(1), BatchJobStatus.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            String status = response.getBody().status();
            if (!status.equals("STARTING") && !status.equals("STARTED")) return response.getBody();
            Thread.sleep(500);
        }
        fail("Deletion job did not finish in 60 seconds");
        return null;
    }

    @Test
    public void startReturnsTheJobUuidAtOnceAndStatusReportsCompletion() throws InterruptedException {
        setCategoryToPrototype();
        assertEquals(1, subjectCount());

        ResponseEntity<String> started = template.postForEntity(base + String.format(START, false, false).substring(1), null, String.class);

        assertEquals(HttpStatus.ACCEPTED, started.getStatusCode());
        assertNotNull(started.getBody());
        assertEquals(36, started.getBody().length());
        BatchJobStatus finished = waitForJobToEnd();
        assertEquals("COMPLETED", finished.status());
        assertEquals(0, subjectCount());
    }

    @Test
    public void refusalIsUnchangedAndStartsNoJob() {
        // setupOrganisation() puts the organisation in the Production category
        ResponseEntity<String> refused = template.postForEntity(base + String.format(START, false, false).substring(1), null, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
        assertEquals("Production or UAT organisation's data cannot be deleted", refused.getBody());
        assertEquals("NOT_FOUND", template.getForEntity(base + STATUS.substring(1), BatchJobStatus.class).getBody().status());
        assertEquals(1, subjectCount());
    }

    @Test
    public void aFailedDeletionIsReportedWithItsMessageAndLeavesTheDataInPlace() throws InterruptedException {
        setCategoryToPrototype();
        // bypass the up-front check to make the job itself fail
        deleteOrgDataJobService.start(false, true);

        BatchJobStatus finished = waitForJobToEnd();
        assertEquals("FAILED", finished.status());
        assertEquals("FAILED", finished.exitCode());
        assertEquals("You cannot delete admin config data without deleting metadata", finished.exitMessage());
        assertEquals(1, subjectCount());
    }
}
