package org.avni.server.service.abdm;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Record sharing is on for an organisation only when ABHA and record sharing are switched on and its Eka settings can
// share: a HIP ID and a record-sharing client. Receiving records (HIU) needs an HIU ID besides.
public class AbdmAccessTest {
    private static final long ORG = 5L;
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final ExternalSystemConfigRepository configRepository = mock(ExternalSystemConfigRepository.class);
    private final AbdmAccess access = new AbdmAccess(organisationConfigService, configRepository);

    @Before
    public void actAsTheOrganisation() {
        UserContext context = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(ORG);
        context.setOrganisation(organisation);
        UserContextHolder.create(context);
    }

    @After
    public void clear() {
        UserContextHolder.clear();
    }

    @Test
    public void withAbhaOffNothingIsOn_andNoEkaSettingIsRead() throws Exception {
        switches(false, true);
        settings(full());

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
        verify(configRepository, never()).getEkaSystemConfig(any());
    }

    @Test
    public void withRecordSharingSwitchedOffNothingIsOn() throws Exception {
        switches(true, false);
        settings(full());

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
        verify(configRepository, never()).getEkaSystemConfig(any());
    }

    @Test
    public void withoutEkaSettingsNothingIsOn() throws Exception {
        switches(true, true);
        when(configRepository.getEkaSystemConfig(ORG)).thenThrow(new EkaNotConfiguredException("none"));

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    @Test
    public void withoutAHipIdNothingIsOn() throws Exception {
        switches(true, true);
        settings(full().with(EkaSystemConfig.HIP_IDS, List.of()));

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    @Test
    public void withoutASharingClientNothingIsOn() throws Exception {
        switches(true, true);
        settings(full().with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, null));

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    // A row saved before the check: the phone holds the M1 client's token, so a sharing client that is the M1 client is none.
    @Test
    public void aSharingClientThatIsTheM1ClientLeavesNothingOn() throws Exception {
        switches(true, true);
        settings(full().with(EkaSystemConfig.CLIENT_ID, " sharing-1 "));

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    @Test
    public void withEverythingSetBothAreOn() throws Exception {
        switches(true, true);
        settings(full());

        assertTrue(access.recordSharingOn());
        assertTrue(access.hiuOn());
    }

    @Test
    public void withoutAnHiuIdOnlyRecordSharingIsOn() throws Exception {
        switches(true, true);
        settings(full().with(EkaSystemConfig.HIU_ID, " "));

        assertTrue(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    @Test
    public void whenOffTheRequiresAnswerNotFound() throws Exception {
        switches(true, true);
        settings(full().with(EkaSystemConfig.HIU_ID, null));

        access.requireRecordSharingOn();
        AbdmRequestException hiu = assertThrows(AbdmRequestException.class, access::requireHiuOn);
        assertEquals(HttpStatus.NOT_FOUND, hiu.getStatus());

        switches(true, false);
        AbdmRequestException sharing = assertThrows(AbdmRequestException.class, access::requireRecordSharingOn);
        assertEquals(HttpStatus.NOT_FOUND, sharing.getStatus());
    }

    @Test
    public void withNoUserNothingIsOn() {
        UserContextHolder.clear();

        assertFalse(access.recordSharingOn());
        assertFalse(access.hiuOn());
    }

    // A webhook or a job has no user: the organisation is named, and its own switches and Eka settings are read.
    @Test
    public void forANamedOrganisationRecordSharingIsOnOnlyWithBothSwitchesAndEkaSettingsThatCanShare() throws Exception {
        UserContextHolder.clear();
        switchesOf(ORG, true, true);
        settings(full());
        assertTrue(access.recordSharingOn(ORG));

        settings(full().with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, null));
        assertFalse(access.recordSharingOn(ORG));

        when(configRepository.getEkaSystemConfig(ORG)).thenThrow(new EkaNotConfiguredException("none"));
        assertFalse(access.recordSharingOn(ORG));
    }

    @Test
    public void forANamedOrganisationWithASwitchOffNothingIsOn_andNoEkaSettingIsRead() throws Exception {
        switchesOf(ORG, false, true);
        assertFalse(access.recordSharingOn(ORG));
        switchesOf(ORG, true, false);
        assertFalse(access.recordSharingOn(ORG));
        when(organisationConfigService.getOrganisationConfigByOrgId(ORG)).thenReturn(null);
        assertFalse(access.recordSharingOn(ORG));

        verify(configRepository, never()).getEkaSystemConfig(any());
    }

    private void switchesOf(long organisationId, boolean abha, boolean recordSharing) {
        OrganisationConfig config = new OrganisationConfig();
        config.setOrganisationId(organisationId);
        config.setSettings(new JsonObject().with("enableAbha", abha).with("enableAbdmRecordSharing", recordSharing));
        when(organisationConfigService.getOrganisationConfigByOrgId(organisationId)).thenReturn(config);
    }

    private void switches(boolean abha, boolean recordSharing) {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(abha);
        when(organisationConfigService.isAbdmRecordSharingEnabled()).thenReturn(recordSharing);
    }

    private void settings(JsonObject json) throws Exception {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(json);
        when(configRepository.getEkaSystemConfig(ORG)).thenReturn(new EkaSystemConfig(row));
    }

    private static JsonObject full() {
        return new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.CLIENT_ID, "client-1")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1"))
                .with(EkaSystemConfig.HIU_ID, "hiu-1")
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1");
    }
}
