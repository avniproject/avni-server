package org.avni.server.service.abdm.hip;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        // An organisation with a contact-number text concept and no PhoneNumber concept.
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
        visits("v-old", "v-young");
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
        visits("v-1");
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
        visits("v-1");
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

    // F5: a link Eka may have taken while failing for everyone (a server error, no answer) waits for its webhook, then is
    // queued again after the deadline with no try counted, however many times that happens.
    @Test
    public void aLinkEkaMayHaveTakenWhileFailingForEveryoneIsQueuedAgainAfterTheDeadline_uncounted() {
        emptyQueue();
        visits("v-1", "v-2");
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        for (int time = 1; time <= AbdmHipStore.LINK_ATTEMPTS + 2; time++) {
            store.markLinkRequested(QUEUE_HIP, time == 1 ? List.of("v-1", "v-2") : List.of("v-1"), "req-" + time);
            if (time == 1) store.markLinked(QUEUE_HIP, List.of("v-2"), "HIP");

            store.awaitLinkAnswer("req-" + time, "Eka gave no clear answer; it may have linked them: HTTP 503");
            store.settleLinkQueue(QUEUE_HIP);

            assertEquals("LINK_REQUESTED", column("v-1", "status"));
            namedJdbc.getJdbcOperations().update("update abdm_care_context set next_attempt_at = now() - interval '1 second' where reference = 'v-1'");
            store.settleLinkQueue(QUEUE_HIP);

            assertEquals("QUEUED", column("v-1", "status"));
            assertEquals(0, column("v-1", "attempts"));
            assertEquals("LINKED", column("v-2", "status"));
        }
    }

    // F5: a link Eka did not take while failing for everyone (its login, 401, 403, a call never made) goes back to the
    // queue with no try counted, behind the patients already waiting; one Eka's webhook settled meanwhile keeps that.
    @Test
    public void aLinkEkaDidNotTakeWhileFailingForEveryoneIsQueuedAgainUncounted_behindThoseWaiting() {
        emptyQueue();
        insertSubject(OTHER, "{}");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        store.queueCareContexts(idOf(OTHER), QUEUE_HIP, OTHER, "OPConsultation", List.of(cc("v-other")));
        age("v-1", "2 hours");
        age("v-2", "2 hours");
        age("v-other", "1 hour");
        assertEquals(List.of(KNOWN, OTHER), store.subjectsToLink(QUEUE_HIP, 50));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1", "v-2"), "req-1");
        store.markLinked(QUEUE_HIP, List.of("v-2"), "HIP");

        store.putBackLinkRequest("req-1", "Eka did not take the link: Eka login failed");

        assertEquals("QUEUED", column("v-1", "status"));
        assertEquals(0, column("v-1", "attempts"));
        assertEquals("Eka did not take the link: Eka login failed", column("v-1", "error"));
        assertEquals("LINKED", column("v-2", "status"));
        assertEquals(List.of(OTHER, KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // F5: an Eka answer or webhook that a link failed never undoes a LINKED Eka or the patient made first.
    @Test
    public void aFailureEkaReportsNeverOverwritesALinkedVisit() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1", "v-2"), "req-1");
        store.markLinked(QUEUE_HIP, List.of("v-1"), "HIP");

        store.markStatus(QUEUE_HIP, List.of("v-1", "v-2"), "FAILED_FINAL", "Eka reported ERRORED");
        store.markByLinkRequest("req-1", "FAILED_FINAL", "Eka reported ERRORED");

        assertEquals("LINKED", column("v-1", "status"));
        assertEquals("FAILED_FINAL", column("v-2", "status"));
        store.markByLinkRequest("req-1", "LINKED", null);
        assertEquals("LINKED", column("v-2", "status"));
    }

    // F5: the status page's requeue takes back visits given up when their tries ran out (Eka's login, 401, 403, a server
    // error, no answer), never one Eka refused, one removed, a removed patient's, or one under a HIP ID no longer the
    // organisation's; they are queued afresh.
    @Test
    public void visitsGivenUpWhenTheirTriesRanOutAreQueuedAgain_neverOnesRefusedOrRemoved() {
        emptyQueue();
        insertSubject(OTHER, "{}");
        visits("v-tries", "v-unanswered", "v-refused", "v-errored", "v-removed", "v-elsewhere", "v-linked");
        visitOf(OTHER, "v-patient-removed");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-tries"), cc("v-refused"), cc("v-errored"),
                cc("v-removed"), cc("v-linked")));
        store.queueCareContexts(knownId(), "OLD-HIP", KNOWN, "OPConsultation", List.of(cc("v-elsewhere")));
        store.queueCareContexts(idOf(OTHER), QUEUE_HIP, OTHER, "OPConsultation", List.of(cc("v-patient-removed")));
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("update abdm_care_context set attempts = ? where reference in ('v-tries', 'v-elsewhere', 'v-patient-removed')",
                AbdmHipStore.LINK_ATTEMPTS - 1);
        store.failQueued(KNOWN, QUEUE_HIP, "Eka link failed: Eka POST /abdm/v1/care-contexts/link refused with HTTP 401");
        store.failQueued(KNOWN, "OLD-HIP", "Eka link failed: Eka login failed");
        store.failQueued(OTHER, QUEUE_HIP, "Eka link failed: Eka login failed");
        assertEquals("FAILED_FINAL", column("v-tries", "status"));
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-unanswered")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-unanswered"), "req-1");
        jdbc.update("update abdm_care_context set attempts = ? where reference = 'v-unanswered'", AbdmHipStore.LINK_ATTEMPTS - 1);
        age("v-unanswered", "30 days");
        store.settleLinkQueue(QUEUE_HIP);
        assertEquals("FAILED_FINAL", column("v-unanswered", "status"));
        store.markStatus(QUEUE_HIP, List.of("v-refused"), "FAILED_FINAL", "Eka refused the link: Eka POST link refused with HTTP 400");
        store.markStatus(QUEUE_HIP, List.of("v-errored"), "FAILED_FINAL", "ABDM-1035: Invalid HIP ID");
        jdbc.update("update abdm_care_context set status = 'FAILED_FINAL', error = 'Eka link failed: Eka login failed; given up after 6 tries' " +
                "where reference = 'v-removed'");
        jdbc.update("update encounter set is_voided = true where uuid = 'v-removed'");
        jdbc.update("update individual set is_voided = true where uuid = ?", OTHER);
        store.markLinked(QUEUE_HIP, List.of("v-linked"), "HIP");

        assertEquals(2, store.requeueGivenUp(List.of(QUEUE_HIP)));

        for (String requeued : List.of("v-tries", "v-unanswered")) {
            assertEquals(requeued, "QUEUED", column(requeued, "status"));
            assertEquals(requeued, 0, column(requeued, "attempts"));
            assertEquals(requeued, null, column(requeued, "error"));
        }
        for (String kept : List.of("v-refused", "v-errored", "v-removed", "v-elsewhere", "v-patient-removed")) {
            assertEquals(kept, "FAILED_FINAL", column(kept, "status"));
        }
        assertEquals("LINKED", column("v-linked", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // F13: a link the patient made from their own app that Eka never answered settles after the deadline like Avni's own:
    // queued, counted as a try, so it is linked rather than shown as being linked for ever; a younger one waits.
    @Test
    public void aPatientsOwnLinkEkaNeverAnsweredSettlesAfterTheDeadlineLikeTheOthers() {
        emptyQueue();
        visits("v-1", "v-2");
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        store.markLinkRequestedIfPending(QUEUE_HIP, List.of("v-1", "v-2"), "Eka gave no clear answer to on-confirm");
        age("v-1", "31 minutes");
        age("v-2", "10 minutes");

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("QUEUED", column("v-1", "status"));
        assertEquals(1, column("v-1", "attempts"));
        assertEquals("LINK_REQUESTED", column("v-2", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    @Test
    public void aLinkEkaNeverAnswersIsGivenUpAtTheCap() {
        emptyQueue();
        visits("v-1");
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-1"), "req-1");
        namedJdbc.getJdbcOperations().update("update abdm_care_context set attempts = ? where reference = 'v-1'", AbdmHipStore.LINK_ATTEMPTS - 1);
        age("v-1", "17 hours");

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("FAILED_FINAL", column("v-1", "status"));
        assertTrue((String) column("v-1", "error"), ((String) column("v-1", "error")).contains(AbdmHipStore.LINK_ATTEMPTS + " tries"));
        assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
    }

    // A refusal that no later try can change (a removed patient, a HIP ID Eka does not know) is final, however long it waits.
    @Test
    public void aFinalFailureIsNeverTakenAgain() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        store.failQueuedForGood(KNOWN, QUEUE_HIP, "Subject not found");
        age("v-1", "30 days");

        assertEquals("FAILED_FINAL", column("v-1", "status"));
        assertEquals("Subject not found", column("v-1", "error"));
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

    // A patient whose ABHA Eka has not verified waits for a verified one: not given up, and not tried by the job meanwhile.
    @Test
    public void aPatientWithoutAVerifiedAbhaWaits_andIsNotTakenByTheJob() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        store.awaitVerifiedAbha(KNOWN, QUEUE_HIP, AbdmHipLinkingService.NO_EKA_ID, store.databaseNow().plusSeconds(1));
        age("v-1", "30 days");

        assertEquals("AWAITING_VERIFIED_ABHA", column("v-1", "status"));
        assertTrue(store.subjectsToLink(QUEUE_HIP, 50).isEmpty());
        assertTrue(store.findCareContextsDue(knownId(), QUEUE_HIP).isEmpty());
        assertEquals(java.util.Map.of("AWAITING_VERIFIED_ABHA", 1L), store.linkQueueCounts());
    }

    // Once the patient's ABHA is verified, their visits waiting for it, and any given up for want of it, are queued afresh;
    // one given up for another reason stays given up.
    @Test
    public void aVerifiedAbhaQueuesAgainTheVisitsWaitingForIt() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2"), cc("v-3")));
        store.awaitVerifiedAbha(KNOWN, QUEUE_HIP, AbdmHipLinkingService.NO_EKA_ID, store.databaseNow().plusSeconds(1));
        namedJdbc.getJdbcOperations().update("update abdm_care_context set status = 'FAILED_FINAL', attempts = 6, error = ? where reference = 'v-2'",
                AbdmHipLinkingService.NO_EKA_ID + "; given up after 6 tries");
        namedJdbc.getJdbcOperations().update("update abdm_care_context set status = 'FAILED_FINAL', error = 'Eka refused the link' where reference = 'v-3'");

        assertEquals(2, store.requeueForVerifiedAbha(KNOWN));

        assertEquals("QUEUED", column("v-1", "status"));
        assertEquals("QUEUED", column("v-2", "status"));
        assertEquals(0, column("v-2", "attempts"));
        assertEquals("FAILED_FINAL", column("v-3", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // What a save of the patient asks before queueing anything again: whether a visit of theirs waits for a verified ABHA,
    // or was given up for want of one.
    @Test
    public void aPatientHasVisitsWaitingForAVerifiedAbhaOnlyWhileOneWaitsOrWasGivenUpForWantOfOne() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        assertFalse(store.hasVisitsWaitingForVerifiedAbha(knownId()));

        namedJdbc.getJdbcOperations().update("update abdm_care_context set status = 'FAILED_FINAL', error = 'Eka refused the link' where reference = 'v-2'");
        assertFalse(store.hasVisitsWaitingForVerifiedAbha(knownId()));

        namedJdbc.getJdbcOperations().update("update abdm_care_context set status = 'FAILED_FINAL', error = ? where reference = 'v-2'",
                AbdmHipLinkingService.NO_EKA_ID + "; given up after 6 tries");
        assertTrue(store.hasVisitsWaitingForVerifiedAbha(knownId()));

        namedJdbc.getJdbcOperations().update("update abdm_care_context set status = 'QUEUED', error = null where reference = 'v-2'");
        store.awaitVerifiedAbha(KNOWN, QUEUE_HIP, AbdmHipLinkingService.NO_EKA_ID, store.databaseNow().plusSeconds(1));
        assertTrue(store.hasVisitsWaitingForVerifiedAbha(knownId()));
    }

    // The job found no verified ABHA, and before it wrote that the visits wait, a verified ABHA was saved and they were
    // queued again: the job's write leaves them queued, as it moves only rows unchanged since it looked.
    @Test
    public void aVerifiedAbhaSavedWhileTheJobLookedIsNotUndoneByTheJob() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));
        age("v-1", "1 minute");
        java.time.Instant jobLooked = store.databaseNow().minusSeconds(30);
        store.requeueForVerifiedAbha(KNOWN);

        store.awaitVerifiedAbha(KNOWN, QUEUE_HIP, AbdmHipLinkingService.NO_EKA_ID, jobLooked);

        assertEquals("QUEUED", column("v-1", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // A removed patient's visits are never sent; their waiting visits are given up, with the reason.
    @Test
    public void aRemovedPatientIsSkipped_andTheirWaitingVisitsGivenUp() {
        emptyQueue();
        insertSubject(OTHER, "{}");
        long other = idOf(OTHER);
        namedJdbc.getJdbcOperations().update("update individual set is_voided = true where id = ?", other);
        visitOf(OTHER, "v-removed");
        visits("v-1");
        store.queueCareContexts(other, QUEUE_HIP, OTHER, "OPConsultation", List.of(cc("v-removed")));
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1")));

        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals("FAILED_FINAL", column("v-removed", "status"));
        assertEquals("The patient was removed", column("v-removed", "error"));
        assertEquals("QUEUED", column("v-1", "status"));
    }

    // F4: a visit voided, cancelled, or of an enrolment voided after it was queued is given up as removed when the queue
    // settles, whether it waits, failed for now or waits for a verified ABHA; one Eka may be linking is left to Eka.
    @Test
    public void aVisitRemovedAfterItWasQueuedIsGivenUpWhenTheQueueSettles() {
        emptyQueue();
        visits("v-live", "v-voided", "v-cancelled", "v-failed", "v-awaiting", "v-requested");
        programmeVisitOf(KNOWN, "v-unenrolled");
        programmeVisitOf(KNOWN, "v-programme");
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-live"), cc("v-voided"), cc("v-cancelled"),
                cc("v-failed"), cc("v-awaiting"), cc("v-unenrolled"), cc("v-programme"), cc("v-never-saved")));
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-requested")));
        store.markLinkRequested(QUEUE_HIP, List.of("v-requested"), "req-1");
        jdbc.update("update abdm_care_context set status = 'FAILED', next_attempt_at = now() + interval '1 hour' where reference = 'v-failed'");
        jdbc.update("update abdm_care_context set status = 'AWAITING_VERIFIED_ABHA' where reference = 'v-awaiting'");
        jdbc.update("update encounter set is_voided = true where uuid in ('v-voided', 'v-failed', 'v-awaiting', 'v-requested')");
        jdbc.update("update encounter set cancel_date_time = now() where uuid = 'v-cancelled'");
        jdbc.update("update program_enrolment set is_voided = true where id = (select program_enrolment_id from program_encounter where uuid = 'v-unenrolled')");

        store.settleLinkQueue(QUEUE_HIP);

        for (String removed : List.of("v-voided", "v-cancelled", "v-failed", "v-awaiting", "v-unenrolled", "v-never-saved")) {
            assertEquals(removed, "FAILED_FINAL", column(removed, "status"));
            assertEquals(removed, AbdmHipStore.VISIT_REMOVED, column(removed, "error"));
        }
        assertEquals("QUEUED", column("v-live", "status"));
        assertEquals("QUEUED", column("v-programme", "status"));
        assertEquals("LINK_REQUESTED", column("v-requested", "status"));
        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
    }

    // F4: patients whose only queued visits were removed never fill the job's batch ahead of a patient with a live visit.
    @Test
    public void fiftyPatientsWhoseQueuedVisitsWereRemovedDoNotHoldUpTheQueue() {
        emptyQueue();
        for (int n = 0; n < 50; n++) {
            String subject = String.format("0a0b0c0d-0000-4000-8000-%012d", n);
            insertSubject(subject, "{}");
            String visit = "removed-visit-" + n;
            visitOf(subject, visit);
            store.queueCareContexts(idOf(subject), QUEUE_HIP, subject, "OPConsultation", List.of(cc(visit)));
        }
        namedJdbc.getJdbcOperations().update("update encounter set is_voided = true where uuid like 'removed-visit-%'");
        namedJdbc.getJdbcOperations().update("update abdm_care_context set updated_at = now() - interval '1 day' where reference like 'removed-visit-%'");
        visits("v-live");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-live")));
        assertFalse(store.subjectsToLink(QUEUE_HIP, 50).contains(KNOWN));

        store.settleLinkQueue(QUEUE_HIP);

        assertEquals(List.of(KNOWN), store.subjectsToLink(QUEUE_HIP, 50));
        assertEquals(java.util.Map.of("FAILED_FINAL", 50L, "QUEUED", 1L), store.linkQueueCounts());
    }

    // F4: what the Link button and the job give up on finding a visit gone: only visits waiting, failed for now or waiting
    // for a verified ABHA, under the HIP IDs named; one linked, being linked or given up for another reason keeps its status.
    @Test
    public void removedVisitsAreGivenUpOnlyWhileTheyWait() {
        emptyQueue();
        store.ensureCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-pending")));
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-queued"), cc("v-linked"), cc("v-requested"), cc("v-refused")));
        store.queueCareContexts(knownId(), "OTHER-HIP", KNOWN, "OPConsultation", List.of(cc("v-elsewhere")));
        store.markLinked(QUEUE_HIP, List.of("v-linked"), "HIP");
        store.markLinkRequested(QUEUE_HIP, List.of("v-requested"), "req-1");
        store.markStatus(QUEUE_HIP, List.of("v-refused"), "FAILED_FINAL", "Eka refused the link");

        store.giveUpRemovedVisits(List.of(QUEUE_HIP), List.of("v-pending", "v-queued", "v-linked", "v-requested", "v-refused", "v-elsewhere"));

        assertEquals(AbdmHipStore.VISIT_REMOVED, column("v-pending", "error"));
        assertEquals("FAILED_FINAL", column("v-queued", "status"));
        assertEquals(AbdmHipStore.VISIT_REMOVED, column("v-queued", "error"));
        assertEquals("LINKED", column("v-linked", "status"));
        assertEquals("LINK_REQUESTED", column("v-requested", "status"));
        assertEquals("Eka refused the link", column("v-refused", "error"));
        assertEquals("QUEUED", column("v-elsewhere", "status"));
    }

    // F4: a visit given up as removed and saved again as done (its void undone) is queued again.
    @Test
    public void aVisitGivenUpAsRemovedIsQueuedAgainWhenSavedAgain() {
        emptyQueue();
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));
        store.giveUpRemovedVisits(List.of(QUEUE_HIP), List.of("v-1"));
        store.markStatus(QUEUE_HIP, List.of("v-2"), "FAILED_FINAL", "Eka refused the link");

        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-1"), cc("v-2")));

        assertEquals("QUEUED", column("v-1", "status"));
        assertEquals(null, column("v-1", "error"));
        assertEquals("FAILED_FINAL", column("v-2", "status"));
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
        store.failQueuedForGood(KNOWN, QUEUE_HIP, "Subject not found");
        store.queueCareContexts(knownId(), QUEUE_HIP, KNOWN, "OPConsultation", List.of(cc("v-4")));

        assertEquals(java.util.Map.of("FAILED_FINAL", 3L, "QUEUED", 1L), store.linkQueueCounts());
    }

    private void emptyQueue() {
        namedJdbc.getJdbcOperations().update("delete from abdm_care_context where organisation_id = 2");
    }

    private static final String ENCOUNTER_TYPE = "(select id from encounter_type where uuid = '3a1535d0-81fd-48fc-85b5-dc9da81064a3')";

    // Done visits of the known subject, so the queue's settling finds them current.
    private void visits(String... references) {
        for (String reference : references) visitOf(KNOWN, reference);
    }

    private void visitOf(String subjectUuid, String reference) {
        namedJdbc.getJdbcOperations().update("INSERT INTO encounter (organisation_id, individual_id, encounter_type_id, observations, encounter_date_time, " +
                "uuid, version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, address_id) VALUES (2, " +
                "(select id from individual where uuid = ?), " + ENCOUNTER_TYPE + ", '{}'::jsonb, now() - interval '1 day', ?, 1, 1, 1, now(), now(), 1)",
                subjectUuid, reference);
    }

    // A done programme visit, in an enrolment of its own.
    private void programmeVisitOf(String subjectUuid, String reference) {
        String enrolment = UUID.randomUUID().toString();
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("INSERT INTO program_enrolment (organisation_id, individual_id, program_id, enrolment_date_time, uuid, version, created_by_id, " +
                "last_modified_by_id, created_date_time, last_modified_date_time, address_id) VALUES (2, (select id from individual where uuid = ?), " +
                "(select id from program where uuid = 'db62a322-0ec2-4bb0-ac24-296dc7216c9a'), now(), ?, 1, 1, 1, now(), now(), 1)", subjectUuid, enrolment);
        jdbc.update("INSERT INTO program_encounter (organisation_id, program_enrolment_id, encounter_type_id, observations, encounter_date_time, uuid, " +
                "version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, individual_id, address_id) VALUES (2, " +
                "(select id from program_enrolment where uuid = ?), " + ENCOUNTER_TYPE + ", '{}'::jsonb, now() - interval '1 day', ?, 1, 1, 1, now(), now(), " +
                "(select id from individual where uuid = ?), 1)", enrolment, reference, subjectUuid);
    }

    private long knownId() {
        return idOf(KNOWN);
    }

    private long idOf(String uuid) {
        return namedJdbc.getJdbcOperations().queryForObject("select id from individual where uuid = ?", Long.class, uuid);
    }

    // F13 (V1_420): the settling's statements on visits waiting for a verified ABHA read the waiting-visits index, not every
    // visit of the HIP ID. Many linked visits make any other path dearer, and sequential scans are priced out.
    @Test
    public void visitsWaitingForAVerifiedAbhaAreReadThroughTheWaitingVisitsIndex() {
        emptyQueue();
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("insert into abdm_care_context (uuid, organisation_id, individual_id, hip_id, patient_reference, reference, display, hi_type, status) " +
                "select 'plan-' || n, 2, (select id from individual where uuid = ?), ?, ?, 'plan-' || n, 'Visit', 'OPConsultation', " +
                "case when n <= 5 then 'AWAITING_VERIFIED_ABHA' else 'LINKED' end from generate_series(1, 2000) n", KNOWN, QUEUE_HIP, KNOWN);
        jdbc.execute("analyze abdm_care_context");
        jdbc.execute("set local enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList("explain select c.id from abdm_care_context c where c.organisation_id = 2 " +
                "and c.hip_id = 'QUEUE-HIP' and c.status in ('QUEUED', 'FAILED', 'AWAITING_VERIFIED_ABHA')", String.class));

        assertTrue(plan, plan.contains("abdm_care_context_unsettled"));
    }

    // Carried from R2-2 (V1_420): a second claim to an Eka ID is found through the organisation and Eka ID index.
    @Test
    public void anEkaIdIsLookedUpThroughItsOwnIndex() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("insert into abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, route, eka_oid, occurred_at, " +
                "created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) select 'plan-' || n, 2, 'subject-' || n, 1, " +
                "'hip-1', 'verified', 'abha_number', 'plan-oid-' || n, now(), 1, 1, now(), now() from generate_series(1, 2000) n");
        jdbc.execute("analyze abha_event");
        jdbc.execute("set local enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList("explain select e.subject_uuid from abha_event e where e.organisation_id = 2 " +
                "and e.eka_oid = 'plan-oid-7' and e.is_voided = false", String.class));

        assertTrue(plan, plan.contains("abha_event_org_eka_oid"));
    }

    // F6: the status page counts the organisation's shares that ended FAILED since the time given. A try to be made again,
    // a share that went, an HIU flow, another organisation's and one older than the time are not counted.
    @Test
    public void failedSharesAreTheOrganisationsHipFlowsThatEndedFailedSinceTheTimeGiven() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        flow(2, "HIP", "FAILED", "now()");
        flow(2, "HIP", "FAILED", "now() - interval '1 day'");
        flow(2, "HIP", "FAILED", "now() - interval '40 days'");
        flow(2, "HIP", "RETRIED", "now()");
        flow(2, "HIP", "TRANSFERRED", "now()");
        flow(2, "HIU", "FAILED", "now()");
        flow(3, "HIP", "FAILED", "now()");

        assertEquals(2L, store.failedSharesSince(Instant.now().minus(Duration.ofDays(30))));
    }

    // Fix round 2: shares Eka may have taken without saying so are counted beside the failed ones, over the same window.
    @Test
    public void unansweredSharesAreTheOrganisationsHipFlowsThatEndedUnansweredSinceTheTimeGiven() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        flow(2, "HIP", "UNANSWERED", "now()");
        flow(2, "HIP", "UNANSWERED", "now() - interval '40 days'");
        flow(2, "HIP", "FAILED", "now()");
        flow(2, "HIU", "UNANSWERED", "now()");
        flow(3, "HIP", "UNANSWERED", "now()");

        assertEquals(1L, store.unansweredSharesSince(Instant.now().minus(Duration.ofDays(30))));
        assertEquals(1L, store.failedSharesSince(Instant.now().minus(Duration.ofDays(30))));
    }

    // Fix round 2: a transaction is sent once its share was sent, or may have been; a refused, retried or still open one,
    // an HIU flow and another organisation's are not.
    @Test
    public void aShareIsSentOnceAFlowOfItsTransactionWasSentOrMayHaveBeen() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        flow(2, "HIP", "TRANSFERRED", "now()", "tx-sent");
        flow(2, "HIP", "UNANSWERED", "now()", "tx-unanswered");
        flow(2, "HIP", "FAILED", "now()", "tx-failed");
        flow(2, "HIP", "RETRIED", "now()", "tx-retried");
        flow(2, "HIP", "REQUESTED", "now()", "tx-open");
        flow(2, "HIU", "RECEIVED", "now()", "tx-hiu");
        flow(3, "HIP", "TRANSFERRED", "now()", "tx-other");

        assertTrue(store.shareSent("tx-sent"));
        assertTrue(store.shareSent("tx-unanswered"));
        for (String tx : List.of("tx-failed", "tx-retried", "tx-open", "tx-hiu", "tx-other", "tx-none")) assertFalse(tx, store.shareSent(tx));
        assertFalse(store.shareSent(null));
    }

    private void flow(long organisationId, String role, String status, String updatedAt) {
        flow(organisationId, role, status, updatedAt, null);
    }

    private void flow(long organisationId, String role, String status, String updatedAt, String transactionId) {
        namedJdbc.getJdbcOperations().update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status, updated_at, transaction_id) " +
                "values (?, ?, ?, 'consent-1', ?, " + updatedAt + ", ?)", UUID.randomUUID().toString(), organisationId, role, status, transactionId);
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
