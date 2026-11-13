package org.avni.server.web.abdm;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaEventGate;
import org.avni.server.service.abdm.eka.EkaEventWorker;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.avni.server.service.abdm.eka.EkaWebhookVerifier;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
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
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Eka posts each organisation's events to its own address, /api/v3/eka/webhook/{webhookId}. The address names the
// organisation, that organisation's key checks the signature, its record sharing must be on, and the event is stored
// (AbdmInbox) before Eka is answered 200. Handling is EkaEventWorker's, tested on its own.
public class EkaWebhookControllerTest {
    private static final long NOW = 1761370000L;
    private static final String KEY = "local-test-signing-key";
    private static final String OTHER_KEY = "another-organisations-key";
    private static final String WEBHOOK = "webhook-1";
    private static final long ORG = 1L;

    private ExternalSystemConfigRepository configRepository;
    private EkaCredentialCipher cipher;
    private AbdmAccess access;
    private AbdmHiuStore hiuStore;
    private AbdmInbox inbox;
    private EkaEventWorker worker;
    private Bugsnag bugsnag;
    private MockMvc mvc;

    @Before
    public void setUp() {
        configRepository = mock(ExternalSystemConfigRepository.class);
        cipher = mock(EkaCredentialCipher.class);
        when(cipher.decrypt("encrypted-key-1")).thenReturn(KEY);
        when(cipher.decrypt("encrypted-key-2")).thenReturn(OTHER_KEY);
        when(configRepository.findEkaSystemConfigByWebhookId(anyString())).thenReturn(Optional.empty());
        organisation(WEBHOOK, ORG, "encrypted-key-1");
        organisation("webhook-2", 2L, "encrypted-key-2");
        access = mock(AbdmAccess.class);
        when(access.recordSharingOn(anyLong())).thenReturn(true);
        hiuStore = mock(AbdmHiuStore.class);
        inbox = mock(AbdmInbox.class);
        when(inbox.receive(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any())).thenReturn(true);
        worker = mock(EkaEventWorker.class);
        bugsnag = mock(Bugsnag.class);
        EkaWebhookVerifier verifier = new EkaWebhookVerifier(cipher, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new EkaWebhookController(configRepository, verifier, new EkaEventGate(access, hiuStore), inbox,
                worker, bugsnag)).build();
    }

