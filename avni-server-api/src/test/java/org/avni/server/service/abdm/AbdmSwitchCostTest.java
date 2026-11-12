package org.avni.server.service.abdm;

import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.hip.AbdmCareContextAspect;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmVerifiedAbhaAspect;
import org.avni.server.service.abdm.hip.AbdmVisitQueue;
import org.avni.server.service.abha.AbhaLinkAspect;
import org.avni.server.service.abha.AbhaLinkListener;
import org.avni.server.service.abha.AbhaLinkService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// What an organisation that has not turned ABHA on pays for ABDM on each save: one read of its settings row
// (organisation_config) per save, and nothing of its Eka settings or of ABDM's tables. The hooks are wired as Spring
// wires them, over the real switch reads.
public class AbdmSwitchCostTest {
    private static final long ORG = 5L;
    private final OrganisationConfigRepository organisationConfigs = mock(OrganisationConfigRepository.class);
    private final ExternalSystemConfigRepository ekaSettings = mock(ExternalSystemConfigRepository.class);
    private final AbdmVisitQueue queue = mock(AbdmVisitQueue.class);
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbhaLinkService abhaLinkService = mock(AbhaLinkService.class);

    private AbdmCareContextAspect visitHook;
    private AbhaLinkAspect subjectHook;

    @Before
    @SuppressWarnings("unchecked")
    public void wireTheHooks() {
        UserContext context = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(ORG);
        context.setOrganisation(organisation);
        UserContextHolder.create(context);
        switches(false, true);

        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationConfigs, null, null, null, null);
        AbdmAccess access = new AbdmAccess(organisationConfigService, ekaSettings);
        visitHook = new AbdmCareContextAspect(access, queue);
        AbdmVerifiedAbhaAspect verifiedAbhaHook = new AbdmVerifiedAbhaAspect(access, queue, store);
        ObjectProvider<AbhaLinkListener> listeners = mock(ObjectProvider.class);
        when(listeners.orderedStream()).thenAnswer(inv -> Stream.of(verifiedAbhaHook));
        subjectHook = new AbhaLinkAspect(organisationConfigService, abhaLinkService, listeners);
    }

    @After
    public void clear() {
        UserContextHolder.clear();
    }

    private void switches(boolean abha, boolean recordSharing) {
        OrganisationConfig config = new OrganisationConfig();
        config.setOrganisationId(ORG);
        config.setSettings(new JsonObject().with("enableAbha", abha).with("enableAbdmRecordSharing", recordSharing));
        when(organisationConfigs.findByOrganisationId(ORG)).thenReturn(config);
    }

    @Test
    public void withAbhaOffAVisitSaveReadsTheOrganisationsSettingsOnce_andTouchesNothingOfAbdm() {
        Encounter visit = new Encounter();
        visit.setUuid("visit-1");

        visitHook.queueForLinking(visit);

        verify(organisationConfigs, times(1)).findByOrganisationId(ORG);
        verifyNoMoreInteractions(organisationConfigs);
        verifyNoInteractions(ekaSettings, store, queue);
    }

    // With ABHA on, record sharing switched off: both switches come from the one read.
    @Test
    public void withRecordSharingOffAVisitSaveReadsTheOrganisationsSettingsOnce_andTouchesNothingOfAbdm() {
        switches(true, false);
        Encounter visit = new Encounter();
        visit.setUuid("visit-1");

        visitHook.queueForLinking(visit);

        verify(organisationConfigs, times(1)).findByOrganisationId(ORG);
        verifyNoMoreInteractions(organisationConfigs);
        verifyNoInteractions(ekaSettings, store, queue);
    }

    @Test
    public void withAbhaOffASubjectSaveReadsTheOrganisationsSettingsOnce_andTouchesNothingOfAbhaOrAbdm() {
        Individual subject = new Individual();
        subject.setId(7L);
        subject.setUuid("subject-1");

        subjectHook.syncLink(subject);

        verify(organisationConfigs, times(1)).findByOrganisationId(ORG);
        verifyNoMoreInteractions(organisationConfigs);
        verifyNoInteractions(abhaLinkService, ekaSettings, store, queue);
    }
}
