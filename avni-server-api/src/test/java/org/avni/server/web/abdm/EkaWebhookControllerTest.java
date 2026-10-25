package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmAsync;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaHipEvents;
import org.avni.server.service.abdm.eka.EkaHiuEvents;
import org.avni.server.service.abdm.eka.EkaWebhookVerifier;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DEMO-ONLY abdm-hackathon: never merged.
public class EkaWebhookControllerTest {
    private static final long NOW = 1761370000L;
    private static final String KEY = "local-test-signing-key";

    private AbdmConfig cfg;
    private ExternalSystemConfigRepository configRepository;
    private EkaCredentialCipher cipher;
    private AbdmAsync async;
    private AbdmMessageLog messageLog;
    private EkaHipEvents hip;
    private EkaHiuEvents hiu;
    private ObjectProvider<EkaHiuEvents> hiuProvider;
    private MockMvc mvc;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        cfg = mock(AbdmConfig.class);
        when(cfg.getEkaWebhookVerify()).thenReturn("log");
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisationId()).thenReturn(1L);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));
        configRepository = mock(ExternalSystemConfigRepository.class);
        EkaSystemConfig ekaConfig = mock(EkaSystemConfig.class);
        when(ekaConfig.getEncryptedWebhookSigningKey()).thenReturn("encrypted-key");
        when(configRepository.getEkaSystemConfig(1L)).thenReturn(ekaConfig);
        cipher = mock(EkaCredentialCipher.class);
        when(cipher.decrypt("encrypted-key")).thenReturn(KEY);
        EkaWebhookVerifier verifier = new EkaWebhookVerifier(orgContext, configRepository, cipher,
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

        async = AbdmTestSupport.inlineAsync();
        messageLog = mock(AbdmMessageLog.class);
        hip = mock(EkaHipEvents.class);
        hiu = mock(EkaHiuEvents.class);
        ObjectProvider<EkaHipEvents> hipProvider = mock(ObjectProvider.class);
        hiuProvider = mock(ObjectProvider.class);
        when(hipProvider.getIfAvailable()).thenReturn(hip);
        when(hiuProvider.getIfAvailable()).thenReturn(hiu);
        mvc = MockMvcBuilders.standaloneSetup(new EkaWebhookController(cfg, verifier, async, messageLog, hipProvider, hiuProvider)).build();
    }

    static String event(String name) {
        try (InputStream in = EkaWebhookControllerTest.class.getResourceAsStream("/abdm/eka/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signature(long t, String body) {
        return "t=" + t + ",v1=" + EkaWebhookVerifier.sign(KEY, String.valueOf(t), body);
    }

    private ResultActions send(String body, String signature) throws Exception {
        var request = post(AbdmPaths.IN_EKA_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) request.header(EkaWebhookVerifier.HEADER, signature);
        return mvc.perform(request);
    }

    private static String renamed(String body, String eventName, String id) {
        ObjectNode event = (ObjectNode) AbdmJson.read(body);
        event.put("event", eventName);
        event.put("transaction_id", id);
        return AbdmJson.write(event);
    }

    @Test
    public void aSignedLinkEventIsAcceptedLoggedAndHandedToTheHip() throws Exception {
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isOk());

        ArgumentCaptor<JsonNode> logged = ArgumentCaptor.forClass(JsonNode.class);
        verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.link_care_context"), any(), logged.capture());
        assertEquals("enc-1", logged.getValue().at("/data/care_context_id").asText());
        ArgumentCaptor<JsonNode> handed = ArgumentCaptor.forClass(JsonNode.class);
        verify(hip).linkCareContext(handed.capture());
        assertEquals("wh-link-1", handed.getValue().get("transaction_id").asText());
        verify(hip, never()).hipDataFetch(any());
        verifyNoInteractions(hiu);
    }

    @Test
    public void eachEventGoesToItsHandler() throws Exception {
        String fetch = event("hip-data-fetch.json");
        String consent = event("consent-update.json");
        String push = event("hiu-data-push-webhook.json");

        send(fetch, signature(NOW, fetch)).andExpect(status().isOk());
        send(consent, signature(NOW, consent)).andExpect(status().isOk());
        send(push, signature(NOW, push)).andExpect(status().isOk());

        verify(hip).hipDataFetch(any());
        verify(hiu).consentUpdate(any());
        verify(hiu).hiuDataPush(any());
        verify(hip, never()).linkCareContext(any());
    }

    @Test
    public void closeVariantsOfTheEventNamesAreRoutedToo() throws Exception {
        String link = event("link-care-context.json");
        String fetch = event("hip-data-fetch.json");

        send(renamed(link, "LINK_CARE_CONTEXT", "v-1"), null).andExpect(status().isOk());
        send(renamed(link, "abha.care_context_link", "v-2"), null).andExpect(status().isOk());
        send(renamed(fetch, "abdm.hip-data-fetch", "v-3"), null).andExpect(status().isOk());
        send(renamed(fetch, "abha.consentUpdate", "v-4"), null).andExpect(status().isOk());

        verify(hip, times(2)).linkCareContext(any());
        verify(hip).hipDataFetch(any());
        verify(hiu).consentUpdate(any());
    }

    @Test
    public void anEventWithoutAHandlerIsLoggedAndIgnored() throws Exception {
        String body = renamed(event("link-care-context.json"), "abha.created", "created-1");

        send(body, signature(NOW, body)).andExpect(status().isOk());

        verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.created"), any(), any());
        verifyNoInteractions(hip, hiu);
    }

    @Test
    public void theDiscoveryConfirmEventIsNotTakenForALinkStatus() throws Exception {
        String body = renamed(event("link-care-context.json"), "abha.context_discover_link_confirm", "confirm-1");

        send(body, null).andExpect(status().isOk());

        verifyNoInteractions(hip, hiu);
    }

    @Test
    public void inLogModeABadSignatureIsStillAcceptedAndHandled() throws Exception {
        String body = event("link-care-context.json");

        send(body, "t=" + NOW + ",v1=deadbeef").andExpect(status().isOk());

        verify(hip).linkCareContext(any());
    }

    @Test
    public void inEnforceModeABadSignatureIsRefusedAndNothingIsHandled() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");
        String body = event("link-care-context.json");

        send(body, "t=" + NOW + ",v1=deadbeef").andExpect(status().isUnauthorized());

        verifyNoInteractions(async, messageLog, hip, hiu);
    }

    @Test
    public void inEnforceModeAMissingSignatureIsRefused() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");

        send(event("link-care-context.json"), null).andExpect(status().isUnauthorized());

        verifyNoInteractions(hip);
    }

    @Test
    public void inEnforceModeAStaleTimestampIsRefusedEvenWithTheRightKey() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");
        String body = event("link-care-context.json");

        send(body, signature(NOW - 181, body)).andExpect(status().isUnauthorized());
        send(body, signature(NOW + 181, body)).andExpect(status().isUnauthorized());

        verifyNoInteractions(hip);
    }

    @Test
    public void inEnforceModeAChangedBodyIsRefused() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");
        String body = event("link-care-context.json");

        send(body.replace("LINKED", "ERRORED"), signature(NOW, body)).andExpect(status().isUnauthorized());

        verifyNoInteractions(hip);
    }

    @Test
    public void inEnforceModeAValidSignatureWithinThreeMinutesIsAccepted() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");
        String body = event("link-care-context.json");

        send(body, signature(NOW - 179, body)).andExpect(status().isOk());

        verify(hip).linkCareContext(any());
    }

    @Test
    public void inEnforceModeNoSigningKeyMeansRefused() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("enforce");
        EkaSystemConfig noKey = mock(EkaSystemConfig.class);
        when(configRepository.getEkaSystemConfig(1L)).thenReturn(noKey);
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isUnauthorized());

        verifyNoInteractions(hip);
    }

    @Test
    public void offModeSkipsTheCheckEntirely() throws Exception {
        when(cfg.getEkaWebhookVerify()).thenReturn("off");

        send(event("link-care-context.json"), null).andExpect(status().isOk());

        verify(hip).linkCareContext(any());
        verifyNoInteractions(configRepository, cipher);
    }

    @Test
    public void aRepeatedEventIdIsHandledOnce() throws Exception {
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isOk());
        send(body, signature(NOW, body)).andExpect(status().isOk());

        verify(hip, times(1)).linkCareContext(any());
        verify(messageLog, times(1)).incoming(any(), any(), any());
    }

    @Test
    public void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        send("not json", null).andExpect(status().isBadRequest());

        verifyNoInteractions(hip, hiu, async);
    }

    @Test
    public void withoutAnHiuHandlerAConsentUpdateIsOnlyLogged() throws Exception {
        when(hiuProvider.getIfAvailable()).thenReturn(null);
        String body = event("consent-update.json");

        send(body, signature(NOW, body)).andExpect(status().isOk());

        verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.consent_update"), any(), any());
        verifyNoInteractions(hiu);
    }

    @Test
    public void theSignatureCheckReadsTheHeaderTolerantly() {
        String body = "{\"a\":1}";
        String v1 = EkaWebhookVerifier.sign(KEY, String.valueOf(NOW), body);

        assertTrue(EkaWebhookVerifier.check(" t=" + NOW + " , v1=" + v1.toUpperCase() + " ", body, KEY, NOW).valid());
        assertTrue(EkaWebhookVerifier.check("v1=other,t=" + NOW + ",v1=" + v1, body, KEY, NOW).valid());
        assertFalse(EkaWebhookVerifier.check("t=" + NOW, body, KEY, NOW).valid());
        assertFalse(EkaWebhookVerifier.check("t=abc,v1=" + v1, body, KEY, NOW).valid());
        assertFalse(EkaWebhookVerifier.check("t=" + NOW + ",v1=" + v1, body, "another-key", NOW).valid());
    }

    @Test
    public void theSignatureIsTheHexHmacOfTimestampDotBody() {
        // openssl: printf '%s' '1761370000.{"a":1}' | openssl dgst -sha256 -hmac 'local-test-signing-key'
        assertEquals("d9630c36f83ef54ff692e59f69762969ae02faa933c018f3ed446eb68ed7750c",
                EkaWebhookVerifier.sign(KEY, String.valueOf(NOW), "{\"a\":1}"));
    }
}
