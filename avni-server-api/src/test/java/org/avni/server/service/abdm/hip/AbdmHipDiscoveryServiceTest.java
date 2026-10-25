package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.fixture;
import static org.avni.server.service.abdm.hip.AbdmTestSupport.headers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmHipDiscoveryServiceTest {
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmGatewayClient gateway = mock(AbdmGatewayClient.class);
    private final AbdmHipLinkingService linkingService = mock(AbdmHipLinkingService.class);
    private AbdmHipDiscoveryService service;

    @Before
    public void setUp() {
        service = new AbdmHipDiscoveryService(AbdmTestSupport.config(), store, gateway, linkingService);
        when(linkingService.loadPatient("subject-1")).thenReturn(new AbdmHipLinkingService.Patient(7, "subject-1", "Ramesh Gond", "M", 1990,
                "12345678568912", "ramesh.gond", List.of(new AbdmHipLinkingService.Visit("enc-1", "OPD Visit, 14 Mar 2026", Instant.now()),
                new AbdmHipLinkingService.Visit("enc-2", "OPD Visit, 2 May 2026", Instant.now()))));
        when(store.findCareContexts(7, HIP_ID)).thenReturn(List.of(row("enc-1", "LINKED"), row("enc-2", "PENDING")));
    }

    @Test
    public void discoverByAddressOffersTheVisitsNotYetLinked() {
        when(store.findSubjectsByAbhaAddress("ramesh.gond@sbx", "@sbx")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Ramesh Gond")));

        service.discover(fixture("s3-discover.json"), headers("cb-discover-1"));

        JsonNode reply = sent(AbdmPaths.OUT_ON_DISCOVER);
        assertEquals("tx-discover-1", reply.get("transactionId").asText());
        assertEquals("cb-discover-1", reply.at("/response/requestId").asText());
        assertEquals("ABHA_ADDRESS", reply.at("/matchedBy/0").asText());
        assertEquals("subject-1", reply.at("/patient/0/referenceNumber").asText());
        assertEquals(1, reply.at("/patient/0/careContexts").size());
        assertEquals("enc-2", reply.at("/patient/0/careContexts/0/referenceNumber").asText());
    }

    @Test
    public void discoverFallsBackToTheVerifiedAbhaNumber() {
        when(store.findSubjectsByAbhaAddress(anyString(), anyString())).thenReturn(List.of());
        when(store.findSubjectsByAbhaNumber("12345678568912")).thenReturn(List.of(new AbdmHipStore.Subject(7, "subject-1", "Ramesh Gond")));

        service.discover(fixture("s3-discover.json"), headers("cb-discover-1"));

        assertEquals("ABHA_NUMBER", sent(AbdmPaths.OUT_ON_DISCOVER).at("/matchedBy/0").asText());
    }

    @Test
    public void discoverWithNoMatchAnswersWithAnError() {
        when(store.findSubjectsByAbhaAddress(anyString(), anyString())).thenReturn(List.of());
        when(store.findSubjectsByAbhaNumber(anyString())).thenReturn(List.of());

        service.discover(fixture("s3-discover.json"), headers("cb-discover-1"));

        JsonNode reply = sent(AbdmPaths.OUT_ON_DISCOVER);
        assertEquals("PATIENT_NOT_FOUND", reply.at("/error/code").asText());
        assertEquals("cb-discover-1", reply.at("/response/requestId").asText());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void linkInitKeepsTheRequestAndAnswersWithALinkReference() {
        when(store.findSubjectByUuid("subject-1")).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Ramesh Gond")));

        service.linkInit(fixture("s3-link-init.json"), headers("cb-init-1"));

        JsonNode reply = sent(AbdmPaths.OUT_LINK_ON_INIT);
        String linkRef = reply.at("/link/referenceNumber").asText();
        assertTrue(!linkRef.isEmpty());
        assertEquals("DIRECT", reply.at("/link/authenticationType").asText());
        assertEquals("OTP", reply.at("/link/meta/communicationHint").asText());
        assertEquals("cb-init-1", reply.at("/response/requestId").asText());
        verify(store).insertLinkRequest(eq("tx-discover-1"), eq(linkRef), eq(7L), eq("ramesh.gond@sbx"), eq(List.of("enc-1", "enc-2")),
                eq("OTP_SENT"), any(Instant.class));
    }

    @Test
    public void linkConfirmWithTheDemoOtpLinksTheVisitsAsUserLinked() {
        when(store.findLinkRequest("link-ref-1")).thenReturn(Optional.of(new AbdmHipStore.LinkRequest(4, "tx-discover-1", "link-ref-1", 7L,
                "ramesh.gond@sbx", List.of("enc-2"), "OTP_SENT", Instant.now().plusSeconds(300))));
        when(store.findSubjectById(7)).thenReturn(Optional.of(new AbdmHipStore.Subject(7, "subject-1", "Ramesh Gond")));
        when(store.findCareContextsByReferences(HIP_ID, List.of("enc-2"))).thenReturn(List.of(row("enc-2", "LINKED")));

        service.linkConfirm(fixture("s3-link-confirm.json"), headers("cb-confirm-1"));

        verify(store).markLinked(HIP_ID, List.of("enc-2"), "USER");
        verify(store).setLinkRequestStatus(4, "LINKED");
        JsonNode reply = sent(AbdmPaths.OUT_LINK_ON_CONFIRM);
        assertEquals("cb-confirm-1", reply.at("/response/requestId").asText());
        assertEquals("enc-2", reply.at("/patient/0/careContexts/0/referenceNumber").asText());
    }

    @Test
    public void linkConfirmWithAWrongOtpLinksNothing() {
        when(store.findLinkRequest("link-ref-1")).thenReturn(Optional.of(new AbdmHipStore.LinkRequest(4, "tx-discover-1", "link-ref-1", 7L,
                "ramesh.gond@sbx", List.of("enc-2"), "OTP_SENT", Instant.now().plusSeconds(300))));
        JsonNode body = fixture("s3-link-confirm.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("confirmation")).put("token", "000000");

        service.linkConfirm(body, headers("cb-confirm-1"));

        verify(store, never()).markLinked(anyString(), any(), anyString());
        assertEquals("OTP_INVALID", sent(AbdmPaths.OUT_LINK_ON_CONFIRM).at("/error/code").asText());
    }

    private JsonNode sent(String path) {
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).post(eq(path), anyString(), body.capture(), any());
        return body.getValue();
    }

    private static AbdmHipStore.CareContext row(String reference, String status) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, 7, HIP_ID, "subject-1", reference, "Display " + reference,
                "OPConsultation", status, "HIP", null, null, null);
    }
}
