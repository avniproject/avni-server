package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// Traffic and webhook events past the retention can carry a patient's details, so they are deleted in every organisation
// holding them, after record sharing is switched off too. The real daily job, runner, log and inbox.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmTrafficPurgeIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long SWITCHED_OFF = 2L;
    private static final long EMPTY = 3L;
    private static final long NO_SYSTEM_USER = 1L;

    @Autowired
    private AbdmHousekeepingJob job;
    @Autowired
    private AbdmJobRuns jobRuns;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    private JdbcTemplate jdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jdbc = (JdbcTemplate) namedJdbc.getJdbcOperations();
        for (String table : new String[]{"abdm_message", "abdm_inbound_event", "abdm_job_run"}) jdbc.update("delete from " + table);
        jdbc.update("update organisation_config set settings = settings || '{\"enableAbha\": true, \"enableAbdmRecordSharing\": false}'::jsonb " +
                "where organisation_id = ?", SWITCHED_OFF);
        jdbc.update("insert into external_system_config (organisation_id, uuid, version, created_by_id, last_modified_by_id, created_date_time, " +
                        "last_modified_date_time, system_name, config) values (?, ?, 0, 1, 1, now(), now(), 'Eka', " +
                        "jsonb_build_object('environment', 'sandbox', 'clientId', 'm1-client', 'avniSystemUser', 'demo-admin'))",
                SWITCHED_OFF, UUID.randomUUID().toString());
    }

    @Test
    public void oldTrafficAndAnOldFailedEventAreDeletedWithRecordSharingOff_andOnlyThatOrganisationsRows() {
        message(SWITCHED_OFF, "old", "now() - interval '40 days'");
        message(SWITCHED_OFF, "recent", "now()");
        message(NO_SYSTEM_USER, "other-old", "now() - interval '40 days'");
        message(NO_SYSTEM_USER, "other-recent", "now()");
        jdbc.update("insert into abdm_inbound_event (organisation_id, source, event_id, event_name, route, body, status, attempts, next_attempt_at, " +
                "received_at) values (?, 'eka', 'failed-1', 'abha.hiu_data_push', 'HIU_DATA_PUSH', null, 'FAILED', 1, now(), now() - interval '40 days')",
                SWITCHED_OFF);

        job.purgeOldTraffic();

        assertEquals(List.of("recent"), jdbc.queryForList("select path from abdm_message where organisation_id = ?", String.class, SWITCHED_OFF));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_inbound_event where organisation_id = ?", Integer.class,
                SWITCHED_OFF));
        assertNotNull(jobRuns.of(SWITCHED_OFF).get(AbdmHousekeepingJob.TRAFFIC_PURGE).get("lastSuccessAt"));
        // F1: an organisation with no system user has its old rows deleted with no login, naming it, and is still reported.
        assertEquals(List.of("other-recent"), jdbc.queryForList("select path from abdm_message where organisation_id = ?", String.class,
                NO_SYSTEM_USER));
        String lastError = (String) jobRuns.of(NO_SYSTEM_USER).get(AbdmHousekeepingJob.TRAFFIC_PURGE).get("lastError");
        assertTrue(lastError, lastError.contains("system user"));
        assertTrue(jobRuns.of(EMPTY).isEmpty());
    }

    private void message(long organisationId, String path, String createdAt) {
        jdbc.update("insert into abdm_message (organisation_id, direction, path, created_at) values (?, 'IN', ?, " + createdAt + ")",
                organisationId, path);
    }
}
