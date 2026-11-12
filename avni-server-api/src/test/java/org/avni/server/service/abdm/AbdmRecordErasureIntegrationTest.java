package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// R10: records received under a consent are erased when it ends even after the organisation has switched record sharing
// off; an organisation holding no consent or record is not touched. The real hourly job, runner and store.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmRecordErasureIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long HOLDING = 2L;
    private static final long EMPTY = 3L;

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
        for (String table : new String[]{"abdm_health_information", "abdm_data_flow", "abdm_consent_status", "abdm_consent", "abdm_job_run"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("update organisation_config set settings = settings || '{\"enableAbha\": true, \"enableAbdmRecordSharing\": false}'::jsonb " +
                "where organisation_id = ?", HOLDING);
        ekaSettings(HOLDING, "demo-admin");
        ekaSettings(EMPTY, "a-demo-admin");
    }

    @Test
    public void recordsOfAConsentPastItsEraseDateAreErasedWithRecordSharingOff_andAnOrganisationHoldingNothingIsNotTouched() {
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status, data_erase_at) " +
                "values (?, ?, 'HIU', 'cr-1', 'GRANTED', now() - interval '1 day')", UUID.randomUUID().toString(), HOLDING);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, consent_id, status, data_erase_at) " +
                "values (?, ?, 'HIU', 'cr-1', 'artefact-1', 'GRANTED', now() - interval '1 day')", UUID.randomUUID().toString(), HOLDING);
        jdbc.update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status) values ('flow-1', ?, 'HIU', 'artefact-1', 'RECEIVED')", HOLDING);
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (?, (select id from abdm_data_flow where uuid = 'flow-1'), 'tx-1', 'artefact-1')", HOLDING);

        job.expireConsentsAndEraseRecords();

        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = ?",
                Integer.class, HOLDING));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("select count(*) from abdm_consent where organisation_id = ? and status = 'EXPIRED'",
                Integer.class, HOLDING));
        Map<String, Object> run = jobRuns.of(HOLDING).get(AbdmHousekeepingJob.CONSENT_EXPIRY);
        assertNotNull(run.get("lastSuccessAt"));
        assertNull(run.get("lastError"));
        assertTrue(jobRuns.of(EMPTY).isEmpty());
        assertNull(jobRuns.of(HOLDING).get(AbdmHousekeepingJob.CONSENT_UPDATE_RETRY));
    }

    @Test
    public void anOrganisationHoldingRecordsWithNoUsableSystemUserIsReported() {
        jdbc.update("update external_system_config set config = config - 'avniSystemUser' where organisation_id = ? and system_name = 'Eka'", HOLDING);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values (?, ?, 'HIU', 'cr-1', 'GRANTED')",
                UUID.randomUUID().toString(), HOLDING);

        job.expireConsentsAndEraseRecords();

        String lastError = (String) jobRuns.of(HOLDING).get(AbdmHousekeepingJob.CONSENT_EXPIRY).get("lastError");
        assertTrue(lastError, lastError.contains("system user"));
    }

    private void ekaSettings(long organisationId, String systemUser) {
        jdbc.update("insert into external_system_config (organisation_id, uuid, version, created_by_id, last_modified_by_id, created_date_time, " +
                        "last_modified_date_time, system_name, config) values (?, ?, 0, 1, 1, now(), now(), 'Eka', " +
                        "jsonb_build_object('environment', 'sandbox', 'clientId', 'm1-client', 'hipIds', jsonb_build_array('hip-' || ?), " +
                        "'avniSystemUser', ?))",
                organisationId, UUID.randomUUID().toString(), organisationId, systemUser);
    }
}
