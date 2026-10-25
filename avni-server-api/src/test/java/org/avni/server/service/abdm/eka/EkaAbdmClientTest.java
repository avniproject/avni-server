package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.avni.server.service.abha.EkaAuthException;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.avni.server.service.abha.EkaToken;
import org.avni.server.service.abha.EkaTokenService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class EkaAbdmClientTest {
    private static final String LINK = "/abdm/v1/care-contexts/link";

    private WireMockServer eka;
    private ExternalSystemConfigRepository configRepository;
    private EkaTokenService tokenService;
    private AbdmMessageLog messageLog;
    private EkaAbdmClient client;

    @Before
    public void setUp() throws Exception {
        eka = new WireMockServer(options().dynamicPort());
        eka.start();
        String baseUrl = "http://localhost:" + eka.port();
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisationId()).thenReturn(1L);
        configRepository = mock(ExternalSystemConfigRepository.class);
        when(configRepository.getEkaSystemConfig(1L)).thenReturn(mock(EkaSystemConfig.class));
        tokenService = mock(EkaTokenService.class);
        when(tokenService.getAccessToken(1L)).thenReturn(new EkaToken("eka-token-1", Instant.now().plusSeconds(3600)));
        messageLog = mock(AbdmMessageLog.class);
        client = new EkaAbdmClient(orgContext, configRepository, tokenService, messageLog, config -> baseUrl,
                Duration.ofSeconds(2), Duration.ofMillis(700));
    }

    @After
    public void tearDown() {
        eka.stop();
    }

    @Test
    public void postSendsTheBearerTokenTheGivenHeadersAndAContentLengthBody() {
        eka.stubFor(post(urlEqualTo(LINK)).willReturn(aResponse().withStatus(202)));
        JsonNode body = AbdmJson.read("{\"oid\":\"oid-1\",\"care_contexts\":[]}");

        AbdmGatewayResponse response = client.post(LINK, body, Map.of(EkaAbdmClient.H_PATIENT_OID, "oid-1",
                EkaAbdmClient.H_PARTNER_PATIENT_ID, "subject-1", EkaAbdmClient.H_HIP_ID, "TANUH"));

        assertEquals(202, response.status());
        assertNull(response.body());
        assertNotNull(response.requestId());
        eka.verify(postRequestedFor(urlEqualTo(LINK))
                .withHeader("Authorization", equalTo("Bearer eka-token-1"))
                .withHeader("X-Pt-Id", equalTo("oid-1"))
                .withHeader("X-Partner-Pt-Id", equalTo("subject-1"))
                .withHeader("X-Hip-Id", equalTo("TANUH"))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Content-Length", matching("[0-9]+"))
                .withoutHeader("Transfer-Encoding")
                .withRequestBody(equalToJson("{\"oid\":\"oid-1\",\"care_contexts\":[]}")));
        verify(messageLog).outgoing("POST", "eka:" + LINK, response.requestId(), body, 202);
    }

    @Test
    public void anyTwoHundredIsAcceptedAndAJsonBodyIsRead() {
        eka.stubFor(post(urlEqualTo("/abdm/v1/hip/onboard")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"hip_id\":\"TANUH\",\"hip_code\":\"C-1\"}")));
        eka.stubFor(post(urlEqualTo("/abdm/v1/consents/create")).willReturn(aResponse().withStatus(204)));
        eka.stubFor(post(urlEqualTo("/created")).willReturn(aResponse().withStatus(201).withBody("not json")));

        AbdmGatewayResponse onboard = client.post("/abdm/v1/hip/onboard", AbdmJson.obj(), Map.of());
        AbdmGatewayResponse noContent = client.post("/abdm/v1/consents/create", AbdmJson.obj(), null);
        AbdmGatewayResponse text = client.post("/created", AbdmJson.obj(), null);

        assertEquals("C-1", onboard.body().get("hip_code").asText());
        assertEquals(204, noContent.status());
        assertNull(noContent.body());
        assertEquals("not json", text.body().asText());
    }

    @Test
    public void getAndPatchUseTheirOwnMethods() {
        eka.stubFor(get(urlEqualTo("/abdm/nhpr/v1/hfr/search/IN2910000001")).willReturn(aResponse().withStatus(200)
                .withBody("{\"data\":{\"facilityId\":\"IN2910000001\"},\"success\":true}")));
        eka.stubFor(patch(urlEqualTo("/abdm/v1/hiu/keyset")).willReturn(aResponse().withStatus(200)));

        AbdmGatewayResponse found = client.get("/abdm/nhpr/v1/hfr/search/IN2910000001", Map.of(EkaAbdmClient.H_HIP_ID, "TANUH"));
        client.patch("/abdm/v1/hiu/keyset", AbdmJson.read("{\"public_key\":\"k\",\"nonce\":\"n\"}"), null);

        assertEquals("IN2910000001", found.body().at("/data/facilityId").asText());
        eka.verify(getRequestedFor(urlEqualTo("/abdm/nhpr/v1/hfr/search/IN2910000001"))
                .withHeader("Authorization", equalTo("Bearer eka-token-1")).withHeader("X-Hip-Id", equalTo("TANUH")));
        eka.verify(patchRequestedFor(urlEqualTo("/abdm/v1/hiu/keyset")).withRequestBody(equalToJson("{\"public_key\":\"k\",\"nonce\":\"n\"}")));
        verify(messageLog).outgoing(eq("GET"), eq("eka:/abdm/nhpr/v1/hfr/search/IN2910000001"), anyString(), isNull(), eq(200));
        verify(messageLog).outgoing(eq("PATCH"), eq("eka:/abdm/v1/hiu/keyset"), anyString(), any(), eq(200));
    }

    @Test
    public void anEkaRefusalCarriesAbdmsOwnCodeFromSourceError() {
        eka.stubFor(post(urlEqualTo(LINK)).willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":400,\"error\":\"Validation failed\",\"source_error\":{\"code\":\"ABDM-1035\",\"message\":\"Invalid HIP ID\"}}")));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post(LINK, AbdmJson.obj(), Map.of()));

        assertEquals(400, e.getStatus());
        assertEquals("ABDM-1035", e.getAbdmCode());
        assertTrue(e.getMessage(), e.getMessage().contains("Invalid HIP ID"));
        assertTrue(e.getMessage(), e.getMessage().contains("Validation failed"));
        verify(messageLog).outgoing(eq("POST"), eq("eka:" + LINK), anyString(), any(), eq(400));
    }

    @Test
    public void anErrorObjectGivesItsCodeAndMessage() {
        eka.stubFor(post(urlEqualTo("/x")).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":{\"message\":\"requested endpoint is already registered\",\"code\":\"INVALID_REQUEST\"}}")));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post("/x", AbdmJson.obj(), Map.of()));

        assertEquals("INVALID_REQUEST", e.getAbdmCode());
        assertTrue(e.getMessage(), e.getMessage().contains("already registered"));
    }

    @Test
    public void aRejectedTokenFailsOnceWithoutRetrying() {
        eka.stubFor(post(urlEqualTo(LINK)).willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post(LINK, AbdmJson.obj(), Map.of()));

        assertEquals(401, e.getStatus());
        assertTrue(e.getMessage(), e.getMessage().contains("401"));
        eka.verify(1, postRequestedFor(urlEqualTo(LINK)));
    }

    @Test
    public void aSlowAnswerFailsWithStatusZero() {
        eka.stubFor(post(urlEqualTo(LINK)).willReturn(aResponse().withStatus(202).withFixedDelay(1500)));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post(LINK, AbdmJson.obj(), Map.of()));

        assertEquals(0, e.getStatus());
        verify(messageLog).outgoing(eq("POST"), eq("eka:" + LINK), anyString(), any(), isNull());
    }

    @Test
    public void noEkaSettingsMeansNoCall() throws Exception {
        when(configRepository.getEkaSystemConfig(1L)).thenThrow(new EkaNotConfiguredException("Eka system config not found"));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post(LINK, AbdmJson.obj(), Map.of()));

        assertEquals(0, e.getStatus());
        assertTrue(e.getMessage(), e.getMessage().contains("Eka"));
        assertEquals(0, eka.getAllServeEvents().size());
    }

    @Test
    public void aFailedEkaLoginMeansNoCall() throws Exception {
        when(tokenService.getAccessToken(1L)).thenThrow(new EkaAuthException("Eka login failed with HTTP 401"));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class, () -> client.post(LINK, AbdmJson.obj(), Map.of()));

        assertTrue(e.getMessage(), e.getMessage().contains("Eka login failed"));
        assertEquals(0, eka.getAllServeEvents().size());
    }

    @Test
    public void headersWithoutAValueAreLeftOut() {
        eka.stubFor(post(urlEqualTo(LINK)).willReturn(aResponse().withStatus(202)));
        Map<String, String> headers = new HashMap<>();
        headers.put(EkaAbdmClient.H_HIP_ID, "TANUH");
        headers.put(EkaAbdmClient.H_PATIENT_OID, null);

        client.post(LINK, AbdmJson.obj(), headers);

        eka.verify(postRequestedFor(urlEqualTo(LINK)).withHeader("X-Hip-Id", equalTo("TANUH")).withoutHeader("X-Pt-Id"));
    }

    @Test
    public void tokenCheckAndBaseUrlUseTheOrganisationsSettings() throws Exception {
        assertEquals("http://localhost:" + eka.port(), client.baseUrl());
        client.checkToken();
        verify(tokenService).getAccessToken(1L);
    }
}
