package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIU_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: Avni as HIU through Eka Care. Real Fidelius crypto, everything else mocked.
public class EkaHiuServiceTest {
    private static final String BUNDLE = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
    private static final String KEYSET = EkaHiuService.KEYSET_CONSENT_ID;

    private final AbdmHiuStore store = mock(AbdmHiuStore.class);
    private final EkaAbdmClient eka = mock(EkaAbdmClient.class);
    private final FideliusCrypto crypto = new FideliusCrypto();
    private final EkaCredentialCipher cipher = mock(EkaCredentialCipher.class);
    private final AbdmHipStore hipStore = mock(AbdmHipStore.class);
    private final AbhaEventRepository abhaEvents = mock(AbhaEventRepository.class);
    private AbdmConfig cfg;
    private EkaHiuService service;
    private FideliusKeyMaterial ours;

    @Before
    public void setUp() {
        cfg = AbdmTestSupport.config();
        when(cfg.getEkaClinicId()).thenReturn("clinic-1");
        when(cfg.getEkaDoctorOid()).thenReturn("doctor-oid-1");
        when(cfg.getEkaRequesterName()).thenReturn("Dr. Demo");
        service = service("x509");
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));

        ours = crypto.generateKeyMaterial();
        when(store.findDataFlowsByConsent(KEYSET)).thenReturn(List.of(keyset(21, ours)));
        when(store.findConsentRequestByInitRequest(anyString())).thenReturn(Optional.of(request(5, null, "REQUESTED")));
        when(hipStore.findSubjectsByAbhaAddress("shyam@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Shyam")));
        when(abhaEvents.findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(Optional.of(abhaEvent("created", "oid-1")));
        when(eka.postSlow(eq(EkaHiuService.CONSENT_CREATE), any(), any()))
                .thenReturn(new EkaResponse("r-1", 200, AbdmJson.read("{\"consent_init_id\":\"init-1\"}")));
    }

    // ---- consent create

    @Test
    @SuppressWarnings("unchecked")
    public void aConsentRequestIsStoredThenSentToEkaWithItsBodyAndHeadersAndEkasIdIsKept() {
        Map<String, Object> answer = service.requestConsent("shyam", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 20),
                List.of("OPConsultation", "Prescription"), "CAREMGT", "Dr. A Sharma");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq("/abdm/v1/consents/create"), body.capture(), headers.capture());
        JsonNode b = body.getValue();
        assertEquals("oid-1", b.at("/patient/oid").asText());
        assertEquals("shyam@sbx", b.at("/patient/health_id").asText());
        assertEquals("Care management", b.get("purpose").asText());
        assertTrue(b.get("record_types").isArray());
        assertEquals("OPConsultation", b.at("/record_types/0").asText());
        assertEquals("Prescription", b.at("/record_types/1").asText());
        assertEquals("2026-01-01T00:00:00Z", b.at("/period/from").asText());
        assertEquals("2026-10-20T23:59:59Z", b.at("/period/to").asText());
        assertFalse(b.at("/period/expiry").isMissingNode());
        assertEquals("clinic-1", b.at("/hiu/clinic_id").asText());
        assertEquals("doctor-oid-1", b.at("/hiu/d_oid").asText());
        assertEquals("Dr. A Sharma", b.at("/hiu/requester/name").asText());
        assertEquals("MH1001", b.at("/hiu/requester/identifier/value").asText());
        assertEquals("oid-1", headers.getValue().get(EkaAbdmClient.H_PATIENT_OID));
        assertEquals("subject-1", headers.getValue().get(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        assertEquals(HIP_ID, headers.getValue().get(EkaAbdmClient.H_HIP_ID));

        InOrder order = inOrder(store, eka);
        order.verify(store).insertConsentRequest(eq((String) answer.get("uuid")), eq("shyam@sbx"), eq(HIU_ID), eq("CAREMGT"),
                eq("OPConsultation,Prescription"), eq(Instant.parse("2026-01-01T00:00:00Z")), eq(Instant.parse("2026-10-20T23:59:59Z")),
                any(Instant.class), anyString(), eq("REQUESTED"));
        order.verify(eka).patch(eq("/abdm/v1/hiu/keyset"), any(), any());
        order.verify(eka).postSlow(eq("/abdm/v1/consents/create"), any(), any());
        order.verify(store).setConsentRequestId(5L, "init-1", "REQUESTED");
        assertEquals("REQUESTED", answer.get("status"));
        assertNotNull(answer.get("uuid"));
    }

    @Test
    public void theStoredHiuKeyIsRegisteredOncePerRunAsX509WithItsNonce() {
        service.requestConsent("shyam", null, null, null, null, null);
        service.requestConsent("shyam", null, null, null, null, null);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka, times(1)).patch(eq("/abdm/v1/hiu/keyset"), body.capture(), eq(Map.of()));
        assertEquals(ours.publicKeyX509Base64(), body.getValue().get("public_key").asText());
        assertEquals(ours.nonceBase64(), body.getValue().get("nonce").asText());
        verify(store, never()).insertDataFlow(eq(KEYSET), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void withNoStoredKeyAFreshOneIsStoredEncryptedThenRegistered_inTheRawFormWhenAskedFor() {
        service = service("raw");
        when(store.findDataFlowsByConsent(KEYSET)).thenReturn(List.of());

        service.requestConsent("shyam", null, null, null, null, null);

        ArgumentCaptor<String> privateKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> publicKey = ArgumentCaptor.forClass(String.class);
        verify(store).insertDataFlow(eq(KEYSET), isNull(), privateKey.capture(), publicKey.capture(), anyString(), any(Instant.class), eq("KEYSET"));
        assertTrue(privateKey.getValue().startsWith("enc:"));
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).patch(eq("/abdm/v1/hiu/keyset"), body.capture(), any());
        byte[] raw = Base64.getDecoder().decode(body.getValue().get("public_key").asText());
        assertEquals(65, raw.length);
        assertEquals(0x04, raw[0]);
        assertFalse(publicKey.getValue().equals(body.getValue().get("public_key").asText()));
    }

    @Test
    public void ekaRefusingTheConsentFailsTheRowWithItsMessage() {
        when(eka.postSlow(eq(EkaHiuService.CONSENT_CREATE), any(), any())).thenThrow(new EkaCallException(400, "ABDM-1040", "Invalid HIU ID"));

        Map<String, Object> answer = service.requestConsent("shyam", null, null, null, null, null);

        verify(store).failConsent(eq(5L), contains("Invalid HIU ID"));
        verify(store, never()).setConsentRequestId(anyLong(), any(), any());
        assertEquals("FAILED", answer.get("status"));
    }

    @Test
    public void ekaRefusingOurKeyFailsTheRowAndNoConsentIsSent() {
        when(eka.patch(eq("/abdm/v1/hiu/keyset"), any(), any())).thenThrow(new EkaCallException(400, null, "Invalid public key"));

        Map<String, Object> answer = service.requestConsent("shyam", null, null, null, null, null);

        verify(store).failConsent(eq(5L), contains("Invalid public key"));
        verify(eka, never()).post(eq(EkaHiuService.CONSENT_CREATE), any(), any());
        assertEquals("FAILED", answer.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aPatientWithNoEkaOidIsStillAskedByAbhaAddressAlone() {
        when(hipStore.findSubjectsByAbhaAddress(any(), any())).thenReturn(List.of());
        when(store.findEkaPatientByAbhaAddress(any())).thenReturn(Optional.empty());

        service.requestConsent("shyam", null, null, null, null, null);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq(EkaHiuService.CONSENT_CREATE), body.capture(), headers.capture());
        assertTrue(body.getValue().at("/patient/oid").isMissingNode());
        assertEquals("shyam@sbx", body.getValue().at("/patient/health_id").asText());
        assertFalse(headers.getValue().containsKey(EkaAbdmClient.H_PATIENT_OID));
        assertEquals(HIP_ID, headers.getValue().get(EkaAbdmClient.H_HIP_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void anAbhaEventNamingTheAddressGivesTheOidWhenNoSubjectHoldsIt() {
        when(hipStore.findSubjectsByAbhaAddress(any(), any())).thenReturn(List.of());
        when(store.findEkaPatientByAbhaAddress(any())).thenReturn(Optional.of(new AbdmHiuStore.EkaPatientRow("oid-2", "subject-2")));

        service.requestConsent("shyam", null, null, null, null, null);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq(EkaHiuService.CONSENT_CREATE), body.capture(), headers.capture());
        assertEquals("oid-2", body.getValue().at("/patient/oid").asText());
        assertEquals("subject-2", headers.getValue().get(EkaAbdmClient.H_PARTNER_PATIENT_ID));
    }

    // ---- abha.consent_update

    @Test
    public void aGrantedUpdateSetsTheRequestsStatusAndKeepsEachArtefact() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));

        service.consentUpdate(ekaFixture("hiu-consent-update-granted.json"));

        verify(store).setConsentRequestStatus("init-1", "GRANTED");
        verify(store).upsertArtefact("init-1", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        // Only the consent list, for the terms granted (HIU_FLOW_106).
        verify(eka).post(eq(EkaHiuService.CONSENT_LIST), any(), any());
        verifyNoMoreInteractions(eka);
    }

    @Test
    public void artefactsAsAnArrayAndALowerCaseStatusAreAccepted() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        ObjectNode event = (ObjectNode) ekaFixture("hiu-consent-update-granted.json");
        ObjectNode notification = (ObjectNode) event.at("/data/notification");
        notification.put("status", "granted");
        ArrayNode artefacts = notification.putArray("consentArtefacts");
        artefacts.addObject().put("id", "artefact-1");
        artefacts.addObject().put("id", "artefact-2");

        service.consentUpdate(event);

        verify(store).setConsentRequestStatus("init-1", "GRANTED");
        verify(store).upsertArtefact("init-1", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        verify(store).upsertArtefact("init-1", "artefact-2", "shyam@sbx", HIU_ID, "GRANTED");
    }

    @Test
    public void aDeniedUpdateSetsTheStatusAndKeepsNoArtefact() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        ObjectNode event = (ObjectNode) ekaFixture("hiu-consent-update-granted.json");
        ((ObjectNode) event.at("/data/notification")).put("status", "DENIED");

        service.consentUpdate(event);

        verify(store).setConsentRequestStatus("init-1", "DENIED");
        verify(store, never()).upsertArtefact(any(), any(), any(), any(), any());
    }

    // A withdrawal or expiry that names only the artefacts was dropped, so the records stayed viewable. Each artefact
    // leads to the request it was granted under.
    @Test
    public void anUpdateNamingOnlyArtefactsIsAppliedToTheRequestTheyWereGrantedUnder() {
        when(store.findArtefact("artefact-1")).thenReturn(Optional.of(artefactOf("abdm-A", "artefact-1")));
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.of(request(5, "abdm-A", "GRANTED")));
        ObjectNode event = (ObjectNode) ekaFixture("hiu-consent-update-granted.json");
        ObjectNode notification = (ObjectNode) event.at("/data/notification");
        notification.remove("consentRequestId");
        notification.put("status", "REVOKED");

        service.consentUpdate(event);

        verify(store).setConsentRequestStatus("abdm-A", "REVOKED");
        verify(store).setArtefactStatus(List.of("artefact-1"), "REVOKED");
    }

    @Test
    public void anUpdateNamingOnlyArtefactsWeDoNotHoldIsIgnored() {
        when(store.findArtefact("artefact-1")).thenReturn(Optional.empty());
        ObjectNode event = (ObjectNode) ekaFixture("hiu-consent-update-granted.json");
        ObjectNode notification = (ObjectNode) event.at("/data/notification");
        notification.remove("consentRequestId");
        notification.put("status", "REVOKED");

        service.consentUpdate(event);

        verify(store, never()).setConsentRequestStatus(any(), any());
        verify(store, never()).setArtefactStatus(any(), any());
        verify(store, never()).parkConsentUpdate(any(), any(), any());
    }

    // AVNI-24: with two requests waiting, an update for the older one used to be filed under the newest.
    @Test
    public void anUpdateForAnIdWeDoNotKnowIsMatchedToItsOwnRequestThroughEkasConsentList() {
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of(request(6, "init-2", "REQUESTED"), request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}", "{\"consent_id\":\"abdm-B\",\"consent_init_id\":\"init-2\"}");

        service.consentUpdate(grantedUpdate("abdm-A"));

        verify(store).mapConsentRequestId(5L, "abdm-A");
        verify(store, never()).mapConsentRequestId(eq(6L), any());
        // 26 Oct: a grant that landed while this match ran was overwritten with the REQUESTED read before it.
        verify(store, never()).setConsentRequestId(anyLong(), any(), any());
        verify(store).setConsentRequestStatus("abdm-A", "GRANTED");
        verify(store).upsertArtefact("abdm-A", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
    }

    @Test
    public void anUpdateThatNoRequestOwnsIsParkedAndNothingIsGuessed() {
        when(store.findConsentRequest("abdm-Z")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of(request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}");

        service.consentUpdate(grantedUpdate("abdm-Z"));

        verify(store, never()).setConsentRequestId(anyLong(), any(), any());
        verify(store, never()).setConsentRequestStatus(any(), any());
        verify(store, never()).upsertArtefact(any(), any(), any(), any(), any());
        verify(store).parkConsentUpdate("abdm-Z", "GRANTED", List.of("artefact-1"));
    }

    @Test
    public void aNewRequestIsMappedToAbdmsIdFromEkasListAndAnUpdateParkedForThatIdIsApplied() {
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}");
        when(store.parkedConsentUpdates("abdm-A")).thenReturn(List.of(new AbdmHiuStore.ParkedConsentUpdate(77, "abdm-A", "GRANTED", List.of("artefact-1"))));

        service.requestConsent("shyam", null, null, null, null, null);

        verify(store).setConsentRequestId(5L, "abdm-A", "REQUESTED");
        verify(store).setConsentRequestStatus("abdm-A", "GRANTED");
        verify(store).upsertArtefact("abdm-A", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        verify(store).resolveParked(77L);
    }

    // A parked update was applied only when another update came; every path that maps a request applies it now.
    @Test
    public void approvalThatMapsTheRequestToAbdmsIdAppliesTheUpdateParkedForThatId() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}");
        when(store.parkedConsentUpdates("abdm-A")).thenReturn(List.of(new AbdmHiuStore.ParkedConsentUpdate(77, "abdm-A", "GRANTED", List.of("artefact-1"))));

        service.approveInSandbox("init-1");

        verify(store).mapConsentRequestId(5L, "abdm-A");
        verify(store).setConsentRequestStatus("abdm-A", "GRANTED");
        verify(store).upsertArtefact("abdm-A", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        verify(store).resolveParked(77L);
    }

    @Test
    public void aPushThatMatchesItsRequestThroughEkaAppliesTheUpdateParkedForIt() {
        when(store.findArtefact("abdm-A")).thenReturn(Optional.empty());
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of(request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}");
        when(store.parkedConsentUpdates("abdm-A")).thenReturn(List.of(new AbdmHiuStore.ParkedConsentUpdate(77, "abdm-A", "GRANTED", List.of("artefact-1"))));
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), ours.publicKeyX509Base64(), ours.nonceBase64());
        ObjectNode event = push(content, AbdmFlows.md5Hex(BUNDLE), hip.publicKeyX509Base64(), hip.nonceBase64());
        ((ObjectNode) event.get("data")).put("consent_id", "abdm-A");

        service.hiuDataPush(event);

        verify(store).mapConsentRequestId(5L, "abdm-A");
        verify(store).upsertArtefact("abdm-A", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        verify(store).resolveParked(77L);
    }

    // The hourly retry: an update parked because Eka's list did not have the id yet is applied once it does.
    @Test
    public void theRetryAppliesAParkedUpdateOnceEkasListMatchesItsRequest() {
        when(store.parkedConsentRequestIds()).thenReturn(List.of("abdm-A"));
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of(request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}");
        when(store.parkedConsentUpdates("abdm-A")).thenReturn(List.of(new AbdmHiuStore.ParkedConsentUpdate(77, "abdm-A", "GRANTED", List.of("artefact-1"))));

        service.retryParkedUpdates();

        verify(store).mapConsentRequestId(5L, "abdm-A");
        verify(store).upsertArtefact("abdm-A", "artefact-1", "shyam@sbx", HIU_ID, "GRANTED");
        verify(store).resolveParked(77L);
    }

    @Test
    public void theRetryAppliesAParkedUpdateWhoseRequestWasMappedSinceWithoutAskingEka() {
        when(store.parkedConsentRequestIds()).thenReturn(List.of("abdm-A"));
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.of(request(5, "abdm-A", "REQUESTED")));
        when(store.parkedConsentUpdates("abdm-A")).thenReturn(List.of(new AbdmHiuStore.ParkedConsentUpdate(77, "abdm-A", "DENIED", List.of())));

        service.retryParkedUpdates();

        verify(store).setConsentRequestStatus("abdm-A", "DENIED");
        verify(store).resolveParked(77L);
        verify(eka, never()).post(eq(EkaHiuService.CONSENT_LIST), any(), any());
    }

    // Several parked ids cost one consent-list call per patient, not one per id.
    @Test
    public void theRetryAsksEkaOncePerPatientForAllParkedIds() {
        when(store.parkedConsentRequestIds()).thenReturn(List.of("abdm-A", "abdm-B"));
        when(store.listConsentRequests()).thenReturn(List.of(request(6, "init-2", "REQUESTED"), request(5, "init-1", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\"}", "{\"consent_id\":\"abdm-B\",\"consent_init_id\":\"init-2\"}");

        service.retryParkedUpdates();

        verify(store).mapConsentRequestId(5L, "abdm-A");
        verify(store).mapConsentRequestId(6L, "abdm-B");
        verify(eka, times(1)).post(eq(EkaHiuService.CONSENT_LIST), any(), any());
    }

    // Only a request still waiting for its answer can be the one an unknown ABDM id belongs to; an answered one is
    // already filed under ABDM's id, so asking Eka about its patient is wasted.
    @Test
    public void matchingThroughEkaAsksOnlyAboutPatientsWithARequestStillWaiting() {
        when(store.findConsentRequest("abdm-Z")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of(request(5, "abdm-A", "GRANTED")));

        service.consentUpdate(grantedUpdate("abdm-Z"));

        verify(eka, never()).post(eq(EkaHiuService.CONSENT_LIST), any(), any());
        verify(store).parkConsentUpdate("abdm-Z", "GRANTED", List.of("artefact-1"));
    }

    // HIU_FLOW_102: the consent's expiry is asked for, not fixed at 30 days.
    @Test
    public void theExpiryAskedForIsSentAndKept() {
        LocalDate expiry = LocalDate.now().plusDays(10);
        service.requestConsent("shyam", null, null, null, null, null, expiry);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(EkaHiuService.CONSENT_CREATE), body.capture(), any());
        Instant end = expiry.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusSeconds(1);
        assertEquals(end.toString(), body.getValue().at("/period/expiry").asText());
        verify(store).insertConsentRequest(anyString(), eq("shyam@sbx"), eq(HIU_ID), eq("CAREMGT"), eq("OPConsultation"), any(), any(),
                eq(end), anyString(), eq("REQUESTED"));
    }

    @Test
    public void anExpiryThatHasPassedOrAPurposeEkaCannotCarryIsRefused() {
        assertThrows(AbdmRequestException.class, () -> service.requestConsent("shyam", null, null, null, null, null, LocalDate.now().minusDays(1)));
        assertThrows(AbdmRequestException.class, () -> service.requestConsent("shyam", null, null, null, "BTG", null, null));
        verify(store, never()).insertConsentRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // HIU_FLOW_106: the patient may narrow the dates, types or expiry when granting; those are what the HIU keeps to.
    @Test
    public void aGrantedUpdateKeepsTheTermsThePatientGrantedFromEkasList() {
        when(store.findConsentRequest("abdm-A")).thenReturn(Optional.of(request(5, "abdm-A", "REQUESTED")));
        ekaListsConsents("{\"consent_id\":\"abdm-A\",\"consent_init_id\":\"init-1\",\"status\":\"GRANTED\",\"hi_types\":[\"OPConsultation\"]," +
                "\"period\":{\"from\":\"2026-03-01T00:00:00Z\",\"to\":\"2026-10-01T00:00:00Z\",\"expiry\":\"2026-11-01T00:00:00Z\"}}");

        service.consentUpdate(grantedUpdate("abdm-A"));

        verify(store).setGrantedTerms("abdm-A", "OPConsultation", Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
    }

    // Two patients holding one ABHA address: neither is picked (the first one used to be).
    @Test
    public void twoPatientsHoldingTheAddressAreNotGuessedBetween() {
        when(hipStore.findSubjectsByAbhaAddress("shyam@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Shyam"),
                new AbdmHipStore.Subject(8, "subject-2", "Shyam K")));
        when(abhaEvents.findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-2")).thenReturn(Optional.of(abhaEvent("created", "oid-2")));

        service.requestConsent("shyam", null, null, null, null, null);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq(EkaHiuService.CONSENT_CREATE), body.capture(), any());
        assertTrue(body.getValue().at("/patient/oid").isMissingNode());
    }

    // AVNI-23: a name and a pasted command were sent to Eka as ABHA addresses.
    @Test
    public void somethingThatIsNotAnAbhaAddressIsRefusedBeforeAnythingIsStoredOrSent() {
        for (String notAnAddress : List.of("Shyam Kumar", "rm -rf /", "shyam@abdm", ".shyam@sbx", "a@sbx")) {
            assertThrows(notAnAddress, AbdmRequestException.class,
                    () -> service.requestConsent(notAnAddress, null, null, null, null, null));
        }
        verify(store, never()).insertConsentRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(eka);
    }

    // ---- abha.hiu_data_push

    @Test
    @SuppressWarnings("unchecked")
    public void anInlineEntryIsDecryptedWithOurKeyAndTheHipsThenStoredAndAcknowledged() {
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), ours.publicKeyX509Base64(), ours.nonceBase64());
        knownArtefactAndNewFlow();

        service.hiuDataPush(push(content, AbdmFlows.md5Hex(BUNDLE), hip.publicKeyX509Base64(), hip.nonceBase64()));

        InOrder order = inOrder(store, eka);
        order.verify(store).setTransaction(31L, "dtx-1", "RECEIVING", null);
        order.verify(store).insertHealthInformation(31L, "dtx-1", "artefact-1", "HIP-X", "cc-1", true, AbdmJson.write(AbdmJson.read(BUNDLE)));
        order.verify(store).finishDataFlow(31L, "RECEIVED", 1, null);
        ArgumentCaptor<JsonNode> ack = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        order.verify(eka).post(eq("/abdm/v1/hiu/care-context/data/on-push"), ack.capture(), headers.capture());
        assertEquals("artefact-1", ack.getValue().get("consent_id").asText());
        assertEquals("dtx-1", ack.getValue().get("transaction_id").asText());
        assertEquals("cc-1", ack.getValue().at("/status_response/0/care_context_id").asText());
        assertTrue(ack.getValue().at("/status_response/0/success").asBoolean());
        assertEquals("oid-1", headers.getValue().get(EkaAbdmClient.H_PATIENT_OID));
        assertEquals("subject-1", headers.getValue().get(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        assertEquals(HIP_ID, headers.getValue().get(EkaAbdmClient.H_HIP_ID));
        verify(eka, never()).get(any(), any());
    }

    @Test
    public void anEmptyEntryIsFetchedByTheDataFlowsTransactionIdNotTheWebhooks_withARawSenderKey() {
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), ours.publicKeyX509Base64(), ours.nonceBase64());
        knownArtefactAndNewFlow();
        ObjectNode entry = AbdmJson.obj();
        entry.put("care_context_id", "cc-1");
        entry.put("checksum", AbdmFlows.md5Hex(BUNDLE));
        entry.put("content", content);
        entry.put("media", "application/fhir+json");
        when(eka.get(eq("/abdm/v1/hiu/care-context/data/dtx-1/entries/ref-1"), any())).thenReturn(new EkaResponse("g-1", 200, entry));

        service.hiuDataPush(push("", null, hip.publicKeyRawBase64(), hip.nonceBase64()));

        verify(eka).get(eq("/abdm/v1/hiu/care-context/data/dtx-1/entries/ref-1"), any());
        verify(store).insertHealthInformation(31L, "dtx-1", "artefact-1", "HIP-X", "cc-1", true, AbdmJson.write(AbdmJson.read(BUNDLE)));
        verify(store).finishDataFlow(31L, "RECEIVED", 1, null);
    }

    @Test
    public void anEntryThatWillNotDecryptIsNotStoredAndIsReportedAsFailed() {
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        FideliusKeyMaterial someoneElse = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), someoneElse.publicKeyX509Base64(), someoneElse.nonceBase64());
        knownArtefactAndNewFlow();

        service.hiuDataPush(push(content, AbdmFlows.md5Hex(BUNDLE), hip.publicKeyX509Base64(), hip.nonceBase64()));

        verify(store, never()).insertHealthInformation(anyLong(), any(), any(), any(), any(), any(), any());
        verify(store).finishDataFlow(eq(31L), eq("FAILED"), eq(0), anyString());
        ArgumentCaptor<JsonNode> ack = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq("/abdm/v1/hiu/care-context/data/on-push"), ack.capture(), any());
        assertFalse(ack.getValue().at("/status_response/0/success").asBoolean());
        assertEquals("cc-1", ack.getValue().at("/status_response/0/care_context_id").asText());
    }

    // AVNI-16: Eka's push names the consent request; the receipt must carry the granted artefact's id (else ABDM-1080).
    @Test
    public void aPushNamingTheConsentRequestIsFiledUnderItsGrantedArtefactAndAcknowledgedWithTheArtefactsId() {
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), ours.publicKeyX509Base64(), ours.nonceBase64());
        when(store.findArtefact("abdm-cr-1")).thenReturn(Optional.empty());
        when(store.findConsentRequest("abdm-cr-1")).thenReturn(Optional.of(request(5, "abdm-cr-1", "GRANTED")));
        when(store.findArtefacts(List.of("abdm-cr-1"))).thenReturn(List.of(new AbdmHiuStore.Artefact("abdm-cr-1", "artefact-1", "GRANTED", null, 0)));
        when(store.findDataFlowByTransaction("dtx-1")).thenReturn(Optional.empty());
        when(store.insertDataFlow(eq("artefact-1"), isNull(), anyString(), anyString(), anyString(), any(), eq("RECEIVING"))).thenReturn(31L);
        ObjectNode event = push(content, AbdmFlows.md5Hex(BUNDLE), hip.publicKeyX509Base64(), hip.nonceBase64());
        ((ObjectNode) event.get("data")).put("consent_id", "abdm-cr-1");

        service.hiuDataPush(event);

        verify(store).insertHealthInformation(31L, "dtx-1", "artefact-1", "HIP-X", "cc-1", true, AbdmJson.write(AbdmJson.read(BUNDLE)));
        verify(store, never()).upsertArtefact(any(), any(), any(), any(), any());
        ArgumentCaptor<JsonNode> ack = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).post(eq("/abdm/v1/hiu/care-context/data/on-push"), ack.capture(), any());
        assertEquals("artefact-1", ack.getValue().get("consent_id").asText());
    }

    @Test
    public void aPushNoGrantedConsentOfOursCoversIsNeitherKeptNorAcknowledgedWithAWrongId() {
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String content = crypto.encrypt(BUNDLE, hip.privateKeyBase64(), hip.nonceBase64(), ours.publicKeyX509Base64(), ours.nonceBase64());
        when(store.findArtefact("abdm-cr-9")).thenReturn(Optional.empty());
        when(store.findConsentRequest("abdm-cr-9")).thenReturn(Optional.empty());
        when(store.listConsentRequests()).thenReturn(List.of());
        ObjectNode event = push(content, AbdmFlows.md5Hex(BUNDLE), hip.publicKeyX509Base64(), hip.nonceBase64());
        ((ObjectNode) event.get("data")).put("consent_id", "abdm-cr-9");

        service.hiuDataPush(event);

        verify(store, never()).insertHealthInformation(anyLong(), any(), any(), any(), any(), any(), any());
        verify(store, never()).upsertArtefact(any(), any(), any(), any(), any());
        verify(eka, never()).post(eq("/abdm/v1/hiu/care-context/data/on-push"), any(), any());
    }

    // ---- sandbox approval

    @Test
    @SuppressWarnings("unchecked")
    public void approvingInTheSandboxPassesThePatientsOidAndOurLinkedCareContexts() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        when(hipStore.findCareContexts(7, HIP_ID)).thenReturn(List.of(careContext("enc-1", "Visit 12 Oct", "LINKED"),
                careContext("enc-2", "Visit 13 Oct", "FAILED")));
        when(eka.postSlow(eq("/abdm/v1/consents/approve?oid=oid-1"), any(), any())).thenReturn(new EkaResponse("a-1", 204, null));

        Map<String, Object> answer = service.approveInSandbox("init-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(eka).postSlow(eq("/abdm/v1/consents/approve?oid=oid-1"), body.capture(), headers.capture());
        JsonNode b = body.getValue();
        assertEquals("init-1", b.get("id").asText());
        assertEquals("view", b.get("access_mode").asText());
        assertEquals("OPConsultation", b.at("/hi_types/0").asText());
        assertEquals("2026-01-01T00:00:00.000Z", b.at("/duration/from").asText());
        assertEquals(HIP_ID, b.at("/consent_artefacts/0/hip_id").asText());
        assertEquals("enc-1", b.at("/consent_artefacts/0/care_contexts/0/id").asText());
        assertEquals(1, b.at("/consent_artefacts/0/care_contexts").size());
        assertEquals("oid-1", headers.getValue().get(EkaAbdmClient.H_PATIENT_OID));
        assertEquals(true, answer.get("ok"));
        assertEquals(204, answer.get("httpStatus"));
    }

    @Test
    public void approvalUsesTheAbdmConsentIdThatEkasListGivesForOurRequest() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        when(eka.post(eq("/abdm/v1/consents/list"), any(), any())).thenReturn(new EkaResponse("l-1", 200,
                AbdmJson.read("{\"consents\":[{\"consent_id\":\"abdm-cr-1\",\"consent_init_id\":\"init-1\",\"status\":\"REQUESTED\"}]}")));

        service.approveInSandbox("init-1");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(eka).postSlow(eq("/abdm/v1/consents/approve?oid=oid-1"), body.capture(), any());
        assertEquals("abdm-cr-1", body.getValue().get("id").asText());
        verify(store).mapConsentRequestId(5L, "abdm-cr-1");
        verify(store, never()).setConsentRequestId(anyLong(), any(), any());
    }

    @Test
    public void ekasRefusalOfAnApprovalIsReturnedAsItCame() {
        when(store.findConsentRequest("init-1")).thenReturn(Optional.of(request(5, "init-1", "REQUESTED")));
        when(eka.postSlow(eq("/abdm/v1/consents/approve?oid=oid-1"), any(), any())).thenThrow(new EkaCallException(491, null, "SessionExpiredTryAgain"));

        Map<String, Object> answer = service.approveInSandbox("init-1");

        assertEquals(false, answer.get("ok"));
        assertEquals(491, answer.get("httpStatus"));
        assertEquals("SessionExpiredTryAgain", answer.get("error"));
        assertNotNull(answer.get("hint"));
    }

    // ---- helpers

    private EkaHiuService service(String keyForm) {
        return new EkaHiuService(cfg, store, eka, crypto, cipher, AbdmTestSupport.orgContext(), hipStore, abhaEvents,
                AbdmTestSupport.noWaitRetry(), keyForm, "MH1001");
    }

    private void ekaListsConsents(String... consents) {
        when(eka.post(eq(EkaHiuService.CONSENT_LIST), any(), any())).thenReturn(new EkaResponse("l-1", 200,
                AbdmJson.read("{\"consents\":[" + String.join(",", consents) + "]}")));
    }

    private static ObjectNode grantedUpdate(String consentRequestId) {
        ObjectNode event = (ObjectNode) ekaFixture("hiu-consent-update-granted.json");
        ((ObjectNode) event.at("/data/notification")).put("consentRequestId", consentRequestId);
        return event;
    }

    private void knownArtefactAndNewFlow() {
        when(store.findArtefact("artefact-1")).thenReturn(Optional.of(new AbdmHiuStore.Consent(9, "uuid-9", "init-1", "artefact-1", null,
                "shyam@sbx", null, HIU_ID, null, null, null, null, null, "GRANTED", null, null, null)));
        when(store.findDataFlowByTransaction("dtx-1")).thenReturn(Optional.empty());
        when(store.insertDataFlow(eq("artefact-1"), isNull(), anyString(), anyString(), anyString(), any(), eq("RECEIVING"))).thenReturn(31L);
    }

    private static ObjectNode push(String content, String checksum, String senderKey, String senderNonce) {
        ObjectNode event = (ObjectNode) ekaFixture("hiu-data-push.json");
        ObjectNode entry = (ObjectNode) event.at("/data/entries/0");
        entry.put("content", content);
        if (checksum == null) entry.remove("checksum");
        else entry.put("checksum", checksum);
        ((ObjectNode) event.at("/data/key_information/dh_public_key")).put("key_value", senderKey);
        ((ObjectNode) event.at("/data/key_information")).put("nonce", senderNonce);
        return event;
    }

    private static AbdmHiuStore.DataFlow keyset(long id, FideliusKeyMaterial keys) {
        return new AbdmHiuStore.DataFlow(id, "keyset-uuid", KEYSET, null, null, "enc:" + keys.privateKeyBase64(),
                keys.publicKeyX509Base64(), keys.nonceBase64(), null, "KEYSET", null, null);
    }

    private static AbdmHiuStore.Consent request(long id, String consentRequestId, String status) {
        return new AbdmHiuStore.Consent(id, "uuid-" + id, consentRequestId, null, "init-request-" + id, "shyam@sbx", null, HIU_ID,
                "CAREMGT", "OPConsultation", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-10-20T23:59:59Z"),
                Instant.parse("2026-11-24T00:00:00Z"), status, null, null, null);
    }

    private static AbdmHiuStore.Consent artefactOf(String consentRequestId, String consentId) {
        return new AbdmHiuStore.Consent(9, "uuid-9", consentRequestId, consentId, null, "shyam@sbx", "HIP-X", HIU_ID,
                "CAREMGT", "OPConsultation", null, null, null, "GRANTED", null, null, null);
    }

    private static AbdmHipStore.CareContext careContext(String reference, String display, String status) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, 7, HIP_ID, "subject-1", reference, display, "OPConsultation", status,
                null, null, null, null);
    }

    private static AbhaEvent abhaEvent(String action, String oid) {
        AbhaEvent event = new AbhaEvent();
        event.setAction(action);
        event.setEkaOid(oid);
        return event;
    }

    private static JsonNode ekaFixture(String name) {
        try (InputStream in = EkaHiuServiceTest.class.getResourceAsStream("/abdm/eka/" + name)) {
            if (in == null) throw new IllegalArgumentException("No fixture " + name);
            return AbdmJson.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
