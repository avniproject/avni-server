package org.avni.server.service.abdm.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

// WireMock stands in for OUR server: the mock calls its callbacks at localhost:<server.port>.
public class MockConsentManagerTest {
    private static final String TIMESTAMP = "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z";
    private static final String ABHA = "asha.devi@sbx";

    private WireMockServer us;
    private MockConsentManager mock;

    @Before
    public void setUp() {
        us = new WireMockServer(options().dynamicPort());
        us.start();
        us.stubFor(post(urlMatching("/api/v3/.*")).willReturn(aResponse().withStatus(202)));
        mock = new MockConsentManager(AbdmTestConfig.mock(us.port(), 10), Duration.ofSeconds(3));
    }

    @After
    public void tearDown() {
        mock.shutdown();
        us.stop();
    }

    private static HttpHeaders headers(String requestId, String... nameValues) {
        HttpHeaders h = new HttpHeaders();
        h.add(AbdmPaths.H_REQUEST_ID, requestId);
        h.add(AbdmPaths.H_TIMESTAMP, "2026-10-26T04:00:00.000Z");
        h.add(AbdmPaths.H_CM_ID, "sbx");
        h.add(HttpHeaders.AUTHORIZATION, "Bearer mock-session");
        for (int i = 0; i < nameValues.length; i += 2) h.add(nameValues[i], nameValues[i + 1]);
        return h;
    }

    private static JsonNode json(String text) {
        return AbdmJson.read(text);
    }

