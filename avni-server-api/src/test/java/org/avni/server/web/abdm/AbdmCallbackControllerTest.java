package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.*;
import org.avni.server.service.abdm.gateway.AbdmCallbackVerifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AbdmCallbackControllerTest {
    private AbdmCallbackVerifier verifier;
    private AbdmAsync async;
    private AbdmMessageLog messageLog;
    private AbdmHipCallbacks hip;
    private AbdmHiuCallbacks hiu;
    private ObjectProvider<AbdmHipCallbacks> hipProvider;
    private ObjectProvider<AbdmHiuCallbacks> hiuProvider;
    private MockMvc mvc;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        verifier = mock(AbdmCallbackVerifier.class);
        async = mock(AbdmAsync.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(async).submit(anyString(), any(Runnable.class));
        messageLog = mock(AbdmMessageLog.class);
        hip = mock(AbdmHipCallbacks.class);
        hiu = mock(AbdmHiuCallbacks.class);
        hipProvider = mock(ObjectProvider.class);
        hiuProvider = mock(ObjectProvider.class);
        when(hipProvider.getIfAvailable()).thenReturn(hip);
        when(hiuProvider.getIfAvailable()).thenReturn(hiu);
        mvc = MockMvcBuilders.standaloneSetup(new AbdmCallbackController(verifier, async, messageLog, hipProvider, hiuProvider)).build();
    }

    private static Map<String, Consumer<Handlers>> expectations() {
        Map<String, Consumer<Handlers>> e = new LinkedHashMap<>();
        e.put(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN, h -> verify(h.hip).onGenerateToken(any(), any()));
        e.put(AbdmPaths.IN_HIP_ON_LINK_CARE_CONTEXT, h -> verify(h.hip).onLinkCareContext(any(), any()));
        e.put(AbdmPaths.IN_HIP_DISCOVER, h -> verify(h.hip).discover(any(), any()));
        e.put(AbdmPaths.IN_HIP_LINK_INIT, h -> verify(h.hip).linkInit(any(), any()));
        e.put(AbdmPaths.IN_HIP_LINK_CONFIRM, h -> verify(h.hip).linkConfirm(any(), any()));
        e.put(AbdmPaths.IN_HIP_CONSENT_NOTIFY, h -> verify(h.hip).consentNotify(any(), any()));
        e.put(AbdmPaths.IN_HIP_HEALTH_INFORMATION_REQUEST, h -> verify(h.hip).healthInformationRequest(any(), any()));
        e.put(AbdmPaths.IN_HIU_CONSENT_ON_INIT, h -> verify(h.hiu).consentRequestOnInit(any(), any()));
        e.put(AbdmPaths.IN_HIU_CONSENT_NOTIFY, h -> verify(h.hiu).consentRequestNotify(any(), any()));
        e.put(AbdmPaths.IN_HIU_CONSENT_ON_STATUS, h -> verify(h.hiu).consentRequestOnStatus(any(), any()));
        e.put(AbdmPaths.IN_HIU_CONSENT_ON_FETCH, h -> verify(h.hiu).consentOnFetch(any(), any()));
        e.put(AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST, h -> verify(h.hiu).healthInformationOnRequest(any(), any()));
        e.put(AbdmPaths.IN_HIU_DATA_PUSH, h -> verify(h.hiu).dataPush(any(), any()));
        return e;
    }

    private record Handlers(AbdmHipCallbacks hip, AbdmHiuCallbacks hiu) {
    }

    @Test
    public void everyCallbackPathAnswers202AndCallsItsHandlerOnce() throws Exception {
        Map<String, Consumer<Handlers>> expectations = expectations();
        assertEquals(13, expectations.size());
        for (Map.Entry<String, Consumer<Handlers>> expectation : expectations.entrySet()) {
            clearInvocations(hip, hiu, messageLog);
            String path = expectation.getKey();

            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .header("REQUEST-ID", "req-" + path.hashCode())
                            .header("TIMESTAMP", "2026-10-26T04:00:00.000Z")
                            .header("X-CM-ID", "sbx")
                            .content("{\"response\":{\"requestId\":\"ours-1\"}}"))
                    .andExpect(status().isAccepted());

            expectation.getValue().accept(new Handlers(hip, hiu));
            verifyNoMoreInteractions(hip, hiu);
            verify(messageLog).incoming(eq(path), any(AbdmCallbackHeaders.class), any(JsonNode.class));
        }
    }

    @Test
    public void handlerGetsTheBodyAndTheHeaders() throws Exception {
        mvc.perform(post(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .header("REQUEST-ID", "gw-req-1")
                        .header("TIMESTAMP", "2026-10-26T04:00:00.000Z")
                        .header("X-CM-ID", "sbx")
                        .header("X-HIP-ID", "TANUH")
                        .header("Authorization", "Bearer gw-jwt")
                        .content("{\"abhaAddress\":\"asha@sbx\",\"linkToken\":\"lt\",\"response\":{\"requestId\":\"ours-1\"}}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<AbdmCallbackHeaders> headers = ArgumentCaptor.forClass(AbdmCallbackHeaders.class);
        verify(hip).onGenerateToken(body.capture(), headers.capture());
        assertEquals("lt", body.getValue().get("linkToken").asText());
        assertEquals("gw-req-1", headers.getValue().requestId());
        assertEquals("TANUH", headers.getValue().hipId());
        assertEquals("Bearer gw-jwt", headers.getValue().authorization());
        assertEquals(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN, headers.getValue().path());
        verify(verifier).check(headers.getValue());
    }

    @Test
    public void aRejectedCallbackAnswers401AndRunsNothing() throws Exception {
        doThrow(new AbdmCallbackVerifier.CallbackRejected("no")).when(verifier).check(any());

        mvc.perform(post(AbdmPaths.IN_HIU_CONSENT_NOTIFY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hiu, messageLog);
        verify(async, never()).submit(anyString(), any());
    }

    @Test
    public void theDataPushIsNotCheckedForAGatewaySignature() throws Exception {
        doThrow(new AbdmCallbackVerifier.CallbackRejected("no")).when(verifier).check(any());

        mvc.perform(post(AbdmPaths.IN_HIU_DATA_PUSH).contentType(MediaType.APPLICATION_JSON).content("{\"transactionId\":\"t1\"}"))
                .andExpect(status().isAccepted());

        verify(hiu).dataPush(any(), any());
        verify(verifier, never()).check(any());
    }

    @Test
    public void anAbsentHandlerIsSkippedButTheMessageIsStillLogged() throws Exception {
        when(hipProvider.getIfAvailable()).thenReturn(null);

        mvc.perform(post(AbdmPaths.IN_HIP_CONSENT_NOTIFY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());

        verify(messageLog).incoming(eq(AbdmPaths.IN_HIP_CONSENT_NOTIFY), any(), any());
    }
}
