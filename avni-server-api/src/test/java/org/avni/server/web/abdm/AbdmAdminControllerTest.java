package org.avni.server.web.abdm;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.accessControl.PrivilegeType;
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
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// The admin page's Status tab answers even when record sharing is off, so the page can say what is missing.
public class AbdmAdminControllerTest {
    private static final Set<String> SHAPE = Set.of("abhaEnabled", "recordSharingEnabled", "recordSharingOn", "hiuOn", "environment",
            "hipIds", "hiuId", "sharingClientConfigured", "webhookUrl", "session", "jobs", "queue", "failedShares", "unansweredShares",
            "privileges");
    private static final List<PrivilegeType> STATUS_PRIVILEGES = List.of(PrivilegeType.EditOrganisationConfiguration,
            PrivilegeType.ShareAbdmRecords, PrivilegeType.ViewAbdmRecords);

    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final EkaAbdmClient ekaClient = mock(EkaAbdmClient.class);
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final AbdmAccess access = mock(AbdmAccess.class);
    private final AbdmSettings settings = mock(AbdmSettings.class);
    private final AbdmJobRuns jobRuns = mock(AbdmJobRuns.class);
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmHipStore hipStore = mock(AbdmHipStore.class);
    private AbdmAdminController controller;

    @Before
    public void setUp() {
        controller = controller("https://avni.example.org/");
    }

    private AbdmAdminController controller(String publicBaseUrl) {
        return new AbdmAdminController(accessControlService, new AbdmConfig(publicBaseUrl, 30), ekaClient,
                organisationConfigService, access, settings, jobRuns, orgContext, hipStore,
                new org.avni.server.service.abha.EkaEnvironments("sandbox,production"));
    }

    // F10: the page shows its HIP tab to a user who may share, and its HIU tab to one who may view.
    @Test
    @SuppressWarnings("unchecked")
    public void theStatusSaysWhichOfSharingAndViewingTheUserMayDo() {
        when(settings.find()).thenReturn(Optional.empty());
        doThrow(org.avni.server.domain.accessControl.AvniAccessException.createNoPrivilegeException(PrivilegeType.ViewAbdmRecords))
                .when(accessControlService).checkPrivilege(PrivilegeType.ViewAbdmRecords);

        Map<String, Object> status = controller.status();

        assertEquals(Map.of("share", true, "view", false), status.get("privileges"));
        verify(accessControlService).checkHasAnyOfSpecificPrivileges(STATUS_PRIVILEGES);

        reset(accessControlService);
        doThrow(org.avni.server.domain.accessControl.AvniAccessException.createNoPrivilegeException(PrivilegeType.ShareAbdmRecords))
                .when(accessControlService).checkPrivilege(PrivilegeType.ShareAbdmRecords);

        assertEquals(Map.of("share", false, "view", true), controller.status().get("privileges"));
    }

    // The status opens to a user who may edit the organisation's settings, share records or view them.
    @Test
    public void theStatusAsksForAnyOfTheSettingsShareAndViewPrivileges() {
        when(settings.find()).thenReturn(Optional.empty());

        controller.status();

        verify(accessControlService).checkHasAnyOfSpecificPrivileges(STATUS_PRIVILEGES);
        verify(accessControlService, never()).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
    }

