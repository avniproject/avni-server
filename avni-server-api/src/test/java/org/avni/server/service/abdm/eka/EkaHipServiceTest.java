package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.fhir.CareContextDocument;
import org.avni.server.service.abdm.fhir.CareContextDocumentAssembler;
import org.avni.server.service.abdm.fhir.FhirBundleBuilder;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abha.AbhaVerifiedRecords;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

public class EkaHipServiceTest {
    private static final long INDIVIDUAL_ID = 7L;
    private static final long ORGANISATION_ID = 2L;
    private static final String OID = "eka-oid-0001";
    private static final String LINK = "/abdm/v1/care-contexts/link";
    private static final String ON_FETCH = "/abdm/v1/hip/care-context/data/on-fetch";

    private final AbdmSettings settings = AbdmTestSupport.settings();
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmHipLinkingService linking = mock(AbdmHipLinkingService.class);
    private final EkaAbdmClient eka = mock(EkaAbdmClient.class);
    private final AbhaEventRepository abhaEventRepository = mock(AbhaEventRepository.class);
    private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
    private final AbhaLinkRepository abhaLinkRepository = mock(AbhaLinkRepository.class);
    private final CareContextDocumentAssembler assembler = mock(CareContextDocumentAssembler.class);
    private final FhirBundleBuilder builder = mock(FhirBundleBuilder.class);
    private final FideliusCrypto crypto = new FideliusCrypto();
    private final org.avni.server.service.abdm.hip.AbdmPatientPhones phones = mock(org.avni.server.service.abdm.hip.AbdmPatientPhones.class);
    private final org.avni.server.dao.ProgramEncounterRepository programEncounterRepository = mock(org.avni.server.dao.ProgramEncounterRepository.class);
    private final IndividualRepository individualRepository = mock(IndividualRepository.class);
    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final List<Map<String, Object>> view = List.of(Map.of("reference", "enc-2", "status", "LINK_REQUESTED"));
    private EkaHipService service;

