package org.avni.server.web.abdm;

import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTestConfig;
import org.avni.server.service.abdm.mock.MockConsentManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MockConsentManagerControllerTest {
    private MockConsentManager mock;
    private MockMvc mvc;

    @Before
    public void setUp() {
        // Callbacks go to a port nothing listens on; these tests only look at the synchronous answers.
        mock = new MockConsentManager(AbdmTestConfig.mock(1, 0));
        mvc = MockMvcBuilders.standaloneSetup(new MockConsentManagerController(mock))
                .setControllerAdvice(new AbdmControllerAdvice()).build();
    }

    @After
    public void tearDown() {
        mock.shutdown();
    }

    @Test
    public void theSessionPathAnswersAToken() throws Exception {
        mvc.perform(post("/mock-abdm/api/hiecm/gateway/v3/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"x\",\"clientSecret\":\"y\",\"grantType\":\"client_credentials\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", startsWith("mock-")))
                .andExpect(jsonPath("$.expiresIn").value(1200));
    }

    @Test
    public void gatewayPathsAreDispatchedWithoutThePrefix() throws Exception {
        mvc.perform(patch("/mock-abdm" + AbdmPaths.OUT_BRIDGE_URL).header("Authorization", "Bearer mock-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://t.example\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/mock-abdm" + AbdmPaths.OUT_BRIDGE_SERVICES).header("Authorization", "Bearer mock-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("TANUH"));
        mvc.perform(post("/mock-abdm" + AbdmPaths.OUT_CONSENT_INIT).header("Authorization", "Bearer mock-1").header("REQUEST-ID", "r1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"consent\":{\"patient\":{\"id\":\"a@sbx\"}}}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/mock-abdm/api/hiecm/no/such/path").header("Authorization", "Bearer mock-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/mock-abdm/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bridgeUrl").value("https://t.example"))
                .andExpect(jsonPath("$.consentRequests[0].abhaAddress").value("a@sbx"));
    }

    @Test
    public void approvingAnUnknownConsentRequestIsA400() throws Exception {
        mvc.perform(post("/web/abdm/mock/consent-requests/nope/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ABDM_BAD_REQUEST"));
    }

    @Test
    public void aHipThatCannotBeReachedIsA502() throws Exception {
        mvc.perform(post("/web/abdm/mock/link/discover").contentType(MediaType.APPLICATION_JSON).content("{\"abhaAddress\":\"a@sbx\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ABDM_GATEWAY"));
    }
}
