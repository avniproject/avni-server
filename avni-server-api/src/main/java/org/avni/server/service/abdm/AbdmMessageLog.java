package org.avni.server.service.abdm;

import org.avni.server.service.abdm.hip.AbdmFlows;
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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.List;

// The abdm_message table: every ABDM message in and out, redacted, so a failed exchange can be traced. Never the session call.
// Swallows its own failures: a log row is never a reason to fail a flow. Each insert runs in its own transaction, so a
// failed insert cannot abort a caller's transaction. Bodies are kept only as redact leaves them, and AbdmHousekeepingJob
// deletes rows older than abdm.message-retention-days.
// No organisation in context (a super admin calling the bridge admin endpoints) means no row: the table's organisation_id
// is not null and its row-level security is per organisation.
@Component
public class AbdmMessageLog {
    private static final Logger logger = LoggerFactory.getLogger(AbdmMessageLog.class);
    public static final String MASK = "***";
    private static final String INSERT = "insert into abdm_message (organisation_id, direction, method, path, request_id, http_status, body) " +
            "values (:organisationId, :direction, :method, :path, :requestId, :httpStatus, cast(:body as jsonb))";

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
                    .addValue("httpStatus", httpStatus)
                    .addValue("body", body == null || body.isNull() ? null : AbdmJson.write(redact(body)));
            ownTransaction.executeWithoutResult(status -> jdbc.update(INSERT, params));
        } catch (RuntimeException e) {
            logger.warn("ABDM message not logged ({} {} {}): {}", direction, method, path, e.getClass().getSimpleName());
        }
    }

    // What the traffic log may keep: ids, statuses, record types, dates and errors. Every other value, at any depth, is
    // masked, so a name, an ABHA, an Eka OID, a phone number, a record or a key never reaches the table (AVNI-13).
    private static final Set<String> KEPT = Set.of("service", "event", "event_time", "timestamp", "transaction_id", "transactionId",
            "request_id", "requestId", "txn_id", "status", "consent_id", "consentId", "consent_request_id", "consentRequestId",
            "consent_init_id", "hip_id", "hipId", "hiu_id", "hiuId", "hi_type", "hiType", "hi_types", "hiTypes", "record_types",
            "purpose", "care_context_id", "careContextReference", "code", "message", "error", "success", "description", "media",
            "checksum", "access_mode", "from", "to", "expiry", "erase_at", "dataEraseAt", "linkRefNumber", "count", "crypto_alg",
            "curve", "business_id", "client_id", "c_at", "u_at", "retry_count", "link_mode");
    private static final Set<String> FREE_TEXT = Set.of("message", "error", "description");
    // An "id" is kept only as a consent artefact's.
    private static final Set<String> ID_KEPT_UNDER = Set.of("consentArtefacts", "consent_artefacts", "consentArtefact");

    public static JsonNode redact(JsonNode body) {
        return body == null ? null : redact(body, null, false);
    }

    private static JsonNode redact(JsonNode node, String parent, boolean keep) {
        if (node.isObject()) {
            ObjectNode out = AbdmJson.obj();
            node.fields().forEachRemaining(f -> {
                String key = f.getKey();
                boolean keepChild = KEPT.contains(key) || ("id".equals(key) && ID_KEPT_UNDER.contains(parent));
                out.set(key, redact(f.getValue(), key, keepChild));
            });
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = AbdmJson.arr();
            node.forEach(n -> out.add(redact(n, parent, keep)));
            return out;
        }
        if (keep && node.isTextual() && FREE_TEXT.contains(parent)) return TextNode.valueOf(AbdmFlows.maskIdentifiers(node.asText()));
        if (keep || node.isNull() || node.isBoolean()) return node;
        return TextNode.valueOf(MASK);
    }

    // The organisation the thread acts for only, named as well as filtered by row-level security; none without one.
    public void purgeOlderThan(Duration age) {
        Long organisationId = organisationId();
        if (organisationId == null) return;
        int deleted = jdbc.update("delete from abdm_message where organisation_id = :org and created_at < :cutoff",
                new MapSqlParameterSource("org", organisationId).addValue("cutoff", Timestamp.from(Instant.now().minus(age))));
        logger.info("ABDM traffic older than {} days deleted: {} message(s)", age.toDays(), deleted);
    }

    // Organisations with traffic older than the cutoff, for the retention purge, which is owed whatever their switches.
    // Across organisations: called by the job runner before it acts as each.
    public List<Long> organisationsWithTrafficBefore(Instant before) {
        return jdbc.queryForList("select distinct organisation_id from abdm_message where created_at < :before order by 1",
                new MapSqlParameterSource("before", Timestamp.from(before)), Long.class);
    }

    private static Long organisationId() {
        Organisation organisation = UserContextHolder.getOrganisation();
        return organisation == null ? null : organisation.getId();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
