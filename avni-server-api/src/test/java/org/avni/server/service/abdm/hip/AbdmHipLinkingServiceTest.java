package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Gender;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AbdmHipLinkingServiceTest {
    private static final DateTimeZone IST = DateTimeZone.forID("Asia/Kolkata");
    private static final long INDIVIDUAL_ID = 7L;
    private static final String DIGITS = "12345678568912";

    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private final IndividualRepository individualRepository = mock(IndividualRepository.class);
    private final AbhaLinkRepository abhaLinkRepository = mock(AbhaLinkRepository.class);
    private final EkaCredentialCipher cipher = mock(EkaCredentialCipher.class);
    private AbdmHipLinkingService service;

    @Before
    public void setUp() {
        service = new AbdmHipLinkingService(AbdmTestSupport.config(), store, gateway, AbdmTestSupport.orgContext(),
                individualRepository, abhaLinkRepository, cipher, AbdmTestSupport.noWaitRetry());
        Individual individual = individual();
        when(individualRepository.findByUuid("subject-1")).thenReturn(individual);
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber(DIGITS);
        link.setAbhaAddress("asha.devi");
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.of(link));
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING", null), row("enc-2", "PENDING", null)));
        when(store.findPatientLink(INDIVIDUAL_ID, HIP_ID)).thenReturn(Optional.empty());
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void linkingAPatientWithoutATokenAsksForOneWithTheirDetails() {
        List<Map<String, Object>> answer = service.linkCareContexts("subject-1");

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> extraHeaders = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_GENERATE_TOKEN), requestId.capture(), body.capture(), extraHeaders.capture());
        JsonNode b = body.getValue();
        assertEquals("12-3456-7856-8912", b.get("abhaNumber").asText());
        assertEquals("asha.devi@sbx", b.get("abhaAddress").asText());
        assertEquals("Asha Devi", b.get("name").asText());
        assertEquals("F", b.get("gender").asText());
        assertTrue(b.get("yearOfBirth").isInt());
        assertEquals(1990, b.get("yearOfBirth").asInt());
        assertEquals(HIP_ID, extraHeaders.getValue().get(AbdmPaths.H_HIP_ID));

        // Persist before send: the correlation row carries the REQUEST-ID before the gateway sees it.
        InOrder order = inOrder(store, gateway);
        order.verify(store).requestToken(eq(INDIVIDUAL_ID), eq(HIP_ID), eq(DIGITS), eq("asha.devi@sbx"), eq(requestId.getValue()));
        order.verify(store).markLinkRequested(eq(HIP_ID), eq(List.of("enc-1", "enc-2")), isNull());
        order.verify(gateway).post(eq(AbdmPaths.OUT_GENERATE_TOKEN), eq(requestId.getValue()), any(), any());
        verify(gateway, never()).post(eq(AbdmPaths.OUT_LINK_CARE_CONTEXT), anyString(), any(), any());

        // Newest visit first, with its stored state.
        assertEquals(2, answer.size());
        assertEquals("enc-2", answer.get(0).get("reference"));
        assertEquals("Display enc-2", answer.get(0).get("display"));
        assertEquals("OPConsultation", answer.get(0).get("hiType"));
        assertEquals("PENDING", answer.get(0).get("status"));
        assertEquals("2026-10-25T10:00:00.000Z", answer.get(0).get("updatedAt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void careContextsAreTheCompletedVisitsNewestFirstWithTypeAndDate() {
        service.linkCareContexts("subject-1");

        ArgumentCaptor<List<AbdmHipStore.NewCareContext>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).ensureCareContexts(eq(INDIVIDUAL_ID), eq(HIP_ID), eq("subject-1"), eq("OPConsultation"), captor.capture());
        List<AbdmHipStore.NewCareContext> visits = captor.getValue();
        assertEquals(2, visits.size());
        assertEquals("enc-2", visits.get(0).reference());
        assertEquals("OPD Visit, 2 May 2026", visits.get(0).display());
        assertEquals("enc-1", visits.get(1).reference());
        assertEquals("OPD Visit, 14 Mar 2026", visits.get(1).display());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aStoredLinkTokenIsUsedWithoutAskingForAnother() {
        when(store.findPatientLink(INDIVIDUAL_ID, HIP_ID)).thenReturn(Optional.of(new AbdmHipStore.PatientLink(3, INDIVIDUAL_ID, HIP_ID,
                DIGITS, "asha.devi@sbx", "LINKED", "req-old", "enc:tok-9", null)));

        service.linkCareContexts("subject-1");

        ArgumentCaptor<Map<String, String>> extraHeaders = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_LINK_CARE_CONTEXT), anyString(), any(), extraHeaders.capture());
        assertEquals("tok-9", extraHeaders.getValue().get(AbdmPaths.H_LINK_TOKEN));
        verify(gateway, never()).post(eq(AbdmPaths.OUT_GENERATE_TOKEN), anyString(), any(), any());
    }

    @Test
    public void aGatewayRefusalOfTheTokenRequestMarksTheVisitsFailed() {
        when(gateway.post(eq(AbdmPaths.OUT_GENERATE_TOKEN), anyString(), any(), any())).thenThrow(new AbdmGatewayException(400, "ABDM-1000", "bad"));
        when(store.findPatientLink(INDIVIDUAL_ID, HIP_ID)).thenReturn(Optional.empty(),
                Optional.of(new AbdmHipStore.PatientLink(3, INDIVIDUAL_ID, HIP_ID, DIGITS, null, "TOKEN_REQUESTED", "r", null, null)));

        service.linkCareContexts("subject-1");

        verify(store).failPatientLink(eq(3L), contains("bad"));
        verify(store).markStatus(eq(HIP_ID), eq(List.of("enc-1", "enc-2")), eq("FAILED"), contains("bad"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onGenerateTokenKeepsTheTokenThenLinksTheUnlinkedVisitsWithIt() {
        when(store.findPatientLinkByTokenRequest("req-token-1")).thenReturn(Optional.of(new AbdmHipStore.PatientLink(3, INDIVIDUAL_ID,
                HIP_ID, DIGITS, "asha.devi@sbx", "TOKEN_REQUESTED", "req-token-1", null, null)));
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-0", "LINKED", "old"),
                row("enc-1", "LINK_REQUESTED", null), row("enc-2", "LINK_REQUESTED", null)));
        when(store.findSubjectById(INDIVIDUAL_ID)).thenReturn(Optional.of(new AbdmHipStore.Subject(INDIVIDUAL_ID, "subject-1", "Asha Devi")));

        service.onGenerateToken(fixture("s3-on-generate-token.json"), headers("cb-1"));

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> extraHeaders = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq(AbdmPaths.OUT_LINK_CARE_CONTEXT), requestId.capture(), body.capture(), extraHeaders.capture());
        assertEquals("link-token-1", extraHeaders.getValue().get(AbdmPaths.H_LINK_TOKEN));
        assertEquals(HIP_ID, extraHeaders.getValue().get(AbdmPaths.H_HIP_ID));
        JsonNode b = body.getValue();
        assertEquals("12-3456-7856-8912", b.get("abhaNumber").asText());
        assertEquals("asha.devi@sbx", b.get("abhaAddress").asText());
        JsonNode patient = b.get("patient").get(0);
        assertEquals("subject-1", patient.get("referenceNumber").asText());
        assertEquals("Asha Devi", patient.get("display").asText());
        assertEquals("OPConsultation", patient.get("hiType").asText());
        assertEquals(2, patient.get("count").asInt());
        assertEquals(2, patient.get("careContexts").size());
        assertEquals("enc-1", patient.get("careContexts").get(0).get("referenceNumber").asText());
        assertEquals("Display enc-1", patient.get("careContexts").get(0).get("display").asText());

        InOrder order = inOrder(store, gateway);
        order.verify(store).storeLinkToken(3L, "enc:link-token-1");
        order.verify(store).markLinkRequested(HIP_ID, List.of("enc-1", "enc-2"), requestId.getValue());
        order.verify(gateway).post(eq(AbdmPaths.OUT_LINK_CARE_CONTEXT), eq(requestId.getValue()), any(), any());
    }

    @Test
    public void onGenerateTokenWithAnErrorFailsTheLinkAndSendsNothing() {
        when(store.findPatientLinkByTokenRequest("req-token-1")).thenReturn(Optional.of(new AbdmHipStore.PatientLink(3, INDIVIDUAL_ID,
                HIP_ID, DIGITS, "asha.devi@sbx", "TOKEN_REQUESTED", "req-token-1", null, null)));

        service.onGenerateToken(fixture("s3-on-generate-token-error.json"), headers("cb-1"));

        verify(store).failPatientLink(eq(3L), contains("ABDM-1204"));
        verify(store).markStatus(eq(HIP_ID), eq(List.of("enc-1", "enc-2")), eq("FAILED"), contains("ABDM-1204"));
        verify(store, never()).storeLinkToken(any(Long.class), anyString());
        verifyNoInteractions(gateway);
    }

    @Test
    public void onGenerateTokenForAnUnknownRequestRetriesThenDrops() {
        when(store.findPatientLinkByTokenRequest("req-token-1")).thenReturn(Optional.empty());

        service.onGenerateToken(fixture("s3-on-generate-token.json"), headers("cb-1"));

        verify(store, times(2)).findPatientLinkByTokenRequest("req-token-1");
        verifyNoInteractions(gateway);
    }

    @Test
    public void onCareContextMarksTheRequestsVisitsLinked() {
        when(store.findCareContextsByLinkRequest("req-link-1")).thenReturn(List.of(row("enc-1", "LINK_REQUESTED", "req-link-1")));

        service.onLinkCareContext(fixture("s3-on-carecontext.json"), headers("cb-2"));

        verify(store).markByLinkRequest("req-link-1", "LINKED", null);
        verify(store).setPatientLinkStatus(INDIVIDUAL_ID, HIP_ID, "LINKED");
    }

    @Test
    public void onCareContextWithAnErrorMarksFailedAndDropsTheToken() {
        when(store.findCareContextsByLinkRequest("req-link-1")).thenReturn(List.of(row("enc-1", "LINK_REQUESTED", "req-link-1")));

        service.onLinkCareContext(fixture("s3-on-carecontext-error.json"), headers("cb-2"));

        verify(store).markByLinkRequest(eq("req-link-1"), eq("FAILED"), contains("ABDM-1091"));
        verify(store).clearLinkToken(INDIVIDUAL_ID, HIP_ID);
    }

    @Test
    public void patientsShowAMaskedNumberAndTheAddressWithItsSuffix() {
        when(store.patientsWithAbha(HIP_ID)).thenReturn(List.of(new AbdmHipStore.PatientRow("subject-1", "Asha Devi", DIGITS, "asha.devi", 3, 1)));

        Map<String, Object> p = service.patientsWithAbha().get(0);

        assertEquals("subject-1", p.get("subjectUUID"));
        assertEquals("xx-xxxx-xxxx-8912", p.get("abhaNumberMasked"));
        assertEquals("asha.devi@sbx", p.get("abhaAddress"));
        assertEquals(3L, p.get("encounters"));
        assertEquals(1L, p.get("linked"));
    }

    private static AbdmHipStore.CareContext row(String reference, String status, String linkRequestId) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, INDIVIDUAL_ID, HIP_ID, "subject-1", reference, "Display " + reference,
                "OPConsultation", status, "HIP", linkRequestId, null, Instant.parse("2026-10-25T10:00:00Z"));
    }

    private static Individual individual() {
        Individual individual = new Individual();
        individual.setId(INDIVIDUAL_ID);
        individual.setUuid("subject-1");
        individual.setFirstName("Asha");
        individual.setLastName("Devi");
        Gender gender = new Gender();
        gender.setName("Female");
        individual.setGender(gender);
        individual.setDateOfBirth(new LocalDate(1990, 5, 1));
        EncounterType opd = new EncounterType();
        opd.setName("OPD Visit");
        Set<Encounter> encounters = new HashSet<>();
        encounters.add(encounter("enc-1", opd, new DateTime(2026, 3, 14, 10, 0, IST), null, false));
        encounters.add(encounter("enc-2", opd, new DateTime(2026, 5, 2, 23, 30, IST), null, false));
        encounters.add(encounter("enc-scheduled", opd, null, null, false));
        encounters.add(encounter("enc-cancelled", opd, new DateTime(2026, 4, 1, 10, 0, IST), new DateTime(2026, 4, 1, 11, 0, IST), false));
        encounters.add(encounter("enc-voided", opd, new DateTime(2026, 4, 2, 10, 0, IST), null, true));
        individual.setEncounters(encounters);
        return individual;
    }

    private static Encounter encounter(String uuid, EncounterType type, DateTime at, DateTime cancelledAt, boolean voided) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setEncounterType(type);
        e.setEncounterDateTime(at, null);
        e.setCancelDateTime(cancelledAt);
        e.setVoided(voided);
        return e;
    }
}