    @Test
    public void withNoneOfTheThreePrivilegesThereIsNoStatus() {
        doThrow(org.avni.server.domain.accessControl.AvniAccessException.createNoPrivilegeException(STATUS_PRIVILEGES))
                .when(accessControlService).checkHasAnyOfSpecificPrivileges(STATUS_PRIVILEGES);

        assertThrows(org.avni.server.domain.accessControl.AvniAccessException.class, () -> controller.status());
        verifyNoInteractions(settings, ekaClient, hipStore);
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

    // F13: both switches on, but record sharing is not, for want of an Eka setting: the session's error names it, so the
    // administrator knows what to fill in; Eka is not asked.
    @Test
    @SuppressWarnings("unchecked")
    public void withBothSwitchesOnButAnEkaSettingMissingTheSessionNamesIt() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(true);
        when(settings.find()).thenReturn(Optional.of(ekaSettings(new JsonObject()
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1"))));

        Map<String, Object> session = (Map<String, Object>) controller.status().get("session");

        assertEquals(false, session.get("ok"));
        String error = (String) session.get("error");
        assertTrue(error, error.contains("a system user") && error.contains("a webhook signing key"));
        verify(ekaClient, never()).checkToken();
    }

    // F9: an organisation saved for Eka's sandbox on a server allowing production only: the session names the environment.
    @Test
    @SuppressWarnings("unchecked")
    public void aSandboxOrganisationOnAProductionOnlyServerHasItsEnvironmentNamed() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(true);
        when(settings.find()).thenReturn(Optional.of(ekaSettings(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1")
                .with(EkaSystemConfig.AVNI_SYSTEM_USER, "system-user-1")
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, "encrypted-key-1"))));
        AbdmAdminController productionOnly = new AbdmAdminController(mock(AccessControlService.class), new AbdmConfig("https://avni.example.org/", 30),
                ekaClient, organisationConfigService, access, settings, jobRuns, orgContext, hipStore,
                new org.avni.server.service.abha.EkaEnvironments("production"));

        Map<String, Object> session = (Map<String, Object>) productionOnly.status().get("session");

        assertEquals(false, session.get("ok"));
        assertEquals("Eka environment sandbox is not allowed on this server", session.get("error"));
        verify(ekaClient, never()).checkToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void withBothSwitchesOnAndNoEkaSettingsTheSessionSaysSo() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(true);
        when(settings.find()).thenReturn(Optional.empty());

        String error = (String) ((Map<String, Object>) controller.status().get("session")).get("error");

        assertTrue(error, error.contains("Eka settings"));
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
    public void withNoOrganisationTheStatusHasNoJobsAndNoQueue() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(null);

        Map<String, Object> status = controller.status();

        assertEquals(Map.of(), status.get("jobs"));
        assertEquals(Map.of(), status.get("queue"));
        assertEquals(0L, status.get("failedShares"));
        assertEquals(0L, status.get("unansweredShares"));
        verifyNoInteractions(jobRuns, hipStore);
    }

    // F6: shares that ended FAILED within the traffic retention (abdm.message-retention-days) are counted, so a share that
    // could not be made is seen; with record sharing off too, as nothing is asked of Eka.
    @Test
    public void theStatusCountsTheOrganisationsFailedSharesWithinTheRetention() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(7L);
        when(hipStore.failedSharesSince(any())).thenReturn(3L);
        java.time.Instant before = java.time.Instant.now();

        assertEquals(3L, controller.status().get("failedShares"));

        org.mockito.ArgumentCaptor<java.time.Instant> since = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        verify(hipStore).failedSharesSince(since.capture());
        java.time.Duration window = java.time.Duration.between(since.getValue(), before);
        assertTrue(window.toString(), window.compareTo(java.time.Duration.ofDays(30).minusMinutes(1)) > 0
                && window.compareTo(java.time.Duration.ofDays(30).plusMinutes(1)) < 0);
        verify(ekaClient, never()).checkToken();
    }

    // How many of the organisation's visits wait to be linked, are being linked, failed for now or for good, so a stuck
    // or given-up visit shows; with record sharing off too, as nothing is asked of Eka.
    @Test
    public void theStatusCountsTheOrganisationsLinkQueueByStatus() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(7L);
        when(hipStore.linkQueueCounts()).thenReturn(Map.of("QUEUED", 2L, "FAILED_FINAL", 1L));

        assertEquals(Map.of("QUEUED", 2L, "FAILED_FINAL", 1L), controller.status().get("queue"));
        verify(ekaClient, never()).checkToken();
    }

    // Fix round 2: shares Eka may have taken without saying so are counted beside the failed ones, over the same window.
    @Test
    public void theStatusCountsTheOrganisationsUnansweredSharesOverTheSameWindow() {
        when(settings.find()).thenReturn(Optional.empty());
        when(orgContext.organisationId()).thenReturn(7L);
        when(hipStore.failedSharesSince(any())).thenReturn(3L);
        when(hipStore.unansweredSharesSince(any())).thenReturn(2L);

        Map<String, Object> status = controller.status();

        assertEquals(2L, status.get("unansweredShares"));
        org.mockito.ArgumentCaptor<java.time.Instant> failedSince = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        org.mockito.ArgumentCaptor<java.time.Instant> unansweredSince = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        verify(hipStore).failedSharesSince(failedSince.capture());
        verify(hipStore).unansweredSharesSince(unansweredSince.capture());
        assertEquals(failedSince.getValue(), unansweredSince.getValue());
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
