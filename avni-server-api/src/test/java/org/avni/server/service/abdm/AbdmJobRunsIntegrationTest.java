package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Each organisation's last success and last error of each ABDM job, kept in abdm_job_run for the status page.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmJobRunsIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    private AbdmJobRuns jobRuns;

    @Test
    public void theLatestRunOfEachTaskIsKept_andASuccessClearsTheError() {
        jobRuns.succeeded(2L, "visitLinking", T0);
        jobRuns.failed(2L, "visitLinking", "IllegalStateException: database down", T0.plusSeconds(300));
        jobRuns.failed(2L, "consentExpiry", "IllegalStateException: database down", T0);
        jobRuns.succeeded(3L, "visitLinking", T0);

        assertEquals(Map.of(
                "consentExpiry", job(null, "IllegalStateException: database down"),
                "visitLinking", job("2026-11-12T10:00:00.000Z", "IllegalStateException: database down")), jobRuns.of(2L));

        jobRuns.succeeded(2L, "visitLinking", T0.plusSeconds(600));

        assertEquals(job("2026-11-12T10:10:00.000Z", null), jobRuns.of(2L).get("visitLinking"));
        assertEquals(Map.of("visitLinking", job("2026-11-12T10:00:00.000Z", null)), jobRuns.of(3L));
    }

    // Carried from R2-3's review: a failing run alarms on its first failure, or the first after a success, and then at most
    // once an hour while it keeps failing; every failure is kept for the status page either way.
    @Test
    public void aFailureIsDueAnAlarmFirstAfterASuccess_thenAtMostHourly() {
        jobRuns.succeeded(2L, "visitLinking", T0);

        assertTrue(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 503", T0.plusSeconds(300)));
        assertFalse(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 503", T0.plusSeconds(600)));
        assertFalse(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 401", T0.plusSeconds(300 + 3599)));
        assertEquals("EkaCallException: HTTP 401", jobRuns.of(2L).get("visitLinking").get("lastError"));
        assertTrue(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 503", T0.plusSeconds(300 + 3600)));
        assertFalse(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 503", T0.plusSeconds(300 + 3900)));

        jobRuns.succeeded(2L, "visitLinking", T0.plusSeconds(300 + 4000));

        assertTrue(jobRuns.failed(2L, "visitLinking", "EkaCallException: HTTP 503", T0.plusSeconds(300 + 4300)));
        assertTrue(jobRuns.failed(3L, "consentExpiry", "IllegalStateException: database down", T0));
    }

    private static Map<String, Object> job(String lastSuccessAt, String lastError) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("lastSuccessAt", lastSuccessAt);
        job.put("lastError", lastError);
        return job;
    }
}
