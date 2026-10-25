package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AbdmGatewayClientTest {
    private static final String TIMESTAMP = "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z";

    private WireMockServer gateway;
    private AbdmSessionService session;
    private AbdmMessageLog messageLog;
    private AbdmGatewayClient client;
    private String baseUrl;

    @Before
    public void setUp() {
        gateway = new WireMockServer(options().dynamicPort());
        gateway.start();
        baseUrl = "http://localhost:" + gateway.port();
        AbdmConfig cfg = AbdmTestConfig.sandbox(baseUrl, AbdmTestConfig.CLIENT_SECRET);
        session = mock(AbdmSessionService.class);
        when(session.accessToken()).thenReturn("token-1");
        messageLog = mock(AbdmMessageLog.class);
        client = new AbdmGatewayClient(cfg, session, messageLog, Duration.ofSeconds(2), Duration.ofMillis(700));
    }

    @After
    public void tearDown() {
        gateway.stop();
    }

    private static JsonNode body(String json) {
        return AbdmJson.read(json);
    }

    @Test
    public void postSendsTheStandardHeadersAndAContentLengthBody() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_GENERATE_TOKEN)).willReturn(aResponse().withStatus(202)));
        JsonNode request = body("{\"abhaAddress\":\"asha@sbx\"}");

        AbdmGatewayResponse response = client.post(AbdmPaths.OUT_GENERATE_TOKEN, "req-1", request, Map.of(AbdmPaths.H_HIP_ID, "TANUH"));

        assertEquals(202, response.status());
        assertEquals("req-1", response.requestId());
        assertNull(response.body());
        gateway.verify(postRequestedFor(urlEqualTo(AbdmPaths.OUT_GENERATE_TOKEN))
                .withHeader("Authorization", equalTo("Bearer token-1"))
                .withHeader("REQUEST-ID", equalTo("req-1"))
                .withHeader("TIMESTAMP", matching(TIMESTAMP))
                .withHeader("X-CM-ID", equalTo("sbx"))
                .withHeader("X-HIP-ID", equalTo("TANUH"))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Content-Length", matching("[0-9]+"))
                .withoutHeader("Transfer-Encoding")
                .withRequestBody(equalToJson("{\"abhaAddress\":\"asha@sbx\"}")));
        verify(messageLog).outgoing("POST", AbdmPaths.OUT_GENERATE_TOKEN, "req-1", request, 202);
    }

    @Test
    public void passesTheLinkTokenThrough() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_LINK_CARE_CONTEXT)).willReturn(aResponse().withStatus(202)));

        client.post(AbdmPaths.OUT_LINK_CARE_CONTEXT, AbdmGatewayClient.newRequestId(), body("{}"),
                Map.of(AbdmPaths.H_LINK_TOKEN, "link-token-1", AbdmPaths.H_HIP_ID, "TANUH"));

        gateway.verify(postRequestedFor(urlEqualTo(AbdmPaths.OUT_LINK_CARE_CONTEXT))
                .withHeader("X-LINK-TOKEN", equalTo("link-token-1"))
                .withHeader("X-HIP-ID", equalTo("TANUH")));
    }

    @Test
    public void aNullRequestIdGetsAFreshOne() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).willReturn(aResponse().withStatus(202)));

        AbdmGatewayResponse response = client.post(AbdmPaths.OUT_CONSENT_INIT, null, body("{}"), null);

        assertNotNull(response.requestId());
        gateway.verify(postRequestedFor(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).withHeader("REQUEST-ID", equalTo(response.requestId())));
    }

    @Test
    public void on401InvalidatesTheSessionAndRetriesOnceWithTheSameRequestId() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).inScenario("expired").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("renewed"));
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).inScenario("expired").whenScenarioStateIs("renewed")
                .willReturn(aResponse().withStatus(202)));
        when(session.accessToken()).thenReturn("token-old", "token-new");

        AbdmGatewayResponse response = client.post(AbdmPaths.OUT_CONSENT_INIT, "req-9", body("{}"), Map.of());

        assertEquals(202, response.status());
        verify(session, times(1)).invalidate();
        gateway.verify(2, postRequestedFor(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).withHeader("REQUEST-ID", equalTo("req-9")));
        gateway.verify(1, postRequestedFor(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).withHeader("Authorization", equalTo("Bearer token-new")));
    }

    @Test
    public void a401AfterTheRetryFailsWithoutRetryingAgain() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).willReturn(aResponse().withStatus(401)));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class,
                () -> client.post(AbdmPaths.OUT_CONSENT_INIT, "req-9", body("{}"), Map.of()));

        assertEquals(401, e.getStatus());
        gateway.verify(2, postRequestedFor(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)));
    }

    @Test
    public void parsesAbdmErrorCodeFromAnErrorObject() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_LINK_CARE_CONTEXT)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"ABDM-1035\",\"message\":\"Invalid link token\"}}")));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class,
                () -> client.post(AbdmPaths.OUT_LINK_CARE_CONTEXT, "req-2", body("{}"), Map.of()));

        assertEquals(400, e.getStatus());
        assertEquals("ABDM-1035", e.getAbdmCode());
        assertTrue(e.getMessage(), e.getMessage().contains("Invalid link token"));
        verify(messageLog).outgoing(eq("POST"), eq(AbdmPaths.OUT_LINK_CARE_CONTEXT), eq("req-2"), any(), eq(400));
    }

    @Test
    public void parsesAbdmErrorCodeAtTopLevel() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_FETCH)).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"ABDM-1017\",\"message\":\"Consent artefact not found\"}")));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class,
                () -> client.post(AbdmPaths.OUT_CONSENT_FETCH, "req-3", body("{}"), Map.of()));

        assertEquals(422, e.getStatus());
        assertEquals("ABDM-1017", e.getAbdmCode());
    }

    @Test
    public void aGatewayThatDoesNotAnswerInTimeFails() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).willReturn(aResponse().withStatus(202).withFixedDelay(2000)));

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class,
                () -> client.post(AbdmPaths.OUT_CONSENT_INIT, "req-4", body("{}"), Map.of()));

        assertEquals(0, e.getStatus());
        assertNull(e.getAbdmCode());
        assertTrue(e.getMessage(), e.getMessage().contains("did not answer"));
    }

    @Test
    public void aGatewayThatIsNotThereFails() {
        gateway.stop();

        AbdmGatewayException e = assertThrows(AbdmGatewayException.class,
                () -> client.post(AbdmPaths.OUT_CONSENT_INIT, "req-5", body("{}"), Map.of()));

        assertEquals(0, e.getStatus());
    }

    @Test
    public void pushToUrlSendsNoAuthorization() {
        gateway.stubFor(post(urlEqualTo("/api/v3/hiu/health-information/transfer")).willReturn(aResponse().withStatus(202)));

        AbdmGatewayResponse response = client.pushToUrl(baseUrl + "/api/v3/hiu/health-information/transfer", body("{\"transactionId\":\"t1\"}"));

        assertEquals(202, response.status());
        gateway.verify(postRequestedFor(urlEqualTo("/api/v3/hiu/health-information/transfer"))
                .withoutHeader("Authorization")
                .withHeader("REQUEST-ID", matching("[0-9a-f-]{36}"))
                .withHeader("TIMESTAMP", matching(TIMESTAMP))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Content-Length", matching("[0-9]+")));
        verify(session, never()).accessToken();
    }

    @Test
    public void patchSendsThePatchMethodWithABody() {
        gateway.stubFor(patch(urlEqualTo(AbdmPaths.OUT_BRIDGE_URL)).willReturn(aResponse().withStatus(202)));

        AbdmGatewayResponse response = client.patch(AbdmPaths.OUT_BRIDGE_URL, body("{\"url\":\"https://tunnel.example\"}"));

        assertEquals(202, response.status());
        gateway.verify(patchRequestedFor(urlEqualTo(AbdmPaths.OUT_BRIDGE_URL))
                .withHeader("Authorization", equalTo("Bearer token-1"))
                .withHeader("X-CM-ID", equalTo("sbx"))
                .withRequestBody(equalToJson("{\"url\":\"https://tunnel.example\"}")));
        verify(messageLog).outgoing(eq("PATCH"), eq(AbdmPaths.OUT_BRIDGE_URL), anyString(), any(), eq(202));
    }

    @Test
    public void getParsesAJsonBody() {
        gateway.stubFor(get(urlEqualTo(AbdmPaths.OUT_BRIDGE_SERVICES)).willReturn(okJson("[{\"id\":\"TANUH\",\"active\":true}]")));

        AbdmGatewayResponse response = client.get(AbdmPaths.OUT_BRIDGE_SERVICES);

        assertEquals(200, response.status());
        assertEquals("TANUH", response.body().get(0).get("id").asText());
        gateway.verify(getRequestedFor(urlEqualTo(AbdmPaths.OUT_BRIDGE_SERVICES)).withHeader("Authorization", equalTo("Bearer token-1")));
    }

    @Test
    public void postAbsoluteUsesTheSessionToken() {
        gateway.stubFor(post(urlEqualTo("/FacilityManagement/v1.5/facility/search")).willReturn(okJson("{\"facilities\":[]}")));

        AbdmGatewayResponse response = client.postAbsolute(baseUrl + "/FacilityManagement/v1.5/facility/search", body("{}"), Map.of("X-Extra", "1"));

        assertEquals(200, response.status());
        gateway.verify(postRequestedFor(urlEqualTo("/FacilityManagement/v1.5/facility/search"))
                .withHeader("Authorization", equalTo("Bearer token-1"))
                .withHeader("X-Extra", equalTo("1")));
    }

    @Test
    public void aMessageLogFailureNeverFailsTheCall() {
        gateway.stubFor(post(urlEqualTo(AbdmPaths.OUT_CONSENT_INIT)).willReturn(aResponse().withStatus(202)));
        doThrow(new RuntimeException("db down")).when(messageLog).outgoing(anyString(), anyString(), anyString(), any(), any());

        assertEquals(202, client.post(AbdmPaths.OUT_CONSENT_INIT, "req-6", body("{}"), Map.of()).status());
    }
}
