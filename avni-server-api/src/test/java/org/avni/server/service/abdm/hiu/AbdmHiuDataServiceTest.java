package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIU_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.PUSH_URL;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmHiuDataServiceTest {
    private static final String BUNDLE = "{\"resourceType\":\"Bundle\",\"type\":\"document\"}";

    private final AbdmHiuStore store = mock(AbdmHiuStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private final FideliusCrypto crypto = mock(FideliusCrypto.class);
    private final EkaCredentialCipher cipher = mock(EkaCredentialCipher.class);
    private AbdmHiuDataService service;

    @Before
    public void setUp() {
        service = new AbdmHiuDataService(AbdmTestSupport.config(), store, gateway, crypto, cipher, AbdmTestSupport.noWaitRetry());
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));
        when(store.findArtefact("artefact-1")).thenReturn(Optional.of(artefact()));
        when(store.findDataFlowByTransaction("tx-1")).thenReturn(Optional.of(new AbdmHiuStore.DataFlow(11, "flow-uuid", "artefact-1", "tx-1",
                "req-hi-1", "enc:our-private", "our-public", "our-nonce", null, "ACKNOWLEDGED", null, null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestingRecordsStoresAFreshKeyPairThenSendsItsPublicHalf() {
        when(crypto.generateKeyMaterial()).thenReturn(new FideliusKeyMaterial("our-private", "our-public-x509", "our-public-raw", "our-nonce"));
        when(store.insertDataFlow(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(11L);

        service.requestHealthInformation("artefact-1");

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> extra = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HI_REQUEST), requestId.capture(), body.capture(), extra.capture());
        assertEquals(HIU_ID, extra.getValue().get(AbdmPaths.H_HIU_ID));
        JsonNode hi = body.getValue().get("hiRequest");
        assertEquals("artefact-1", hi.at("/consent/id").asText());
        assertEquals("2026-01-01T00:00:00.000Z", hi.at("/dateRange/from").asText());
        assertEquals("2026-10-25T23:59:59.999Z", hi.at("/dateRange/to").asText());
        assertEquals(PUSH_URL, hi.get("dataPushUrl").asText());
        assertEquals("ECDH", hi.at("/keyMaterial/cryptoAlg").asText());
        assertEquals("Curve25519", hi.at("/keyMaterial/curve").asText());
        assertEquals("Curve25519/32byte random key", hi.at("/keyMaterial/dhPublicKey/parameters").asText());
        assertEquals("our-public-x509", hi.at("/keyMaterial/dhPublicKey/keyValue").asText());
        assertEquals("our-nonce", hi.at("/keyMaterial/nonce").asText());

        InOrder order = inOrder(store, gateway);
        order.verify(store).insertDataFlow(eq("artefact-1"), eq(requestId.getValue()), eq("enc:our-private"), eq("our-public-x509"),
                eq("our-nonce"), any(Instant.class), eq("REQUESTED"));
        order.verify(gateway).post(eq(AbdmPaths.OUT_HI_REQUEST), eq(requestId.getValue()), any(), any());
    }

    @Test
    public void onRequestSetsTheTransactionOnTheMatchingFlow() {
        when(store.findDataFlowByRequest("req-hi-1")).thenReturn(Optional.of(new AbdmHiuStore.DataFlow(11, "flow-uuid", "artefact-1", null,
                "req-hi-1", "enc:our-private", "our-public", "our-nonce", null, "REQUESTED", null, null)));

        service.onRequest(fixture("s3-hiu-hi-on-request.json"), headers("cb-1"));

        verify(store).setTransaction(11L, "tx-1", "ACKNOWLEDGED", null);
    }

    @Test
    public void aPushIsDecryptedWithOurKeyAndTheirsThenStoredAndNotifiedOk() {
        String checksum = AbdmFlows.md5Hex(BUNDLE);
        JsonNode push = fixture("s3-hiu-data-push.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) push.get("entries").get(0)).put("checksum", checksum);
        when(crypto.decrypt("cipher-1", "our-private", "our-nonce", "hip-public-key", "hip-nonce")).thenReturn(BUNDLE);

        service.dataPush(push, headers("cb-push-1"));

        InOrder order = inOrder(store, gateway);
        order.verify(store).insertHealthInformation(11L, "tx-1", "artefact-1", "TANUH", "enc-1", true,
                AbdmJson.write(AbdmJson.read(BUNDLE)));
        order.verify(store).finishDataFlow(11L, "RECEIVED", 1, null);
        ArgumentCaptor<JsonNode> notify = ArgumentCaptor.forClass(JsonNode.class);
        order.verify(gateway).post(eq(AbdmPaths.OUT_HI_NOTIFY), anyString(), notify.capture(), any());
        JsonNode n = notify.getValue().get("notification");
        assertEquals("artefact-1", n.get("consentId").asText());
        assertEquals("tx-1", n.get("transactionId").asText());
        assertEquals("HIU", n.at("/notifier/type").asText());
        assertEquals(HIU_ID, n.at("/notifier/id").asText());
        assertEquals("TRANSFERRED", n.at("/statusNotification/sessionStatus").asText());
        assertEquals("TANUH", n.at("/statusNotification/hipId").asText());
        assertEquals("enc-1", n.at("/statusNotification/statusResponses/0/careContextReference").asText());
        assertEquals("OK", n.at("/statusNotification/statusResponses/0/hiStatus").asText());
    }

    @Test
    public void aChecksumMismatchIsKeptButRecorded() {
        when(crypto.decrypt(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(BUNDLE);

        service.dataPush(fixture("s3-hiu-data-push.json"), headers("cb-push-1"));

        verify(store).insertHealthInformation(eq(11L), eq("tx-1"), eq("artefact-1"), eq("TANUH"), eq("enc-1"), eq(false), anyString());
    }

    @Test
    public void anEntryThatWillNotDecryptIsErroredAndNotStored() {
        when(crypto.decrypt(anyString(), anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("tag mismatch"));

        service.dataPush(fixture("s3-hiu-data-push.json"), headers("cb-push-1"));

        verify(store, never()).insertHealthInformation(anyLong(), any(), any(), any(), any(), any(), any());
        verify(store).finishDataFlow(eq(11L), eq("FAILED"), eq(0), anyString());
        ArgumentCaptor<JsonNode> notify = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HI_NOTIFY), anyString(), notify.capture(), any());
        assertEquals("ERRORED", notify.getValue().at("/notification/statusNotification/statusResponses/0/hiStatus").asText());
    }

    @Test
    public void aPushForAnUnknownTransactionIsDropped() {
        when(store.findDataFlowByTransaction("tx-1")).thenReturn(Optional.empty());

        service.dataPush(fixture("s3-hiu-data-push.json"), headers("cb-push-1"));

        verify(gateway, never()).post(anyString(), anyString(), any(), any());
        verify(crypto, never()).decrypt(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void recordsAreThoseReceivedUnderTheRequestsArtefacts() {
        when(store.findConsentRequestByUuid("uuid-3")).thenReturn(Optional.of(new AbdmHiuStore.Consent(3, "uuid-3", "cr-1", null, null,
                null, "ramesh.gond@sbx", null, HIU_ID, null, null, null, null, null, "GRANTED", null, null, null, null)));
        when(store.records("cr-1")).thenReturn(List.of(new AbdmHiuStore.Record("enc-1", "TANUH", "tx-1",
                Instant.parse("2026-10-25T10:00:00Z"), true, AbdmJson.read(BUNDLE), "artefact-1")));
        when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("cr-1", "artefact-1", "GRANTED", "RECEIVED", 1)));

        Map<String, Object> r = service.records("uuid-3").get(0);

        assertEquals("enc-1", r.get("careContextReference"));
        assertEquals("TANUH", r.get("hipId"));
        assertEquals("tx-1", r.get("transactionId"));
        assertEquals("2026-10-25T10:00:00.000Z", r.get("receivedAt"));
        assertEquals(true, r.get("checksumMatched"));
        assertEquals("Bundle", ((JsonNode) r.get("bundle")).get("resourceType").asText());
    }

    // HIU_FLOW_202 and 301: records stayed on screen after the patient withdrew the consent, or it expired.
    @Test
    public void noRecordIsShownOnceTheConsentIsWithdrawnOrHasExpired() {
        for (String ended : List.of("REVOKED", "EXPIRED", "DENIED")) {
            when(store.findConsentRequestByUuid("uuid-3")).thenReturn(Optional.of(requestWith(ended, null)));
            when(store.records("cr-1")).thenReturn(List.of(record("artefact-1")));
            when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("cr-1", "artefact-1", "GRANTED", "RECEIVED", 1)));

            assertTrue(ended, service.records("uuid-3").isEmpty());
        }
    }

    @Test
    public void noRecordIsShownPastTheConsentsEraseDate() {
        when(store.findConsentRequestByUuid("uuid-3")).thenReturn(Optional.of(requestWith("GRANTED", Instant.now().minusSeconds(60))));
        when(store.records("cr-1")).thenReturn(List.of(record("artefact-1")));
        when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("cr-1", "artefact-1", "GRANTED", "RECEIVED", 1)));

        assertTrue(service.records("uuid-3").isEmpty());
    }

    @Test
    public void onlyRecordsUnderAnArtefactStillGrantedAreShown() {
        when(store.findConsentRequestByUuid("uuid-3")).thenReturn(Optional.of(requestWith("GRANTED", Instant.now().plusSeconds(3600))));
        when(store.records("cr-1")).thenReturn(List.of(record("artefact-1"), record("artefact-2")));
        when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("cr-1", "artefact-1", "GRANTED", "RECEIVED", 1),
                new AbdmHiuStore.Artefact("cr-1", "artefact-2", "REVOKED", "RECEIVED", 1)));

        List<Map<String, Object>> shown = service.records("uuid-3");

        assertEquals(1, shown.size());
    }

    @Test
    public void endedConsentsAreMarkedExpiredAndTheirRecordsErased() {
        when(store.expireOverdueConsents()).thenReturn(2);
        when(store.eraseRecordsOfEndedConsents()).thenReturn(5);

        service.expireAndErase();

        InOrder order = inOrder(store);
        order.verify(store).expireOverdueConsents();
        order.verify(store).eraseRecordsOfEndedConsents();
    }

    private static AbdmHiuStore.Consent requestWith(String status, Instant dataEraseAt) {
        return new AbdmHiuStore.Consent(3, "uuid-3", "cr-1", null, null, null, "ramesh.gond@sbx", null, HIU_ID, null, null, null, null,
                dataEraseAt, status, null, null, null, null);
    }

    private static AbdmHiuStore.Record record(String consentId) {
        return new AbdmHiuStore.Record("enc-" + consentId, "TANUH", "tx-1", Instant.parse("2026-10-25T10:00:00Z"), true, AbdmJson.read(BUNDLE), consentId);
    }

    private static AbdmHiuStore.Consent artefact() {
        JsonNode detail = fixture("s3-hiu-consent-on-fetch.json").at("/consent/consentDetail");
        return new AbdmHiuStore.Consent(9, "uuid-9", "cr-1", "artefact-1", null, "req-fetch-1", "ramesh.gond@sbx", "TANUH", HIU_ID,
                "CAREMGT", "OPConsultation", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-10-25T23:59:59.999Z"), null,
                "GRANTED", detail, null, null, null);
    }
}
