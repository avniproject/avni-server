package org.avni.server.service.abdm.hip;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DEMO-ONLY abdm-hackathon: never merged.
// AbdmHipStore's SQL against Postgres. Patient-led linking finds a patient by the phone Avni holds for them, read as
// IndividualService.findPhoneNumber reads it, so the number searched is the number the code is texted to. test-data.sql
// holds subject 4378dce3 (Male, org 2) with "9282738493" under the Text contact-number concept 7c39cb04.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmHipStoreIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String KNOWN = "4378dce3-247e-4393-8dd5-032c6eb0a655";
    private static final String CONTACT_NUMBER_CONCEPT = "7c39cb04-4f02-4c49-8f94-a5b697d40365";
    private static final String PHONE_NUMBER_CONCEPT = "3f2b8a64-6f0e-4d3a-9c1e-5a7d2b9e4c10";
    private static final String STORED_AS_PHONE_NUMBER = "8d1e6f2a-0b7c-4e5d-a3f9-2c4b6e8a1d07";
    private static final String STORED_WITH_LEADING_ZERO = "c5a9e3b7-1d2f-4a6c-8e0b-7f3d5a9c2e14";

    @Autowired
    private AbdmPatientPhones phones;
    @Autowired
    private AbdmHipStore store;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void aPatientIsFoundByTheLastTenDigitsOfThePhoneAvniHolds_inEitherForm() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("INSERT INTO concept (id, NAME, data_type, uuid, version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                "VALUES (90001, 'Mobile', 'PhoneNumber', ?, 1, 1, 1, now(), now())", PHONE_NUMBER_CONCEPT);
        insertSubject(STORED_AS_PHONE_NUMBER, "{\"" + PHONE_NUMBER_CONCEPT + "\": {\"phoneNumber\": \"+91 92827 38493\", \"verified\": true}}");
        insertSubject(STORED_WITH_LEADING_ZERO, "{\"" + CONTACT_NUMBER_CONCEPT + "\": \"09282738493\"}");
        Integer year = jdbc.queryForObject("select extract(year from date_of_birth)::int from individual where uuid = ?", Integer.class, KNOWN);

        AbdmPatientPhones.PhoneConcepts concepts = phones.phoneConcepts();
        assertEquals(PHONE_NUMBER_CONCEPT, concepts.phoneNumberConceptUuid());
        assertEquals(CONTACT_NUMBER_CONCEPT, concepts.contactNumberConceptUuid());

        List<String> found = store.findSubjectsByPhone(concepts, "9282738493", "M", year).stream().map(AbdmHipStore.Candidate::uuid).toList();
        assertTrue(found.toString(), found.containsAll(List.of(KNOWN, STORED_AS_PHONE_NUMBER, STORED_WITH_LEADING_ZERO)));

        List<String> female = store.findSubjectsByPhone(concepts, "9282738493", "F", year).stream().map(AbdmHipStore.Candidate::uuid).toList();
        assertFalse(female.contains(KNOWN));
        assertTrue(store.findSubjectsByPhone(concepts, "9282738493", "M", year - 1).stream().noneMatch(c -> c.uuid().equals(KNOWN)));
        assertTrue(store.findSubjectsByPhone(concepts, "9000000000", "M", year).isEmpty());

        // TANUH's configuration: a contact-number text concept and no PhoneNumber concept.
        List<String> textOnly = store.findSubjectsByPhone(new AbdmPatientPhones.PhoneConcepts(null, CONTACT_NUMBER_CONCEPT), "9282738493", "M", year)
                .stream().map(AbdmHipStore.Candidate::uuid).toList();
        assertTrue(textOnly.toString(), textOnly.containsAll(List.of(KNOWN, STORED_WITH_LEADING_ZERO)));
        assertFalse(textOnly.contains(STORED_AS_PHONE_NUMBER));
    }

    // Discovery reaches the phone search only when the ABHA asking matched no patient here, so a patient who already
    // holds an ABHA is someone else: a relative on the family phone must not be offered their visits.
    @Test
    public void aPatientHoldingAnotherAbhaIsNotFoundByPhone() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        insertSubject(STORED_WITH_LEADING_ZERO, "{\"" + CONTACT_NUMBER_CONCEPT + "\": \"09282738493\"}");
        jdbc.update("INSERT INTO abha_link (uuid, organisation_id, individual_id, abha_number, abha_address, created_by_id, last_modified_by_id, " +
                "created_date_time, last_modified_date_time) VALUES (?, 2, (select id from individual where uuid = ?), '91000000000042', 'relative@sbx', 1, 1, now(), now())",
                "e7b1c3d5-9f2a-4b6c-8d0e-1a3c5e7f9b24", STORED_WITH_LEADING_ZERO);
        Integer year = jdbc.queryForObject("select extract(year from date_of_birth)::int from individual where uuid = ?", Integer.class, KNOWN);

        List<String> found = store.findSubjectsByPhone(new AbdmPatientPhones.PhoneConcepts(null, CONTACT_NUMBER_CONCEPT), "9282738493", "M", year)
                .stream().map(AbdmHipStore.Candidate::uuid).toList();

        assertTrue(found.toString(), found.contains(KNOWN));
        assertFalse(found.contains(STORED_WITH_LEADING_ZERO));
    }

    @Test
    public void theYearOfBirthTakesEveryDayOfThatYearAndNoneOfTheNext() {
        insertSubjectBornOn(STORED_AS_PHONE_NUMBER, "1990-01-01");
        insertSubjectBornOn(STORED_WITH_LEADING_ZERO, "1990-12-31");
        insertSubjectBornOn("f3c8a1e5-6b2d-4f7a-9c0e-8d1b3a5c7e92", "1991-01-01");

        List<String> found = store.findSubjectsByPhone(new AbdmPatientPhones.PhoneConcepts(null, CONTACT_NUMBER_CONCEPT), "9282738493", "M", 1990)
                .stream().map(AbdmHipStore.Candidate::uuid).toList();

        assertTrue(found.toString(), found.containsAll(List.of(STORED_AS_PHONE_NUMBER, STORED_WITH_LEADING_ZERO)));
        assertFalse(found.contains("f3c8a1e5-6b2d-4f7a-9c0e-8d1b3a5c7e92"));
    }

    // At a large organisation the search narrows by the date-of-birth index (V1_391), which a year read out of the date
    // cannot use. The year's condition is planned alone, with sequential scans off: the whole search's plan depends on
    // how many rows the test database holds, and with few Postgres walks the primary key for the search's order.
    @Test
    public void theYearOfBirthIsSearchedThroughTheDateOfBirthIndex() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.execute("set local enable_seqscan = off");
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("org", 2L).addValue("pn", null, java.sql.Types.VARCHAR).addValue("text", CONTACT_NUMBER_CONCEPT)
                .addValue("digits", "9282738493").addValue("gender", "M").addValue("year", 1990)
                .addValue("bornFrom", java.time.LocalDate.of(1990, 1, 1)).addValue("bornBefore", java.time.LocalDate.of(1991, 1, 1))
                .addValue("limit", 21);

        List<String> plan = namedJdbc.queryForList("explain select i.id from individual i where i.organisation_id = :org and "
                + AbdmHipStore.BORN_IN_YEAR, params, String.class);

        assertTrue(String.join("\n", plan), plan.stream().anyMatch(line -> line.contains("Index Cond") && line.contains("date_of_birth")));
    }

    // When Eka does not answer on-confirm, only visits still PENDING when the update runs become LINK_REQUESTED: one linked,
    // queued on save or failed by another path in the meantime keeps its status, so the job still links a queued one.
    @Test
    public void markLinkRequestedIfPendingChangesOnlyPendingVisits() {
        long individualId = namedJdbc.getJdbcOperations().queryForObject("select id from individual where uuid = ?", Long.class, KNOWN);
        String hip = "TEST-HIP";
        store.ensureCareContexts(individualId, hip, KNOWN, "OPConsultation", List.of(new AbdmHipStore.NewCareContext("v-pending", "Visit 1"),
                new AbdmHipStore.NewCareContext("v-linked", "Visit 2"), new AbdmHipStore.NewCareContext("v-failed", "Visit 3")));
        store.queueCareContexts(individualId, hip, KNOWN, "OPConsultation", List.of(new AbdmHipStore.NewCareContext("v-queued", "Visit 4")));
        store.markLinked(hip, List.of("v-linked"), "HIP");
        store.markStatus(hip, List.of("v-failed"), "FAILED", "earlier failure");

        store.markLinkRequestedIfPending(hip, List.of("v-pending", "v-linked", "v-failed", "v-queued"), "Eka did not answer on-confirm");

        java.util.Map<String, String> status = store.findCareContexts(individualId, hip).stream()
                .collect(java.util.stream.Collectors.toMap(AbdmHipStore.CareContext::reference, AbdmHipStore.CareContext::status));
        assertEquals("LINK_REQUESTED", status.get("v-pending"));
        assertEquals("LINKED", status.get("v-linked"));
        assertEquals("FAILED", status.get("v-failed"));
        assertEquals("QUEUED", status.get("v-queued"));
    }

    // A link call that fails settles only the visits still LINK_REQUESTED: one Eka's webhook marked LINKED first keeps it.
    @Test
    public void settleLinkRequestChangesOnlyVisitsStillRequested() {
        long individualId = namedJdbc.getJdbcOperations().queryForObject("select id from individual where uuid = ?", Long.class, KNOWN);
        String hip = "TEST-HIP";
        store.ensureCareContexts(individualId, hip, KNOWN, "OPConsultation", List.of(new AbdmHipStore.NewCareContext("v-1", "Visit 1"),
                new AbdmHipStore.NewCareContext("v-2", "Visit 2")));
        store.markLinkRequested(hip, List.of("v-1", "v-2"), "req-1");
        store.markLinked(hip, List.of("v-2"), "HIP");

        store.settleLinkRequest("req-1", "FAILED", "Eka refused");

        java.util.Map<String, String> status = store.findCareContexts(individualId, hip).stream()
                .collect(java.util.stream.Collectors.toMap(AbdmHipStore.CareContext::reference, AbdmHipStore.CareContext::status));
        assertEquals("FAILED", status.get("v-1"));
        assertEquals("LINKED", status.get("v-2"));
    }

    // A patient-led link is confirmed under the centre its code was sent for.
    @Test
    public void aLinkRequestKeepsTheCentreItWasMadeAt() {
        store.insertLinkRequest("txn-1", "link-ref-1", knownId(), "asha.devi@sbx", List.of("v-1"), "OTP_SENT",
                java.time.Instant.now().plusSeconds(600), "hip-2");

        assertEquals("hip-2", store.findLinkRequest("link-ref-1").orElseThrow().hipId());
    }

    // ---- the link queue

    private static final String QUEUE_HIP = "QUEUE-HIP";
    private static final String OTHER = "b2d4f6a8-1c3e-4a5b-9d7f-0e2c4a6b8d13";

    // Eka's link webhook comes within minutes: a link it has said nothing about for half an hour is queued again, counted
    // as a try; a younger one is left to its webhook.
    @Test
    public void aLinkEkaHasNotAnsweredForHalfAnHourIsQueuedAgain_andAYoungerOneWaits() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-old"), cc("v-young")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-old", "v-young"), "req-1");
        age("v-old", "31 minutes");
        age("v-young", "10 minutes");

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("QUEUED", column("v-old", "status"));
        assertEquals(1, column("v-old", "attempts"));
        assertEquals("LINK_REQUESTED", column("v-young", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // Each link Eka leaves unanswered waits twice as long as the last before it is sent again: 30 minutes, then 1, 2, 4
    // and 8 hours, and the next is given up.
    @Test
    public void anUnansweredLinkWaitsTwiceAsLongEachTime() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        for (int attempts = 0; attempts < AbdmHipStore.LINK_ATTEMPTS; attempts++) {
            store.markLinkRequested(QUEUE_HIP, List.of("v-1"), "req-" + attempts);
            namedJdbc.getJdbcOperations().update("update abdm_care_context set attempts = ? where reference = 'v-1'", attempts);
            long wait = AbdmHipStore.LINK_ANSWER_DEADLINE.toMinutes() << attempts;
            age("v-1", (wait - 1) + " minutes");

            store.settleLinkQueue(QUEUE_HIP);

            assertEquals("attempts " + attempts, "LINK_REQUESTED", column("v-1", "status"));
            age("v-1", (wait + 1) + " minutes");

            store.settleLinkQueue(QUEUE_HIP);

            assertEquals("attempts " + attempts, attempts + 1 < AbdmHipStore.LINK_ATTEMPTS ? "QUEUED" : "FAILED_FINAL", column("v-1", "status"));
            assertEquals(attempts + 1, column("v-1", "attempts"));
        }
    }

    // Eka out for an afternoon, every link unanswered, the job running every 5 minutes and sending each requeued visit at
    // once: the visit is still waiting to be linked when Eka comes back, not given up.
    @Test
    public void anAfternoonOfUnansweredLinksDoesNotGiveTheVisitUp() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1"), "req-0");
        for (int minute = 5; minute <= 5 * 60; minute += 5) {
            namedJdbc.getJdbcOperations().update("update abdm_care_context set updated_at = updated_at - interval '5 minutes' where reference = 'v-1'");
            store.settleLinkQueue(QUEUE_HIP);
            if ("QUEUED".equals(column("v-1", "status"))) store.markLinkRequested(QUEUE_HIP, List.of("v-1"), "req-" + minute);
        }

        assertEquals("LINK_REQUESTED", column("v-1", "status"));
        assertEquals(3, column("v-1", "attempts"));
    }

    // A link the patient made from their own app is not the queue's to send again.
    @Test
    public void aPatientsOwnLinkEkaDidNotAnswerIsNotTakenOverByTheQueue() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        store.markLinkRequestedIfPending(QUEUE_HIP, List.of("v-1"), "Eka gave no clear answer to on-confirm");
        age("v-1", "2 hours");

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("LINK_REQUESTED", column("v-1", "status"));
    }

    @Test
    public void aLinkEkaNeverAnswersIsGivenUpAtTheCap() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1"), "req-1");
        namedJdbc.getJdbcOperations().update("update abdm_care_context set attempts = ? where reference = 'v-1'", AbdmHipStore.LINK_ATTEMPTS - 1);
        age("v-1", "17 hours");

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("FAILED_FINAL", column("v-1", "status"));
        assertTrue((String) column("v-1", "error"), ((String) column("v-1", "error")).contains(AbdmHipStore.LINK_ATTEMPTS + " tries"));
        assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
    }

    // A refusal that no later try can change (no Eka ID, a HIP ID Eka does not know) is final, however long it waits.
    @Test
    public void aFinalFailureIsNeverTakenAgain() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        store.failQueuedForGood(KNOWN, QUEUE_HIP, "Patient has no Eka ID");
        age("v-1", "30 days");

        assertEquals("FAILED_FINAL", column("v-1", "status"));
        assertEquals("Patient has no Eka ID", column("v-1", "error"));
        assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
        assertTrue(store.findCareContextsDue(knownId(), QUEUE_HIP).isEmpty());
    }

    // A failure that may pass waits an hour, then twice as long each time, and the sixth gives up.
    @Test
    public void aFailureThatMayPassIsTriedAgainAfterADoublingWait_andGivenUpAtTheCap() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        for (int attempt = 1; attempt < AbdmHipStore.LINK_ATTEMPTS; attempt++) {
            store.failQueued(KNOWN, QUEUE_HIP, "Eka did not answer");

            assertEquals("FAILED", column("v-1", "status"));
            assertEquals(attempt, column("v-1", "attempts"));
            assertEquals(Long.valueOf(3600L << (attempt - 1)), namedJdbc.getJdbcOperations().queryForObject(
                    "select extract(epoch from next_attempt_at - now())::bigint from abdm_care_context where reference = 'v-1'", Long.class));
            assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
            due("v-1");
            assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
        }
        store.failQueued(KNOWN, QUEUE_HIP, "Eka did not answer");

        assertEquals("FAILED_FINAL", column("v-1", "status"));
        due("v-1");
        assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
    }

    // A link call that failed in a way a later try may not: counted and tried again after the wait, like a queued visit.
    @Test
    public void aLinkCallThatFailedIsCountedAndWaits_andAVisitAlreadyLinkedKeepsItsStatus() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1", "v-2"), "req-1");
        store.markLinked(QUEUE_HIP, List.of("v-2"), "HIP");

        store.failLinkRequest("req-1", "Eka link failed: no connection");

        assertEquals("FAILED", column("v-1", "status"));
        assertEquals(1, column("v-1", "attempts"));
        assertEquals("LINKED", column("v-2", "status"));
        assertTrue(store.findCareContextsDue(knownId(), QUEUE_HIP).isEmpty());
        due("v-1");
        assertEquals(List.of("v-1"), store.findCareContextsDue(knownId(), QUEUE_HIP).stream().map(AbdmHipStore.CareContext::reference).toList());
    }

    // A removed patient's visits are never sent; their waiting visits are given up, with the reason.
    @Test
    public void aRemovedPatientIsSkipped_andTheirWaitingVisitsGivenUp() {
        emptyQueue();
        insertSubject(OTHER, "{}");
        long other = idOf(OTHER);
        namedJdbc.getJdbcOperations().update("update individual set is_voided = true where id = ?", other);
        store.queueCareContexts(other, QUEUE_HIP, OTHER, "OPConsultation", List.of(cc("v-removed")));
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("FAILED_FINAL", column("v-removed", "status"));
        assertEquals("QUEUED", column("v-1", "status"));
    }

    @Test
    public void theLongestWaitingPatientIsTakenFirst() {
        emptyQueue();
        insertSubject(OTHER, "{}");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        store.queueCareContexts(idOf(OTHER), QUEUE_HIP, OTHER, "OPConsultation", List.of(cc("v-2")));
        age("v-1", "1 hour");
        age("v-2", "2 hours");

        assertEquals(List.of(OTHER, KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
        assertEquals(List.of(OTHER), store.subjectsToLink(QUEUE_HIP, 1));
    }

    // The status page shows how many visits wait, are being linked, failed for now or for good.
    @Test
    public void theQueueIsCountedByStatusForTheOrganisation() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2"), cc("v-3")));
        store.failQueuedForGood(KNOWN, QUEUE_HIP, "Patient has no Eka ID");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-4")));

        assertEquals(java.util.Map.of("FAILED_FINAL", 3L, "QUEUED", 1L), store.linkQueueCounts());
    }

    private void emptyQueue() {
        namedJdbc.getJdbcOperations().update("delete from abdm_care_context where organisation_id = 2");
    }

    private long knownId() {
        return idOf(KNOWN);
    }

    private long idOf(String uuid) {
        return namedJdbc.getJdbcOperations().queryForObject("select id from individual where uuid = ?", Long.class, uuid);
    }

    private static AbdmHipStore.NewCareContext cc(String reference) {
        return new AbdmHipStore.NewCareContext(reference, "Visit " + reference);
    }

    private void age(String reference, String by) {
        namedJdbc.getJdbcOperations().update("update abdm_care_context set updated_at = now() - cast(? as interval) where reference = ?", by, reference);
    }

    private void due(String reference) {
        namedJdbc.getJdbcOperations().update("update abdm_care_context set next_attempt_at = now() - interval '1 second' where reference = ?", reference);
    }

    private Object column(String reference, String column) {
        return namedJdbc.getJdbcOperations().queryForObject("select " + column + " from abdm_care_context where reference = ?", Object.class, reference);
    }

    private void insertSubjectBornOn(String uuid, String dateOfBirth) {
        insertSubject(uuid, "{\"" + CONTACT_NUMBER_CONCEPT + "\": \"9282738493\"}");
        namedJdbc.getJdbcOperations().update("update individual set date_of_birth = ?::date where uuid = ?", dateOfBirth, uuid);
    }

    private void insertSubject(String uuid, String observations) {
        namedJdbc.getJdbcOperations().update("INSERT INTO individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, last_name, gender_id, organisation_id, " +
                "subject_type_id, observations, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                "VALUES (?, 1, 1, current_timestamp, FALSE, 'Prabhu', 'Kumar', 2, 2, (select id from subject_type where name = 'Individual'), ?::jsonb, 1, 1, now(), now())",
                uuid, observations);
    }
}
