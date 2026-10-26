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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// The HIU's ABDM state (V1_413, V1_414), all rows role HIU. In abdm_consent a consent request is the row with no consent_id; each
// artefact granted under it is a row of its own carrying the request's consent_request_id. Plain JDBC, autocommitted per
// statement; organisation_id set here. Every lookup names the role: in mock mode this server is the HIP too, and the two
// sides share consent and transaction ids.
@Component
public class AbdmHiuStore {
    public static final String ROLE = "HIU";

    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;
    private final AbdmRecordCipher recordCipher;

    public AbdmHiuStore(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext, AbdmRecordCipher recordCipher) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
        this.recordCipher = recordCipher;
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
                         Boolean checksumMatched, JsonNode bundle, String consentId) {
    }

    // The patient an ABHA address belongs to in this organisation, for the HIU list (M1 abha_link).
    public record Patient(String name, String abhaNumber) {
    }

    // One status a consent request (consentId null) or an artefact took, and when (V1_414 abdm_consent_status).
    public record StatusChange(String consentRequestId, String consentId, String status, Instant at) {
    }

    // Eka mode: a patient's Eka OID and subject, from the M1 abha_event table.
    public record EkaPatientRow(String ekaOid, String subjectUuid) {
    }

    // A consent update for a request id no request of ours carries yet (V1_414 abdm_parked_event).
    public record ParkedConsentUpdate(long id, String consentRequestId, String status, List<String> artefactIds) {
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
        history("c.uuid = :uuid", params().addValue("uuid", uuid, Types.VARCHAR), status);
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
        history("c.id = :id", params().addValue("id", id), status);
    }

    public void failConsent(long id, String error) {
        jdbc.update("update abdm_consent set status = 'FAILED', error = :error, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("error", error, Types.VARCHAR));
        history("c.id = :id", params().addValue("id", id), "FAILED");
    }

    public int setConsentRequestStatus(String consentRequestId, String status) {
        int updated = jdbc.update("update abdm_consent set status = :status, updated_at = now() " +
                        "where organisation_id = :org and role = 'HIU' and consent_id is null and consent_request_id = :cr",
                params().addValue("cr", consentRequestId, Types.VARCHAR).addValue("status", status, Types.VARCHAR));
        history("c.consent_id is null and c.consent_request_id = :cr", params().addValue("cr", consentRequestId, Types.VARCHAR), status);
        return updated;
    }

    public List<Consent> listConsentRequests() {
        return jdbc.query("select * from abdm_consent where organisation_id = :org and role = 'HIU' and consent_id is null order by created_at desc limit 200",
                params(), CONSENT);
    }

    // ---- parked consent updates

    public void parkConsentUpdate(String consentRequestId, String status, List<String> artefactIds) {
        String detail = AbdmJson.write(AbdmJson.M.createObjectNode().set("artefacts", AbdmJson.M.valueToTree(artefactIds == null ? List.of() : artefactIds)));
        jdbc.update("insert into abdm_parked_event (organisation_id, kind, reference, status, detail) " +
                        "values (:org, 'consent_update', :ref, :status, cast(:detail as jsonb))",
                params().addValue("ref", consentRequestId, Types.VARCHAR).addValue("status", status, Types.VARCHAR)
                        .addValue("detail", detail, Types.VARCHAR));
    }

    // Oldest first, so they are applied in the order they came.
    public List<ParkedConsentUpdate> parkedConsentUpdates(String consentRequestId) {
        if (consentRequestId == null) return new ArrayList<>();
        return jdbc.query("select id, reference, status, detail from abdm_parked_event where organisation_id = :org " +
                        "and kind = 'consent_update' and reference = :ref and resolved_at is null order by id",
                params().addValue("ref", consentRequestId, Types.VARCHAR),
                (rs, n) -> {
                    List<String> artefacts = new ArrayList<>();
                    JsonNode ids = AbdmJson.at(AbdmJson.read(rs.getString("detail")), "artefacts");
                    if (ids != null && ids.isArray()) ids.forEach(id -> artefacts.add(id.asText()));
                    return new ParkedConsentUpdate(rs.getLong("id"), rs.getString("reference"), rs.getString("status"), artefacts);
                });
    }

    public void resolveParked(long id) {
        jdbc.update("update abdm_parked_event set resolved_at = now() where organisation_id = :org and id = :id", params().addValue("id", id));
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
        history("c.consent_id = :consentId", params().addValue("consentId", consentId, Types.VARCHAR), status);
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
        history("c.consent_id in (:ids)", params().addValue("ids", consentIds), status);
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

    // Newest first. Eka mode keeps its HIU keysets as flows of a consent id of their own (EkaHiuService.KEYSET_CONSENT_ID).
    public List<DataFlow> findDataFlowsByConsent(String consentId) {
        if (consentId == null) return new ArrayList<>();
        return jdbc.query("select * from abdm_data_flow where organisation_id = :org and role = 'HIU' and consent_id = :consentId order by id desc",
                params().addValue("consentId", consentId, Types.VARCHAR), DATA_FLOW);
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

    // Encrypted under AbdmRecordCipher's own key (AVNI-14); erased by eraseRecordsOfEndedConsents. Throws
    // AbdmRecordCipher.NoKeyException, storing nothing, when that key is not set.
    public void insertHealthInformation(long dataFlowId, String transactionId, String consentId, String hipId,
                                        String careContextReference, Boolean checksumMatched, String fhirBundleJson) {
        String encrypted = recordCipher.encrypt(fhirBundleJson);
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id, hip_id, " +
                        "care_context_reference, checksum_matched, fhir_bundle_encrypted) values (:org, :flow, :tx, :consentId, :hip, :ref, :matched, :bundle)",
                params().addValue("flow", dataFlowId).addValue("tx", transactionId, Types.VARCHAR)
                        .addValue("consentId", consentId, Types.VARCHAR).addValue("hip", hipId, Types.VARCHAR)
                        .addValue("ref", careContextReference, Types.VARCHAR).addValue("matched", checksumMatched, Types.BOOLEAN)
                        .addValue("bundle", encrypted, Types.VARCHAR));
    }

    // Eka mode: a webhook delivered twice, or a push in several parts, keeps one record per care context.
    public boolean hasHealthInformation(String transactionId, String careContextReference) {
        if (transactionId == null) return false;
        Integer count = jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = :org and transaction_id = :tx " +
                        "and care_context_reference is not distinct from :ref",
                params().addValue("tx", transactionId, Types.VARCHAR).addValue("ref", careContextReference, Types.VARCHAR), Integer.class);
        return count != null && count > 0;
    }

    // Eka mode, read-only: the Eka OID of the latest live ABHA event (M1) naming one of these ABHA addresses.
    public Optional<EkaPatientRow> findEkaPatientByAbhaAddress(Collection<String> abhaAddresses) {
        List<String> addresses = abhaAddresses == null ? List.of() : abhaAddresses.stream().filter(a -> a != null && !a.isBlank())
                .map(a -> a.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        if (addresses.isEmpty()) return Optional.empty();
        return one(jdbc.query("select eka_oid, subject_uuid from abha_event where organisation_id = :org and is_voided = false " +
                        "and eka_oid is not null and action <> 'removed' and lower(abha_address) in (:addresses) " +
                        "order by created_date_time desc, id desc limit 1",
                params().addValue("addresses", addresses),
                (rs, n) -> new EkaPatientRow(rs.getString("eka_oid"), rs.getString("subject_uuid"))));
    }

    // Every record received under the consent request's artefacts.
    public List<Record> records(String consentRequestId) {
        if (consentRequestId == null) return new ArrayList<>();
        return jdbc.query("select h.care_context_reference, h.hip_id, h.transaction_id, h.received_at, h.checksum_matched, h.fhir_bundle::text as fhir_bundle, " +
                        "h.fhir_bundle_encrypted, h.consent_id " +
                        "from abdm_health_information h join abdm_consent c on c.organisation_id = h.organisation_id and c.role = 'HIU' " +
                        "and c.consent_id = h.consent_id where h.organisation_id = :org and c.consent_request_id = :cr order by h.received_at, h.id",
                params().addValue("cr", consentRequestId, Types.VARCHAR),
                (rs, n) -> new Record(rs.getString("care_context_reference"), rs.getString("hip_id"), rs.getString("transaction_id"),
                        instant(rs, "received_at"), (Boolean) rs.getObject("checksum_matched"), bundleOf(rs), rs.getString("consent_id")));
    }

    // Rows from before V1_414 hold the bundle in plaintext; they go when their consent ends.
    private JsonNode bundleOf(ResultSet rs) throws SQLException {
        String encrypted = rs.getString("fhir_bundle_encrypted");
        return AbdmJson.read(encrypted != null ? recordCipher.decrypt(encrypted) : rs.getString("fhir_bundle"));
    }

    // By ABHA address as given (any case, suffix or not): the live patient holding it and their ABHA number.
    public java.util.Map<String, Patient> patientsByAbhaAddress(Collection<String> abhaAddresses) {
        java.util.Map<String, String> byName = new java.util.LinkedHashMap<>();
        if (abhaAddresses != null) for (String a : abhaAddresses) if (a != null) byName.put(a.toLowerCase(Locale.ROOT).split("@")[0], a);
        java.util.Map<String, Patient> out = new java.util.LinkedHashMap<>();
        if (byName.isEmpty()) return out;
        jdbc.query("select split_part(lower(l.abha_address), '@', 1) as name_part, concat_ws(' ', i.first_name, i.middle_name, i.last_name) as name, " +
                        "l.abha_number from abha_link l join individual i on i.id = l.individual_id where l.organisation_id = :org " +
                        "and l.is_voided = false and i.is_voided = false and split_part(lower(l.abha_address), '@', 1) in (:names) order by i.id",
                params().addValue("names", byName.keySet()),
                rs -> {
                    String given = byName.get(rs.getString("name_part"));
                    if (given != null) out.putIfAbsent(given, new Patient(rs.getString("name"), rs.getString("abha_number")));
                });
        return out;
    }

    // Each status the requests took, oldest first.
    public List<StatusChange> statusHistory(Collection<String> consentRequestIds) {
        if (consentRequestIds == null || consentRequestIds.isEmpty()) return new ArrayList<>();
        return jdbc.query("select c.consent_request_id, c.consent_id, s.status, s.changed_at from abdm_consent_status s " +
                        "join abdm_consent c on c.id = s.consent_row_id where s.organisation_id = :org and c.role = 'HIU' " +
                        "and c.consent_request_id in (:ids) order by s.changed_at, s.id",
                params().addValue("ids", consentRequestIds),
                (rs, n) -> new StatusChange(rs.getString("consent_request_id"), rs.getString("consent_id"), rs.getString("status"),
                        instant(rs, "changed_at")));
    }

    // What the patient granted, which may be narrower than what was asked: kept on the request and its artefacts.
    public void setGrantedTerms(String consentRequestId, String hiTypes, Instant dateFrom, Instant dateTo, Instant dataEraseAt) {
        jdbc.update("update abdm_consent set hi_types = coalesce(:hiTypes, hi_types), date_from = coalesce(cast(:from as timestamptz), date_from), " +
                        "date_to = coalesce(cast(:to as timestamptz), date_to), data_erase_at = coalesce(cast(:erase as timestamptz), data_erase_at), " +
                        "updated_at = now() where organisation_id = :org and role = 'HIU' and consent_request_id = :cr",
                params().addValue("cr", consentRequestId, Types.VARCHAR).addValue("hiTypes", hiTypes, Types.VARCHAR)
                        .addValue("from", AbdmFlows.iso(dateFrom), Types.VARCHAR).addValue("to", AbdmFlows.iso(dateTo), Types.VARCHAR)
                        .addValue("erase", AbdmFlows.iso(dataEraseAt), Types.VARCHAR));
    }

    // Requests and artefacts past their erase date become EXPIRED (HIU_FLOW_301). Returns the requests expired.
    public int expireOverdueConsents() {
        int requests = jdbc.update("update abdm_consent set status = 'EXPIRED', updated_at = now() where organisation_id = :org and role = 'HIU' " +
                "and consent_id is null and status in ('GRANTED', 'REQUESTED') and data_erase_at is not null and data_erase_at < now()", params());
        jdbc.update("update abdm_consent a set status = 'EXPIRED', updated_at = now() where a.organisation_id = :org and a.role = 'HIU' " +
                "and a.consent_id is not null and a.status = 'GRANTED' and ((a.data_erase_at is not null and a.data_erase_at < now()) " +
                "or exists (select 1 from abdm_consent r where r.organisation_id = a.organisation_id and r.role = 'HIU' and r.consent_id is null " +
                "   and r.consent_request_id = a.consent_request_id and r.status = 'EXPIRED'))", params());
        history("c.status = 'EXPIRED'", params(), "EXPIRED");
        return requests;
    }

    // Records whose artefact is no longer granted, or whose request was withdrawn, refused, expired or is past its erase
    // date, are deleted (HIU_FLOW_202, HIU_FLOW_301, AVNI-14). Returns how many.
    public int eraseRecordsOfEndedConsents() {
        return jdbc.update("delete from abdm_health_information h where h.organisation_id = :org and (" +
                "not exists (select 1 from abdm_consent a where a.organisation_id = h.organisation_id and a.role = 'HIU' and a.consent_id = h.consent_id " +
                "   and a.status = 'GRANTED' and (a.data_erase_at is null or a.data_erase_at > now())) " +
                "or exists (select 1 from abdm_consent a join abdm_consent r on r.organisation_id = a.organisation_id and r.role = 'HIU' " +
                "   and r.consent_id is null and r.consent_request_id = a.consent_request_id " +
                "   where a.organisation_id = h.organisation_id and a.role = 'HIU' and a.consent_id = h.consent_id " +
                "   and (r.status in ('REVOKED', 'EXPIRED', 'DENIED') or (r.data_erase_at is not null and r.data_erase_at < now()))))", params());
    }

    // One row in abdm_consent_status per change, keyed by the abdm_consent row so it survives the request id being mapped.
    private void history(String rowFilter, MapSqlParameterSource p, String status) {
        if (status == null) return;
        jdbc.update("insert into abdm_consent_status (organisation_id, consent_row_id, status) select c.organisation_id, c.id, :historyStatus " +
                        "from abdm_consent c where c.organisation_id = :org and c.role = 'HIU' and " + rowFilter + " and coalesce((select s.status " +
                        "from abdm_consent_status s where s.consent_row_id = c.id order by s.id desc limit 1), '') <> :historyStatus",
                p.addValue("historyStatus", status, Types.VARCHAR));
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
