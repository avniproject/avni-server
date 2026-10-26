package org.avni.server.service.abdm;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;

// DEMO-ONLY abdm-hackathon: never merged.
// Inbound events already taken (V1_414 abdm_inbound_event), so a webhook delivered again, before or after a restart, has
// one effect (AVNI-15). claim inserts the event's id and says whether this call was the first; release takes the claim
// back when handling failed, so the sender's next delivery is handled. Runs as the organisation (row-level security).
@Component
public class AbdmInboundEvents {
    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmInboundEvents(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    public boolean claim(String source, String eventId) {
        Integer inserted = jdbc.update("insert into abdm_inbound_event (organisation_id, source, event_id) values (:org, :source, :id) " +
                "on conflict (organisation_id, source, event_id) do nothing", params(source, eventId));
        return inserted != null && inserted > 0;
    }

    public void release(String source, String eventId) {
        jdbc.update("delete from abdm_inbound_event where organisation_id = :org and source = :source and event_id = :id", params(source, eventId));
    }

    private MapSqlParameterSource params(String source, String eventId) {
        return new MapSqlParameterSource("org", orgContext.organisationId()).addValue("source", source, Types.VARCHAR)
                .addValue("id", eventId, Types.VARCHAR);
    }
}
