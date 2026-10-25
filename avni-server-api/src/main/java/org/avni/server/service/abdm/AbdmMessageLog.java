package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The abdm_message table: every ABDM message in and out, for the demo's traffic view. Never the session call.
// Swallows its own failures: a log row is never a reason to fail a flow. Each insert runs in its own transaction, so a
// failed insert cannot abort a caller's transaction. DEMO-ONLY: bodies are stored in plaintext.
// No organisation in context (a super admin calling the bridge admin endpoints) means no row: the table's organisation_id
// is not null and its row-level security is per organisation.
@Component
public class AbdmMessageLog {
    private static final Logger logger = LoggerFactory.getLogger(AbdmMessageLog.class);
    private static final int MAX_RECENT = 500;
    private static final String INSERT = "insert into abdm_message (organisation_id, direction, method, path, request_id, correlation_id, http_status, body) " +
            "values (:organisationId, :direction, :method, :path, :requestId, :correlationId, :httpStatus, cast(:body as jsonb))";
    private static final String RECENT = "select id, direction, method, path, request_id, correlation_id, http_status, created_at, body::text as body " +
            "from abdm_message order by id desc limit :limit";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    @Autowired
    public AbdmMessageLog(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void outgoing(String method, String path, String requestId, JsonNode body, Integer httpStatus) {
        insert("OUT", method, path, requestId, body, httpStatus);
    }

    public void incoming(String path, AbdmCallbackHeaders headers, JsonNode body) {
        insert("IN", "POST", path, headers == null ? null : headers.requestId(), body, 202);
    }

    public List<Map<String, Object>> recent(int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_RECENT));
        return jdbc.query(RECENT, new MapSqlParameterSource("limit", bounded), (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("direction", rs.getString("direction"));
            row.put("method", rs.getString("method"));
            row.put("path", rs.getString("path"));
            row.put("requestId", rs.getString("request_id"));
            row.put("correlationId", rs.getString("correlation_id"));
            int status = rs.getInt("http_status");
            row.put("httpStatus", rs.wasNull() ? null : status);
            Timestamp createdAt = rs.getTimestamp("created_at");
            row.put("createdAt", createdAt == null ? null : AbdmTime.format(createdAt.toInstant()));
            row.put("body", readQuietly(rs.getString("body")));
            return row;
        });
    }

    private void insert(String direction, String method, String path, String requestId, JsonNode body, Integer httpStatus) {
        Long organisationId = organisationId();
        if (organisationId == null) return;
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("organisationId", organisationId)
                    .addValue("direction", direction)
                    .addValue("method", method)
                    .addValue("path", truncate(path, 500))
                    .addValue("requestId", truncate(requestId, 100))
                    .addValue("correlationId", truncate(body == null ? null : AbdmJson.requestIdOf(body), 100))
                    .addValue("httpStatus", httpStatus)
                    .addValue("body", body == null || body.isNull() ? null : AbdmJson.write(body));
            ownTransaction.executeWithoutResult(status -> jdbc.update(INSERT, params));
        } catch (RuntimeException e) {
            logger.warn("ABDM message not logged ({} {} {}): {}", direction, method, path, e.getClass().getSimpleName());
        }
    }

    private static Long organisationId() {
        Organisation organisation = UserContextHolder.getOrganisation();
        return organisation == null ? null : organisation.getId();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static JsonNode readQuietly(String json) {
        try {
            return AbdmJson.read(json);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
