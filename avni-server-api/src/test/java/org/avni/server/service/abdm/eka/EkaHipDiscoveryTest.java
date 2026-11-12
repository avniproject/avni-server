package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.AbhaLink;
import org.avni.server.service.abdm.AbdmSettings;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Patient-led linking through Eka (USER_INIT_LINK_601 to 607): the patient finds the health centre in their
// ABHA app, picks visits, and confirms with a code sent to the phone Avni holds for them. EKA_ABDM_SPEC.md section 2.7.
public class EkaHipDiscoveryTest {
    private static final String ON_DISCOVER = "/abdm/v1/care-contexts/on-discover";
    private static final String ON_INIT = "/abdm/v1/care-contexts/discover/link/on-init";
    private static final String ON_CONFIRM = "/abdm/v1/care-contexts/discover/link/on-confirm";

    private final AbdmSettings settings = AbdmTestSupport.settings();
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmHipLinkingService linking = mock(AbdmHipLinkingService.class);
    private final EkaAbdmClient eka = mock(EkaAbdmClient.class);
    private final AbdmPatientPhones phones = mock(AbdmPatientPhones.class);
    private final AbdmPhoneOtp otp = mock(AbdmPhoneOtp.class);
    private final AbhaLinkRepository abhaLinks = mock(AbhaLinkRepository.class);
    private final AbhaEventRepository abhaEvents = mock(AbhaEventRepository.class);
    private EkaHipDiscovery discovery;

    @Before
    public void setUp() {
        discovery = discovery(settings);
        holdsAbha(7, "subject-1", "12345678568912", "asha.devi", "oid-1");
        holdsAbha(8, "subject-2", "12345678568913", "asha.devi", "oid-2");
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
        // A match carries no error: Eka passes code 0 on to ABDM, whose check refuses it, and the whole reply with it
        // ("ABDM-9999 Invalid message", 28 Oct run).
        assertFalse(reply.toString(), reply.has("error"));
    }

