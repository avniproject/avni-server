package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmAsync;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.fhir.CareContextDocumentAssembler;
import org.avni.server.service.abdm.fhir.FhirBundleBuilder;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AbdmHipDataTransferServiceTest {
    private static final String PUSH_URL = "https://hiu.example/api/v3/hiu/health-information/transfer";

    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private final AbdmAsync async = AbdmTestSupport.inlineAsync();
    private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
    private final AbhaLinkRepository abhaLinkRepository = mock(AbhaLinkRepository.class);
    private final CareContextDocumentAssembler assembler = mock(CareContextDocumentAssembler.class);
    private final FhirBundleBuilder builder = mock(FhirBundleBuilder.class);
    private final FideliusCrypto crypto = mock(FideliusCrypto.class);
    private AbdmHipDataTransferService service;

    @Before
    public void setUp() {
        service = new AbdmHipDataTransferService(AbdmTestSupport.config(), store, gateway, async, AbdmTestSupport.orgContext(),
                encounterRepository, abhaLinkRepository, assembler, builder, crypto);
        Individual patient = new Individual();
        patient.setId(7L);
        patient.setUuid("subject-1");
        when(encounterRepository.findByUuid("enc-1")).thenReturn(encounter("enc-1", patient, new DateTime(2026, 3, 14, 10, 0)));
        when(encounterRepository.findByUuid("enc-2")).thenReturn(encounter("enc-2", patient, new DateTime(2026, 5, 2, 10, 0)));
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber("12345678568912");
        link.setAbhaAddress("ramesh.gond");
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.of(link));
        when(builder.build(any())).thenAnswer(inv -> {
            com.fasterxml.jackson.databind.node.ObjectNode bundle = AbdmJson.obj();
            bundle.put("resourceType", "Bundle");
            return bundle;
        });
        when(crypto.generateKeyMaterial()).thenReturn(new FideliusKeyMaterial("our-private", "our-public-x509", "our-public-raw", "our-nonce"));
        when(crypto.encrypt(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("cipher");
        when(store.insertDataFlow(anyString(), anyString(), anyString(), anyString(), eq("ACKNOWLEDGED"), any())).thenReturn(42L);
    }

    @Test
    public void anUnknownConsentIsRefusedOnRequestAndNothingIsSent() {
        when(store.findConsent("consent-hip-1")).thenReturn(Optional.empty());

        service.healthInformationRequest(fixture("s3-hip-hi-request.json"), headers("cb-hi-1"));

        JsonNode reply = onRequestReply();
        assertEquals("cb-hi-1", reply.at("/response/requestId").asText());
        assertEquals("tx-1", reply.at("/hiRequest/transactionId").asText());
        assertEquals("FAILED", reply.at("/hiRequest/sessionStatus").asText());
        assertEquals("CONSENT_NOT_FOUND", reply.at("/error/code").asText());
        verifyNoInteractions(async, crypto);
        verify(gateway, never()).pushToUrl(anyString(), any());
    }

    @Test
    public void aRevokedConsentIsRefusedOnRequest() {
        when(store.findConsent("consent-hip-1")).thenReturn(Optional.of(consent("REVOKED")));

        service.healthInformationRequest(fixture("s3-hip-hi-request.json"), headers("cb-hi-1"));

        JsonNode reply = onRequestReply();
        assertEquals("CONSENT_NOT_GRANTED", reply.at("/error/code").asText());
        verify(store).insertDataFlow(eq("consent-hip-1"), eq("tx-1"), eq("cb-hi-1"), eq(PUSH_URL), eq("FAILED"), anyString());
        verifyNoInteractions(async, crypto);
        verify(gateway, never()).pushToUrl(anyString(), any());
    }

    @Test
    public void aGrantedConsentSendsOneEncryptedEntryPerVisitThenNotifiesTransferred() {
        when(store.findConsent("consent-hip-1")).thenReturn(Optional.of(consent("GRANTED")));

        service.healthInformationRequest(fixture("s3-hip-hi-request.json"), headers("cb-hi-1"));

        JsonNode reply = onRequestReply();
        assertEquals("cb-hi-1", reply.at("/response/requestId").asText());
        assertEquals("ACKNOWLEDGED", reply.at("/hiRequest/sessionStatus").asText());

        // Encrypted with our fresh key and nonce, to the requester's key and nonce.
        verify(crypto, org.mockito.Mockito.times(2)).encrypt(eq("{\"resourceType\":\"Bundle\"}"), eq("our-private"), eq("our-nonce"),
                eq("requester-public-key"), eq("requester-nonce"));
        verify(assembler, org.mockito.Mockito.times(2)).assemble(any(Encounter.class), any(Individual.class), eq("12-3456-7856-8912"), eq("ramesh.gond@sbx"), eq(HIP_ID), eq("TANUH Health Centre"));

        ArgumentCaptor<JsonNode> push = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).pushToUrl(eq(PUSH_URL), push.capture());
        JsonNode p = push.getValue();
        assertEquals("tx-1", p.get("transactionId").asText());
        assertEquals(1, p.get("pageNumber").asInt());
        assertEquals(1, p.get("pageCount").asInt());
        assertEquals(2, p.get("entries").size());
        JsonNode entry = p.get("entries").get(0);
        assertEquals("cipher", entry.get("content").asText());
        assertEquals("application/fhir+json", entry.get("media").asText());
        assertEquals(AbdmFlows.md5Hex("{\"resourceType\":\"Bundle\"}"), entry.get("checksum").asText());
        assertEquals("enc-1", entry.get("careContextReference").asText());
        assertEquals("our-public-x509", p.at("/keyMaterial/dhPublicKey/keyValue").asText());
        assertEquals("our-nonce", p.at("/keyMaterial/nonce").asText());
        assertEquals("Curve25519", p.at("/keyMaterial/curve").asText());

        ArgumentCaptor<JsonNode> notify = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(gateway, store);
        order.verify(gateway).pushToUrl(eq(PUSH_URL), any());
        order.verify(store).finishDataFlow(42L, "TRANSFERRED", 2, null);
        order.verify(gateway).post(eq(AbdmPaths.OUT_HI_NOTIFY), anyString(), notify.capture(), any());
        JsonNode n = notify.getValue().get("notification");
        assertEquals("consent-hip-1", n.get("consentId").asText());
        assertEquals("tx-1", n.get("transactionId").asText());
        assertEquals("HIP", n.at("/notifier/type").asText());
        assertEquals(HIP_ID, n.at("/notifier/id").asText());
        assertEquals("TRANSFERRED", n.at("/statusNotification/sessionStatus").asText());
        assertEquals(2, n.at("/statusNotification/statusResponses").size());
        assertEquals("DELIVERED", n.at("/statusNotification/statusResponses/0/hiStatus").asText());
        assertEquals("DELIVERED", n.at("/statusNotification/statusResponses/1/hiStatus").asText());
    }

    @Test
    public void aVisitOutsideTheRequestedRangeIsNotSent() {
        when(store.findConsent("consent-hip-1")).thenReturn(Optional.of(consent("GRANTED")));
        when(encounterRepository.findByUuid("enc-2")).thenReturn(encounter("enc-2", individual(), new DateTime(2025, 5, 2, 10, 0)));

        service.healthInformationRequest(fixture("s3-hip-hi-request.json"), headers("cb-hi-1"));

        ArgumentCaptor<JsonNode> push = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).pushToUrl(eq(PUSH_URL), push.capture());
        assertEquals(1, push.getValue().get("entries").size());
        ArgumentCaptor<JsonNode> notify = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HI_NOTIFY), anyString(), notify.capture(), any());
        assertEquals("ERRORED", notify.getValue().at("/notification/statusNotification/statusResponses/1/hiStatus").asText());
    }

    @Test
    public void aFailedPushNotifiesFailed() {
        when(store.findConsent("consent-hip-1")).thenReturn(Optional.of(consent("GRANTED")));
        when(gateway.pushToUrl(anyString(), any())).thenThrow(new RuntimeException("connection refused"));

        service.healthInformationRequest(fixture("s3-hip-hi-request.json"), headers("cb-hi-1"));

        ArgumentCaptor<JsonNode> notify = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HI_NOTIFY), anyString(), notify.capture(), any());
        assertEquals("FAILED", notify.getValue().at("/notification/statusNotification/sessionStatus").asText());
        assertEquals("ERRORED", notify.getValue().at("/notification/statusNotification/statusResponses/0/hiStatus").asText());
        verify(store).finishDataFlow(eq(42L), eq("FAILED"), eq(0), anyString());
    }

    private JsonNode onRequestReply() {
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(AbdmPaths.OUT_HIP_HI_ON_REQUEST), anyString(), body.capture(), any());
        return body.getValue();
    }

    private static AbdmHipStore.Consent consent(String status) {
        JsonNode detail = fixture("s3-hip-consent-notify-granted.json").at("/notification/consentDetail");
        return new AbdmHipStore.Consent(1, "consent-hip-1", "ramesh.gond@sbx", HIP_ID, "TANUH-HIU", "CAREMGT", "OPConsultation",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-10-25T23:59:59.999Z"), null, status, detail, null);
    }

    private static Individual individual() {
        Individual patient = new Individual();
        patient.setId(7L);
        patient.setUuid("subject-1");
        return patient;
    }

    private static Encounter encounter(String uuid, Individual individual, DateTime at) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setIndividual(individual);
        e.setEncounterDateTime(at, null);
        return e;
    }
}
