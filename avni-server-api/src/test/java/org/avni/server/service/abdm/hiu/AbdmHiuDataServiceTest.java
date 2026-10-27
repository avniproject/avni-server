package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmJson;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIU_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmHiuDataServiceTest {
    private static final String BUNDLE = "{\"resourceType\":\"Bundle\",\"type\":\"document\"}";

    private final AbdmHiuStore store = mock(AbdmHiuStore.class);
    private AbdmHiuDataService service;

    @Before
    public void setUp() {
        service = new AbdmHiuDataService(store);
    }

    @Test
    public void recordsAreThoseReceivedUnderTheRequestsArtefacts() {
        when(store.findConsentRequestByUuid("uuid-3")).thenReturn(Optional.of(new AbdmHiuStore.Consent(3, "uuid-3", "cr-1", null, null,
                "ramesh.gond@sbx", null, HIU_ID, null, null, null, null, null, "GRANTED", null, null, null)));
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
        return new AbdmHiuStore.Consent(3, "uuid-3", "cr-1", null, null, "ramesh.gond@sbx", null, HIU_ID, null, null, null, null,
                dataEraseAt, status, null, null, null);
    }

    private static AbdmHiuStore.Record record(String consentId) {
        return new AbdmHiuStore.Record("enc-" + consentId, "TANUH", "tx-1", Instant.parse("2026-10-25T10:00:00Z"), true, AbdmJson.read(BUNDLE), consentId);
    }

}
