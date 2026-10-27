package org.avni.server.service.abdm.hiu;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIU_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbdmHiuConsentServiceTest {
    private final AbdmHiuStore store = mock(AbdmHiuStore.class);
    private AbdmHiuConsentService service;

    @Before
    public void setUp() {
        service = new AbdmHiuConsentService(store);
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

    // HIU_FLOW_104: NHA's list shows the patient, the ABHA number, the record types, the dates, the expiry, and when each
    // status came.
    @Test
    public void theListShowsThePatientWhatWasGrantedAndEachStatusWithItsTime() {
        AbdmHiuStore.Consent granted = new AbdmHiuStore.Consent(3, "uuid-3", "cr-1", null, "req-init-1", "ramesh.gond@sbx", null, HIU_ID,
                "CAREMGT", "OPConsultation,Prescription", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-10-25T23:59:59Z"),
                Instant.parse("2026-11-25T00:00:00Z"), "GRANTED", null, Instant.parse("2026-10-25T10:00:00Z"), null);
        when(store.listConsentRequests()).thenReturn(List.of(granted));
        when(store.findArtefacts(List.of("cr-1"))).thenReturn(List.of());
        when(store.patientsByAbhaAddress(List.of("ramesh.gond@sbx"))).thenReturn(Map.of("ramesh.gond@sbx",
                new AbdmHiuStore.Patient("Ramesh Gond", "91123456789012")));
        when(store.statusHistory(List.of("cr-1"))).thenReturn(List.of(
                new AbdmHiuStore.StatusChange("cr-1", null, "REQUESTED", Instant.parse("2026-10-25T10:00:00Z")),
                new AbdmHiuStore.StatusChange("cr-1", null, "GRANTED", Instant.parse("2026-10-25T10:05:00Z"))));

        Map<String, Object> r = service.list().get(0);

        assertEquals("Ramesh Gond", r.get("patientName"));
        assertEquals("xx-xxxx-xxxx-9012", r.get("abhaNumber"));
        assertEquals("CAREMGT", r.get("purposeCode"));
        assertEquals(List.of("OPConsultation", "Prescription"), r.get("hiTypes"));
        assertEquals("2026-01-01T00:00:00.000Z", r.get("from"));
        assertEquals("2026-10-25T23:59:59.000Z", r.get("to"));
        assertEquals("2026-11-25T00:00:00.000Z", r.get("expiry"));
        assertEquals(List.of(Map.of("status", "REQUESTED", "at", "2026-10-25T10:00:00.000Z"),
                Map.of("status", "GRANTED", "at", "2026-10-25T10:05:00.000Z")), r.get("statusHistory"));
    }

    private static AbdmHiuStore.Consent request(long id, String consentRequestId, String status) {
        return new AbdmHiuStore.Consent(id, "uuid-" + id, consentRequestId, null, "req-init-1", "ramesh.gond@sbx", null, HIU_ID,
                "CAREMGT", "OPConsultation", null, null, null, status, null, Instant.parse("2026-10-25T10:00:00Z"), null);
    }

}
