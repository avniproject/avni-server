package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmPatientPhones;
import org.avni.server.service.abdm.hip.AbdmPhoneOtp;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: patient-led linking through Eka (USER_INIT_LINK_601 to 607): the patient finds TANUH in their
// ABHA app, picks visits, and confirms with a code sent to the phone Avni holds for them. EKA_ABDM_SPEC.md section 2.7.
public class EkaHipDiscoveryTest {
    private static final String ON_DISCOVER = "/abdm/v1/care-contexts/on-discover";
    private static final String ON_INIT = "/abdm/v1/care-contexts/discover/link/on-init";
    private static final String ON_CONFIRM = "/abdm/v1/care-contexts/discover/link/on-confirm";

    private final AbdmConfig cfg = AbdmTestSupport.config();
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmHipLinkingService linking = mock(AbdmHipLinkingService.class);
    private final EkaAbdmClient eka = mock(EkaAbdmClient.class);
    private final AbdmPatientPhones phones = mock(AbdmPatientPhones.class);
    private final AbdmPhoneOtp otp = mock(AbdmPhoneOtp.class);
    private EkaHipDiscovery discovery;

    @Before
    public void setUp() {
        discovery = new EkaHipDiscovery(cfg, store, linking, eka, phones, otp);
        when(eka.postSlow(anyString(), any(), any())).thenReturn(new EkaResponse("r", 204, null));
        when(linking.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(7, "subject-1", "Asha Devi", "F", 1990,
                "12345678568912", "asha.devi", List.of(new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.parse("2026-05-02T10:00:00Z")),
                new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.parse("2026-03-14T10:00:00Z")))));
        when(store.findCareContexts(7, HIP_ID)).thenReturn(List.of(row("enc-2", "PENDING"), row("enc-1", "LINKED")));
        when(phones.phoneOf(7)).thenReturn(Optional.of("9876543210"));
    }

    // ---- discover

    @Test
    public void aPatientFoundByTheirAbhaAddressIsOfferedTheirVisitsNotYetLinked() {
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals("req-1", reply.get("request_id").asText());
        assertEquals("txn-1", reply.get("txn_id").asText());
        assertEquals("subject-1", reply.at("/patients/0/ref_num").asText());
        assertEquals(1, reply.at("/patients/0/care_contexts").size());
        assertEquals("enc-2", reply.at("/patients/0/care_contexts/0/ref_num").asText());
    }

    @Test
    public void twoPatientsMatchingAreNeverGuessedBetween() {
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi"),
                new AbdmHipStore.Subject(8, "subject-2", "Asha Devi")));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().length() > 0);
    }

    // NHA USER_INIT_LINK_603: with no ABHA match, search by the verified mobile, then a fuzzy match on the name.
    @Test
    public void withNoAbhaMatchThePatientHoldingTheMobileWithAMatchingNameIsOffered() {
        onTheMobile(candidate(7, "subject-1", "Asha Devi K"));

        discovery.discover(discoverEvent());

        assertEquals("subject-1", sent(ON_DISCOVER).at("/patients/0/ref_num").asText());
    }

    @Test
    public void aPatientOnTheMobileWhoseNameDiffersIsNotOffered() {
        onTheMobile(candidate(7, "subject-1", "Asha Bai"));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().length() > 0);
    }

    @Test
    public void patientsSharingTheMobileAreNarrowedByName() {
        onTheMobile(candidate(7, "subject-1", "Asha Devi"), candidate(8, "subject-8", "Ravi Kumar"));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(1, reply.path("patients").size());
        assertEquals("subject-1", reply.at("/patients/0/ref_num").asText());
    }

    @Test
    public void twoPatientsOnTheMobileStillMatchingAreNeverGuessedBetween() {
        onTheMobile(candidate(7, "subject-1", "Asha Devi"), candidate(8, "subject-8", "Asha Devi"));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().contains("More than one"));
    }

    // A health worker's phone entered for many patients: past the search's limit, Avni cannot say no one else matches.
    @Test
    public void aMobileSharedByMoreThanTwentyPatientsIsRefused() {
        AbdmHipStore.Candidate[] many = new AbdmHipStore.Candidate[21];
        for (int i = 0; i < many.length; i++) many[i] = candidate(100 + i, "subject-" + (100 + i), i == 0 ? "Asha Devi" : "Ravi Kumar " + i);
        onTheMobile(many);

        discovery.discover(discoverEvent());

        assertEquals(0, sent(ON_DISCOVER).path("patients").size());
    }

    @Test
    public void withoutAMobileOrWithABlankGenderNoSearchByPhoneIsMade() {
        discovery.discover(AbdmJson.read("{\"event\":\"abha.care_context_discover\",\"data\":{\"gender\":\"F\",\"patient_name\":\"Asha Devi\"," +
                "\"request_id\":\"req-1\",\"txn_id\":\"txn-1\",\"year_of_birth\":1990}}"));
        discovery.discover(AbdmJson.read("{\"event\":\"abha.care_context_discover\",\"data\":{\"gender\":\" \",\"patient_name\":\"Asha Devi\"," +
                "\"identifiers\":[{\"type\":\"MOBILE\",\"value\":\"9876543210\"}],\"request_id\":\"req-2\",\"txn_id\":\"txn-2\",\"year_of_birth\":1990}}"));

        verify(store, never()).findSubjectsByPhone(any(), any(), any(), anyInt());
    }

    // ---- link init

    @Test
    public void linkInitSendsACodeToThePatientsPhoneAndAnswersWithTheLinkReference() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.send("9876543210")).thenReturn(true);

        discovery.linkInit(linkInitEvent("enc-2"));

        verify(otp).send("9876543210");
        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        verify(store).insertLinkRequest(eq("txn-1"), reference.capture(), eq(7L), eq("asha.devi@sbx"), eq(List.of("enc-2")), eq("OTP_SENT"), any());
        JsonNode reply = sent(ON_INIT);
        assertEquals(reference.getValue(), reply.get("ref_num").asText());
        assertFalse(reply.path("otp_expiry").asText().isEmpty());
    }

    @Test
    public void linkInitWithNoPhoneToSendTheCodeToAnswersWithAnError() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(phones.phoneOf(7)).thenReturn(Optional.empty());

        discovery.linkInit(linkInitEvent("enc-2"));

        verify(otp, never()).send(anyString());
        verify(store, never()).insertLinkRequest(any(), any(), any(), any(), anyList(), any(), any());
        assertTrue(sent(ON_INIT).at("/error/message").asText().length() > 0);
    }

    @Test
    public void linkInitForAVisitThatIsNotThePatientsIsRefused() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));

        discovery.linkInit(linkInitEvent("enc-of-someone-else"));

        verify(otp, never()).send(anyString());
        assertTrue(sent(ON_INIT).at("/error/message").asText().length() > 0);
    }

    // ---- link confirm

    @Test
    public void theRightCodeLinksTheVisitsAndAnswersWithThem() {
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(linkRequest(Instant.now().plusSeconds(300))));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify("9876543210", "424242")).thenReturn(true);
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-2"))).thenReturn(List.of(row("enc-2", "LINKED")));

        discovery.linkConfirm(confirmEvent("424242"));

        // Calling on-confirm is what links the contexts (EKA_ABDM_SPEC.md:356): Avni says LINKED only after Eka accepts.
        InOrder order = inOrder(eka, store);
        order.verify(eka).postSlow(eq(ON_CONFIRM), any(), any());
        order.verify(store).markLinked(HIP_ID, List.of("enc-2"), "USER");
        order.verify(store).setLinkRequestStatus(31L, "LINKED");
        JsonNode reply = sent(ON_CONFIRM);
        assertEquals("enc-2", reply.at("/patients/0/care_contexts/0/ref_num").asText());
    }

    @Test
    public void aRefusedOnConfirmLeavesTheVisitsUnlinkedAndTheRequestFailed() {
        rightCodeFor("enc-2", "PENDING");
        when(eka.postSlow(eq(ON_CONFIRM), any(), any())).thenThrow(new EkaCallException(400, null, "refused"));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markLinked(any(), any(), any());
        verify(store, never()).markStatus(any(), any(), any(), any());
        verify(store).setLinkRequestStatus(31L, "FAILED");
    }

    // Eka may have linked them: they stay shareable on a data fetch and are offered again, never shown LINKED.
    @Test
    public void anUnansweredOnConfirmLeavesTheVisitsLinkRequested() {
        rightCodeFor("enc-2", "PENDING");
        when(eka.postSlow(eq(ON_CONFIRM), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(true, "did not answer within 60000 ms"));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markLinked(any(), any(), any());
        verify(store).markLinkRequestedIfPending(eq(HIP_ID), eq(List.of("enc-2")), contains("did not answer"));
        verify(store).setLinkRequestStatus(31L, "UNANSWERED");
    }

    // A visit linked, queued or failed by another path during the 60 s wait must keep its status: the update is guarded
    // in SQL at the time it runs (AbdmHipStore.markLinkRequestedIfPending), never by a status read before the wait.
    @Test
    public void anUnansweredOnConfirmChangesOnlyVisitsStillPendingWhenItWrites() {
        rightCodeFor("enc-2", "LINKED");
        when(eka.postSlow(eq(ON_CONFIRM), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(true, "did not answer within 60000 ms"));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markStatus(any(), any(), any(), any());
        verify(store).markLinkRequestedIfPending(eq(HIP_ID), eq(List.of("enc-2")), any());
        verify(store).setLinkRequestStatus(31L, "UNANSWERED");
    }

    private void rightCodeFor(String careContext, String status) {
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(linkRequest(Instant.now().plusSeconds(300))));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify("9876543210", "424242")).thenReturn(true);
        when(store.findCareContextsByReferences(HIP_ID, List.of(careContext))).thenReturn(List.of(row(careContext, status)));
    }

    @Test
    public void aWrongCodeIsCounted_andAfterThreeTheRequestIsClosed() {
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(linkRequest(Instant.now().plusSeconds(300))));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify(anyString(), anyString())).thenReturn(false);
        when(store.countLinkAttempt(31L)).thenReturn(3);

        discovery.linkConfirm(confirmEvent("000000"));

        verify(store, never()).markLinked(anyString(), anyList(), anyString());
        verify(store).setLinkRequestStatus(31L, "FAILED");
        assertTrue(sent(ON_CONFIRM).at("/error/message").asText().length() > 0);
    }

    @Test
    public void aCodeAfterItsExpiryIsRefusedWithoutChecking() {
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(linkRequest(Instant.now().minusSeconds(1))));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(otp, never()).verify(anyString(), anyString());
        verify(store).setLinkRequestStatus(31L, "EXPIRED");
    }

    // ---- helpers

    private JsonNode sent(String path) {
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(path), body.capture(), any());
        return body.getValue();
    }

    private static JsonNode discoverEvent() {
        return AbdmJson.read("{\"event\":\"abha.care_context_discover\",\"transaction_id\":\"wh-1\",\"data\":{\"abha_address\":\"asha.devi@sbx\"," +
                "\"gender\":\"F\",\"identifiers\":[{\"type\":\"MOBILE\",\"value\":\"9876543210\"}],\"patient_name\":\"Asha Devi\"," +
                "\"request_id\":\"req-1\",\"txn_id\":\"txn-1\",\"year_of_birth\":1990,\"hip_id\":\"TANUH\",\"oid\":\"oid-1\"}}");
    }

    private static JsonNode linkInitEvent(String careContext) {
        ObjectNode event = (ObjectNode) AbdmJson.read("{\"event\":\"abha.care_context_discover_link_init\",\"data\":{\"abha_address\":\"asha.devi@sbx\"," +
                "\"request_id\":\"req-2\",\"txn_id\":\"txn-1\",\"patient\":[{\"ref_num\":\"subject-1\",\"care_contexts\":[]}]}}");
        ((com.fasterxml.jackson.databind.node.ArrayNode) event.at("/data/patient/0/care_contexts")).addObject().put("reference_number", careContext);
        return event;
    }

    private static JsonNode confirmEvent(String code) {
        return AbdmJson.read("{\"event\":\"abha.context_discover_link_confirm\",\"data\":{\"linkRefNumber\":\"link-1\",\"request_id\":\"req-3\",\"token\":\"" + code + "\"}}");
    }

    private void onTheMobile(AbdmHipStore.Candidate... candidates) {
        AbdmPatientPhones.PhoneConcepts concepts = new AbdmPatientPhones.PhoneConcepts(null, "phone-concept");
        when(phones.phoneConcepts()).thenReturn(concepts);
        when(store.findSubjectsByPhone(concepts, "9876543210", "F", 1990)).thenReturn(List.of(candidates));
    }

    private static AbdmHipStore.Candidate candidate(long id, String uuid, String name) {
        return new AbdmHipStore.Candidate(id, uuid, name);
    }

    private static AbdmHipStore.LinkRequest linkRequest(Instant expiresAt) {
        return new AbdmHipStore.LinkRequest(31L, "txn-1", "link-1", 7L, "asha.devi@sbx", List.of("enc-2"), "OTP_SENT", expiresAt);
    }

    private static AbdmHipStore.CareContext row(String reference, String status) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, 7, HIP_ID, "subject-1", reference, "Display " + reference,
                "OPConsultation", status, "HIP", null, null, Instant.parse("2026-10-25T10:00:00Z"));
    }
}