    private void organisation(String webhookId, long organisationId, String encryptedKey) {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setOrganisationId(organisationId);
        row.setConfig(new JsonObject().with(EkaSystemConfig.WEBHOOK_ID, webhookId)
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, encryptedKey));
        when(configRepository.findEkaSystemConfigByWebhookId(webhookId)).thenReturn(Optional.of(new EkaSystemConfig(row)));
    }

    static String event(String name) {
        try (InputStream in = EkaWebhookControllerTest.class.getResourceAsStream("/abdm/eka/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signature(long t, String body) {
        return signature(KEY, t, body);
    }

    private static String signature(String key, long t, String body) {
        return "t=" + t + ",v1=" + EkaWebhookVerifier.sign(key, String.valueOf(t), body);
    }

    private ResultActions send(String webhookId, String body, String signature) throws Exception {
        var request = post(AbdmPaths.IN_EKA_WEBHOOK + "/" + webhookId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) request.header(EkaWebhookVerifier.HEADER, signature);
        return mvc.perform(request);
    }

    private ResultActions send(String body, String signature) throws Exception {
        return send(WEBHOOK, body, signature);
    }

    private ResultActions sendSigned(String body) throws Exception {
        return send(body, signature(NOW, body));
    }

    private static String renamed(String body, String eventName, String id) {
        ObjectNode event = (ObjectNode) AbdmJson.read(body);
        event.put("event", eventName);
        if (id == null) event.remove("transaction_id");
        else event.put("transaction_id", id);
        return AbdmJson.write(event);
    }

    @Test
    public void aSignedEventIsStoredForItsOrganisationAsReceived_thenAccepted_thenTheWorkerIsWoken() throws Exception {
        String body = event("link-care-context.json");

        sendSigned(body).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"accepted\"}"));

        InOrder order = inOrder(inbox, worker);
        order.verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-link-1"), eq("abha.link_care_context"), eq("LINK_CARE_CONTEXT"),
                isNull(), eq(body), any());
        order.verify(worker).wake();
    }

    // The 200 tells Eka the event is ours; an event that could not be stored is not acknowledged, so Eka sends it again.
    @Test
    public void anEventThatCannotBeStoredIsNotAcknowledged() throws Exception {
        when(inbox.receive(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("database down"));

        sendSigned(event("link-care-context.json")).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"not stored\"}", true));

        verifyNoInteractions(worker);
        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    @Test
    public void anUnknownWebhookIdIs404_andNothingIsReadOrStored() throws Exception {
        String body = event("link-care-context.json");

        send("no-such-webhook", body, signature(NOW, body)).andExpect(status().isNotFound());

        verifyNoInteractions(cipher, access, inbox, worker);
    }

    @Test
    public void aBadSignatureIs401WithNoInternalDetail_andNothingIsStored() throws Exception {
        String body = event("link-care-context.json");

        send(body, "t=" + NOW + ",v1=deadbeef").andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":\"rejected\"}", true));

        verifyNoInteractions(inbox, worker);
    }

    // The key is the organisation's that the address names, never another's.
    @Test
    public void anEventSignedWithAnotherOrganisationsKeyIsRefused() throws Exception {
        String body = event("link-care-context.json");

        send("webhook-2", body, signature(KEY, NOW, body)).andExpect(status().isUnauthorized());
        send("webhook-2", body, signature(OTHER_KEY, NOW, body)).andExpect(status().isOk());

        verify(inbox).receive(eq(2L), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
        verify(inbox, never()).receive(eq(ORG), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    public void anOrganisationWithRecordSharingOffIs404_evenWithAValidSignature() throws Exception {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isNotFound());

        verifyNoInteractions(inbox, worker);
    }

    // A patient's withdrawal is honoured, and what was received under the consent erased, after record sharing is off.
    @Test
    public void withRecordSharingOffAWithdrawalIsStillTaken_forAnOrganisationHoldingConsentsOrRecords() throws Exception {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        when(hiuStore.holdsConsentsOrRecords(ORG)).thenReturn(true);
        for (String status : new String[]{"REVOKED", "DENIED", "EXPIRED"}) {
            String update = withStatus(event("consent-update.json"), status, "wh-" + status);

            sendSigned(update).andExpect(status().isOk());

            verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-" + status), eq("abha.consent_update"), eq("CONSENT_UPDATE"),
                    eq("consent-request-1"), eq(update), any());
        }
    }

    @Test
    public void withRecordSharingOffEveryOtherEventIsRefused() throws Exception {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        when(hiuStore.holdsConsentsOrRecords(ORG)).thenReturn(true);

        sendSigned(event("consent-update.json")).andExpect(status().isNotFound());
        sendSigned(event("hiu-data-push-webhook.json")).andExpect(status().isNotFound());
        sendSigned(event("hip-data-fetch.json")).andExpect(status().isNotFound());
        sendSigned(event("link-care-context.json")).andExpect(status().isNotFound());

        verifyNoInteractions(inbox, worker);
    }

    @Test
    public void withRecordSharingOffAWithdrawalForAnOrganisationHoldingNothingIsRefused() throws Exception {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        when(hiuStore.holdsConsentsOrRecords(ORG)).thenReturn(false);

        sendSigned(withStatus(event("consent-update.json"), "REVOKED", "wh-revoked")).andExpect(status().isNotFound());

        verifyNoInteractions(inbox, worker);
    }

    // The signature comes first, with sharing on or off.
    @Test
    public void withRecordSharingOffABadlySignedWithdrawalIs401() throws Exception {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        when(hiuStore.holdsConsentsOrRecords(ORG)).thenReturn(true);
        String update = withStatus(event("consent-update.json"), "REVOKED", "wh-revoked");

        send(update, signature(OTHER_KEY, NOW, update)).andExpect(status().isUnauthorized());

        verifyNoInteractions(inbox, worker, hiuStore);
    }

    private static String withStatus(String update, String status, String id) {
        ObjectNode event = (ObjectNode) AbdmJson.read(update);
        ((ObjectNode) event.at("/data")).put("status", status);
        ((ObjectNode) event.at("/data/notification")).put("status", status);
        event.put("transaction_id", id);
        return AbdmJson.write(event);
    }

    @Test
    public void aMissingSignatureIsRefused() throws Exception {
        send(event("link-care-context.json"), null).andExpect(status().isUnauthorized());

        verifyNoInteractions(inbox);
    }

    @Test
    public void aStaleTimestampIsRefusedEvenWithTheRightKey() throws Exception {
        String body = event("link-care-context.json");

        send(body, signature(NOW - 181, body)).andExpect(status().isUnauthorized());
        send(body, signature(NOW + 181, body)).andExpect(status().isUnauthorized());

        verifyNoInteractions(inbox, bugsnag);
    }

    // F8: Eka's own signature outside the time allowed, again and again, points at a clock rather than a replay: still
    // refused, and reported, once per run of three. Another organisation's refusals are counted apart.
    @Test
    public void staleSignaturesStayRefused_andAreReportedWhenTheyRepeat() throws Exception {
        String body = event("link-care-context.json");
        send("webhook-2", body, signature(OTHER_KEY, NOW - 600, body)).andExpect(status().isUnauthorized());
        send("webhook-2", body, signature(OTHER_KEY, NOW - 600, body)).andExpect(status().isUnauthorized());
        send(body, signature(NOW - 600, body)).andExpect(status().isUnauthorized());
        send(body, signature(NOW - 600, body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(bugsnag);

        send(body, signature(NOW - 600, body)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":\"rejected\"}", true));

        verify(bugsnag, times(1)).notify(any(IllegalStateException.class));
        verifyNoInteractions(inbox);
    }

    // F8: a bad signature is not reported however often it comes: anyone can send one.
    @Test
    public void badSignaturesAreNotReportedHoweverOftenTheyCome() throws Exception {
        String body = event("link-care-context.json");
        for (int i = 0; i < 5; i++) send(body, "t=" + NOW + ",v1=deadbeef").andExpect(status().isUnauthorized());

        verifyNoInteractions(bugsnag, inbox);
    }

    @Test
    public void aChangedBodyIsRefused() throws Exception {
        String body = event("link-care-context.json");

        send(body.replace("LINKED", "ERRORED"), signature(NOW, body)).andExpect(status().isUnauthorized());

        verifyNoInteractions(inbox);
    }

    @Test
    public void aValidSignatureWithinThreeMinutesIsAccepted() throws Exception {
        String body = event("link-care-context.json");

        send(body, signature(NOW - 179, body)).andExpect(status().isOk());

        verify(inbox).receive(eq(ORG), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
    }

    // F8: Avni cannot check the signature without the organisation's key: Avni's fault, not Eka's, so 500 (Eka sends it
    // again) and an alarm, never a quiet 401. Nothing is stored and nothing internal is said.
    @Test
    public void noSigningKeyIs500AndAlarms_andNothingIsStored() throws Exception {
        organisation(WEBHOOK, ORG, null);
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"not checked\"}", true));

        verify(bugsnag).notify(any(IllegalStateException.class));
        verifyNoInteractions(inbox, worker);
    }

    @Test
    public void anUnreadableSigningKeyIs500AndAlarms_notA401() throws Exception {
        when(cipher.decrypt("encrypted-key-1")).thenThrow(new IllegalStateException("the deployment's key changed"));
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":\"not checked\"}", true));

        verify(bugsnag).notify(any(IllegalStateException.class));
        verifyNoInteractions(inbox, worker);
    }

    // Fix round 1: every delivery Eka retries would alarm again; the first one alarms, then at most one per window for
    // the organisation. Each delivery still answers 500, and another organisation alarms on its own.
    @Test
    public void anUnusableSigningKeyAlarmsOnce_thenAtMostOncePerWindowForTheOrganisation() throws Exception {
        when(cipher.decrypt("encrypted-key-1")).thenThrow(new IllegalStateException("the deployment's key changed"));
        when(cipher.decrypt("encrypted-key-2")).thenThrow(new IllegalStateException("the deployment's key changed"));
        String body = event("link-care-context.json");

        for (int i = 0; i < 3; i++) send(body, signature(NOW, body)).andExpect(status().isInternalServerError());
        send("webhook-2", body, signature(OTHER_KEY, NOW, body)).andExpect(status().isInternalServerError());

        verify(bugsnag, times(2)).notify(any(IllegalStateException.class));
        verifyNoInteractions(inbox, worker);
    }

    @Test
    public void anEmptySigningKeyIs500AndAlarms() throws Exception {
        when(cipher.decrypt("encrypted-key-1")).thenReturn("");
        String body = event("link-care-context.json");

        send(body, signature(NOW, body)).andExpect(status().isInternalServerError());

        verify(bugsnag).notify(any(IllegalStateException.class));
        verifyNoInteractions(inbox);
    }

    // H3: no setting turns the check off; an unsigned event of every kind is refused and nothing is stored.
    @Test
    public void anUnsignedEventIsAlwaysRefused() throws Exception {
        for (String name : new String[]{"link-care-context.json", "hip-data-fetch.json", "consent-update.json", "hiu-data-push-webhook.json"}) {
            send(event(name), null).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(inbox, worker);
    }

    // Eka may deliver an event again; the inbox keeps one row per event id and organisation, and Eka is answered 200 again.
    @Test
    public void aRepeatedEventIsAcceptedAgain() throws Exception {
        String body = event("link-care-context.json");
        when(inbox.receive(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any())).thenReturn(true, false);

        sendSigned(body).andExpect(status().isOk());
        sendSigned(body).andExpect(status().isOk());

        verify(inbox, times(2)).receive(eq(ORG), eq("eka"), eq("wh-link-1"), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    public void anEventWithoutAnIdIsKeyedByItsBody_soARedeliveryIsStillOneEvent() throws Exception {
        String body = renamed(event("link-care-context.json"), "abha.link_care_context", null);

        sendSigned(body).andExpect(status().isOk());
        sendSigned(body).andExpect(status().isOk());

        verify(inbox, times(2)).receive(eq(ORG), eq("eka"), eq("sha256:" + EkaWebhookController.sha256(body)), anyString(), anyString(),
                any(), anyString(), any());
    }

    // A consent's events are handled in the order they came, so they carry the consent they are about.
    @Test
    public void consentUpdatesAndDataPushesAreStoredWithTheConsentTheyConcern() throws Exception {
        String update = event("consent-update.json");
        String push = event("hiu-data-push-webhook.json");

        sendSigned(update).andExpect(status().isOk());
        sendSigned(push).andExpect(status().isOk());

        verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-consent-1"), eq("abha.consent_update"), eq("CONSENT_UPDATE"),
                eq("consent-request-1"), eq(update), any());
        verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-push-1"), eq("abha.hiu_data_push"), eq("HIU_DATA_PUSH"),
                eq("artefact-1"), eq(push), any());
    }

    // A withdrawal naming only its artefacts is stored under the consent of the grant still waiting that names them, so
    // the inbox handles it after that grant and it is never dropped for want of the artefacts the grant makes.
    @Test
    public void aWithdrawalNamingOnlyArtefactsIsStoredUnderTheConsentOfItsWaitingGrant() throws Exception {
        ObjectNode withdrawal = (ObjectNode) AbdmJson.read(event("consent-update.json"));
        ObjectNode notification = (ObjectNode) withdrawal.at("/data/notification");
        notification.remove("consentRequestId");
        notification.put("status", "REVOKED");
        ((ObjectNode) withdrawal.get("data")).put("status", "REVOKED");
        withdrawal.put("transaction_id", "wh-revoke-1");
        String body = AbdmJson.write(withdrawal);
        when(inbox.pendingConsentKeyOf(ORG, List.of("artefact-1"))).thenReturn(Optional.of("consent-request-1"));

        sendSigned(body).andExpect(status().isOk());

        verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-revoke-1"), eq("abha.consent_update"), eq("CONSENT_UPDATE"),
                eq("consent-request-1"), eq(body), any());
    }

    @Test
    public void aWithdrawalWhoseGrantIsNotWaitingIsStoredWithNoConsent() throws Exception {
        ObjectNode withdrawal = (ObjectNode) AbdmJson.read(event("consent-update.json"));
        ((ObjectNode) withdrawal.at("/data/notification")).remove("consentRequestId");
        String body = AbdmJson.write(withdrawal);

        sendSigned(body).andExpect(status().isOk());

        verify(inbox).receive(eq(ORG), eq("eka"), eq("wh-consent-1"), eq("abha.consent_update"), eq("CONSENT_UPDATE"),
                isNull(), eq(body), any());
    }

    @Test
    public void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        sendSigned("not json").andExpect(status().isBadRequest());

        verifyNoInteractions(inbox, worker);
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

    @Test
    public void theEventsAreRoutedByName_closeVariantsIncluded() {
        assertEquals(List.of("LINK_CARE_CONTEXT", "LINK_CARE_CONTEXT", "HIP_DATA_FETCH", "CONSENT_UPDATE", "HIU_DATA_PUSH", "NONE"),
                List.of("abha.link_care_context", "LINK_CARE_CONTEXT", "abdm.hip-data-fetch", "abha.consentUpdate", "abha.hiu_data_push",
                        "abha.created").stream().map(name -> org.avni.server.service.abdm.eka.EkaEventRoute.of(name).name()).toList());
    }
}