    @Before
    public void setUp() {
        service = new EkaHipService(settings, store, linking, eka, AbdmTestSupport.orgContext(), new AbhaVerifiedRecords(abhaEventRepository, bugsnag),
                encounterRepository, abhaLinkRepository, assembler, builder, crypto, phones, programEncounterRepository, individualRepository, bugsnag);

        when(linking.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(INDIVIDUAL_ID, "subject-1", "Asha Devi", "F",
                1990, "12345678568912", "asha.devi", List.of(
                new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.parse("2026-05-02T18:00:00Z")),
                new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.parse("2026-03-14T04:30:00Z")))));
        when(linking.careContexts("subject-1")).thenReturn(view);
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", OID, "asha.devi@sbx")));
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row("enc-2", "PENDING")));
        when(eka.post(anyString(), any(), any())).thenReturn(new EkaResponse("r-1", 202, null));

        Individual patient = new Individual();
        patient.setId(INDIVIDUAL_ID);
        patient.setUuid("subject-1");
        when(encounterRepository.findByUuid("enc-1")).thenReturn(encounter("enc-1", patient, new DateTime(2026, 3, 14, 10, 0)));
        when(encounterRepository.findByUuid("enc-2")).thenReturn(encounter("enc-2", patient, new DateTime(2026, 5, 2, 10, 0)));
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber("12345678568912");
        link.setAbhaAddress("asha.devi");
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.of(link));
        when(assembler.assemble(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> new CareContextDocument(
                ((org.avni.server.domain.AbstractEncounter) inv.getArgument(0)).getUuid(), "OPD Visit", null, "subject-1", "Asha Devi", "Female", null,
                null, null, null, HIP_ID, "Test Health Centre", List.of()));
        when(builder.build(any())).thenAnswer(inv -> bundle(inv.getArgument(0)));
        when(store.insertDataFlow(anyString(), anyString(), any(), anyString(), anyString(), any())).thenReturn(42L);
    }

    // ---- linking

    @Test
    @SuppressWarnings("unchecked")
    public void linkingSendsTheUnlinkedVisitsToEkaWithThePatientsIdsAndNoRecords() {
        List<Map<String, Object>> answer = service.linkCareContexts("subject-1");

        assertSame(view, answer);
        ArgumentCaptor<List<AbdmHipStore.NewCareContext>> visits = ArgumentCaptor.forClass(List.class);
        verify(store).ensureCareContexts(eq(INDIVIDUAL_ID), eq(HIP_ID), eq("subject-1"), eq("OPConsultation"), visits.capture());
        assertEquals(List.of("enc-2", "enc-1"), visits.getValue().stream().map(AbdmHipStore.NewCareContext::reference).toList());

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).post(eq(LINK), body.capture(), headers.capture());
        assertEquals(OID, headers.getValue().get(EkaAbdmClient.H_PATIENT_OID));
        assertEquals("subject-1", headers.getValue().get(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        assertEquals(HIP_ID, headers.getValue().get(EkaAbdmClient.H_HIP_ID));

        JsonNode b = body.getValue();
        assertEquals("asha.devi@sbx", b.get("abha_address").asText());
        assertEquals(OID, b.get("oid").asText());
        assertEquals("subject-1", b.get("partner_user_id").asText());
        JsonNode careContexts = b.get("care_contexts");
        assertEquals(2, careContexts.size());
        JsonNode first = careContexts.get(0);
        assertEquals("enc-1", first.get("care_context_id").asText());
        assertEquals("Display enc-1", first.get("display").asText());
        assertEquals("OPConsultation", first.get("hi_type").asText());
        // Avni keeps custody: the record goes only on Eka's hip_data_fetch, never with the link.
        for (JsonNode c : careContexts) {
            assertFalse(c.has("data"));
            assertFalse(c.has("hi_types"));
        }
        verifyNoInteractions(assembler, builder);

        // Persist before send: the rows carry the link request before Eka sees it.
        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(store, eka);
        order.verify(store).markLinkRequested(eq(HIP_ID), eq(List.of("enc-1", "enc-2")), linkRequestId.capture());
        order.verify(eka).post(eq(LINK), any(), any());
        assertNotNull(linkRequestId.getValue());
        verify(store, never()).markByLinkRequest(anyString(), anyString(), any());
    }

    @Test
    public void aPatientWithoutAnEkaIdGetsAClearErrorAndNothingIsSent() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", null, "asha.devi@sbx")));

        AbdmRequestException e = assertThrows(EkaHipService.NoVerifiedAbha.class, () -> service.linkCareContexts("subject-1"));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("Patient has no Eka ID: register or verify their ABHA through the app first", e.getMessage());
        verifyNoInteractions(eka);
        verify(store, never()).ensureCareContexts(anyLong(), anyString(), anyString(), anyString(), any());
    }

    // A lookup of another person's ABHA, left unsaved in an edit, is the latest record: the visits still go to the ABHA
    // on the saved record, under its own Eka ID, never to the other person's (27 Oct run, same cause as the card).
    @Test
    public void aLaterLookupOfAnotherAbhaLeftUnsavedIsNeverLinkedTo() {
        AbhaEvent other = abhaEvent("linked", "oid-someone-else", "someone.else@sbx", "91000000004533");
        when(abhaEventRepository.findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(Optional.of(other));
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(other, abhaEvent("linked", OID, "asha.devi@sbx", "12345678568912")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(OID, body.getValue().get("oid").asText());
        assertEquals("asha.devi@sbx", body.getValue().get("abha_address").asText());
    }

    @Test
    public void onlyALookupOfAnotherAbhaMeansNoEkaId() {
        AbhaEvent other = abhaEvent("linked", "oid-someone-else", "someone.else@sbx", "91000000004533");
        when(abhaEventRepository.findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(Optional.of(other));
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(other));

        assertThrows(AbdmRequestException.class, () -> service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);
    }

    // The phone's record names the saved number with another person's address: the visits wait, and nothing goes to Eka.
    @Test
    public void aRecordWithTheSavedNumberButAnotherAddressIsNeverLinkedTo() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", "oid-someone-else", "someone.else@sbx", "12345678568912")));

        assertThrows(EkaHipService.NoVerifiedAbha.class, () -> service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);
    }

    // A later record naming another Eka ID for the saved ABHA is the phone's word alone: the visits go to the first one.
    @Test
    public void aLaterRecordNamingAnotherEkaIdForTheSavedAbhaIsNeverLinkedTo() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(
                abhaEvent("verified", "oid-someone-else", "asha.devi@sbx", "12345678568912"),
                abhaEvent("linked", OID, "asha.devi@sbx", "12345678568912")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(OID, body.getValue().get("oid").asText());
    }

    // One Eka ID, one live patient: another patient's saved ABHA claimed this Eka ID first, so this one's visits are
    // neither linked nor shared under it, and the clash is reported.
    @Test
    public void anEkaIdAnotherLivePatientClaimedFirstIsNeitherLinkedNorShared() {
        claimedFirstByAnotherPatient();

        assertThrows(EkaHipService.NoVerifiedAbha.class, () -> service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);

        EkaHipService.Prepared prepared = service.prepare("enc-1", "subject-1", OID, null, HIP_ID, settings.current());
        assertNull(prepared.bundleJson());
        assertEquals("the patient's ABHA is not verified with Eka", prepared.problem());
        verify(bugsnag, atLeastOnce()).notify(any(IllegalStateException.class));
    }

    private void claimedFirstByAnotherPatient() {
        AbhaEvent ours = abhaEvent("linked", OID, "asha.devi@sbx", "12345678568912");
        ours.setId(9L);
        ours.setOrganisationId(ORGANISATION_ID);
        AbhaEvent theirs = abhaEvent("linked", OID, "first.holder@sbx", "91333344444533");
        theirs.setSubjectUuid("subject-0");
        theirs.setId(3L);
        theirs.setOrganisationId(ORGANISATION_ID);
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(ours));
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-0")).thenReturn(List.of(theirs));
        when(abhaEventRepository.findOtherLiveSubjectsNamingEkaOid(ORGANISATION_ID, OID, "subject-1")).thenReturn(List.of(new AbhaEventRepository.SavedAbha() {
            public String getSubjectUuid() { return "subject-0"; }
            public String getAbhaNumber() { return "91333344444533"; }
            public String getAbhaAddress() { return "first.holder@sbx"; }
        }));
    }

    @Test
    public void aRemovedAbhaCountsAsNoEkaId() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("removed", OID, null)));

        assertThrows(AbdmRequestException.class, () -> service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);
    }

    @Test
    public void linkedVisitsAreNotSentAgain() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "FAILED")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(1, body.getValue().get("care_contexts").size());
        assertEquals("enc-2", body.getValue().get("care_contexts").get(0).get("care_context_id").asText());
    }

    // The job links what was queued on save and is due, never a visit the patient may be linking from their own app nor
    // one still waiting out its wait after a failure.
    @Test
    public void theJobLinksOnlyVisitsQueuedOnSaveThatAreDue() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "FAILED"), row("enc-2", "QUEUED")));
        when(store.findCareContextsDue(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-2", "QUEUED")));

        service.linkCareContexts("subject-1", true);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(1, body.getValue().get("care_contexts").size());
        assertEquals("enc-2", body.getValue().get("care_contexts").get(0).get("care_context_id").asText());
    }

    // F4: a visit voided or cancelled since it was queued is no longer one of the patient's visits: given up as removed,
    // never sent, and the rest are linked.
    @Test
    public void theJobGivesUpAQueuedVisitRemovedSinceItWasQueued_andLinksTheRest() {
        when(store.findCareContextsDue(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "QUEUED"), row("enc-3", "QUEUED")));

        service.linkCareContexts("subject-1", true);

        verify(store).giveUpRemovedVisits(List.of(HIP_ID), List.of("enc-3"));
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(1, body.getValue().get("care_contexts").size());
        assertEquals("enc-1", body.getValue().get("care_contexts").get(0).get("care_context_id").asText());
    }

    // F4: the Link button sends only the patient's current visits too.
    @Test
    public void theLinkButtonGivesUpAVisitRemovedSinceItsCareContextWasMade() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row("enc-3", "FAILED"), row("enc-4", "LINKED")));

        service.linkCareContexts("subject-1");

        verify(store).giveUpRemovedVisits(List.of(HIP_ID), List.of("enc-3"));
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(1, body.getValue().get("care_contexts").size());
        assertEquals("enc-1", body.getValue().get("care_contexts").get(0).get("care_context_id").asText());
    }

    // F4: a HIP ID under which the patient has no current visit left still has its removed visits given up, so they do
    // not wait there forever and hold the patient at the head of the queue.
    @Test
    public void aRemovedVisitUnderAHipIdWithNoCurrentVisitIsGivenUp() {
        EkaHipService service = withTwoHips();
        visitsRecordedAt(null, null);
        when(store.findCareContextsDue(INDIVIDUAL_ID, "hip-2")).thenReturn(List.of(row("enc-9", "QUEUED")));

        service.linkCareContexts("subject-1", true);

        verify(store).giveUpRemovedVisits(List.of("hip-2"), List.of("enc-9"));
        verify(store, never()).ensureCareContexts(anyLong(), eq("hip-2"), any(), any(), any());
        verify(store, never()).markLinkRequested(eq("hip-2"), any(), any());
    }

    @Test
    public void nothingToLinkMeansNoCall() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        assertSame(view, service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);
    }

    // Eka refusing the request itself (a HIP ID it does not know) is final: sending it again changes nothing.
    @Test
    public void anEkaRefusalGivesUpTheVisitsWithTheReason() {
        when(eka.post(eq(LINK), any(), any())).thenThrow(new EkaCallException(400, "ABDM-1035", "Eka POST " + LINK + " refused with HTTP 400 ABDM-1035: Invalid HIP ID"));

        assertSame(view, service.linkCareContexts("subject-1"));

        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
        verify(store).settleLinkRequest(eq(linkRequestId.getValue()), eq("FAILED_FINAL"), contains("Invalid HIP ID"));
        verify(store, never()).failLinkRequest(anyString(), anyString());
        verify(store, never()).markByLinkRequest(anyString(), anyString(), any());
    }

    // F5: Eka failing for every patient (its login, a lapsed key, an outage, no answer) is not this patient's fault: the
    // visits go back to the queue with no try counted, and the failure is thrown so the run stops instead of using up
    // every other patient's tries the same way.
    @Test
    public void anEkaWideFailureThatTookNothingPutsTheVisitsBackUncounted_andIsThrown() {
        for (EkaCallException e : List.of(new EkaCallException(0, null, "Eka login failed: Eka login failed with HTTP 401"),
                new EkaCallException(401, null, "Eka POST " + LINK + " refused with HTTP 401"),
                new EkaCallException(403, null, "Eka POST " + LINK + " refused with HTTP 403"),
                new EkaCallException(429, null, "Eka POST " + LINK + " refused with HTTP 429"),
                new EkaAbdmClient.NoAnswer(false, "Eka POST " + LINK + " did not answer: ConnectException"),
                new EkaAbdmClient.NoAnswer(false, "Eka POST " + LINK + " not sent: interrupted"))) {
            reset(store, eka);
            when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING")));
            when(eka.post(eq(LINK), any(), any())).thenThrow(e);

            assertSame(e, assertThrows(EkaCallException.class, () -> service.linkCareContexts("subject-1")));

            ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
            verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
            verify(store).putBackLinkRequest(eq(linkRequestId.getValue()), contains(e.getMessage()));
            verify(store, never()).failLinkRequest(anyString(), anyString());
            verify(store, never()).settleLinkRequest(anyString(), anyString(), any());
        }
    }

    // F5: Eka may have linked them (a server error, or no answer to a call it received): they stay requested, a LINKED
    // stays, and they are looked at again after the deadline with no try counted; the run stops.
    @Test
    public void anEkaWideFailureEkaMayHaveActedOnLeavesTheVisitsRequestedUncounted_andIsThrown() {
        for (EkaCallException e : List.of(new EkaCallException(504, null, "Eka POST " + LINK + " refused with HTTP 504"),
                new EkaAbdmClient.NoAnswer(true, "Eka POST " + LINK + " did not answer within 10000 ms"))) {
            reset(store, eka);
            when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING")));
            when(eka.post(eq(LINK), any(), any())).thenThrow(e);

            assertSame(e, assertThrows(EkaCallException.class, () -> service.linkCareContexts("subject-1")));

            ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
            verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
            verify(store).awaitLinkAnswer(eq(linkRequestId.getValue()), contains("may have linked them"));
            verify(store, never()).settleLinkRequest(anyString(), anyString(), any());
            verify(store, never()).failLinkRequest(anyString(), anyString());
            verify(store, never()).markByLinkRequest(anyString(), anyString(), any());
        }
    }

    // F5: once Eka fails for everyone, the patient's other record types are not sent to fail the same way.
    @Test
    public void anEkaWideFailureSendsNoMoreOfThePatientsVisits() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row(OTHER_TYPE, "PENDING", "Prescription")));
        when(eka.post(eq(LINK), any(), any())).thenThrow(new EkaCallException(503, null, "Eka POST " + LINK + " refused with HTTP 503"));

        assertThrows(EkaCallException.class, () -> service.linkCareContexts("subject-1"));

        verify(eka, times(1)).post(eq(LINK), any(), any());
    }

    // A fault of Avni's own while linking (not Eka's answer) may pass later: counted, and tried again after a wait.
    @Test
    public void aFaultOfAvnisOwnWhileLinkingIsCountedForAnotherTry() {
        when(eka.post(eq(LINK), any(), any())).thenThrow(new IllegalStateException("connection pool closed"));

        assertSame(view, service.linkCareContexts("subject-1"));

        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
        verify(store).failLinkRequest(eq(linkRequestId.getValue()), contains("connection pool closed"));
        verify(store, never()).putBackLinkRequest(anyString(), anyString());
    }

    @Test
    public void aSynchronousAnswerWithAStatusIsApplied() {
        when(eka.post(eq(LINK), any(), any())).thenReturn(new EkaResponse("r", 200, AbdmJson.read("{\"status\":\"LINKED\"}")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
        verify(store).markByLinkRequest(linkRequestId.getValue(), "LINKED", null);
    }

    @Test
    public void aSynchronousAnswerPerCareContextIsAppliedPerCareContext() {
        when(eka.post(eq(LINK), any(), any())).thenReturn(new EkaResponse("r", 200, AbdmJson.read(
                "{\"care_contexts\":[{\"care_context_id\":\"enc-1\",\"status\":\"LINKED\"},{\"care_context_id\":\"enc-2\",\"status\":\"ERRORED\",\"error\":\"duplicate\"}]}")));

        service.linkCareContexts("subject-1");

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
        verify(store).markStatus(HIP_ID, List.of("enc-2"), "FAILED_FINAL", "duplicate");
    }

    // ---- link status webhook

    @Test
    public void aLinkedWebhookMarksItsCareContextLinked() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
        verify(store, never()).markStatus(anyString(), any(), anyString(), any());
    }

    // Eka reports ERRORED after its own retries (an unknown HIP ID, say): final.
    @Test
    public void anErroredWebhookGivesUpItsCareContextWithTheReason() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-2"))).thenReturn(List.of(row("enc-2", "LINK_REQUESTED")));

        service.linkCareContext(event("link-care-context-errored.json"));

        verify(store).markStatus(eq(HIP_ID), eq(List.of("enc-2")), eq("FAILED_FINAL"), contains("ABDM-1035"));
        verify(store, never()).markLinked(anyString(), any(), anyString());
    }

    @Test
    public void anErroredWebhookStoresItsReasonWithoutThePatientsIdentifiers() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-2"))).thenReturn(List.of(row("enc-2", "LINK_REQUESTED")));
        JsonNode webhook = event("link-care-context-errored.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) webhook.at("/data/error")).put("message", "No care context for test.patient@sbx");

        service.linkCareContext(webhook);

        verify(store).markStatus(eq(HIP_ID), eq(List.of("enc-2")), eq("FAILED_FINAL"),
                argThat(reason -> reason.contains("ABDM-1035") && !reason.contains("@sbx")));
    }

    @Test
    public void aWebhookForAnUnknownCareContextFallsBackToThePatientsRequestedVisits() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of());
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(INDIVIDUAL_ID, "subject-1", "Asha Devi")));
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-5", "LINK_REQUESTED"), row("enc-6", "LINKED")));

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-5"), "HIP");
    }

    // HIP_INIT_NOTIFY_HIECM: NHA's call "informs patients that a care context has been successfully linked"; Eka's takes
    // the mobile number only (EKA_ABDM_SPEC.md:188).
    @Test
    public void aLinkedVisitTellsThePatientByTextMessage() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.of("9876543210"));

        service.linkCareContext(event("link-care-context.json"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postAtMostOnce(eq("/abdm/v1/care-contexts/sms/notify"), body.capture(), any());
        assertEquals("9876543210", body.getValue().get("mobile_number").asText());
        // Each try reaches the patient's phone: never sent again after a server error.
        verify(eka, never()).post(eq("/abdm/v1/care-contexts/sms/notify"), any(), any());
    }

    @Test
    public void withNoPhoneNumberNoTextMessageIsSent() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.empty());

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
        verify(eka, never()).postAtMostOnce(eq("/abdm/v1/care-contexts/sms/notify"), any(), any());
        verify(eka, never()).post(eq("/abdm/v1/care-contexts/sms/notify"), any(), any());
    }

    // On 26 Oct a visit linked and no text went, with nothing in the log to say why.
    @Test
    public void withNoPhoneNumberTheSkippedTextIsLogged() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.empty());
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EkaHipService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logged = new ch.qos.logback.core.read.ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            service.linkCareContext(event("link-care-context.json"));
        } finally {
            logger.detachAppender(logged);
        }

        assertTrue(logged.list.stream().anyMatch(e -> e.getFormattedMessage().contains("no phone number")));
    }

    @Test
    public void aTextMessageEkaRefusesLeavesTheLinkAsItIs() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.of("9876543210"));
        when(eka.postAtMostOnce(eq("/abdm/v1/care-contexts/sms/notify"), any(), any())).thenThrow(new EkaCallException(500, null, "down"));

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
    }

    @Test
    public void aWebhookWithAnUnknownStatusChangesNothing() {
        ObjectNode e = (ObjectNode) event("link-care-context.json");
        ((ObjectNode) e.get("data")).put("status", "PENDING_AT_ABDM");

        service.linkCareContext(e);

        verify(store, never()).markLinked(anyString(), any(), anyString());
        verify(store, never()).markStatus(anyString(), any(), anyString(), any());
    }

    // ---- HIP IDs: the organisation's, never one fixed for the server

    private EkaHipService withTwoHips() {
        return new EkaHipService(AbdmTestSupport.settings(AbdmTestSupport.ekaSettings(new org.avni.server.domain.JsonObject()
                .with(org.avni.server.domain.extenalSystem.EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2"))
                .with(org.avni.server.domain.extenalSystem.EkaSystemConfig.HIP_NAMES, Map.of("hip-1", "First Centre", "hip-2", "Second Centre")))),
                store, linking, eka, AbdmTestSupport.orgContext(), new AbhaVerifiedRecords(abhaEventRepository, bugsnag), encounterRepository,
                abhaLinkRepository, assembler, builder, crypto, phones, programEncounterRepository, individualRepository, bugsnag);
    }

    private void visitsRecordedAt(String enc2Hip, String enc1Hip) {
        when(linking.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(INDIVIDUAL_ID, "subject-1", "Asha Devi", "F",
                1990, "12345678568912", "asha.devi", List.of(
                new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.parse("2026-05-02T18:00:00Z"), enc2Hip),
                new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.parse("2026-03-14T04:30:00Z"), enc1Hip))));
    }

    // Avni starts the link: each visit under the HIP ID of the user who recorded it, else the organisation's first.
    @Test
    @SuppressWarnings("unchecked")
    public void visitsRecordedAtTwoOfTheOrganisationsCentresAreLinkedEachUnderItsOwnHipId() {
        EkaHipService service = withTwoHips();
        visitsRecordedAt("hip-2", null);
        when(store.findCareContexts(INDIVIDUAL_ID, "hip-1")).thenReturn(List.of(), List.of(row("enc-1", "PENDING")));
        when(store.findCareContexts(INDIVIDUAL_ID, "hip-2")).thenReturn(List.of(), List.of(row("enc-2", "PENDING")));

        service.linkCareContexts("subject-1");

        verify(store).ensureCareContexts(eq(INDIVIDUAL_ID), eq("hip-1"), eq("subject-1"), eq("OPConsultation"),
                eq(List.of(new AbdmHipStore.NewCareContext("enc-1", "OPD Visit, 14 Mar 2026"))));
        verify(store).ensureCareContexts(eq(INDIVIDUAL_ID), eq("hip-2"), eq("subject-1"), eq("OPConsultation"),
                eq(List.of(new AbdmHipStore.NewCareContext("enc-2", "OPD Visit, 2 May 2026"))));
        verify(store).markLinkRequested(eq("hip-1"), eq(List.of("enc-1")), anyString());
        verify(store).markLinkRequested(eq("hip-2"), eq(List.of("enc-2")), anyString());
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka, times(2)).post(eq(LINK), any(), headers.capture());
        assertEquals(List.of("hip-2", "hip-1"), headers.getAllValues().stream().map(h -> h.get(EkaAbdmClient.H_HIP_ID)).toList());
    }

    @Test
    public void aVisitKeepsTheHipIdItsCareContextWasMadeUnder() {
        EkaHipService service = withTwoHips();
        visitsRecordedAt("hip-2", null);
        when(store.findCareContexts(INDIVIDUAL_ID, "hip-1")).thenReturn(List.of(row("enc-2", "LINKED"), row("enc-1", "PENDING")));

        service.linkCareContexts("subject-1");

        verify(store).ensureCareContexts(eq(INDIVIDUAL_ID), eq("hip-1"), any(), any(), any());
        verify(store, never()).ensureCareContexts(anyLong(), eq("hip-2"), any(), any(), any());
        verify(store, never()).markLinkRequested(eq("hip-2"), any(), any());
    }

    @Test
    public void aLinkStatusNamingAHipIdNotTheOrganisationsIsDropped() {
        ObjectNode e = (ObjectNode) event("link-care-context.json");
        ((ObjectNode) e.get("data")).put("hip_id", "SOMEONE-ELSES-HIP");

        service.linkCareContext(e);

        verify(store, never()).findCareContextsByReferences(any(), any());
        verify(store, never()).markLinked(any(), any(), any());
        verify(store, never()).markStatus(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aLinkStatusIsAppliedUnderTheHipIdItNames_andThePatientIsTextedAsThatCentre() {
        EkaHipService service = withTwoHips();
        ObjectNode e = (ObjectNode) event("link-care-context.json");
        ((ObjectNode) e.get("data")).put("hip_id", "hip-2");
        when(store.findCareContextsByReferences("hip-2", List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.of("9876543210"));

        service.linkCareContext(e);

        verify(store).markLinked("hip-2", List.of("enc-1"), "HIP");
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postAtMostOnce(eq("/abdm/v1/care-contexts/sms/notify"), any(), headers.capture());
        assertEquals("hip-2", headers.getValue().get(EkaAbdmClient.H_HIP_ID));
    }

    @Test
    public void aDataFetchNamingAHipIdNotTheOrganisationsSendsNothing() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        ObjectNode e = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) e.get("data")).put("hip_id", "SOMEONE-ELSES-HIP");

        service.hipDataFetch(e, false);

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("HIP ID"));
        verify(eka, never()).postSlow(any(), any(), any());
        verifyNoInteractions(assembler, bugsnag);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aDataFetchIsAnsweredAsTheCentreItNames() {
        EkaHipService service = withTwoHips();
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        ObjectNode e = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) e.get("data")).put("hip_id", "hip-2");
        when(store.findCareContextsByReferences("hip-2", List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(e, false);

        verify(assembler, times(2)).assemble(any(), any(), any(), any(), eq("hip-2"), eq("Second Centre"));
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq(ON_FETCH), any(), headers.capture());
        assertEquals("hip-2", headers.getValue().get(EkaAbdmClient.H_HIP_ID));
    }

    // ---- data fetch (self-custody)

    @Test
    @SuppressWarnings("unchecked")
    public void aDataFetchEncryptsEachRecordToTheRequestersKeyAndPostsItOnFetch() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), headers.capture());
        assertEquals(OID, headers.getValue().get(EkaAbdmClient.H_PATIENT_OID));
        assertEquals("subject-1", headers.getValue().get(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        assertEquals(HIP_ID, headers.getValue().get(EkaAbdmClient.H_HIP_ID));

        JsonNode b = body.getValue();
        assertEquals("flow-tx-1", b.get("transaction_id").asText());
        assertEquals(1, b.get("page_number").asInt());
        assertEquals(1, b.get("page_count").asInt());
        JsonNode ours = b.get("key_information");
        assertEquals("ECDH", ours.get("crypto_alg").asText());
        assertEquals("Curve25519", ours.get("curve").asText());
        assertEquals("Curve25519/32byte random key", ours.at("/dh_public_key/parameters").asText());
        assertFalse(ours.at("/dh_public_key/expiry").asText().isBlank());
        assertNotEquals(requester.nonceBase64(), ours.get("nonce").asText());

        JsonNode entries = b.get("entries");
        assertEquals(2, entries.size());
        JsonNode entry = entries.get(0);
        assertEquals("enc-1", entry.get("care_context_id").asText());
        assertEquals("application/fhir+json", entry.get("media").asText());
        String plain = crypto.decrypt(entry.get("content").asText(), requester.privateKeyBase64(), requester.nonceBase64(),
                ours.at("/dh_public_key/key_value").asText(), ours.get("nonce").asText());
        assertEquals("enc-1", AbdmJson.read(plain).get("id").asText());
        assertEquals(AbdmFlows.md5Hex(plain), entry.get("checksum").asText());

        InOrder order = inOrder(store, eka);
        order.verify(store).insertDataFlow(eq("eka:flow-tx-1"), eq("flow-tx-1"), eq("wh-fetch-1"), eq("eka:" + ON_FETCH), eq("REQUESTED"), isNull());
        order.verify(eka).postSlow(eq(ON_FETCH), any(), any());
        order.verify(store).finishDataFlow(42L, "TRANSFERRED", 2, null);
    }

    @Test
    public void aRequesterKeyAsARawPointWorksToo() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyRawBase64(), requester.nonceBase64(), "flow-tx-1"), false);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), any());
        JsonNode b = body.getValue();
        assertEquals(1, b.get("entries").size());
        String plain = crypto.decrypt(b.at("/entries/0/content").asText(), requester.privateKeyBase64(), requester.nonceBase64(),
                b.at("/key_information/dh_public_key/key_value").asText(), b.at("/key_information/nonce").asText());
        assertEquals("enc-1", AbdmJson.read(plain).get("id").asText());
        verify(store).finishDataFlow(eq(42L), eq("TRANSFERRED"), eq(1), contains("enc-2 (not linked through this HIP)"));
    }

    @Test
    public void anEmptyDataTransactionIdFallsBackToTheTopLevelOne() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), ""), false);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), any());
        assertEquals("wh-fetch-1", body.getValue().get("transaction_id").asText());
    }

    @Test
    public void aRecordOfAnotherPatientOrNotLinkedHereIsNotSent() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        Individual other = new Individual();
        other.setId(9L);
        other.setUuid("subject-9");
        when(encounterRepository.findByUuid("enc-2")).thenReturn(encounter("enc-2", other, new DateTime(2026, 5, 2, 10, 0)));
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), any());
        assertEquals(1, body.getValue().get("entries").size());
        assertEquals("enc-1", body.getValue().at("/entries/0/care_context_id").asText());
        verify(store).finishDataFlow(eq(42L), eq("TRANSFERRED"), eq(1), contains("enc-2"));
    }

    // NHA: share only what the consent covers. Avni holds visit notes (OPConsultation) only.
    // A visit under a programme is a care context too; its record is built from the programme visit.
    @Test
    public void aProgrammeVisitIsPreparedFromItsEnrolmentsPatient() {
        Individual patient = new Individual();
        patient.setId(INDIVIDUAL_ID);
        patient.setUuid("subject-1");
        org.avni.server.domain.ProgramEnrolment enrolment = new org.avni.server.domain.ProgramEnrolment();
        enrolment.setIndividual(patient);
        org.avni.server.domain.ProgramEncounter visit = new org.avni.server.domain.ProgramEncounter();
        visit.setUuid("penc-1");
        visit.setProgramEnrolment(enrolment);
        visit.setEncounterDateTime(new DateTime(2026, 10, 26, 9, 0), null);
        when(programEncounterRepository.findByUuid("penc-1")).thenReturn(visit);

        EkaHipService.Prepared prepared = service.prepare("penc-1", "subject-1", OID, null, HIP_ID, settings.current());

        assertNotNull(prepared.problem(), prepared.bundleJson());
        verify(assembler).assemble(eq(visit), eq(patient), any(), any(), any(), any());
    }

    @Test
    public void aDataFetchForRecordTypesWeDoNotHoldSendsNothing() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode e = (ObjectNode) event("hip-data-fetch.json");
        ((ObjectNode) e.get("data")).putArray("hi_types").add("Prescription");

        service.hipDataFetch(e, false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("asked for Prescription"));
        verifyNoInteractions(bugsnag);
    }

    @Test
    public void aDataFetchWithoutKeyMaterialSendsNothingAndRecordsTheFailure() {
        ObjectNode e = (ObjectNode) event("hip-data-fetch.json");
        ((ObjectNode) e.get("data")).remove("key_information");

        service.hipDataFetch(e, false);

        verifyNoInteractions(eka);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("key"));
    }

    @Test
    public void anEkaRefusalOfOnFetchIsRecordedAsFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaCallException(400, null, "Eka POST " + ON_FETCH + " refused with HTTP 400"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("HTTP 400"));
        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    // A visit stays linked at NHA under the ABHA it was linked with. Once the record holds another ABHA, or none, a
    // request from the old one gets nothing (27 Oct sweep: a wrong ABHA corrected on the record kept the records flowing).
    @Test
    public void visitsAreNotSentToAnAbhaTheRecordNoLongerHolds() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("oid", "oid-old-abha").put("abha_address", "old.abha@sbx");

        service.hipDataFetch(fetch, false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("no longer"));
        verifyNoInteractions(bugsnag);
    }

    // Eka naming visits and no patient's ABHA leaves nothing to check the ABHA against: nothing is built or sent.
    @Test
    public void aDataRequestNamingNoPatientsAbhaIsRefused() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).remove(List.of("oid", "abha_address"));

        service.hipDataFetch(fetch, false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verifyNoInteractions(assembler, builder, bugsnag);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("names no patient"));
    }

    // One request is one patient's: visits of two patients asked together are refused whole.
    @Test
    public void aDataRequestForVisitsOfMoreThanOnePatientIsRefused() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        AbdmHipStore.CareContext anotherPatients = new AbdmHipStore.CareContext(2, "cc-enc-2", 9L, HIP_ID, "subject-9", "enc-2", "Display enc-2",
                "OPConsultation", "LINKED", "HIP", null, null, Instant.parse("2026-10-25T10:00:00Z"));
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), anotherPatients));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verifyNoInteractions(assembler, builder);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("more than one patient"));
        // Fix round 2: never caused by a patient, so it alarms.
        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    // A visit of another patient only discovered here, not linked, still makes the request one for two patients.
    @Test
    public void aDataRequestAlsoNamingAnotherPatientsUnlinkedVisitIsRefused() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        AbdmHipStore.CareContext anotherPatients = new AbdmHipStore.CareContext(2, "cc-enc-2", 9L, HIP_ID, "subject-9", "enc-2", "Display enc-2",
                "OPConsultation", "PENDING", null, null, null, Instant.parse("2026-10-25T10:00:00Z"));
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), anotherPatients));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("more than one patient"));
    }

    // An ABHA typed on the record and never verified with Eka gets nothing, even when the request names that address.
    @Test
    public void visitsAreNotSentToAnAbhaOnTheRecordThatEkaNeverVerified() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", null, "asha.devi@sbx")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("abha_address", "asha.devi@sbx").remove("oid");

        service.hipDataFetch(fetch, false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verifyNoInteractions(assembler);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("not verified"));
    }

    @Test
    public void noVisitIsSentForAPatientWhoseAbhaWasRemoved() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.empty());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
    }

    // The verified ABHA number's own default address (its 14 digits) is the same ABHA as its readable address.
    @Test
    public void aRequestFromTheVerifiedNumbersDefaultAddressIsServed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", OID, "asha.devi@sbx", "12345678568912")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("abha_address", "12345678568912@sbx").remove("oid");

        service.hipDataFetch(fetch, false);

        verify(eka).postSlow(eq(ON_FETCH), any(), any());
    }

    // The saved number with another address is not the saved ABHA's record, so nothing is served on it, whoever asks; and
    // an Eka ID that is not the verified one refuses whatever address comes with it.
    @Test
    public void aRecordWithAnotherAddressServesNothing_norDoesAnotherEkaId() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        String[][] cases = {
                // the record's address, the Eka ID asking, the address asking, why nothing is served
                {"asha.real@sbx", null, "asha.real@sbx", "not verified"},
                {"asha.devi@sbx", "oid-someone-else", "asha.devi@sbx", "no longer"}};
        for (String[] c : cases) {
            reset(eka, store);
            when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
            when(store.insertDataFlow(anyString(), anyString(), any(), anyString(), anyString(), any())).thenReturn(42L);
            when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                    .thenReturn(List.of(abhaEvent("linked", OID, c[0], "12345678568912")));
            FideliusKeyMaterial requester = crypto.generateKeyMaterial();
            ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
            ObjectNode data = (ObjectNode) fetch.get("data");
            data.put("abha_address", c[2]);
            if (c[1] == null) data.remove("oid");
            else data.put("oid", c[1]);

            service.hipDataFetch(fetch, false);

            verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
            verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains(c[3]));
        }
    }

    // The record sent names the patient by the verified ABHA, never the record's typed answers: a typed address in another
    // case or with another suffix, or a typed answer with no number while the verified ABHA has one.
    @Test
    public void theRecordSentNamesThePatientByTheVerifiedAbha() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        String[][] cases = {
                // typed number, typed address, verified address, expected address
                {"12345678568912", "Asha.Devi@abdm", "asha.devi@sbx", "asha.devi@sbx"},
                {null, "asha.devi", "asha.devi@sbx", "asha.devi@sbx"}};
        for (String[] c : cases) {
            reset(assembler);
            when(assembler.assemble(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> new CareContextDocument(
                    ((org.avni.server.domain.AbstractEncounter) inv.getArgument(0)).getUuid(), "OPD Visit", null, "subject-1", "Asha Devi", "Female", null,
                    null, null, null, HIP_ID, "Test Health Centre", List.of()));
            AbhaLink link = new AbhaLink();
            link.setAbhaNumber(c[0]);
            link.setAbhaAddress(c[1]);
            when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.of(link));
            when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                    .thenReturn(List.of(abhaEvent("linked", OID, c[2], "12345678568912")));
            FideliusKeyMaterial requester = crypto.generateKeyMaterial();

            service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

            verify(assembler, times(2)).assemble(any(), any(), eq("12-3456-7856-8912"), eq(c[3]), eq(HIP_ID), any());
        }
    }

    // A verified ABHA with a number and no address is still the patient's: linked and served under the number's default
    // address, its 14 digits with the environment's suffix.
    @Test
    public void aVerifiedAbhaWithNoAddressLinksAndServesUnderItsNumbersDefaultAddress() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", OID, null, "12345678568912")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals("12345678568912@sbx", body.getValue().get("abha_address").asText());

        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("abha_address", "12345678568912@sbx").remove("oid");

        service.hipDataFetch(fetch, false);

        verify(eka).postSlow(eq(ON_FETCH), any(), any());
        verify(assembler, times(2)).assemble(any(), any(), eq("12-3456-7856-8912"), eq("12345678568912@sbx"), eq(HIP_ID), any());
    }

    // The link goes to Eka under the verified ABHA's address as Eka wrote it, never as it was typed.
    @Test
    public void linkingSendsTheVerifiedAbhasAddressNotTheTypedOne() {
        when(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1"))
                .thenReturn(List.of(abhaEvent("linked", OID, "Asha.Devi@sbx", "12345678568912")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals("Asha.Devi@sbx", body.getValue().get("abha_address").asText());
    }

    // On 26 Oct Eka took over 10 s to take an on-fetch 3 times in 18, and each of those shares was lost.
    @Test
    public void onFetchGoesOnTheSlowLimit() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka).postSlow(eq(ON_FETCH), any(), any());
        verify(eka, never()).post(eq(ON_FETCH), any(), any());
    }

    // Eka may have taken the records, so an unanswered share is neither a success nor a failure.
    @Test
    public void anOnFetchEkaNeverAnsweredIsRecordedAsUnansweredNotFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(true, "Eka POST " + ON_FETCH + " did not answer within 60000 ms"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(store).finishDataFlow(eq(42L), eq("UNANSWERED"), eq(2), contains("did not answer"));
        verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
    }

    // A server error after the last try leaves it unknown whether Eka took the records, as no answer does.
    @Test
    public void aServerErrorOnOnFetchAfterTheLastTryIsRecordedAsUnansweredNotFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaCallException(502, null, "Eka POST " + ON_FETCH + " refused with HTTP 502"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(store).finishDataFlow(eq(42L), eq("UNANSWERED"), eq(2), contains("HTTP 502"));
        verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
    }

    // F6: an on-fetch that may pass later (never reached Eka, an expired login, too many requests) is thrown, so the inbox
    // tries the share again; this try is recorded as one to be made again, not as a failed share, and raises no alarm.
    @Test
    public void anOnFetchThatMayPassLaterIsThrownForTheInboxToTryAgain_notRecordedAsFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        for (EkaCallException e : List.of(new EkaAbdmClient.NoAnswer(false, "Eka POST " + ON_FETCH + " did not answer: ConnectException"),
                new EkaCallException(401, null, "Eka POST " + ON_FETCH + " refused with HTTP 401"),
                new EkaCallException(429, null, "Eka POST " + ON_FETCH + " refused with HTTP 429"))) {
            reset(eka, store);
            when(store.insertDataFlow(anyString(), anyString(), any(), anyString(), anyString(), any())).thenReturn(42L);
            when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
            when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(e);

            EkaCallException thrown = assertThrows(EkaCallException.class,
                    () -> service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false));

            assertSame(e, thrown);
            verify(store).finishDataFlow(eq(42L), eq("RETRIED"), eq(0), contains(e.getMessage()));
            verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
        }
        verifyNoInteractions(bugsnag);
    }

    // F6: on the inbox's last try nothing is left to try again: the share ends FAILED and is reported.
    @Test
    public void onTheLastTryAnOnFetchThatNeverReachedEkaEndsFailed_andIsReported() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(false, "Eka POST " + ON_FETCH + " did not answer: ConnectException"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), true);

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("ConnectException"));
        verify(store, never()).finishDataFlow(eq(42L), eq("UNANSWERED"), anyInt(), any());
        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    // F6: a visit the database could not read for now may be read on the next try, so the share is tried again whole
    // rather than sent without it; on the last try the rest is sent, that visit is named as not sent, and it is reported.
    @Test
    public void aVisitTheDatabaseCouldNotReadForNowIsTriedAgain_andOnTheLastTryTheRestIsSent() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(encounterRepository.findByUuid("enc-1")).thenThrow(new org.springframework.dao.TransientDataAccessResourceException("connection reset"));

        assertThrows(org.springframework.dao.TransientDataAccessResourceException.class,
                () -> service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false));

        verify(eka, never()).postSlow(any(), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("RETRIED"), eq(0), contains("TransientDataAccessResourceException"));
        verifyNoInteractions(bugsnag);

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), true);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), any());
        assertEquals(1, body.getValue().get("entries").size());
        assertEquals("enc-2", body.getValue().at("/entries/0/care_context_id").asText());
        verify(store).finishDataFlow(eq(42L), eq("TRANSFERRED"), eq(1), contains("enc-1 (could not be prepared)"));
        verify(bugsnag).notify(any(org.springframework.dao.TransientDataAccessResourceException.class));
    }

    // Fix round 2: the inbox may hand over a share already sent (its done was not recorded, or the server stopped between
    // the post and done). Once a flow of this transaction was sent, or may have been, nothing is posted again.
    @Test
    public void aShareAlreadySentIsNotSentAgain_whenTheInboxHandsItOverAgain() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(store.shareSent("flow-tx-1")).thenReturn(true);

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka, never()).postSlow(any(), any(), any());
        verify(store, never()).insertDataFlow(anyString(), anyString(), any(), anyString(), anyString(), any());
        verifyNoInteractions(assembler, bugsnag);
    }

    // Fix round 2: a dropped connection is a database fault that may pass, however the driver or Hibernate names it.
    @Test
    public void aDroppedConnectionIsADatabaseFaultThatMayPass_andHoldsTheShareForAnotherTry() {
        for (Throwable fault : List.<Throwable>of(new org.springframework.dao.DataAccessResourceFailureException("connection dropped"),
                new org.hibernate.exception.JDBCConnectionException("connection dropped", new java.sql.SQLException("08006")),
                new RuntimeException(new java.sql.SQLTransientConnectionException("08001")),
                new RuntimeException(new java.sql.SQLRecoverableException("socket closed")),
                new org.springframework.dao.TransientDataAccessResourceException("deadlock"))) {
            assertTrue(fault.toString(), EkaHipService.transientDatabaseFault(fault));
        }
        assertFalse(EkaHipService.transientDatabaseFault(new IllegalStateException("a form element the builder cannot read")));

        // Carried from R2-1: Postgres's own codes, wherever the driver's exception sits in the chain: a connection fault
        // (class 08), a serialization failure (40001) or a deadlock (40P01). A constraint broken (23505) is not one.
        for (String state : List.of("08006", "08003", "40001", "40P01")) {
            Throwable fault = new IllegalStateException("could not read", new RuntimeException(new java.sql.SQLException("database fault", state)));
            assertTrue(state, EkaHipService.transientDatabaseFault(fault));
        }
        assertFalse(EkaHipService.transientDatabaseFault(new RuntimeException(new java.sql.SQLException("duplicate key", "23505"))));
        assertFalse(EkaHipService.transientDatabaseFault(new RuntimeException(new java.sql.SQLException("no state"))));

        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(encounterRepository.findByUuid("enc-1")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("connection dropped",
                new java.sql.SQLRecoverableException("socket closed")));

        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false));
        verify(eka, never()).postSlow(any(), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("RETRIED"), eq(0), any());
    }

    // Fix round 1: a visit that cannot be built (not a passing database fault) would fail on every try, so it is not sent,
    // it is reported, and the rest of the share goes on the first try.
    @Test
    public void aVisitThatCannotBeBuiltIsReported_andTheRestOfTheShareGoesOnTheFirstTry() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        IllegalStateException buildBug = new IllegalStateException("a form element the builder cannot read");
        doThrow(buildBug).when(assembler).assemble(argThat(e -> e != null && "enc-1".equals(((org.avni.server.domain.AbstractEncounter) e).getUuid())),
                any(), any(), any(), any(), any());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(ON_FETCH), body.capture(), any());
        assertEquals(1, body.getValue().get("entries").size());
        assertEquals("enc-2", body.getValue().at("/entries/0/care_context_id").asText());
        verify(store).finishDataFlow(eq(42L), eq("TRANSFERRED"), eq(1), contains("enc-1 (could not be prepared)"));
        verify(store, never()).finishDataFlow(eq(42L), eq("RETRIED"), anyInt(), any());
        verify(bugsnag).notify(buildBug);
    }

    // Fix round 1: once Eka has taken the records, a failure to note that is reported and never sends them again: nothing
    // is thrown back to the inbox and no try is recorded as to be made again.
    @Test
    public void aShareEkaTookIsNotSentAgain_whenNotingItFails() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenReturn(new EkaResponse("r-1", 202, null));
        IllegalStateException noted = new IllegalStateException("database down");
        doThrow(noted).when(store).finishDataFlow(eq(42L), eq("TRANSFERRED"), anyInt(), any());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verify(eka, times(1)).postSlow(eq(ON_FETCH), any(), any());
        verify(store, never()).finishDataFlow(eq(42L), eq("RETRIED"), anyInt(), any());
        verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    // Fix round 1: a fault before anything is built (reading the visits asked for) is tried again by the inbox, and on its
    // last try the share ends FAILED and is reported, as a fault while building is.
    @Test
    public void aFaultReadingTheRequestIsTriedAgain_andOnTheLastTryEndsFailedAndIsReported() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        org.springframework.dao.TransientDataAccessResourceException down = new org.springframework.dao.TransientDataAccessResourceException("connection reset");
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenThrow(down);

        assertSame(down, assertThrows(RuntimeException.class,
                () -> service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false)));
        verify(store).finishDataFlow(eq(42L), eq("RETRIED"), eq(0), contains("TransientDataAccessResourceException"));
        verifyNoInteractions(bugsnag);

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), true);

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("TransientDataAccessResourceException"));
        verify(bugsnag).notify(any(IllegalStateException.class));
        verify(eka, never()).postSlow(any(), any(), any());
    }

    @Test
    public void nothingToSendMeansNoOnFetch() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"), false);

        verifyNoInteractions(eka, bugsnag);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), anyString());
    }

    // ---- helpers

    static JsonNode event(String name) {
        try (InputStream in = EkaHipServiceTest.class.getResourceAsStream("/abdm/eka/" + name)) {
            return AbdmJson.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode fetchEvent(String requesterKey, String requesterNonce, String dataTransactionId) {
        ObjectNode e = (ObjectNode) event("hip-data-fetch.json");
        ObjectNode data = (ObjectNode) e.get("data");
        data.put("transaction_id", dataTransactionId);
        ((ObjectNode) data.at("/key_information/dh_public_key")).put("key_value", requesterKey);
        ((ObjectNode) data.get("key_information")).put("nonce", requesterNonce);
        return e;
    }

    // A bundle whose id is the encounter's, so a test can tell which visit an entry carries.
    private static ObjectNode bundle(CareContextDocument document) {
        ObjectNode b = AbdmJson.obj();
        b.put("resourceType", "Bundle");
        b.put("id", document.encounterUuid());
        return b;
    }

    private static AbhaEvent abhaEvent(String action, String oid, String abhaAddress, String abhaNumber) {
        AbhaEvent event = abhaEvent(action, oid, abhaAddress);
        event.setAbhaNumber(abhaNumber);
        return event;
    }

    private static AbhaEvent abhaEvent(String action, String oid, String abhaAddress) {
        AbhaEvent event = new AbhaEvent();
        event.setSubjectUuid("subject-1");
        event.setAction(action);
        event.setEkaOid(oid);
        event.setAbhaAddress(abhaAddress);
        return event;
    }

    // A patient voided at the HIP is removed, though a visit of theirs stays linked at NHA: it is never sent (26 Oct run).
    @Test
    public void aVisitOfAVoidedPatientIsNotSent() {
        Individual voided = new Individual();
        voided.setId(INDIVIDUAL_ID);
        voided.setUuid("subject-1");
        voided.setVoided(true);
        when(encounterRepository.findByUuid("enc-1")).thenReturn(encounter("enc-1", voided, new DateTime(2026, 3, 14, 10, 0)));

        EkaHipService.Prepared prepared = service.prepare("enc-1", null, OID, null, HIP_ID, settings.current());

        assertNull(prepared.bundleJson());
        verifyNoInteractions(builder);
    }

    private static AbdmHipStore.CareContext row(String reference, String status) {
        return row(reference, status, "OPConsultation");
    }

    private static AbdmHipStore.CareContext row(String reference, String status, String hiType) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, INDIVIDUAL_ID, HIP_ID, "subject-1", reference, "Display " + reference,
                hiType, status, "HIP", null, null, Instant.parse("2026-10-25T10:00:00Z"));
    }

    // ---- record types

    private static final String OTHER_TYPE = "doc-1";

    // One link call per record type: a type Eka refuses fails only its own care contexts, never the patient's visits.
    @Test
    @SuppressWarnings("unchecked")
    public void linkingSendsEachRecordTypeInACallOfItsOwn() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row(OTHER_TYPE, "PENDING", "Prescription")));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka, times(2)).post(eq(LINK), body.capture(), any());
        JsonNode visits = body.getAllValues().get(0).get("care_contexts");
        assertEquals(1, visits.size());
        assertEquals("enc-1", visits.get(0).get("care_context_id").asText());
        assertEquals("OPConsultation", visits.get(0).get("hi_type").asText());
        JsonNode others = body.getAllValues().get(1).get("care_contexts");
        assertEquals(1, others.size());
        assertEquals(OTHER_TYPE, others.get(0).get("care_context_id").asText());
        assertEquals("Prescription", others.get(0).get("hi_type").asText());
        ArgumentCaptor<String> requestIds = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).markLinkRequested(eq(HIP_ID), any(), requestIds.capture());
        assertNotEquals(requestIds.getAllValues().get(0), requestIds.getAllValues().get(1));
    }

    @Test
    public void aTypeEkaRefusesFailsOnlyItsOwnCareContexts() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row(OTHER_TYPE, "PENDING", "Prescription")));
        when(eka.post(eq(LINK), argThat(b -> b != null && b.toString().contains("Prescription")), any()))
                .thenThrow(new EkaCallException(400, null, "Eka POST link refused with HTTP 400"));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<String> requestIds = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).markLinkRequested(eq(HIP_ID), any(), requestIds.capture());
        verify(store).settleLinkRequest(eq(requestIds.getAllValues().get(1)), eq("FAILED_FINAL"), anyString());
        verify(store, never()).settleLinkRequest(eq(requestIds.getAllValues().get(0)), anyString(), anyString());
    }

    private static Individual patient(String uuid) {
        Individual p = new Individual();
        p.setId(INDIVIDUAL_ID);
        p.setUuid(uuid);
        p.setFirstName("Asha");
        p.setLastName("Devi");
        return p;
    }

    private static Encounter encounter(String uuid, Individual individual, DateTime at) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setIndividual(individual);
        e.setEncounterDateTime(at, null);
        return e;
    }
}
