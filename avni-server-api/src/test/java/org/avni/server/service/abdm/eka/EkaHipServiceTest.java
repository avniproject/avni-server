package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmConfig;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class EkaHipServiceTest {
    private static final long INDIVIDUAL_ID = 7L;
    private static final String OID = "eka-oid-0001";
    private static final String LINK = "/abdm/v1/care-contexts/link";
    private static final String ON_FETCH = "/abdm/v1/hip/care-context/data/on-fetch";

    private final AbdmConfig cfg = AbdmTestSupport.config();
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
    private final List<Map<String, Object>> view = List.of(Map.of("reference", "enc-2", "status", "LINK_REQUESTED"));
    private EkaHipService service;

    @Before
    public void setUp() {
        service = new EkaHipService(cfg, store, linking, eka, AbdmTestSupport.orgContext(), abhaEventRepository, encounterRepository,
                abhaLinkRepository, assembler, builder, crypto, phones, programEncounterRepository);

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
                null, null, null, HIP_ID, "TANUH Health Centre", List.of()));
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

        AbdmRequestException e = assertThrows(AbdmRequestException.class, () -> service.linkCareContexts("subject-1"));

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

    // The job links what was queued on save, never a visit the patient may be linking from their own app.
    @Test
    public void theJobLinksOnlyVisitsQueuedOnSave() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING"), row("enc-2", "QUEUED")));

        service.linkCareContexts("subject-1", true);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq(LINK), body.capture(), any());
        assertEquals(1, body.getValue().get("care_contexts").size());
        assertEquals("enc-2", body.getValue().get("care_contexts").get(0).get("care_context_id").asText());
    }

    @Test
    public void nothingToLinkMeansNoCall() {
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        assertSame(view, service.linkCareContexts("subject-1"));
        verifyNoInteractions(eka);
    }

    @Test
    public void anEkaRefusalMarksTheVisitsFailedWithTheReason() {
        when(eka.post(eq(LINK), any(), any())).thenThrow(new EkaCallException(400, "ABDM-1035", "Eka POST " + LINK + " refused with HTTP 400 ABDM-1035: Invalid HIP ID"));

        assertSame(view, service.linkCareContexts("subject-1"));

        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
        verify(store).settleLinkRequest(eq(linkRequestId.getValue()), eq("FAILED"), contains("Invalid HIP ID"));
        verify(store, never()).markByLinkRequest(anyString(), anyString(), any());
    }

    // Eka may have linked them, and its webhook may already have said so: they stay requested, and a LINKED stays.
    @Test
    public void aLinkCallEkaMayHaveActedOnLeavesTheVisitsLinkRequested() {
        when(eka.post(eq(LINK), any(), any())).thenThrow(new EkaCallException(504, null, "Eka POST " + LINK + " refused with HTTP 504"));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<String> linkRequestId = ArgumentCaptor.forClass(String.class);
        verify(store).markLinkRequested(eq(HIP_ID), any(), linkRequestId.capture());
        verify(store).settleLinkRequest(eq(linkRequestId.getValue()), eq("LINK_REQUESTED"), contains("may have linked them"));
        verify(store, never()).settleLinkRequest(anyString(), eq("FAILED"), any());
        verify(store, never()).markByLinkRequest(anyString(), anyString(), any());
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
        verify(store).markStatus(HIP_ID, List.of("enc-2"), "FAILED", "duplicate");
    }

    // ---- link status webhook

    @Test
    public void aLinkedWebhookMarksItsCareContextLinked() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
        verify(store, never()).markStatus(anyString(), any(), anyString(), any());
    }

    @Test
    public void anErroredWebhookMarksItsCareContextFailedWithTheReason() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-2"))).thenReturn(List.of(row("enc-2", "LINK_REQUESTED")));

        service.linkCareContext(event("link-care-context-errored.json"));

        verify(store).markStatus(eq(HIP_ID), eq(List.of("enc-2")), eq("FAILED"), contains("ABDM-1035"));
        verify(store, never()).markLinked(anyString(), any(), anyString());
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
        verify(eka).post(eq("/abdm/v1/care-contexts/sms/notify"), body.capture(), any());
        assertEquals("9876543210", body.getValue().get("mobile_number").asText());
    }

    @Test
    public void withNoPhoneNumberNoTextMessageIsSent() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1"))).thenReturn(List.of(row("enc-1", "LINK_REQUESTED")));
        when(phones.phoneOf(INDIVIDUAL_ID)).thenReturn(Optional.empty());

        service.linkCareContext(event("link-care-context.json"));

        verify(store).markLinked(HIP_ID, List.of("enc-1"), "HIP");
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
        when(eka.post(eq("/abdm/v1/care-contexts/sms/notify"), any(), any())).thenThrow(new EkaCallException(500, null, "down"));

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

    // ---- data fetch (self-custody)

    @Test
    @SuppressWarnings("unchecked")
    public void aDataFetchEncryptsEachRecordToTheRequestersKeyAndPostsItOnFetch() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

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

        service.hipDataFetch(fetchEvent(requester.publicKeyRawBase64(), requester.nonceBase64(), "flow-tx-1"));

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

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), ""));

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

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

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

        EkaHipService.Prepared prepared = service.prepare("penc-1", "subject-1");

        assertNotNull(prepared.problem(), prepared.bundleJson());
        verify(assembler).assemble(eq(visit), eq(patient), any(), any(), any(), any());
    }

    @Test
    public void aDataFetchForRecordTypesWeDoNotHoldSendsNothing() {
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode e = (ObjectNode) event("hip-data-fetch.json");
        ((ObjectNode) e.get("data")).putArray("hi_types").add("Prescription");

        service.hipDataFetch(e);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("OPConsultation"));
    }

    @Test
    public void aDataFetchWithoutKeyMaterialSendsNothingAndRecordsTheFailure() {
        ObjectNode e = (ObjectNode) event("hip-data-fetch.json");
        ((ObjectNode) e.get("data")).remove("key_information");

        service.hipDataFetch(e);

        verifyNoInteractions(eka);
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("key"));
    }

    @Test
    public void anEkaRefusalOfOnFetchIsRecordedAsFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaCallException(400, null, "Eka POST " + ON_FETCH + " refused with HTTP 400"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("HTTP 400"));
    }

    // A visit stays linked at NHA under the ABHA it was linked with. Once the record holds another ABHA, or none, a
    // request from the old one gets nothing (27 Oct sweep: a wrong ABHA corrected on the record kept the records flowing).
    @Test
    public void visitsAreNotSentToAnAbhaTheRecordNoLongerHolds() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("oid", "oid-old-abha").put("abha_address", "old.abha@sbx");

        service.hipDataFetch(fetch);

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("no longer"));
    }

    @Test
    public void noVisitIsSentForAPatientWhoseAbhaWasRemoved() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.empty());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(eka, never()).postSlow(eq(ON_FETCH), any(), any());
    }

    // The ABHA number's own default address (its 14 digits) is the same ABHA as the readable one on the record.
    @Test
    public void aRequestFromTheSavedNumbersDefaultAddressIsServed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        ObjectNode fetch = (ObjectNode) fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1");
        ((ObjectNode) fetch.get("data")).put("oid", "oid-not-recorded").put("abha_address", "12345678568912@sbx");

        service.hipDataFetch(fetch);

        verify(eka).postSlow(eq(ON_FETCH), any(), any());
    }

    // On 26 Oct Eka took over 10 s to take an on-fetch 3 times in 18, and each of those shares was lost.
    @Test
    public void onFetchGoesOnTheSlowLimit() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(eka).postSlow(eq(ON_FETCH), any(), any());
        verify(eka, never()).post(eq(ON_FETCH), any(), any());
    }

    // Eka may have taken the records, so an unanswered share is neither a success nor a failure.
    @Test
    public void anOnFetchEkaNeverAnsweredIsRecordedAsUnansweredNotFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(true, "Eka POST " + ON_FETCH + " did not answer within 60000 ms"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(store).finishDataFlow(eq(42L), eq("UNANSWERED"), eq(2), contains("did not answer"));
        verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
    }

    // A server error after the last try leaves it unknown whether Eka took the records, as no answer does.
    @Test
    public void aServerErrorOnOnFetchAfterTheLastTryIsRecordedAsUnansweredNotFailed() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaCallException(502, null, "Eka POST " + ON_FETCH + " refused with HTTP 502"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(store).finishDataFlow(eq(42L), eq("UNANSWERED"), eq(2), contains("HTTP 502"));
        verify(store, never()).finishDataFlow(eq(42L), eq("FAILED"), anyInt(), any());
    }

    @Test
    public void anOnFetchThatNeverReachedEkaIsRecordedAsFailedWithNothingSent() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "LINKED")));
        when(eka.postSlow(eq(ON_FETCH), any(), any())).thenThrow(new EkaAbdmClient.NoAnswer(false, "Eka POST " + ON_FETCH + " did not answer: ConnectException"));

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), contains("ConnectException"));
        verify(store, never()).finishDataFlow(eq(42L), eq("UNANSWERED"), anyInt(), any());
    }

    @Test
    public void nothingToSendMeansNoOnFetch() {
        FideliusKeyMaterial requester = crypto.generateKeyMaterial();
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of());

        service.hipDataFetch(fetchEvent(requester.publicKeyX509Base64(), requester.nonceBase64(), "flow-tx-1"));

        verifyNoInteractions(eka);
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

        EkaHipService.Prepared prepared = service.prepare("enc-1", null);

        assertNull(prepared.bundleJson());
        verifyNoInteractions(builder);
    }

    private static AbdmHipStore.CareContext row(String reference, String status) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, INDIVIDUAL_ID, HIP_ID, "subject-1", reference, "Display " + reference,
                "OPConsultation", status, "HIP", null, null, Instant.parse("2026-10-25T10:00:00Z"));
    }

    private static Encounter encounter(String uuid, Individual individual, DateTime at) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setIndividual(individual);
        e.setEncounterDateTime(at, null);
        return e;
    }
}
