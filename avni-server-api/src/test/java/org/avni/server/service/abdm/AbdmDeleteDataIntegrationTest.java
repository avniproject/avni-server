package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

// Delete data on a trial organisation with ABDM rows: abdm_care_context and abdm_link_request hold its subjects, so
// the delete fails unless every abdm_* row of the organisation goes first. Not @Transactional: the delete commits.
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbdmDeleteDataIntegrationTest extends AbstractControllerIntegrationTest {
    // Every ABDM table this test fills. A new abdm_* table fails abdmTablesAreTheOnesThisTestFills until it is added
    // here and to delete data.
    private static final List<String> ABDM_TABLES = List.of("abdm_care_context", "abdm_consent", "abdm_consent_status",
            "abdm_data_flow", "abdm_health_information", "abdm_inbound_event", "abdm_job_run", "abdm_link_request", "abdm_message",
            "abdm_parked_event");
    private static final String SUBJECT_TYPE = "9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3";

    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    // An organisation with a parent has its rows deleted through the repositories.
    @Test
    public void deleteDataClearsTheAbdmRowsOfAnOrganisationWithAParent() {
        assertDeleteDataLeavesNoAbdmRows("abdmdeletechild", true);
    }

    // An organisation with no parent has its rows deleted table by table.
    @Test
    public void deleteDataClearsTheAbdmRowsOfAnOrganisationWithoutAParent() {
        assertDeleteDataLeavesNoAbdmRows("abdmdelete", false);
    }

    @Test
    public void abdmTablesAreTheOnesThisTestFills() {
        setUser(userRepository.getDefaultSuperAdmin());
        List<String> inSchema = new JdbcTemplate(dataSource).queryForList("select table_name from information_schema.columns " +
                "where table_schema = 'public' and table_name like 'abdm\\_%' and column_name = 'organisation_id'", String.class);
        assertEquals(new TreeSet<>(ABDM_TABLES), new TreeSet<>(inSchema));
    }

    private void assertDeleteDataLeavesNoAbdmRows(String organisationSuffix, boolean withParent) {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation(organisationSuffix);
        long organisationId = organisation.getOrganisationId();
        setUser(userRepository.getDefaultSuperAdmin());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if (withParent) {
            jdbc.update("update public.organisation set parent_organisation_id = 1 where id = ?", organisationId);
        }
        String subjectUUID = UUID.randomUUID().toString();
        jdbc.update("insert into public.individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, last_name, " +
                        "gender_id, organisation_id, subject_type_id, observations, created_by_id, last_modified_by_id, created_date_time, " +
                        "last_modified_date_time) values (?, 1, 1, current_date, false, 'ABDM', 'Patient', 1, ?, " +
                        "(select id from public.subject_type where uuid = ?), '{}'::jsonb, 1, 1, now(), now())",
                subjectUUID, organisationId, SUBJECT_TYPE);
        insertOneRowInEveryAbdmTable(jdbc, organisationId, subjectUUID);
        for (String table : ABDM_TABLES) {
            assertEquals(table, Integer.valueOf(1), count(jdbc, table, organisationId));
        }
        setUser(organisation.getUser());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                organisationService.deleteTransactionalData(organisationRepository.findOne(organisationId)));

        setUser(userRepository.getDefaultSuperAdmin());
        for (String table : ABDM_TABLES) {
            assertEquals(table, Integer.valueOf(0), count(jdbc, table, organisationId));
        }
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from public.individual where uuid = ?", Integer.class, subjectUUID));
    }

    private static void insertOneRowInEveryAbdmTable(JdbcTemplate jdbc, long org, String subjectUUID) {
        String individual = "(select id from public.individual where uuid = '" + subjectUUID + "')";
        jdbc.update("insert into public.abdm_message (organisation_id, direction, path) values (?, 'OUT', '/test')", org);
        jdbc.update("insert into public.abdm_care_context (uuid, organisation_id, individual_id, hip_id, patient_reference, reference, " +
                "display, hi_type, status) values (?, ?, " + individual + ", 'hip-1', ?, 'visit-1', 'Visit', 'OPConsultation', 'QUEUED')",
                UUID.randomUUID().toString(), org, subjectUUID);
        jdbc.update("insert into public.abdm_link_request (organisation_id, transaction_id, link_reference, individual_id, status) " +
                "values (?, 'txn-1', 'link-1', " + individual + ", 'PENDING')", org);
        jdbc.update("insert into public.abdm_consent (uuid, organisation_id, role, status) values (?, ?, 'HIU', 'REQUESTED')",
                UUID.randomUUID().toString(), org);
        jdbc.update("insert into public.abdm_consent_status (organisation_id, consent_row_id, status) " +
                "values (?, (select id from public.abdm_consent where organisation_id = ?), 'REQUESTED')", org, org);
        jdbc.update("insert into public.abdm_data_flow (uuid, organisation_id, role, consent_id, status) values (?, ?, 'HIU', 'consent-1', 'REQUESTED')",
                UUID.randomUUID().toString(), org);
        jdbc.update("insert into public.abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (?, (select id from public.abdm_data_flow where organisation_id = ?), 'txn-1', 'consent-1')", org, org);
        jdbc.update("insert into public.abdm_parked_event (organisation_id, kind, reference) values (?, 'consent', 'request-1')", org);
        jdbc.update("insert into public.abdm_inbound_event (organisation_id, source, event_id, status) values (?, 'eka', 'event-1', 'RECEIVED')", org);
        jdbc.update("insert into public.abdm_job_run (organisation_id, task, last_success_at) values (?, 'visitLinking', now())", org);
    }

    private static Integer count(JdbcTemplate jdbc, String table, long org) {
        return jdbc.queryForObject("select count(*) from public." + table + " where organisation_id = ?", Integer.class, org);
    }
}
