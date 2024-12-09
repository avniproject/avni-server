package org.avni.server.service;

import jakarta.transaction.Transactional;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PrepareForTest({UserContextHolder.class})
@Transactional
public class OrganisationConfigServiceTest {
    @Test
    public void shouldRetrieveOptionalObjectFromOrganisationSettings() {
        OrganisationConfigRepository organisationRepository = mock(OrganisationConfigRepository.class);
        OrganisationConfig organisationConfig = new OrganisationConfig();
        JsonObject settings = new JsonObject().with(OrganisationConfigSettingKey.customRegistrationLocations.name(), true);
        organisationConfig.setSettings(settings);
        when(organisationRepository.findByOrganisationId(25l)).thenReturn(organisationConfig);
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationRepository, null, null, null, null);

        Organisation organisation = new Organisation();
        organisation.setId(25l);
        Optional<Object> enableComments = organisationConfigService.getOrganisationSettingsValue(organisation, OrganisationConfigSettingKey.enableComments);
        Optional<Object> optionalKeyValue = organisationConfigService.getOrganisationSettingsValue(organisation, OrganisationConfigSettingKey.customRegistrationLocations);

        assertThat(enableComments.isPresent(), is(false));
        assertThat(optionalKeyValue.isPresent(), is(true));
        assertThat(optionalKeyValue.get(), is(true));
    }

    @Test
    public void shouldCheckIfMessagingFeatureEnabled() {
        OrganisationConfigRepository organisationConfigRepository = mock(OrganisationConfigRepository.class);
        long organisationId = 25l;
        UserContext context = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);
        context.setOrganisation(organisation);
        UserContextHolder.create(context);

        OrganisationConfig organisationConfigWithoutMessagingEnabled = new OrganisationConfig();
        organisationConfigWithoutMessagingEnabled.setSettings(new JsonObject());
        OrganisationConfig organisationConfigWithMessagingEnabled = new OrganisationConfig();
        JsonObject settings = new JsonObject().with(OrganisationConfigSettingKey.enableMessaging.name(), true);
        organisationConfigWithMessagingEnabled.setSettings(settings);
        when(organisationConfigRepository.findByOrganisationId(organisationId)).thenReturn(organisationConfigWithoutMessagingEnabled).thenReturn(organisationConfigWithMessagingEnabled);
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationConfigRepository, null, null, null, null);

        assertThat(organisationConfigService.isMessagingEnabled(), is(false));
        assertThat(organisationConfigService.isMessagingEnabled(), is(true));
    }
}
