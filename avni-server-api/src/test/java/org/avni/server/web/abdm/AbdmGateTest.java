package org.avni.server.web.abdm;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hiu.AbdmHiuConsentService;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every /web/abdm endpoint but the status answers 404 for an organisation whose record sharing is off; the HIU's
// endpoints also when it has no HIU ID. The privilege is checked before the gate.
public class AbdmGateTest {
    private static final long ORG = 3L;
    private static final String CONSENT = "{\"abhaAddress\":\"a.patient\",\"requesterName\":\"A Doctor\",\"requesterRegistrationNumber\":\"REG-1\"}";

    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final ExternalSystemConfigRepository configRepository = mock(ExternalSystemConfigRepository.class);
    private final AbdmHipLinkingService linking = mock(AbdmHipLinkingService.class);
    private final EkaHipService ekaHip = mock(EkaHipService.class);
    private final EkaHiuService ekaHiu = mock(EkaHiuService.class);
    private final AbdmHiuConsentService consents = mock(AbdmHiuConsentService.class);
    private final AbdmHiuDataService data = mock(AbdmHiuDataService.class);
    private AbdmHipController hipController;
    private AbdmHiuController hiuController;
    private MockMvc mvc;

    @Before
    public void setUp() {
        UserContext context = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(ORG);
        context.setOrganisation(organisation);
        UserContextHolder.create(context);
        AbdmAccess access = new AbdmAccess(organisationConfigService, configRepository, new org.avni.server.service.abha.EkaEnvironments("sandbox,production"));
        hipController = new AbdmHipController(linking, ekaHip, accessControlService, access);
        hiuController = new AbdmHiuController(ekaHiu, consents, data, accessControlService, access);
        mvc = MockMvcBuilders.standaloneSetup(hipController, hiuController).build();
        when(linking.patientsWithAbha()).thenReturn(List.of());
        when(linking.careContexts(anyString())).thenReturn(List.of());
        when(ekaHip.linkCareContexts(anyString())).thenReturn(List.of());
        when(ekaHiu.requestConsent(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Map.of("uuid", "u-1", "status", "REQUESTED"));
        when(consents.list()).thenReturn(List.of());
        when(data.records(anyString())).thenReturn(List.of());
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private static final List<RequestBuilder> HIP = List.of(
            get("/web/abdm/hip/patients"),
            get("/web/abdm/hip/subjects/s-1/care-contexts"),
            post("/web/abdm/hip/subjects/s-1/link"),
            post("/web/abdm/hip/requeue-failed"));

    private static List<RequestBuilder> hiu() {
        return List.of(
                post("/web/abdm/hiu/consent-requests").contentType(MediaType.APPLICATION_JSON).content(CONSENT),
                get("/web/abdm/hiu/consent-requests"),
                get("/web/abdm/hiu/consent-requests/c-1/records"));
    }

    @Test
    public void withRecordSharingSwitchedOffEveryEndpointIs404_andNothingIsDone() throws Exception {
        settings(true, false, full());

        for (RequestBuilder request : HIP) mvc.perform(request).andExpect(status().isNotFound());
        for (RequestBuilder request : hiu()) mvc.perform(request).andExpect(status().isNotFound());

        verifyNoInteractions(linking, ekaHip, ekaHiu, consents, data);
    }

    @Test
    public void withAbhaOffEveryEndpointIs404() throws Exception {
        settings(false, true, full());

        for (RequestBuilder request : HIP) mvc.perform(request).andExpect(status().isNotFound());
        for (RequestBuilder request : hiu()) mvc.perform(request).andExpect(status().isNotFound());

        verifyNoInteractions(linking, ekaHip, ekaHiu, consents, data);
    }

    @Test
    public void withoutASharingClientEveryEndpointIs404() throws Exception {
        settings(true, true, full().with(EkaSystemConfig.SHARING_CLIENT_ID, null));

        for (RequestBuilder request : HIP) mvc.perform(request).andExpect(status().isNotFound());
        for (RequestBuilder request : hiu()) mvc.perform(request).andExpect(status().isNotFound());
    }

    @Test
    public void whenOnEveryEndpointWorks() throws Exception {
        settings(true, true, full());

        for (RequestBuilder request : HIP) mvc.perform(request).andExpect(status().isOk());
        for (RequestBuilder request : hiu()) mvc.perform(request).andExpect(status().isOk());

        verify(linking).patientsWithAbha();
        verify(linking).careContexts("s-1");
        verify(ekaHip).linkCareContexts("s-1");
        verify(linking).requeueGivenUp();
        verify(ekaHiu).requestConsent(any(), any(), any(), any(), any(), any(), any(), any());
        verify(consents).list();
        verify(data).records("c-1");
    }

    @Test
    public void withoutAnHiuIdTheHipEndpointsWorkAndTheHiuOnesAre404() throws Exception {
        settings(true, true, full().with(EkaSystemConfig.HIU_ID, null));

        for (RequestBuilder request : HIP) mvc.perform(request).andExpect(status().isOk());
        for (RequestBuilder request : hiu()) mvc.perform(request).andExpect(status().isNotFound());

        verifyNoInteractions(ekaHiu, consents, data);
    }

    // F5: the status page's requeue answers how many visits it queued again.
    @Test
    public void theRequeueAnswersHowManyVisitsItQueuedAgain() throws Exception {
        settings(true, true, full());
        when(linking.requeueGivenUp()).thenReturn(3);

        mvc.perform(post("/web/abdm/hip/requeue-failed")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"requeued\":3}", true));
    }

    @Test
    public void thePrivilegeIsCheckedBeforeTheGate() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.EditOrganisationConfiguration)).when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);

        assertThrows(AvniAccessException.class, () -> hipController.patients());
        assertThrows(AvniAccessException.class, () -> hipController.requeueFailed());
        assertThrows(AvniAccessException.class, () -> hiuController.consentRequests());

        verifyNoInteractions(organisationConfigService, configRepository);
    }

    private void settings(boolean abha, boolean recordSharing, JsonObject json) throws Exception {
        org.avni.server.domain.OrganisationConfig config = new org.avni.server.domain.OrganisationConfig();
        config.setOrganisationId(ORG);
        config.setSettings(new JsonObject().with("enableAbha", abha).with("enableAbdmRecordSharing", recordSharing));
        when(organisationConfigService.getOrganisationConfigByOrgId(ORG)).thenReturn(config);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(json);
        when(configRepository.getEkaSystemConfig(ORG)).thenReturn(new EkaSystemConfig(row));
    }

    private static JsonObject full() {
        return new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.HIU_ID, "hiu-1")
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1")
                .with(EkaSystemConfig.AVNI_SYSTEM_USER, "system-user-1")
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, "encrypted-key-1");
    }
}
