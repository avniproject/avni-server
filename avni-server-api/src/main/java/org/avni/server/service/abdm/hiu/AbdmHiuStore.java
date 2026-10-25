package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// The HIU's ABDM state (V1_413), all rows role HIU. In abdm_consent a consent request is the row with no consent_id; each
// artefact granted under it is a row of its own carrying the request's consent_request_id. Plain JDBC, autocommitted per
// statement; organisation_id set here. Every lookup names the role: in mock mode this server is the HIP too, and the two
// sides share consent and transaction ids.
@Component
public class AbdmHiuStore {
    public static final String ROLE = "HIU";

    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmHiuStore(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    public record Consent(long id, String uuid, String consentRequestId, String consentId, String initRequestId,
                          String fetchRequestId, String abhaAddress, String hipId, String hiuId, String purposeCode,
                          String hiTypes, Instant dateFrom, Instant dateTo, Instant dataEraseAt, String status,
                          JsonNode consentDetail, String error, Instant createdAt, Instant updatedAt) {
    }

    public record Artefact(String consentRequestId, String consentId, String status, String dataFlowStatus, long records) {
    }

    public record DataFlow(long id, String uuid, String consentId, String transactionId, String requestId,
                           String encryptedPrivateKey, String publicKey, String nonce, Instant keyExpiry, String status,
                           Integer entries, String error) {
    }

    public record Record(String careContextReference, String hipId, String transactionId, Instant receivedAt,
                         Boolean checksumMatched, JsonNode bundle) {
    }

    // ---- consent requests

    public void insertConsentRequest(String uuid, String abhaAddress, String hiuId, String purposeCode, String hiTypes,
                                     Instant dateFrom, Instant dateTo, Instant dataEraseAt, String initRequestId, String status) {
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, init_request_id, abha_address, hiu_id, purpose_code, hi_types, " +
                        "date_from, date_to, data_erase_at, status) values (:uuid, :org, 'HIU', :init, :address, :hiu, :purpose, :hiTypes, " +
                        "cast(:from as timestamptz), cast(:to as timestamptz), cast(:erase as timestamptz), :status)",
                params().addValue("uuid", uuid, Types.VARCHAR).addValue("init", initRequestId, Types.VARCHAR)
                        .addValue("address", abhaAddress, Types.VARCHAR).addValue("hiu", hiuId, Types.VARCHAR)
                        .addValue("purpose", purposeCode, Types.VARCHAR).addValue("hiTypes", hiTypes, Types.VARCHAR)
                        .addValue("from", AbdmFlows.iso(dateFrom), Types.VARCHAR).addValue("to", AbdmFlows.iso(dateTo), Types.VARCHAR)
                        .addValue("erase", AbdmFlows.iso(dataEraseAt), Types.VARCHAR).addValue("status", status, Types.VARCHAR));
    }

