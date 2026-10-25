package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmHipConsentServiceTest {
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private AbdmHipConsentService service;

    @Before
    public void setUp() {
        service = new AbdmHipConsentService(AbdmTestSupport.config(), store, gateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aGrantedConsentIsKeptAndAcknowledgedEchoingTheCallbacksRequestId() {
        service.consentNotify(fixture("s3-hip-consent-notify-granted.json"), headers("cb-notify-1"));

        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        verify(store).upsertConsent(eq("consent-hip-1"), eq("ramesh.gond@sbx"), eq(HIP_ID), eq("TANUH-HIU"), eq("CAREMGT"),
                eq("OPConsultation"), eq(Instant.parse("2026-01-01T00:00:00Z")), eq(Instant.parse("2026-10-25T23:59:59.999Z")),
                eq(Instant.parse("2026-11-25T00:00:00Z")), eq("GRANTED"), detail.capture());
        assertEquals(2, detail.getValue().get("careContexts").size());

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> extra = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HIP_CONSENT_ON_NOTIFY), requestId.capture(), body.capture(), extra.capture());
        JsonNode reply = body.getValue();
        assertEquals("cb-notify-1", reply.at("/response/requestId").asText());
        assertEquals("ok", reply.at("/acknowledgement/status").asText());
        assertEquals("consent-hip-1", reply.at("/acknowledgement/consentId").asText());
        assertEquals(HIP_ID, extra.getValue().get(AbdmPaths.H_HIP_ID));
        // Our own call gets a fresh REQUEST-ID; the callback's goes in the body.
        assertNotEquals("cb-notify-1", requestId.getValue());
    }

    @Test
    public void aRevocationUpdatesTheStatusAndIsAcknowledged() {
        when(store.setConsentStatus("consent-hip-1", "REVOKED")).thenReturn(1);

        service.consentNotify(fixture("s3-hip-consent-notify-revoked.json"), headers("cb-notify-2"));

        verify(store).setConsentStatus("consent-hip-1", "REVOKED");
        verify(store, never()).upsertConsent(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HIP_CONSENT_ON_NOTIFY), anyString(), body.capture(), any());
        assertEquals("cb-notify-2", body.getValue().at("/response/requestId").asText());
    }

    @Test
    public void aRevocationOfAConsentNeverSeenKeepsARowForIt() {
        when(store.setConsentStatus("consent-hip-1", "REVOKED")).thenReturn(0);

        service.consentNotify(fixture("s3-hip-consent-notify-revoked.json"), headers("cb-notify-2"));

        verify(store).upsertConsent(eq("consent-hip-1"), isNull(), eq(HIP_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq("REVOKED"), isNull());
    }

    @Test
    public void theListShowsEachConsentWithItsVisitsAndDataFlows() {
        JsonNode detail = fixture("s3-hip-consent-notify-granted.json").at("/notification/consentDetail");
        when(store.listConsents()).thenReturn(List.of(new AbdmHipStore.Consent(1, "consent-hip-1", "ramesh.gond@sbx", HIP_ID, "TANUH-HIU",
                "CAREMGT", "OPConsultation", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-10-25T23:59:59.999Z"), null,
                "GRANTED", detail, Instant.parse("2026-10-25T10:00:00Z"))));
        when(store.findDataFlows(List.of("consent-hip-1"))).thenReturn(List.of(new AbdmHipStore.DataFlow(5, "f", "consent-hip-1", "tx-1",
                "r", "TRANSFERRED", 2, null, null)));
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-1", "enc-2"))).thenReturn(List.of(new AbdmHipStore.CareContext(1,
                "u", 7, HIP_ID, "subject-1", "enc-1", "OPD Visit, 14 Mar 2026", "OPConsultation", "LINKED", "HIP", null, null, null)));

        Map<String, Object> c = service.list().get(0);

        assertEquals("consent-hip-1", c.get("consentId"));
        assertEquals("ramesh.gond@sbx", c.get("abhaAddress"));
        assertEquals("TANUH-HIU", c.get("hiuId"));
        assertEquals("GRANTED", c.get("status"));
        assertEquals(List.of("OPConsultation"), c.get("hiTypes"));
        assertEquals("2026-01-01T00:00:00.000Z", c.get("dateFrom"));
        assertEquals(List.of("OPD Visit, 14 Mar 2026", "enc-2"), c.get("careContexts"));
        List<?> flows = (List<?>) c.get("dataFlows");
        assertEquals(Map.of("transactionId", "tx-1", "status", "TRANSFERRED", "entries", 2), flows.get(0));
        assertEquals("2026-10-25T10:00:00.000Z", c.get("updatedAt"));
    }
}
