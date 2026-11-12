package org.avni.server.web.abdm;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJobRuns;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.eka.EkaCallException;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// The admin page's Status tab answers even when record sharing is off, so the page can say what is missing.
public class AbdmAdminControllerTest {
    private static final Set<String> SHAPE = Set.of("abhaEnabled", "recordSharingEnabled", "recordSharingOn", "hiuOn", "environment",
            "hipIds", "hiuId", "sharingClientConfigured", "webhookUrl", "session", "jobs");

    private final EkaAbdmClient ekaClient = mock(EkaAbdmClient.class);
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final AbdmAccess access = mock(AbdmAccess.class);
    private final AbdmSettings settings = mock(AbdmSettings.class);
    private final AbdmJobRuns jobRuns = mock(AbdmJobRuns.class);
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private AbdmAdminController controller;

    @Before
    public void setUp() {
        controller = controller("https://avni.example.org/");
    }

    private AbdmAdminController controller(String publicBaseUrl) {
        return new AbdmAdminController(mock(AccessControlService.class), new AbdmConfig(publicBaseUrl), ekaClient,
                organisationConfigService, access, settings, jobRuns, orgContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void whenOffTheStatusStillAnswersWithItsShape_andEkaIsNotCalled() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(false);
        when(settings.find()).thenReturn(Optional.of(ekaSettings(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "production")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.WEBHOOK_ID, "webhook-1"))));

        Map<String, Object> status = controller.status();

        assertEquals(SHAPE, status.keySet());
        assertEquals(true, status.get("abhaEnabled"));
        assertEquals(false, status.get("recordSharingEnabled"));
        assertEquals(false, status.get("recordSharingOn"));
        assertEquals(false, status.get("hiuOn"));
        assertEquals("production", status.get("environment"));
        assertEquals(List.of("hip-1"), status.get("hipIds"));
        assertNull(status.get("hiuId"));
        assertEquals(false, status.get("sharingClientConfigured"));
        assertEquals("https://avni.example.org/api/v3/eka/webhook/webhook-1", status.get("webhookUrl"));
        Map<String, Object> session = (Map<String, Object>) status.get("session");
        assertEquals(false, session.get("ok"));
        assertNotNull(session.get("error"));
        verify(ekaClient, never()).checkToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void withoutEkaSettingsTheStatusSaysSo() {
        when(settings.find()).thenReturn(Optional.empty());

        Map<String, Object> status = controller.status();

        assertEquals(SHAPE, status.keySet());
        assertNull(status.get("environment"));
        assertEquals(List.of(), status.get("hipIds"));
        assertNull(status.get("hiuId"));
        assertEquals(false, status.get("sharingClientConfigured"));
        assertNull(status.get("webhookUrl"));
        assertEquals(false, ((Map<String, Object>) status.get("session")).get("ok"));
        verify(ekaClient, never()).checkToken();
    }

    @Test
    public void withoutAPublicAddressThereIsNoWebhookUrl() {
        when(settings.find()).thenReturn(Optional.of(ekaSettings(new JsonObject().with(EkaSystemConfig.WEBHOOK_ID, "webhook-1"))));

        assertNull(controller("").status().get("webhookUrl"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void whenOnTheStatusChecksTheSharingClientsSession() {
        on();

        Map<String, Object> status = controller.status();

        assertEquals(SHAPE, status.keySet());
        assertEquals(true, status.get("recordSharingOn"));
        assertEquals(true, status.get("hiuOn"));
        assertEquals("hiu-1", status.get("hiuId"));
        assertEquals(true, status.get("sharingClientConfigured"));
        assertEquals(true, ((Map<String, Object>) status.get("session")).get("ok"));
        verify(ekaClient).checkToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void withoutAnEkaTokenTheStatusSaysWhy() {
        on();
        doThrow(new EkaCallException(0, null, "Eka login failed: Eka login failed with HTTP 401")).when(ekaClient).checkToken();

        Map<String, Object> session = (Map<String, Object>) controller.status().get("session");

        assertEquals(false, session.get("ok"));
        assertTrue(((String) session.get("error")).contains("Eka login failed"));
    }

    // Each job's last success and last error, for the user's own organisation only.
    @Test
    @SuppressWarnings("unchecked")
    public void theStatusCarriesTheOrganisationsJobRuns() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(7L);
        Map<String, Object> linking = new java.util.LinkedHashMap<>();
        linking.put("lastSuccessAt", "2026-11-12T10:00:00.000Z");
        linking.put("lastError", null);
        when(jobRuns.of(7L)).thenReturn(Map.of("visitLinking", linking));

        Map<String, Map<String, Object>> jobs = (Map<String, Map<String, Object>>) controller.status().get("jobs");

        assertEquals(Map.of("visitLinking", linking), jobs);
    }

    @Test
    public void withNoOrganisationTheStatusHasNoJobs() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(null);

        assertEquals(Map.of(), controller.status().get("jobs"));
        verifyNoInteractions(jobRuns);
    }

    private void on() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(true);
        when(access.recordSharingOn()).thenReturn(true);
        when(access.hiuOn()).thenReturn(true);
        when(settings.find()).thenReturn(Optional.of(ekaSettings(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.HIU_ID, "hiu-1")
                .with(EkaSystemConfig.WEBHOOK_ID, "webhook-1")
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1"))));
    }

    private static EkaSystemConfig ekaSettings(JsonObject json) {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(json);
        return new EkaSystemConfig(row);
    }
}