    public Optional<Consent> findConsentRequestByInitRequest(String initRequestId) {
        if (initRequestId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id is null and init_request_id = :init",
                params().addValue("init", initRequestId, Types.VARCHAR), CONSENT));
    }

    public Optional<Consent> findConsentRequest(String consentRequestId) {
        if (consentRequestId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id is null and consent_request_id = :cr",
                params().addValue("cr", consentRequestId, Types.VARCHAR), CONSENT));
    }

    public Optional<Consent> findConsentRequestByUuid(String uuid) {
        if (uuid == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id is null and uuid = :uuid",
                params().addValue("uuid", uuid, Types.VARCHAR), CONSENT));
    }

    public void setConsentRequestId(long id, String consentRequestId, String status) {
        jdbc.update("update abdm_consent set consent_request_id = :cr, status = :status, error = null, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("cr", consentRequestId, Types.VARCHAR).addValue("status", status, Types.VARCHAR));
    }

    public void failConsent(long id, String error) {
        jdbc.update("update abdm_consent set status = 'FAILED', error = :error, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("error", error, Types.VARCHAR));
    }

    public int setConsentRequestStatus(String consentRequestId, String status) {
        return jdbc.update("update abdm_consent set status = :status, updated_at = now() " +
                        "where organisation_id = :org and role = 'HIU' and consent_id is null and consent_request_id = :cr",
                params().addValue("cr", consentRequestId, Types.VARCHAR).addValue("status", status, Types.VARCHAR));
    }

    public List<Consent> listConsentRequests() {
        return jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id is null order by created_at desc limit 200",
                params(), CONSENT);
    }

    // ---- artefacts

    // Writes the fetch request id before the fetch is sent; an artefact notified twice keeps one row.
    public void upsertArtefact(String consentRequestId, String consentId, String abhaAddress, String hiuId, String status, String fetchRequestId) {
        MapSqlParameterSource p = params().addValue("cr", consentRequestId, Types.VARCHAR).addValue("consentId", consentId, Types.VARCHAR)
                .addValue("address", abhaAddress, Types.VARCHAR).addValue("hiu", hiuId, Types.VARCHAR)
                .addValue("status", status, Types.VARCHAR).addValue("fetch", fetchRequestId, Types.VARCHAR);
        int updated = jdbc.update("update abdm_consent set consent_request_id = coalesce(:cr, consent_request_id), status = :status, " +
                "fetch_request_id = coalesce(:fetch, fetch_request_id), updated_at = now() " +
                "where organisation_id = :org and role = 'HIU' and consent_id = :consentId", p);
        if (updated == 0) {
            jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, consent_id, fetch_request_id, abha_address, hiu_id, status) " +
                    "values (:uuid, :org, 'HIU', :cr, :consentId, :fetch, :address, :hiu, :status)", p.addValue("uuid", UUID.randomUUID().toString(), Types.VARCHAR));
        }
    }

    public Optional<Consent> findArtefactByFetchRequest(String fetchRequestId) {
        if (fetchRequestId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and fetch_request_id = :fetch",
                params().addValue("fetch", fetchRequestId, Types.VARCHAR), CONSENT));
    }

    public Optional<Consent> findArtefact(String consentId) {
        if (consentId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id = :consentId",
                params().addValue("consentId", consentId, Types.VARCHAR), CONSENT));
    }

    public void storeArtefactDetail(long id, String status, String hipId, String hiTypes, Instant dateFrom, Instant dateTo,
                                    Instant dataEraseAt, JsonNode consentDetail) {
        jdbc.update("update abdm_consent set status = coalesce(:status, status), hip_id = :hip, hi_types = :hiTypes, " +
                        "date_from = cast(:from as timestamptz), date_to = cast(:to as timestamptz), data_erase_at = cast(:erase as timestamptz), " +
                        "consent_detail = cast(:detail as jsonb), error = null, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("status", status, Types.VARCHAR).addValue("hip", hipId, Types.VARCHAR)
                        .addValue("hiTypes", hiTypes, Types.VARCHAR).addValue("from", AbdmFlows.iso(dateFrom), Types.VARCHAR)
                        .addValue("to", AbdmFlows.iso(dateTo), Types.VARCHAR).addValue("erase", AbdmFlows.iso(dataEraseAt), Types.VARCHAR)
                        .addValue("detail", consentDetail == null ? null : AbdmJson.write(consentDetail), Types.VARCHAR));
    }

    public void setArtefactStatus(Collection<String> consentIds, String status) {
        if (consentIds == null || consentIds.isEmpty()) return;
        jdbc.update("update abdm_consent set status = :status, updated_at = now() where organisation_id = :org and role = 'HIU' and consent_id in (:ids)",
                params().addValue("ids", consentIds).addValue("status", status, Types.VARCHAR));
    }

    public List<Artefact> findArtefacts(Collection<String> consentRequestIds) {
        if (consentRequestIds == null || consentRequestIds.isEmpty()) return new ArrayList<>();
        return jdbc.query("select c.consent_request_id, c.consent_id, c.status, " +
                        "(select f.status from abdm_data_flow f where f.organisation_id = c.organisation_id and f.role = 'HIU' " +
                        "   and f.consent_id = c.consent_id order by f.created_at desc limit 1) as data_flow_status, " +
                        "(select count(*) from abdm_health_information h where h.organisation_id = c.organisation_id " +
                        "   and h.consent_id = c.consent_id) as records " +
                        "from abdm_consent c where c.organisation_id = :org and c.role = 'HIU' and c.consent_id is not null " +
                        "and c.consent_request_id in (:ids) order by c.id",
                params().addValue("ids", consentRequestIds),
                (rs, n) -> new Artefact(rs.getString("consent_request_id"), rs.getString("consent_id"), rs.getString("status"),
                        rs.getString("data_flow_status"), rs.getLong("records")));
    }

    // ---- data flows

    public long insertDataFlow(String consentId, String requestId, String encryptedPrivateKey, String publicKey, String nonce,
                               Instant keyExpiry, String status) {
        Long id = jdbc.queryForObject("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, request_id, encrypted_private_key, " +
                        "public_key, nonce, key_expiry, status) values (:uuid, :org, 'HIU', :consentId, :req, :priv, :pub, :nonce, " +
                        "cast(:expiry as timestamptz), :status) returning id",
                params().addValue("uuid", UUID.randomUUID().toString(), Types.VARCHAR).addValue("consentId", consentId, Types.VARCHAR)
                        .addValue("req", requestId, Types.VARCHAR).addValue("priv", encryptedPrivateKey, Types.VARCHAR)
                        .addValue("pub", publicKey, Types.VARCHAR).addValue("nonce", nonce, Types.VARCHAR)
                        .addValue("expiry", AbdmFlows.iso(keyExpiry), Types.VARCHAR).addValue("status", status, Types.VARCHAR), Long.class);
        return id == null ? 0 : id;
    }

    public Optional<DataFlow> findDataFlowByRequest(String requestId) {
        if (requestId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_data_flow where organisation_id = :org and role = 'HIU' and request_id = :req",
                params().addValue("req", requestId, Types.VARCHAR), DATA_FLOW));
    }

    public Optional<DataFlow> findDataFlowByTransaction(String transactionId) {
        if (transactionId == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_data_flow where organisation_id = :org and role = 'HIU' and transaction_id = :tx order by id desc",
                params().addValue("tx", transactionId, Types.VARCHAR), DATA_FLOW));
    }

    public void setTransaction(long id, String transactionId, String status, String error) {
        jdbc.update("update abdm_data_flow set transaction_id = coalesce(:tx, transaction_id), status = :status, error = :error, updated_at = now() " +
                        "where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("tx", transactionId, Types.VARCHAR).addValue("status", status, Types.VARCHAR)
                        .addValue("error", error, Types.VARCHAR));
    }

    public void finishDataFlow(long id, String status, int entries, String error) {
        jdbc.update("update abdm_data_flow set status = :status, entries = :entries, error = :error, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("status", status, Types.VARCHAR).addValue("entries", entries)
                        .addValue("error", error, Types.VARCHAR));
    }

    // ---- health information

    // DEMO-ONLY: stored in plaintext, not erased at dataEraseAt.
    public void insertHealthInformation(long dataFlowId, String transactionId, String consentId, String hipId,
                                        String careContextReference, Boolean checksumMatched, String fhirBundleJson) {
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id, hip_id, " +
                        "care_context_reference, checksum_matched, fhir_bundle) values (:org, :flow, :tx, :consentId, :hip, :ref, :matched, cast(:bundle as jsonb))",
                params().addValue("flow", dataFlowId).addValue("tx", transactionId, Types.VARCHAR)
                        .addValue("consentId", consentId, Types.VARCHAR).addValue("hip", hipId, Types.VARCHAR)
                        .addValue("ref", careContextReference, Types.VARCHAR).addValue("matched", checksumMatched, Types.BOOLEAN)
                        .addValue("bundle", fhirBundleJson, Types.VARCHAR));
    }

    // Every record received under the consent request's artefacts.
    public List<Record> records(String consentRequestId) {
        if (consentRequestId == null) return new ArrayList<>();
        return jdbc.query("select h.care_context_reference, h.hip_id, h.transaction_id, h.received_at, h.checksum_matched, h.fhir_bundle " +
                        "from abdm_health_information h join abdm_consent c on c.organisation_id = h.organisation_id and c.role = 'HIU' " +
                        "and c.consent_id = h.consent_id where h.organisation_id = :org and c.consent_request_id = :cr order by h.received_at, h.id",
                params().addValue("cr", consentRequestId, Types.VARCHAR),
                (rs, n) -> new Record(rs.getString("care_context_reference"), rs.getString("hip_id"), rs.getString("transaction_id"),
                        instant(rs, "received_at"), (Boolean) rs.getObject("checksum_matched"), AbdmJson.read(rs.getString("fhir_bundle"))));
    }

    // ---- plumbing

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("org", orgContext.organisationId());
    }

    private static <T> Optional<T> one(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static final RowMapper<Consent> CONSENT = (rs, n) -> new Consent(rs.getLong("id"), rs.getString("uuid"),
            rs.getString("consent_request_id"), rs.getString("consent_id"), rs.getString("init_request_id"),
            rs.getString("fetch_request_id"), rs.getString("abha_address"), rs.getString("hip_id"), rs.getString("hiu_id"),
            rs.getString("purpose_code"), rs.getString("hi_types"), instant(rs, "date_from"), instant(rs, "date_to"),
            instant(rs, "data_erase_at"), rs.getString("status"), AbdmJson.read(rs.getString("consent_detail")),
            rs.getString("error"), instant(rs, "created_at"), instant(rs, "updated_at"));

    private static final RowMapper<DataFlow> DATA_FLOW = (rs, n) -> new DataFlow(rs.getLong("id"), rs.getString("uuid"),
            rs.getString("consent_id"), rs.getString("transaction_id"), rs.getString("request_id"),
            rs.getString("encrypted_private_key"), rs.getString("public_key"), rs.getString("nonce"), instant(rs, "key_expiry"),
            rs.getString("status"), nullableInt(rs, "entries"), rs.getString("error"));
}
