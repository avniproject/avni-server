package org.avni.server.service.abdm.hip;

import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// The HIP's ABDM state (V1_413): care contexts, patient-led link requests, and a record of each data request Eka relayed.
// Plain JDBC, one autocommitted statement each, so a correlation row is visible to the webhook thread as soon as it is
// written. organisation_id is set here: the Hibernate interceptor does not see these statements.
@Component
public class AbdmHipStore {
    public static final String ROLE = "HIP";

    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmHipStore(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    public record CareContext(long id, String uuid, long individualId, String hipId, String patientReference, String reference,
                              String display, String hiType, String status, String linkMode, String linkRequestId,
                              String error, Instant updatedAt) {
    }

    // A care context to be: an encounter's uuid and its display.
    public record NewCareContext(String reference, String display) {
    }

    public record LinkRequest(long id, String transactionId, String linkReference, Long individualId, String abhaAddress,
                              List<String> careContextRefs, String status, Instant expiresAt) {
    }

    public record Subject(long id, String uuid, String name) {
    }

    public record Candidate(long id, String uuid, String name) {
    }

    public record PatientRow(String subjectUuid, String name, String abhaNumber, String abhaAddress, long encounters, long linked) {
    }

    // ---- subjects

    private static final String NAME = "concat_ws(' ', i.first_name, i.middle_name, i.last_name)";

    public Optional<Subject> findSubjectById(long individualId) {
        return one(jdbc.query("select i.id, i.uuid, " + NAME + " as name from individual i where i.id = :id",
                params().addValue("id", individualId), SUBJECT));
    }

    public Optional<Subject> findSubjectByUuid(String uuid) {
        return one(jdbc.query("select i.id, i.uuid, " + NAME + " as name from individual i where i.uuid = :uuid and i.is_voided = false",
                params().addValue("uuid", uuid, Types.VARCHAR), SUBJECT));
    }

    // DEMO-ONLY: no index on the address; a lower() scan of abha_link is fine at demo scale.
    public List<Subject> findSubjectsByAbhaAddress(String abhaAddress, String suffix) {
        return jdbc.query("select i.id, i.uuid, " + NAME + " as name from abha_link l join individual i on i.id = l.individual_id " +
                        "where l.organisation_id = :org and l.is_voided = false and i.is_voided = false " +
                        "and (lower(l.abha_address) = lower(:address) or lower(l.abha_address || :suffix) = lower(:address)) order by i.id",
                params().addValue("address", abhaAddress, Types.VARCHAR).addValue("suffix", suffix == null ? "" : suffix, Types.VARCHAR),
                SUBJECT);
    }

    public List<Subject> findSubjectsByAbhaNumber(String digits) {
        return jdbc.query("select i.id, i.uuid, " + NAME + " as name from abha_link l join individual i on i.id = l.individual_id " +
                        "where l.organisation_id = :org and l.is_voided = false and i.is_voided = false and l.abha_number = :number order by i.id",
                params().addValue("number", digits, Types.VARCHAR), SUBJECT);
    }

    public List<PatientRow> patientsWithAbha(String hipId) {
        return jdbc.query("select i.uuid, " + NAME + " as name, l.abha_number, l.abha_address, " +
                        "(select count(*) from encounter e where e.individual_id = i.id and e.is_voided = false " +
                        "   and e.encounter_date_time is not null and e.cancel_date_time is null) as encounters, " +
                        "(select count(*) from abdm_care_context c where c.organisation_id = l.organisation_id and c.individual_id = i.id " +
                        "   and c.hip_id = :hip and c.status = 'LINKED') as linked " +
                        "from abha_link l join individual i on i.id = l.individual_id " +
                        "where l.organisation_id = :org and l.is_voided = false and i.is_voided = false " +
                        "order by i.first_name, i.last_name limit 500",
                params().addValue("hip", hipId, Types.VARCHAR),
                (rs, n) -> new PatientRow(rs.getString("uuid"), rs.getString("name"), rs.getString("abha_number"),
                        rs.getString("abha_address"), rs.getLong("encounters"), rs.getLong("linked")));
    }

    // Patients with visits queued on save (QUEUED), or whose last try failed over an hour ago.
    public List<String> subjectsToLink(String hipId, int limit) {
        return jdbc.queryForList("select distinct i.uuid from abdm_care_context c join individual i on i.id = c.individual_id " +
                        "where c.organisation_id = :org and c.hip_id = :hip and (c.status = 'QUEUED' " +
                        "or (c.status = 'FAILED' and c.updated_at < now() - interval '1 hour')) limit :limit",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("limit", limit), String.class);
    }

    // A patient whose queued visits could not be linked: FAILED with the reason, so the next try waits an hour.
    public void failQueued(String subjectUuid, String hipId, String reason) {
        jdbc.update("update abdm_care_context c set status = 'FAILED', error = :reason, updated_at = now() from individual i " +
                        "where i.id = c.individual_id and i.uuid = :uuid and c.organisation_id = :org and c.hip_id = :hip " +
                        "and c.status in ('QUEUED', 'FAILED')",
                params().addValue("uuid", subjectUuid, Types.VARCHAR).addValue("hip", hipId, Types.VARCHAR)
                        .addValue("reason", reason, Types.VARCHAR));
    }

    // ---- care contexts

    // New ones start PENDING; an existing row keeps its status.
    public void ensureCareContexts(long individualId, String hipId, String patientReference, String hiType, List<NewCareContext> careContexts) {
        for (NewCareContext c : careContexts) {
            jdbc.update("insert into abdm_care_context (uuid, organisation_id, individual_id, hip_id, patient_reference, reference, display, hi_type, status) " +
                            "values (:uuid, :org, :ind, :hip, :patient, :ref, :display, :hiType, 'PENDING') " +
                            "on conflict (organisation_id, hip_id, reference) do update set display = excluded.display",
                    params().addValue("uuid", UUID.randomUUID().toString(), Types.VARCHAR).addValue("ind", individualId)
                            .addValue("hip", hipId, Types.VARCHAR).addValue("patient", patientReference, Types.VARCHAR)
                            .addValue("ref", c.reference(), Types.VARCHAR).addValue("display", c.display(), Types.VARCHAR)
                            .addValue("hiType", hiType, Types.VARCHAR));
        }
    }

    // A visit saved while linking on save is on: QUEUED for AbdmHousekeepingJob. A row already known as PENDING (seen by
    // discovery or listed) is queued too; one being linked, linked or failed keeps its status.
    public void queueCareContexts(long individualId, String hipId, String patientReference, String hiType, List<NewCareContext> careContexts) {
        for (NewCareContext c : careContexts) {
            jdbc.update("insert into abdm_care_context (uuid, organisation_id, individual_id, hip_id, patient_reference, reference, display, hi_type, status) " +
                            "values (:uuid, :org, :ind, :hip, :patient, :ref, :display, :hiType, 'QUEUED') " +
                            "on conflict (organisation_id, hip_id, reference) do update set display = excluded.display, " +
                            "status = case when abdm_care_context.status = 'PENDING' then 'QUEUED' else abdm_care_context.status end",
                    params().addValue("uuid", UUID.randomUUID().toString(), Types.VARCHAR).addValue("ind", individualId)
                            .addValue("hip", hipId, Types.VARCHAR).addValue("patient", patientReference, Types.VARCHAR)
                            .addValue("ref", c.reference(), Types.VARCHAR).addValue("display", c.display(), Types.VARCHAR)
                            .addValue("hiType", hiType, Types.VARCHAR));
        }
    }

    public List<CareContext> findCareContexts(long individualId, String hipId) {
        return jdbc.query("select * from abdm_care_context where organisation_id = :org and individual_id = :ind and hip_id = :hip order by id",
                params().addValue("ind", individualId).addValue("hip", hipId, Types.VARCHAR), CARE_CONTEXT);
    }

    public List<CareContext> findCareContextsByReferences(String hipId, Collection<String> references) {
        if (references == null || references.isEmpty()) return new ArrayList<>();
        return jdbc.query("select * from abdm_care_context where organisation_id = :org and hip_id = :hip and reference in (:refs) order by id",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("refs", references), CARE_CONTEXT);
    }

    // linkRequestId null while the link token is still awaited.
    public void markLinkRequested(String hipId, Collection<String> references, String linkRequestId) {
        if (references == null || references.isEmpty()) return;
        jdbc.update("update abdm_care_context set status = 'LINK_REQUESTED', link_mode = 'HIP', link_request_id = :req, error = null, updated_at = now() " +
                        "where organisation_id = :org and hip_id = :hip and reference in (:refs)",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("refs", references).addValue("req", linkRequestId, Types.VARCHAR));
    }

    public int markByLinkRequest(String linkRequestId, String status, String error) {
        return jdbc.update("update abdm_care_context set status = :status, error = :error, updated_at = now() " +
                        "where organisation_id = :org and link_request_id = :req",
                params().addValue("req", linkRequestId, Types.VARCHAR).addValue("status", status, Types.VARCHAR)
                        .addValue("error", error, Types.VARCHAR));
    }

    public void markStatus(String hipId, Collection<String> references, String status, String error) {
        if (references == null || references.isEmpty()) return;
        jdbc.update("update abdm_care_context set status = :status, error = :error, updated_at = now() " +
                        "where organisation_id = :org and hip_id = :hip and reference in (:refs)",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("refs", references)
                        .addValue("status", status, Types.VARCHAR).addValue("error", error, Types.VARCHAR));
    }

    // Only rows still PENDING when this runs: a visit linked, queued on save or failed by another path in the meantime keeps
    // its status, and a queued one is still linked by the job.
    public void markLinkRequestedIfPending(String hipId, Collection<String> references, String error) {
        if (references == null || references.isEmpty()) return;
        jdbc.update("update abdm_care_context set status = 'LINK_REQUESTED', error = :error, updated_at = now() " +
                        "where organisation_id = :org and hip_id = :hip and reference in (:refs) and status = 'PENDING'",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("refs", references).addValue("error", error, Types.VARCHAR));
    }

    public void markLinked(String hipId, Collection<String> references, String linkMode) {
        if (references == null || references.isEmpty()) return;
        jdbc.update("update abdm_care_context set status = 'LINKED', link_mode = :mode, error = null, updated_at = now() " +
                        "where organisation_id = :org and hip_id = :hip and reference in (:refs)",
                params().addValue("hip", hipId, Types.VARCHAR).addValue("refs", references).addValue("mode", linkMode, Types.VARCHAR));
    }

    // ---- user-initiated link requests

    public void insertLinkRequest(String transactionId, String linkReference, Long individualId, String abhaAddress,
                                  List<String> careContextRefs, String status, Instant expiresAt) {
        jdbc.update("insert into abdm_link_request (organisation_id, transaction_id, link_reference, individual_id, abha_address, care_context_refs, status, expires_at) " +
                        "values (:org, :tx, :ref, :ind, :address, cast(:refs as jsonb), :status, cast(:expires as timestamptz))",
                params().addValue("tx", transactionId, Types.VARCHAR).addValue("ref", linkReference, Types.VARCHAR)
                        .addValue("ind", individualId, Types.BIGINT).addValue("address", abhaAddress, Types.VARCHAR)
                        .addValue("refs", AbdmJson.write(AbdmJson.M.valueToTree(careContextRefs)), Types.VARCHAR)
                        .addValue("status", status, Types.VARCHAR).addValue("expires", AbdmFlows.iso(expiresAt), Types.VARCHAR));
    }

    public Optional<LinkRequest> findLinkRequest(String linkReference) {
        if (linkReference == null) return Optional.empty();
        return one(jdbc.query("select * from abdm_link_request where organisation_id = :org and link_reference = :ref",
                params().addValue("ref", linkReference, Types.VARCHAR), LINK_REQUEST));
    }

    // Search limit for one mobile: past it, Avni cannot say that no one else on the number matches.
    public static final int PHONE_SEARCH_LIMIT = 20;

    // Live subjects whose phone, read as IndividualService.findPhoneNumber reads it (the PhoneNumber value first, else
    // the contact-number text), ends in these ten digits, of this gender letter and year of birth, and who hold no ABHA:
    // discovery searches by phone only when the asking ABHA matched no patient here, so one holding an ABHA is someone
    // else, such as a relative on the family phone. At most PHONE_SEARCH_LIMIT + 1 rows, so a caller can tell the limit
    // was passed. DEMO-ONLY: a scan of the organisation's subjects, with no index on the phone.
    // The year of birth as a date range, so the date-of-birth index (V1_391) narrows the search.
    static final String PHONE_SEARCH_SQL = "select i.id, i.uuid, " + NAME + " as name from individual i left join gender g on g.id = i.gender_id " +
            "where i.organisation_id = :org and i.is_voided = false " +
            "and right(regexp_replace(coalesce(i.observations -> :pn ->> 'phoneNumber', i.observations ->> :text, ''), '[^0-9]', '', 'g'), 10) = :digits " +
            "and upper(left(g.name, 1)) = :gender and i.date_of_birth >= :bornFrom and i.date_of_birth < :bornBefore " +
            "and not exists (select 1 from abha_link al where al.individual_id = i.id and al.is_voided = false) order by i.id limit :limit";

    public List<Candidate> findSubjectsByPhone(AbdmPatientPhones.PhoneConcepts concepts, String lastTenDigits, String genderLetter, int yearOfBirth) {
        if (concepts == null || (concepts.phoneNumberConceptUuid() == null && concepts.contactNumberConceptUuid() == null)
                || lastTenDigits == null || !lastTenDigits.matches("[0-9]{10}") || genderLetter == null) return new ArrayList<>();
        return jdbc.query(PHONE_SEARCH_SQL,
                params().addValue("pn", concepts.phoneNumberConceptUuid(), Types.VARCHAR)
                        .addValue("text", concepts.contactNumberConceptUuid(), Types.VARCHAR)
                        .addValue("digits", lastTenDigits, Types.VARCHAR).addValue("gender", genderLetter, Types.VARCHAR)
                        .addValue("bornFrom", LocalDate.of(yearOfBirth, 1, 1)).addValue("bornBefore", LocalDate.of(yearOfBirth + 1, 1, 1))
                        .addValue("limit", PHONE_SEARCH_LIMIT + 1),
                (rs, n) -> new Candidate(rs.getLong("id"), rs.getString("uuid"), rs.getString("name")));
    }

    // One more wrong code on a link request; returns how many so far.
    public int countLinkAttempt(long id) {
        Integer attempts = jdbc.queryForObject("update abdm_link_request set attempts = attempts + 1, updated_at = now() " +
                "where organisation_id = :org and id = :id returning attempts", params().addValue("id", id), Integer.class);
        return attempts == null ? 0 : attempts;
    }

    public void setLinkRequestStatus(long id, String status) {
        jdbc.update("update abdm_link_request set status = :status, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("status", status, Types.VARCHAR));
    }

    // ---- data flows (role HIP)

    public long insertDataFlow(String consentId, String transactionId, String requestId, String dataPushUrl, String status, String error) {
        Long id = jdbc.queryForObject("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, transaction_id, request_id, data_push_url, status, error) " +
                        "values (:uuid, :org, 'HIP', :consentId, :tx, :req, :url, :status, :error) returning id",
                params().addValue("uuid", UUID.randomUUID().toString(), Types.VARCHAR).addValue("consentId", consentId, Types.VARCHAR)
                        .addValue("tx", transactionId, Types.VARCHAR).addValue("req", requestId, Types.VARCHAR)
                        .addValue("url", dataPushUrl, Types.VARCHAR).addValue("status", status, Types.VARCHAR)
                        .addValue("error", error, Types.VARCHAR), Long.class);
        return id == null ? 0 : id;
    }

    public void finishDataFlow(long id, String status, int entries, String error) {
        jdbc.update("update abdm_data_flow set status = :status, entries = :entries, error = :error, updated_at = now() where organisation_id = :org and id = :id",
                params().addValue("id", id).addValue("status", status, Types.VARCHAR).addValue("entries", entries)
                        .addValue("error", error, Types.VARCHAR));
    }

    // ---- plumbing

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("org", orgContext.organisationId());
    }

    private static <T> Optional<T> one(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static final RowMapper<Subject> SUBJECT = (rs, n) -> new Subject(rs.getLong("id"), rs.getString("uuid"), rs.getString("name"));

    private static final RowMapper<CareContext> CARE_CONTEXT = (rs, n) -> new CareContext(rs.getLong("id"), rs.getString("uuid"),
            rs.getLong("individual_id"), rs.getString("hip_id"), rs.getString("patient_reference"), rs.getString("reference"),
            rs.getString("display"), rs.getString("hi_type"), rs.getString("status"), rs.getString("link_mode"),
            rs.getString("link_request_id"), rs.getString("error"), instant(rs, "updated_at"));

    private static final RowMapper<LinkRequest> LINK_REQUEST = (rs, n) -> new LinkRequest(rs.getLong("id"), rs.getString("transaction_id"),
            rs.getString("link_reference"), nullableLong(rs, "individual_id"), rs.getString("abha_address"),
            AbdmFlows.texts(AbdmJson.read(rs.getString("care_context_refs")), null), rs.getString("status"), instant(rs, "expires_at"));

}