    @Test
    public void twoPatientsMatchingAreNeverGuessedBetween() {
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi"),
                new AbdmHipStore.Subject(8, "subject-2", "Asha Devi")));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().length() > 0);
        // ABDM accepts an error code of 3 to 6 digits (or ABDM-nnnn); a 1 fails its check as a 0 does.
        assertTrue(reply.at("/error/code").asText(), reply.at("/error/code").asText().matches("[0-9]{3,6}"));
    }

    // A one-digit slip in a typed ABHA must not hand a patient's name and visits to whoever holds the ABHA typed: only an
    // ABHA Eka verified on the patient's record (its record carries an Eka ID) is matched, by address or by number.
    @Test
    public void aPatientWhoseAbhaWasTypedButNeverVerifiedIsNotOffered() {
        holdsAbha(7, "subject-1", "12345678568912", "asha.devi", null);
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(store.findSubjectsByAbhaNumber("12345678568912")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        ObjectNode event = (ObjectNode) discoverEvent();
        ((com.fasterxml.jackson.databind.node.ArrayNode) event.at("/data/identifiers")).addObject().put("type", "ABHA_NUMBER").put("value", "12-3456-7856-8912");

        discovery.discover(event);

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().length() > 0);
        verify(linking, never()).loadPatient(anyString());
        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
    }

    @Test
    public void aPatientHoldingNoAbhaRecordAtAllIsNotOfferedByAbha() {
        when(abhaEvents.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of());
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));

        discovery.discover(discoverEvent());

        assertEquals(0, sent(ON_DISCOVER).path("patients").size());
    }

    // The record's ABHA answers are typed: a verified number with a typed address that is not the verified ABHA's matches
    // nobody by that address, and still matches by the verified number.
    @Test
    public void aTypedAddressThatIsNotTheVerifiedAbhasOffersNothing_whileTheVerifiedNumberStillFindsThePatient() {
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber("12345678568912");
        link.setAbhaAddress("asha.devi");
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.of(link));
        AbhaEvent record = new AbhaEvent();
        record.setSubjectUuid("subject-1");
        record.setAction("linked");
        record.setAbhaNumber("12345678568912");
        record.setAbhaAddress("asha.real@sbx");
        record.setEkaOid("oid-1");
        when(abhaEvents.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(record));
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(store.findSubjectsByAbhaNumber("12345678568912")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));

        discovery.discover(discoverEvent());

        assertEquals(0, sent(ON_DISCOVER).path("patients").size());
        verify(linking, never()).loadPatient(anyString());

        org.mockito.Mockito.reset(eka);
        when(eka.postSlow(anyString(), any(), any())).thenReturn(new EkaResponse("r", 204, null));
        ObjectNode byNumber = (ObjectNode) discoverEvent();
        ((ObjectNode) byNumber.get("data")).remove("abha_address");
        ((com.fasterxml.jackson.databind.node.ArrayNode) byNumber.at("/data/identifiers")).addObject().put("type", "ABHA_NUMBER").put("value", "12-3456-7856-8912");

        discovery.discover(byNumber);

        assertEquals("subject-1", sent(ON_DISCOVER).at("/patients/0/ref_num").asText());
    }

    // Of two patients holding the typed ABHA, the one Eka verified is the one it is.
    @Test
    public void ofTwoHoldingTheTypedAbhaTheVerifiedOneIsOffered() {
        holdsAbha(8, "subject-2", "12345678568913", "asha.devi", null);
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi"),
                new AbdmHipStore.Subject(8, "subject-2", "Asha Devi")));

        discovery.discover(discoverEvent());

        JsonNode reply = sent(ON_DISCOVER);
        assertEquals(1, reply.path("patients").size());
        assertEquals("subject-1", reply.at("/patients/0/ref_num").asText());
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

    // ---- HIP IDs: each step is for the centre it names, which must be one of the organisation's

    @Test
    public void aDiscoveryNamingAHipIdNotTheOrganisationsIsNotAnswered() {
        ObjectNode event = (ObjectNode) discoverEvent();
        ((ObjectNode) event.get("data")).put("hip_id", "SOMEONE-ELSES-HIP");

        discovery.discover(event);

        verifyNoInteractions(eka);
        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
    }

    @Test
    public void aLinkConfirmNamingAHipIdNotTheOrganisationsIsNotAnswered() {
        ObjectNode event = (ObjectNode) confirmEvent("424242");
        ((ObjectNode) event.get("data")).put("hip_id", "SOMEONE-ELSES-HIP");

        discovery.linkConfirm(event);

        verifyNoInteractions(eka, otp);
        verify(store, never()).markLinked(any(), any(), any());
    }

    // A patient discovering one of the organisation's centres is offered only the visits shared under it.
    @Test
    @SuppressWarnings("unchecked")
    public void discoveryAtOneCentreOffersOnlyTheVisitsSharedUnderIt() {
        discovery = discovery(AbdmTestSupport.settings(AbdmTestSupport.ekaSettings(new org.avni.server.domain.JsonObject()
                .with(org.avni.server.domain.extenalSystem.EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2")))));
        when(linking.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(7, "subject-1", "Asha Devi", "F", 1990,
                "12345678568912", "asha.devi", List.of(new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.parse("2026-05-02T10:00:00Z"), "hip-2"),
                new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.parse("2026-03-14T10:00:00Z"), null))));
        when(store.findCareContexts(7, "hip-2")).thenReturn(List.of(), List.of(row("enc-2", "PENDING")));
        when(store.findSubjectsByAbhaAddress("asha.devi@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        ObjectNode event = (ObjectNode) discoverEvent();
        ((ObjectNode) event.get("data")).put("hip_id", "hip-2");

        discovery.discover(event);

        verify(store).ensureCareContexts(7L, "hip-2", "subject-1", "OPConsultation",
                List.of(new AbdmHipStore.NewCareContext("enc-2", "OPD Visit, 2 May 2026")));
        verify(store, never()).ensureCareContexts(anyLong(), eq("hip-1"), any(), any(), any());
        ArgumentCaptor<java.util.Map<String, String>> headers = ArgumentCaptor.forClass(java.util.Map.class);
        verify(eka).postSlow(eq(ON_DISCOVER), any(), headers.capture());
        assertEquals("hip-2", headers.getValue().get(EkaAbdmClient.H_HIP_ID));
        assertEquals(1, sent(ON_DISCOVER).at("/patients/0/care_contexts").size());
    }

    // ---- link init

    @Test
    public void linkInitSendsACodeToThePatientsPhoneAndAnswersWithTheLinkReference() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.send("9876543210")).thenReturn(true);

        discovery.linkInit(linkInitEvent("enc-2"));

        verify(otp).send("9876543210");
        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        verify(store).insertLinkRequest(eq("txn-1"), reference.capture(), eq(7L), eq("asha.devi@sbx"), eq(List.of("enc-2")), eq("OTP_SENT"), any(),
                eq(HIP_ID));
        JsonNode reply = sent(ON_INIT);
        assertEquals(reference.getValue(), reply.get("ref_num").asText());
        assertFalse(reply.path("otp_expiry").asText().isEmpty());
        assertFalse(reply.toString(), reply.has("error"));
    }

    @Test
    public void linkInitWithNoPhoneToSendTheCodeToAnswersWithAnError() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(phones.phoneOf(7)).thenReturn(Optional.empty());

        discovery.linkInit(linkInitEvent("enc-2"));

        verify(otp, never()).send(anyString());
        verify(store, never()).insertLinkRequest(any(), any(), any(), any(), anyList(), any(), any(), any());
        assertTrue(sent(ON_INIT).at("/error/message").asText().length() > 0);
    }

    @Test
    public void linkInitForAVisitThatIsNotThePatientsIsRefused() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));

        discovery.linkInit(linkInitEvent("enc-of-someone-else"));

        verify(otp, never()).send(anyString());
        assertTrue(sent(ON_INIT).at("/error/message").asText().length() > 0);
    }

    // A link request is for one centre: a visit of the patient's shared under another of the organisation's HIP IDs is
    // not offered at this one, so it cannot be asked for here.
    @Test
    public void linkInitForAVisitSharedUnderAnotherOfTheOrganisationsCentresIsRefused() {
        twoCentres();
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        ObjectNode event = (ObjectNode) linkInitEvent("enc-2");
        ((ObjectNode) event.get("data")).put("hip_id", "hip-1");

        discovery.linkInit(event);

        verify(otp, never()).send(anyString());
        verify(store, never()).insertLinkRequest(any(), any(), any(), any(), anyList(), any(), any(), any());
        assertTrue(sent(ON_INIT).at("/error/message").asText().length() > 0);
    }

    @Test
    public void linkInitKeepsTheCentreTheVisitsAreSharedUnder() {
        twoCentres();
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.send("9876543210")).thenReturn(true);

        discovery.linkInit(linkInitEvent("enc-2"));

        verify(store).insertLinkRequest(eq("txn-1"), anyString(), eq(7L), eq("asha.devi@sbx"), eq(List.of("enc-2")), eq("OTP_SENT"), any(), eq("hip-2"));
    }

    // ---- link confirm

    // The confirm links under the centre the code was sent for, and only when every visit asked for is still shared
    // there: a request is never marked linked with fewer visits than asked, or none.
    @Test
    public void aConfirmIsRefusedWhenTheVisitsToLinkAreNoLongerTheOnesAsked() {
        twoCentres();
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(new AbdmHipStore.LinkRequest(31L, "txn-1", "link-1", 7L, "asha.devi@sbx",
                List.of("enc-2", "enc-1"), "OTP_SENT", Instant.now().plusSeconds(300), "hip-2")));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify("9876543210", "424242")).thenReturn(true);

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markLinked(any(), any(), any());
        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
        verify(store).setLinkRequestStatus(31L, "FAILED");
        JsonNode reply = sent(ON_CONFIRM);
        assertEquals(0, reply.path("patients").size());
        assertTrue(reply.at("/error/message").asText().length() > 0);
    }

    @Test
    public void aConfirmNamingAnotherCentreThanTheCodeWasSentForIsRefused() {
        twoCentres();
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(new AbdmHipStore.LinkRequest(31L, "txn-1", "link-1", 7L, "asha.devi@sbx",
                List.of("enc-2"), "OTP_SENT", Instant.now().plusSeconds(300), "hip-2")));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify("9876543210", "424242")).thenReturn(true);
        ObjectNode event = (ObjectNode) confirmEvent("424242");
        ((ObjectNode) event.get("data")).put("hip_id", "hip-1");

        discovery.linkConfirm(event);

        verify(store, never()).markLinked(any(), any(), any());
        verify(store).setLinkRequestStatus(31L, "FAILED");
    }

    @Test
    public void aConfirmLinksUnderTheCentreTheCodeWasSentFor() {
        twoCentres();
        when(store.findLinkRequest("link-1")).thenReturn(Optional.of(new AbdmHipStore.LinkRequest(31L, "txn-1", "link-1", 7L, "asha.devi@sbx",
                List.of("enc-2"), "OTP_SENT", Instant.now().plusSeconds(300), "hip-2")));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Asha Devi")));
        when(otp.verify("9876543210", "424242")).thenReturn(true);
        when(store.findCareContextsByReferences("hip-2", List.of("enc-2"))).thenReturn(List.of(row("enc-2", "PENDING")));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store).markLinked("hip-2", List.of("enc-2"), "USER");
        verify(store).setLinkRequestStatus(31L, "LINKED");
    }

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
        assertFalse(reply.toString(), reply.has("error"));
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
        verify(store).markLinkRequestedIfPending(eq(HIP_ID), eq(List.of("enc-2")), contains("may have linked them"));
        verify(store).setLinkRequestStatus(31L, "UNANSWERED");
    }

    // A server error after the last try leaves it unknown whether Eka linked them, as no answer does.
    @Test
    public void aServerErrorOnOnConfirmAfterTheLastTryLeavesTheVisitsLinkRequested() {
        rightCodeFor("enc-2", "PENDING");
        when(eka.postSlow(eq(ON_CONFIRM), any(), any())).thenThrow(new EkaCallException(503, null, "Eka POST on-confirm refused with HTTP 503"));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markLinked(any(), any(), any());
        verify(store).markLinkRequestedIfPending(eq(HIP_ID), eq(List.of("enc-2")), any());
        verify(store).setLinkRequestStatus(31L, "UNANSWERED");
    }

    @Test
    public void anOnConfirmThatNeverReachedEkaLeavesTheVisitsUnlinkedAndTheRequestFailed() {
        rightCodeFor("enc-2", "PENDING");
        when(eka.postSlow(eq(ON_CONFIRM), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(false, "did not answer: ConnectException"));

        discovery.linkConfirm(confirmEvent("424242"));

        verify(store, never()).markLinked(any(), any(), any());
        verify(store, never()).markLinkRequestedIfPending(any(), any(), any());
        verify(store).setLinkRequestStatus(31L, "FAILED");
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

    // The organisation has two centres: enc-2 was recorded at hip-2, enc-1 at none, so it goes under the first, hip-1.
    private void twoCentres() {
        discovery = discovery(AbdmTestSupport.settings(AbdmTestSupport.ekaSettings(new org.avni.server.domain.JsonObject()
                .with(org.avni.server.domain.extenalSystem.EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2")))));
        when(linking.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(7, "subject-1", "Asha Devi", "F", 1990,
                "12345678568912", "asha.devi", List.of(new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.parse("2026-05-02T10:00:00Z"), "hip-2"),
                new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.parse("2026-03-14T10:00:00Z"), null))));
    }

    private EkaHipDiscovery discovery(AbdmSettings settings) {
        return new EkaHipDiscovery(settings, store, linking, eka, phones, otp, AbdmTestSupport.orgContext(), abhaLinks, abhaEvents);
    }

    // The subject's saved ABHA and its record; a null Eka ID is an ABHA typed in and never verified with Eka.
    private void holdsAbha(long individualId, String subjectUuid, String abhaNumber, String abhaAddress, String ekaOid) {
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber(abhaNumber);
        link.setAbhaAddress(abhaAddress);
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(individualId)).thenReturn(Optional.of(link));
        AbhaEvent record = new AbhaEvent();
        record.setSubjectUuid(subjectUuid);
        record.setAction("linked");
        record.setAbhaNumber(abhaNumber);
        record.setAbhaAddress(abhaAddress + "@sbx");
        record.setEkaOid(ekaOid);
        when(abhaEvents.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid)).thenReturn(List.of(record));
    }

    private JsonNode sent(String path) {
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(path), body.capture(), any());
        return body.getValue();
    }

    private static JsonNode discoverEvent() {
        return AbdmJson.read("{\"event\":\"abha.care_context_discover\",\"transaction_id\":\"wh-1\",\"data\":{\"abha_address\":\"asha.devi@sbx\"," +
                "\"gender\":\"F\",\"identifiers\":[{\"type\":\"MOBILE\",\"value\":\"9876543210\"}],\"patient_name\":\"Asha Devi\"," +
                "\"request_id\":\"req-1\",\"txn_id\":\"txn-1\",\"year_of_birth\":1990,\"hip_id\":\"test-hip\",\"oid\":\"oid-1\"}}");
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
        return new AbdmHipStore.LinkRequest(31L, "txn-1", "link-1", 7L, "asha.devi@sbx", List.of("enc-2"), "OTP_SENT", expiresAt, HIP_ID);
    }

    private static AbdmHipStore.CareContext row(String reference, String status) {
        return row(reference, status, "OPConsultation");
    }

    private static AbdmHipStore.CareContext row(String reference, String status, String hiType) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, 7, HIP_ID, "subject-1", reference, "Display " + reference,
                hiType, status, "HIP", null, null, Instant.parse("2026-10-25T10:00:00Z"));
    }
}