    private List<LoggedRequest> await(String path, int count) {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            List<LoggedRequest> found = us.findAll(postRequestedFor(urlEqualTo(path)));
            if (found.size() >= count) return found;
            if (System.currentTimeMillis() > deadline) fail("expected " + count + " call(s) to " + path + ", got " + found.size());
            sleep(20);
        }
    }

    private JsonNode awaitBody(String path) {
        return json(await(path, 1).get(0).getBodyAsString());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void link() {
        MockConsentManager.Answer answer = mock.handle("POST", AbdmPaths.OUT_LINK_CARE_CONTEXT,
                headers("req-link", AbdmPaths.H_HIP_ID, "TANUH", AbdmPaths.H_LINK_TOKEN, "mock-link-1"),
                json("{\"abhaNumber\":\"91-1111-2222-3333\",\"abhaAddress\":\"" + ABHA + "\",\"patient\":[{\"referenceNumber\":\"patient-1\",\"display\":\"Asha Devi\"," +
                        "\"careContexts\":[{\"referenceNumber\":\"visit-1\",\"display\":\"ANC visit 1\"},{\"referenceNumber\":\"visit-2\",\"display\":\"ANC visit 2\"}]," +
                        "\"hiType\":\"OPConsultation\",\"count\":2}]}"));
        assertEquals(202, answer.status());
    }

    private String consentRequest() {
        MockConsentManager.Answer answer = mock.handle("POST", AbdmPaths.OUT_CONSENT_INIT, headers("req-init", AbdmPaths.H_HIU_ID, "TANUH-HIU"),
                json("{\"consent\":{\"purpose\":{\"text\":\"Care Management\",\"code\":\"CAREMGT\"},\"patient\":{\"id\":\"" + ABHA + "\"}," +
                        "\"hiu\":{\"id\":\"TANUH-HIU\"},\"requester\":{\"name\":\"Dr Rao\"},\"hiTypes\":[\"OPConsultation\"]," +
                        "\"permission\":{\"accessMode\":\"VIEW\",\"dateRange\":{\"from\":\"2025-01-01T00:00:00.000Z\",\"to\":\"2026-10-26T00:00:00.000Z\"}," +
                        "\"dataEraseAt\":\"2026-12-31T00:00:00.000Z\",\"frequency\":{\"unit\":\"HOUR\",\"value\":1,\"repeats\":0}}}}"));
        assertEquals(202, answer.status());
        JsonNode onInit = awaitBody(AbdmPaths.IN_HIU_CONSENT_ON_INIT);
        return AbdmJson.text(onInit, "consentRequest.id");
    }

    private String approvedConsentId() {
        link();
        String consentRequestId = consentRequest();
        mock.approve(consentRequestId);
        return AbdmJson.text(awaitBody(AbdmPaths.IN_HIP_CONSENT_NOTIFY), "notification.consentId");
    }

    @Test
    public void sessionAnswersAMockTokenAtOnce() {
        MockConsentManager.Answer answer = mock.handle("POST", "/api/hiecm/gateway/v3/sessions", new HttpHeaders(),
                json("{\"clientId\":\"x\",\"clientSecret\":\"y\",\"grantType\":\"client_credentials\"}"));

        assertEquals(200, answer.status());
        assertTrue(AbdmJson.text(answer.body(), "accessToken").startsWith("mock-"));
        assertEquals(1200, answer.body().get("expiresIn").asInt());
    }

    @Test
    public void aGatewayCallWithoutATokenIsRefused() {
        HttpHeaders h = headers("req-1");
        h.remove(HttpHeaders.AUTHORIZATION);

        assertEquals(401, mock.handle("POST", AbdmPaths.OUT_CONSENT_INIT, h, json("{}")).status());
    }

    @Test
    public void anUnknownPathIs404() {
        assertEquals(404, mock.handle("POST", "/api/hiecm/nothing/here", headers("req-1"), json("{}")).status());
    }

    @Test
    public void bridgeServicesListsOurHipAndHiu() {
        MockConsentManager.Answer answer = mock.handle("GET", AbdmPaths.OUT_BRIDGE_SERVICES, headers("req-1"), null);

        assertEquals(200, answer.status());
        assertEquals("TANUH", answer.body().get(0).get("id").asText());
        assertEquals("TANUH-HIU", answer.body().get(1).get("id").asText());
        assertEquals(202, mock.handle("PATCH", AbdmPaths.OUT_BRIDGE_URL, headers("req-2"), json("{\"url\":\"https://t.example\"}")).status());
    }

    @Test
    public void generateTokenCallsBackWithALinkToken() {
        MockConsentManager.Answer answer = mock.handle("POST", AbdmPaths.OUT_GENERATE_TOKEN, headers("req-gt", AbdmPaths.H_HIP_ID, "TANUH"),
                json("{\"abhaNumber\":\"91-1111-2222-3333\",\"abhaAddress\":\"" + ABHA + "\",\"name\":\"Asha Devi\",\"gender\":\"F\",\"yearOfBirth\":1994}"));

        assertEquals(202, answer.status());
        LoggedRequest callback = await(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN, 1).get(0);
        JsonNode body = json(callback.getBodyAsString());
        assertTrue(AbdmJson.text(body, "linkToken").startsWith("mock-link-"));
        assertEquals(ABHA, AbdmJson.text(body, "abhaAddress"));
        assertEquals("req-gt", AbdmJson.text(body, "response.requestId"));
        assertEquals("TANUH", callback.getHeader("X-HIP-ID"));
        assertEquals("Bearer mock-gateway", callback.getHeader("Authorization"));
        assertEquals("sbx", callback.getHeader("X-CM-ID"));
        assertTrue(callback.getHeader("TIMESTAMP").matches(TIMESTAMP));
        assertNotNull(callback.getHeader("REQUEST-ID"));
        assertNotEquals("req-gt", callback.getHeader("REQUEST-ID"));
    }

    @Test
    public void linkCareContextIsRecordedAndCalledBack() {
        link();

        JsonNode body = awaitBody(AbdmPaths.IN_HIP_ON_LINK_CARE_CONTEXT);
        assertEquals("SUCCESS", AbdmJson.text(body, "status"));
        assertEquals(ABHA, AbdmJson.text(body, "abhaAddress"));
        assertEquals("req-link", AbdmJson.text(body, "response.requestId"));
        String state = AbdmJson.write(mock.state());
        assertTrue(state, state.contains("visit-2"));
    }

    @Test
    public void consentInitCallsBackWithAConsentRequestId() {
        String consentRequestId = consentRequest();

        assertNotNull(consentRequestId);
        JsonNode onInit = awaitBody(AbdmPaths.IN_HIU_CONSENT_ON_INIT);
        assertEquals("req-init", AbdmJson.text(onInit, "response.requestId"));
        assertEquals("TANUH-HIU", await(AbdmPaths.IN_HIU_CONSENT_ON_INIT, 1).get(0).getHeader("X-HIU-ID"));
    }

    @Test
    public void approveNotifiesTheHipWithTheLinkedCareContextsAndTheHiuWithTheArtefact() {
        link();
        String consentRequestId = consentRequest();

        JsonNode approved = mock.approve(consentRequestId);

        assertEquals("GRANTED", AbdmJson.text(approved, "status"));
        LoggedRequest hipNotify = await(AbdmPaths.IN_HIP_CONSENT_NOTIFY, 1).get(0);
        JsonNode hip = json(hipNotify.getBodyAsString());
        assertEquals("TANUH", hipNotify.getHeader("X-HIP-ID"));
        assertEquals("GRANTED", AbdmJson.text(hip, "notification.status"));
        String consentId = AbdmJson.text(hip, "notification.consentId");
        JsonNode detail = AbdmJson.at(hip, "notification.consentDetail");
        assertEquals(consentId, AbdmJson.text(detail, "consentId"));
        assertEquals(ABHA, AbdmJson.text(detail, "patient.id"));
        assertEquals("TANUH", AbdmJson.text(detail, "hip.id"));
        assertEquals("TANUH-HIU", AbdmJson.text(detail, "hiu.id"));
        assertEquals("OPConsultation", detail.get("hiTypes").get(0).asText());
        assertEquals("VIEW", AbdmJson.text(detail, "permission.accessMode"));
        JsonNode careContexts = detail.get("careContexts");
        assertEquals(2, careContexts.size());
        assertEquals("patient-1", AbdmJson.text(careContexts.get(0), "patientReference"));
        assertEquals("visit-1", AbdmJson.text(careContexts.get(0), "careContextReference"));
        assertEquals("visit-2", AbdmJson.text(careContexts.get(1), "careContextReference"));

        LoggedRequest hiuNotify = await(AbdmPaths.IN_HIU_CONSENT_NOTIFY, 1).get(0);
        JsonNode hiu = json(hiuNotify.getBodyAsString());
        assertEquals("TANUH-HIU", hiuNotify.getHeader("X-HIU-ID"));
        assertEquals("GRANTED", AbdmJson.text(hiu, "notification.status"));
        assertEquals(consentRequestId, AbdmJson.text(hiu, "notification.consentRequestId"));
        assertEquals(consentId, AbdmJson.at(hiu, "notification.consentArtefacts").get(0).get("id").asText());
    }

    @Test
    public void denyNotifiesOnlyTheHiu() {
        link();
        String consentRequestId = consentRequest();

        mock.deny(consentRequestId);

        JsonNode hiu = awaitBody(AbdmPaths.IN_HIU_CONSENT_NOTIFY);
        assertEquals("DENIED", AbdmJson.text(hiu, "notification.status"));
        assertEquals(0, AbdmJson.at(hiu, "notification.consentArtefacts").size());
        sleep(100);
        us.verify(0, postRequestedFor(urlEqualTo(AbdmPaths.IN_HIP_CONSENT_NOTIFY)));
    }

    @Test
    public void approveWithNoLinksGrantsWithNoArtefacts() {
        String consentRequestId = consentRequest();

        mock.approve(consentRequestId);

        JsonNode hiu = awaitBody(AbdmPaths.IN_HIU_CONSENT_NOTIFY);
        assertEquals("GRANTED", AbdmJson.text(hiu, "notification.status"));
        assertEquals(0, AbdmJson.at(hiu, "notification.consentArtefacts").size());
    }

    @Test
    public void approvingAnUnknownRequestIsABadRequest() {
        assertThrows(IllegalArgumentException.class, () -> mock.approve("no-such-request"));
    }

    @Test
    public void fetchCallsBackWithTheArtefact() {
        String consentId = approvedConsentId();

        assertEquals(202, mock.handle("POST", AbdmPaths.OUT_CONSENT_FETCH, headers("req-fetch", AbdmPaths.H_HIU_ID, "TANUH-HIU"),
                json("{\"consentId\":\"" + consentId + "\"}")).status());

        JsonNode onFetch = awaitBody(AbdmPaths.IN_HIU_CONSENT_ON_FETCH);
        assertEquals("req-fetch", AbdmJson.text(onFetch, "response.requestId"));
        assertEquals("GRANTED", AbdmJson.text(onFetch, "consent.status"));
        assertEquals(consentId, AbdmJson.text(onFetch, "consent.consentDetail.consentId"));
        assertEquals(2, AbdmJson.at(onFetch, "consent.consentDetail.careContexts").size());
        assertEquals("mock", AbdmJson.text(onFetch, "consent.signature"));
    }

    @Test
    public void statusCallsBackWithTheRequestStatus() {
        link();
        String consentRequestId = consentRequest();
        mock.approve(consentRequestId);

        mock.handle("POST", AbdmPaths.OUT_CONSENT_STATUS, headers("req-status", AbdmPaths.H_HIU_ID, "TANUH-HIU"),
                json("{\"consentRequestId\":\"" + consentRequestId + "\"}"));

        JsonNode onStatus = awaitBody(AbdmPaths.IN_HIU_CONSENT_ON_STATUS);
        assertEquals("req-status", AbdmJson.text(onStatus, "response.requestId"));
        assertEquals(consentRequestId, AbdmJson.text(onStatus, "consentRequest.id"));
        assertEquals("GRANTED", AbdmJson.text(onStatus, "consentRequest.status"));
        assertEquals(1, AbdmJson.at(onStatus, "consentRequest.consentArtefacts").size());
    }

    @Test
    public void healthInformationRequestCallsTheHiuThenTheHipWithTheSameKeyMaterialAndPushUrl() {
        String consentId = approvedConsentId();
        String keyMaterial = "{\"cryptoAlg\":\"ECDH\",\"curve\":\"Curve25519\",\"dhPublicKey\":{\"expiry\":\"2026-10-27T00:00:00.000Z\"," +
                "\"parameters\":\"Curve25519/32byte random key\",\"keyValue\":\"BASE64KEY\"},\"nonce\":\"NONCE1\"}";
        String pushUrl = "http://localhost:" + us.port() + AbdmPaths.IN_HIU_DATA_PUSH;

        assertEquals(202, mock.handle("POST", AbdmPaths.OUT_HI_REQUEST, headers("req-hi", AbdmPaths.H_HIU_ID, "TANUH-HIU"),
                json("{\"hiRequest\":{\"consent\":{\"id\":\"" + consentId + "\"},\"dateRange\":{\"from\":\"2025-01-01T00:00:00.000Z\"," +
                        "\"to\":\"2026-10-26T00:00:00.000Z\"},\"dataPushUrl\":\"" + pushUrl + "\",\"keyMaterial\":" + keyMaterial + "}}")).status());

        LoggedRequest hiuCall = await(AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST, 1).get(0);
        JsonNode onRequest = json(hiuCall.getBodyAsString());
        String transactionId = AbdmJson.text(onRequest, "hiRequest.transactionId");
        assertNotNull(transactionId);
        assertEquals("REQUESTED", AbdmJson.text(onRequest, "hiRequest.sessionStatus"));
        assertEquals("req-hi", AbdmJson.text(onRequest, "response.requestId"));

        LoggedRequest hipCall = await(AbdmPaths.IN_HIP_HEALTH_INFORMATION_REQUEST, 1).get(0);
        JsonNode hipRequest = json(hipCall.getBodyAsString());
        assertEquals("TANUH", hipCall.getHeader("X-HIP-ID"));
        assertEquals(transactionId, AbdmJson.text(hipRequest, "transactionId"));
        assertEquals(consentId, AbdmJson.text(hipRequest, "hiRequest.consent.id"));
        assertEquals(pushUrl, AbdmJson.text(hipRequest, "hiRequest.dataPushUrl"));
        assertEquals(json(keyMaterial), AbdmJson.at(hipRequest, "hiRequest.keyMaterial"));
        assertTrue("the HIU hears first", hiuCall.getLoggedDate().compareTo(hipCall.getLoggedDate()) <= 0);
    }

    @Test
    public void phrDiscoveryLinkInitAndConfirmWaitForTheHipsAnswers() throws Exception {
        CompletableFuture<JsonNode> discovered = CompletableFuture.supplyAsync(() -> mock.phrDiscover(ABHA, "TANUH"));
        LoggedRequest discover = await(AbdmPaths.IN_HIP_DISCOVER, 1).get(0);
        JsonNode discoverBody = json(discover.getBodyAsString());
        assertEquals(ABHA, AbdmJson.text(discoverBody, "patient.id"));
        assertEquals("ABHA_ADDRESS", AbdmJson.at(discoverBody, "patient.verifiedIdentifiers").get(0).get("type").asText());
        String transactionId = AbdmJson.text(discoverBody, "transactionId");
        mock.handle("POST", AbdmPaths.OUT_ON_DISCOVER, headers("hip-req-1"), json("{\"transactionId\":\"" + transactionId + "\"," +
                "\"patient\":[{\"referenceNumber\":\"patient-1\",\"display\":\"Asha Devi\",\"careContexts\":[{\"referenceNumber\":\"visit-3\"," +
                "\"display\":\"ANC visit 3\"}],\"hiType\":\"OPConsultation\",\"count\":1}],\"matchedBy\":[\"ABHA_ADDRESS\"]," +
                "\"response\":{\"requestId\":\"" + discover.getHeader("REQUEST-ID") + "\"}}"));
        assertEquals("patient-1", discovered.get(5, TimeUnit.SECONDS).get("patient").get(0).get("referenceNumber").asText());

        CompletableFuture<String> initiated = CompletableFuture.supplyAsync(() -> mock.phrLinkInit(transactionId, ABHA, "patient-1", List.of("visit-3")));
        LoggedRequest init = await(AbdmPaths.IN_HIP_LINK_INIT, 1).get(0);
        JsonNode initBody = json(init.getBodyAsString());
        assertEquals("visit-3", initBody.get("patient").get(0).get("careContexts").get(0).get("referenceNumber").asText());
        mock.handle("POST", AbdmPaths.OUT_LINK_ON_INIT, headers("hip-req-2"), json("{\"transactionId\":\"" + transactionId + "\"," +
                "\"link\":{\"referenceNumber\":\"link-ref-1\",\"authenticationType\":\"DIRECT\"},\"response\":{\"requestId\":\"" + init.getHeader("REQUEST-ID") + "\"}}"));
        assertEquals("link-ref-1", initiated.get(5, TimeUnit.SECONDS));

        CompletableFuture<JsonNode> confirmed = CompletableFuture.supplyAsync(() -> mock.phrLinkConfirm("link-ref-1", "123456"));
        LoggedRequest confirm = await(AbdmPaths.IN_HIP_LINK_CONFIRM, 1).get(0);
        JsonNode confirmBody = json(confirm.getBodyAsString());
        assertEquals("link-ref-1", AbdmJson.text(confirmBody, "confirmation.linkRefNumber"));
        assertEquals("123456", AbdmJson.text(confirmBody, "confirmation.token"));
        mock.handle("POST", AbdmPaths.OUT_LINK_ON_CONFIRM, headers("hip-req-3"), json("{\"patient\":[{\"referenceNumber\":\"patient-1\"," +
                "\"display\":\"Asha Devi\",\"careContexts\":[{\"referenceNumber\":\"visit-3\",\"display\":\"ANC visit 3\"}],\"hiType\":\"OPConsultation\"," +
                "\"count\":1}],\"response\":{\"requestId\":\"" + confirm.getHeader("REQUEST-ID") + "\"}}"));
        assertEquals("LINKED", AbdmJson.text(confirmed.get(5, TimeUnit.SECONDS), "status"));

        String consentRequestId = consentRequest();
        mock.approve(consentRequestId);
        JsonNode careContexts = AbdmJson.at(awaitBody(AbdmPaths.IN_HIP_CONSENT_NOTIFY), "notification.consentDetail.careContexts");
        assertEquals("visit-3", AbdmJson.text(careContexts.get(0), "careContextReference"));
    }

    @Test
    public void aHipThatNeverAnswersDiscoveryTimesOut() {
        MockConsentManager quick = new MockConsentManager(AbdmTestConfig.mock(us.port(), 10), Duration.ofMillis(300));
        try {
            RuntimeException e = assertThrows(RuntimeException.class, () -> quick.phrDiscover(ABHA, "TANUH"));
            assertTrue(e.getMessage(), e.getMessage().contains("did not answer"));
        } finally {
            quick.shutdown();
        }
    }

    @Test
    public void stateListsConsentRequests() {
        String consentRequestId = consentRequest();

        Map<?, ?> state = AbdmJson.M.convertValue(mock.state(), Map.class);

        assertTrue(AbdmJson.write(mock.state()).contains(consentRequestId));
        assertNotNull(state.get("consentRequests"));
    }
}
