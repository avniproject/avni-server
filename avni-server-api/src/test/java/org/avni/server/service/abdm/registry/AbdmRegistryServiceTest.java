package org.avni.server.service.abdm.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class AbdmRegistryServiceTest {
    private static final String HFR = "https://facilitysbx.abdm.gov.in";
    private static final String HFR_SEARCH = "/FacilityManagement/v1.5/facility/search";
    private static final String HFR_SUBMIT = "/FacilityManagement/v1.5/facility/submit-facility";
    private static final String HPR = "https://hpridsbx.abdm.gov.in";
    private static final String HPR_SEARCH = "/api/v1/search/searchHealthProfessional";

    private AbdmGatewayClient gatewayClient;

    private static AbdmConfig config(String mode, String hfrSearchPath) {
        return new AbdmConfig(new MockEnvironment(), mode, "https://dev.abdm.gov.in", "/api/hiecm/gateway/v3/sessions",
                "/api/hiecm/gateway/v3/certs", "sbx", "client", "", "log", "", "TANUH", "TANUH Health Centre", "TANUH-HIU", "", "",
                "123456", "@sbx", 300, HFR + "/", hfrSearchPath, HFR_SUBMIT, HPR, HPR_SEARCH);
    }

    private AbdmRegistryService service(String mode) {
        return new AbdmRegistryService(config(mode, HFR_SEARCH), gatewayClient);
    }

    private static ObjectNode criteria(String field, String value) {
        ObjectNode c = AbdmJson.obj();
        c.put(field, value);
        return c;
    }

    @Before
    public void setUp() {
        gatewayClient = mock(AbdmGatewayClient.class);
    }

    @Test
    public void mockFacilitySearchReturnsCannedKarnatakaFacilities() {
        JsonNode answer = service("mock").searchFacilities(AbdmJson.obj());

        assertEquals("mock", answer.get("mode").asText());
        JsonNode facilities = answer.at("/results/facilities");
        assertTrue(facilities.size() >= 2);
        assertEquals(facilities.size(), answer.at("/results/totalFacilities").asInt());
        boolean hoskote = false;
        for (JsonNode f : facilities) {
            assertTrue(f.get("facilityId").asText().matches("IN29\\d{8}"));
            hoskote |= f.get("facilityName").asText().equals("Hoskote PHC");
        }
        assertTrue(hoskote);
        verifyNoInteractions(gatewayClient);
    }

    @Test
    public void mockFacilitySearchFiltersByName() {
        JsonNode answer = service("mock").searchFacilities(criteria("facilityName", "hoskote"));

        assertEquals(1, answer.at("/results/facilities").size());
        assertEquals("Hoskote PHC", answer.at("/results/facilities/0/facilityName").asText());
        assertEquals(1, answer.at("/results/totalFacilities").asInt());
        assertEquals(0, service("mock").searchFacilities(criteria("facilityName", "Nowhere")).at("/results/facilities").size());
    }

    @Test
    public void mockProfessionalSearchReturnsHprIds() {
        JsonNode answer = service("mock").searchProfessionals(null);

        assertEquals("mock", answer.get("mode").asText());
        JsonNode professionals = answer.at("/results/professionals");
        assertTrue(professionals.size() >= 2);
        for (JsonNode p : professionals) {
            assertTrue(p.get("hprId").asText().matches("71-\\d{4}-\\d{4}-\\d{4}"));
            assertTrue(p.hasNonNull("name"));
        }
        assertEquals(1, service("mock").searchProfessionals(criteria("hprId", professionals.get(0).get("hprId").asText())).at("/results/professionals").size());
    }

    @Test
    public void mockSubmitGivesAFreshTrackingId() {
        AbdmRegistryService service = service("mock");
        JsonNode first = service.submitFacility(criteria("facilityName", "TANUH Health Centre"));
        JsonNode second = service.submitFacility(AbdmJson.obj());

        assertEquals("mock", first.get("mode").asText());
        assertEquals("Submitted", first.at("/results/status").asText());
        assertEquals("Submitted for NHA verification", first.at("/results/message").asText());
        assertTrue(first.at("/results/trackingId").asText().startsWith("MOCK-"));
        assertNotEquals(first.at("/results/trackingId").asText(), second.at("/results/trackingId").asText());
        verifyNoInteractions(gatewayClient);
    }

    @Test
    public void sandboxCallsTheConfiguredRegistryUrls() {
        ObjectNode body = AbdmJson.obj();
        body.putArray("facilities").addObject().put("facilityId", "IN2910000999");
        when(gatewayClient.postAbsolute(anyString(), any(), anyMap())).thenReturn(new AbdmGatewayResponse("r1", 200, body));
        AbdmRegistryService service = service("sandbox");
        ObjectNode search = criteria("facilityName", "Hoskote");

        JsonNode answer = service.searchFacilities(search);
        service.searchProfessionals(criteria("name", "Rao"));
        service.submitFacility(criteria("facilityName", "TANUH Health Centre"));

        assertEquals("sandbox", answer.get("mode").asText());
        assertEquals(body, answer.get("results"));
        verify(gatewayClient).postAbsolute(eq(HFR + HFR_SEARCH), eq(search), eq(Map.of()));
        verify(gatewayClient).postAbsolute(eq(HPR + HPR_SEARCH), any(), eq(Map.of()));
        verify(gatewayClient).postAbsolute(eq(HFR + HFR_SUBMIT), any(), eq(Map.of()));
    }

    @Test
    public void sandboxAnswerWithoutABodyIsAnEmptyResult() {
        when(gatewayClient.postAbsolute(anyString(), any(), anyMap())).thenReturn(new AbdmGatewayResponse("r1", 202, null));

        JsonNode answer = service("sandbox").submitFacility(AbdmJson.obj());

        assertEquals("sandbox", answer.get("mode").asText());
        assertTrue(answer.get("results").isObject());
        assertEquals(202, answer.get("status").asInt());
    }

    @Test
    public void sandboxGatewayRefusalIsReturnedNotThrown() {
        when(gatewayClient.postAbsolute(anyString(), any(), anyMap())).thenThrow(new AbdmGatewayException(401, "ABDM-1017", "Unauthorized request"));

        JsonNode answer = service("sandbox").searchFacilities(AbdmJson.obj());

        assertEquals("sandbox", answer.get("mode").asText());
        assertEquals("Unauthorized request", answer.get("error").asText());
        assertEquals(401, answer.get("status").asInt());
        assertEquals("ABDM-1017", answer.get("abdmCode").asText());
        assertFalse(answer.has("results"));
    }

    @Test
    public void sandboxRefusalWithoutAnAbdmCodeLeavesTheCodeOut() {
        when(gatewayClient.postAbsolute(anyString(), any(), anyMap())).thenThrow(new AbdmGatewayException(503, null, "Gateway did not answer"));

        JsonNode answer = service("sandbox").searchProfessionals(AbdmJson.obj());

        assertEquals(503, answer.get("status").asInt());
        assertFalse(answer.has("abdmCode"));
    }

    @Test
    public void sandboxWithoutAConfiguredPathSaysSoAndCallsNothing() {
        JsonNode answer = new AbdmRegistryService(config("sandbox", ""), gatewayClient).searchFacilities(AbdmJson.obj());

        assertEquals("sandbox", answer.get("mode").asText());
        assertTrue(answer.get("error").asText().contains("abdm.m4.hfr-search-path"));
        verifyNoInteractions(gatewayClient);
    }
}
