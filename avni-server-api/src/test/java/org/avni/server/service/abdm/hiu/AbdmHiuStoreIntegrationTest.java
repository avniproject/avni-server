package org.avni.server.service.abdm.hiu;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertEquals;

// DEMO-ONLY abdm-hackathon: never merged.
// AbdmHiuStore's SQL against Postgres.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmHiuStoreIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private AbdmHiuStore store;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    // The hourly retry asks for each id with an update still parked, once, and leaves one parked over a week ago:
    // no request of ours took it in that time, so none will.
    @Test
    public void parkedConsentRequestIdsAreTheUnresolvedOnesOfTheLastWeekEachOnce() {
        store.parkConsentUpdate("abdm-A", "GRANTED", List.of("artefact-1"));
        store.parkConsentUpdate("abdm-A", "REVOKED", List.of("artefact-1"));
        store.parkConsentUpdate("abdm-B", "DENIED", List.of());
        store.parkConsentUpdate("abdm-C", "GRANTED", List.of("artefact-3"));
        store.parkedConsentUpdates("abdm-B").forEach(p -> store.resolveParked(p.id()));
        namedJdbc.getJdbcOperations().update("update abdm_parked_event set created_at = now() - interval '8 days' where reference = 'abdm-C'");

        assertEquals(List.of("abdm-A"), store.parkedConsentRequestIds());
    }
}
