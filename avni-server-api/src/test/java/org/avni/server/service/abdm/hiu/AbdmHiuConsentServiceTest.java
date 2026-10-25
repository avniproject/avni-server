package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIU_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AbdmHiuConsentServiceTest {
    private final AbdmHiuStore store = mock(AbdmHiuStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private final AbdmHiuDataService dataService = mock(AbdmHiuDataService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EkaHiuService> ekaProvider = mock(ObjectProvider.class);
    private AbdmHiuConsentService service;

    @Before
    public void setUp() {
        service = new AbdmHiuConsentService(AbdmTestSupport.config(), store, gateway, dataService, AbdmTestSupport.noWaitRetry(), ekaProvider);
    }

    @Test
    public void inEkaModeAConsentRequestGoesThroughEka() {
        AbdmConfig cfg = AbdmTestSupport.config();
        when(cfg.isEka()).thenReturn(true);
        EkaHiuService eka = mock(EkaHiuService.class);
        when(ekaProvider.getObject()).thenReturn(eka);
        Map<String, Object> ekaAnswer = Map.of("uuid", "u-1", "status", "REQUESTED");
        when(eka.requestConsent("ramesh.gond", null, null, List.of("OPConsultation"), "CAREMGT", "Dr. A")).thenReturn(ekaAnswer);
        service = new AbdmHiuConsentService(cfg, store, gateway, dataService, AbdmTestSupport.noWaitRetry(), ekaProvider);

        Map<String, Object> answer = service.requestConsent("ramesh.gond", null, null, List.of("OPConsultation"), "CAREMGT", "Dr. A");

        assertEquals(ekaAnswer, answer);
        verifyNoInteractions(gateway);
        verify(store, never()).insertConsentRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aConsentRequestIsStoredThenSentWithTheContractsBody() {
        Map<String, Object> answer = service.requestConsent("ramesh.gond", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 25),
                null, null, null);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> extra = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_CONSENT_INIT), requestId.capture(), body.capture(), extra.capture());
        assertEquals(HIU_ID, extra.getValue().get(AbdmPaths.H_HIU_ID));
        JsonNode c = body.getValue().get("consent");
        assertEquals("Care Management", c.at("/purpose/text").asText());
        assertEquals("CAREMGT", c.at("/purpose/code").asText());
        assertEquals("http://terminology.hl7.org/ValueSet/v3-PurposeOfUse", c.at("/purpose/refUri").asText());
        assertEquals("ramesh.gond@sbx", c.at("/patient/id").asText());
        assertEquals(HIU_ID, c.at("/hiu/id").asText());
        assertEquals("Dr. Demo", c.at("/requester/name").asText());
        assertEquals("REGNO", c.at("/requester/identifier/type").asText());
        assertEquals("MH1001", c.at("/requester/identifier/value").asText());
        assertEquals("https://www.mciindia.org", c.at("/requester/identifier/system").asText());
        assertEquals("OPConsultation", c.at("/hiTypes/0").asText());
        assertEquals("VIEW", c.at("/permission/accessMode").asText());
        assertEquals("2026-01-01T00:00:00.000Z", c.at("/permission/dateRange/from").asText());
        assertEquals("2026-10-25T23:59:59.999Z", c.at("/permission/dateRange/to").asText());
        assertEquals("HOUR", c.at("/permission/frequency/unit").asText());
        assertEquals(1, c.at("/permission/frequency/value").asInt());
        assertEquals(0, c.at("/permission/frequency/repeats").asInt());
        assertEquals(false, c.at("/permission/dataEraseAt").isMissingNode());

        InOrder order = inOrder(store, gateway);
        order.verify(store).insertConsentRequest(eq((String) answer.get("uuid")), eq("ramesh.gond@sbx"), eq(HIU_ID), eq("CAREMGT"),
                eq("OPConsultation"), eq(Instant.parse("2026-01-01T00:00:00Z")), eq(Instant.parse("2026-10-25T23:59:59.999Z")),
                any(Instant.class), eq(requestId.getValue()), eq("REQUESTED"));
        order.verify(gateway).post(eq(AbdmPaths.OUT_CONSENT_INIT), eq(requestId.getValue()), any(), any());
        assertEquals("REQUESTED", answer.get("status"));
    }

    @Test
    public void aRefusedConsentRequestIsMarkedFailed() {
        when(gateway.post(eq(AbdmPaths.OUT_CONSENT_INIT), anyString(), any(), any())).thenThrow(new AbdmGatewayException(400, "ABDM-1100", "bad"));
        when(store.findConsentRequestByInitRequest(anyString())).thenReturn(Optional.of(request(3, null, "REQUESTED")));

        Map<String, Object> answer = service.requestConsent("ramesh.gond@sbx", null, null, List.of("Prescription"), "PUBHLTH", "Dr. A");

        assertEquals("FAILED", answer.get("status"));
        verify(store).failConsent(eq(3L), contains("bad"));
    }

    @Test
    public void onInitKeepsTheConsentRequestIdOfTheMatchingRequest() {
        when(store.findConsentRequestByInitRequest("req-init-1")).thenReturn(Optional.of(request(3, null, "REQUESTED")));

        service.onInit(fixture("s3-hiu-consent-on-init.json"), headers("cb-1"));

        verify(store).setConsentRequestId(3L, "cr-1", "REQUESTED");
    }

    @Test
    public void onInitWithAnErrorFailsTheRequest() {
        when(store.findConsentRequestByInitRequest("req-init-1")).thenReturn(Optional.of(request(3, null, "REQUESTED")));

        service.onInit(fixture("s3-hiu-consent-on-init-error.json"), headers("cb-1"));

        verify(store).failConsent(eq(3L), contains("ABDM-1100"));
        verify(store, never()).setConsentRequestId(any(Long.class), anyString(), anyString());
    }

    @Test
    public void onInitForAnUnknownRequestRetriesThenDrops() {
        when(store.findConsentRequestByInitRequest("req-init-1")).thenReturn(Optional.empty());

        service.onInit(fixture("s3-hiu-consent-on-init.json"), headers("cb-1"));

        verify(store, times(2)).findConsentRequestByInitRequest("req-init-1");
        verify(store, never()).setConsentRequestId(any(Long.class), anyString(), anyString());
    }

    @Test
    public void aGrantedNotifyIsAcknowledgedThenEachArtefactIsStoredAndFetched() {
        when(store.findConsentRequest("cr-1")).thenReturn(Optional.of(request(3, "cr-1", "REQUESTED")));

        service.notify(fixture("s3-hiu-consent-notify-granted.json"), headers("cb-notify-1"));

        verify(store).setConsentRequestStatus("cr-1", "GRANTED");
        ArgumentCaptor<JsonNode> ack = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HIU_CONSENT_ON_NOTIFY), anyString(), ack.capture(), any());
        assertEquals("cb-notify-1", ack.getValue().at("/response/requestId").asText());
        assertEquals(2, ack.getValue().get("acknowledgement").size());
        assertEquals("ok", ack.getValue().at("/acknowledgement/0/status").asText());
        assertEquals("artefact-1", ack.getValue().at("/acknowledgement/0/consentId").asText());
        assertEquals("artefact-2", ack.getValue().at("/acknowledgement/1/consentId").asText());

        ArgumentCaptor<String> fetchIds = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> fetches = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway, times(2)).post(eq(AbdmPaths.OUT_CONSENT_FETCH), fetchIds.capture(), fetches.capture(), any());
        assertEquals("artefact-1", fetches.getAllValues().get(0).get("consentId").asText());
        assertEquals("artefact-2", fetches.getAllValues().get(1).get("consentId").asText());

        InOrder order = inOrder(store, gateway);
        order.verify(gateway).post(eq(AbdmPaths.OUT_HIU_CONSENT_ON_NOTIFY), anyString(), any(), any());
        order.verify(store).upsertArtefact("cr-1", "artefact-1", "ramesh.gond@sbx", HIU_ID, "GRANTED", fetchIds.getAllValues().get(0));
        order.verify(gateway).post(eq(AbdmPaths.OUT_CONSENT_FETCH), eq(fetchIds.getAllValues().get(0)), any(), any());
        order.verify(store).upsertArtefact("cr-1", "artefact-2", "ramesh.gond@sbx", HIU_ID, "GRANTED", fetchIds.getAllValues().get(1));
        order.verify(gateway).post(eq(AbdmPaths.OUT_CONSENT_FETCH), eq(fetchIds.getAllValues().get(1)), any(), any());
    }

    @Test
    public void onFetchKeepsTheDetailThenRequestsTheRecords() {
        when(store.findArtefactByFetchRequest("req-fetch-1")).thenReturn(Optional.of(artefact(9, "artefact-1")));

        service.onFetch(fixture("s3-hiu-consent-on-fetch.json"), headers("cb-fetch-1"));

        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(store, dataService);
        order.verify(store).storeArtefactDetail(eq(9L), eq("GRANTED"), eq("TANUH"), eq("OPConsultation"),
                eq(Instant.parse("2026-01-01T00:00:00Z")), eq(Instant.parse("2026-10-25T23:59:59.999Z")),
                eq(Instant.parse("2026-11-25T00:00:00Z")), detail.capture());
        order.verify(dataService).requestHealthInformation("artefact-1");
        assertEquals("artefact-1", detail.getValue().get("consentId").asText());
    }

    @Test
    public void onFetchForAnUnknownRequestRequestsNothing() {
        when(store.findArtefactByFetchRequest("req-fetch-1")).thenReturn(Optional.empty());

        service.onFetch(fixture("s3-hiu-consent-on-fetch.json"), headers("cb-fetch-1"));

        verifyNoInteractions(dataService);
    }

    @Test
    public void theListShowsEachRequestWithItsArtefacts() {
        when(store.listConsentRequests()).thenReturn(List.of(request(3, "cr-1", "GRANTED")));
        when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("cr-1", "artefact-1", "GRANTED", "RECEIVED", 2)));

        Map<String, Object> r = service.list().get(0);

        assertEquals("uuid-3", r.get("uuid"));
        assertEquals("ramesh.gond@sbx", r.get("abhaAddress"));
        assertEquals("cr-1", r.get("consentRequestId"));
        assertEquals("GRANTED", r.get("status"));
        assertEquals("2026-10-25T10:00:00.000Z", r.get("createdAt"));
        assertEquals(List.of(Map.of("consentId", "artefact-1", "status", "GRANTED", "dataFlowStatus", "RECEIVED", "records", 2L)), r.get("artefacts"));
    }

    private static AbdmHiuStore.Consent request(long id, String consentRequestId, String status) {
        return new AbdmHiuStore.Consent(id, "uuid-" + id, consentRequestId, null, "req-init-1", null, "ramesh.gond@sbx", null, HIU_ID,
                "CAREMGT", "OPConsultation", null, null, null, status, null, null, Instant.parse("2026-10-25T10:00:00Z"), null);
    }

    private static AbdmHiuStore.Consent artefact(long id, String consentId) {
        return new AbdmHiuStore.Consent(id, "uuid-" + id, "cr-1", consentId, null, "req-fetch-1", "ramesh.gond@sbx", null, HIU_ID,
                null, null, null, null, null, "GRANTED", null, null, null, null);
    }
}
