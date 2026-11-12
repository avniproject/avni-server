package org.avni.server.service.abdm.hip;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

// The order of AbdmHipStore's statements where a concurrent job depends on it.
public class AbdmHipStoreTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AbdmHipStore store = new AbdmHipStore(jdbc, AbdmTestSupport.orgContext());

    // The stamp locks the patient's queued and failed visits first, so a job about to set them waiting blocks on them and,
    // once this commits, sees them changed and leaves them; then the waiting ones are queued again.
    @Test
    public void aVerifiedAbhaStampsThePatientsQueuedVisitsBeforeQueueingTheWaitingOnes() {
        store.requeueForVerifiedAbha("subject-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(jdbc);
        order.verify(jdbc, times(2)).update(sql.capture(), any(MapSqlParameterSource.class));
        List<String> statements = sql.getAllValues();
        assertTrue(statements.get(0), statements.get(0).startsWith("update abdm_care_context c set updated_at = now()"));
        assertTrue(statements.get(1), statements.get(1).startsWith("update abdm_care_context c set status = 'QUEUED'"));
    }
}
