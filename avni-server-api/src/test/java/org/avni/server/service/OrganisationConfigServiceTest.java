package org.avni.server.service;

import jakarta.transaction.Transactional;
import org.avni.server.application.KeyType;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

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
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationRepository, null, null, null);

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
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationConfigRepository, null, null, null);

        assertThat(organisationConfigService.isMessagingEnabled(), is(false));
        assertThat(organisationConfigService.isMessagingEnabled(), is(true));
    }

    @Test
    public void shouldRemoveVoidedAddressLevelTypeFromCustomRegistrationLocations() {
        // Setup
        OrganisationConfigRepository organisationConfigRepository = mock(OrganisationConfigRepository.class);
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationConfigRepository, null, null, null);

        // Create test data
        Organisation organisation = new Organisation();
        organisation.setId(1L);

        // Create subject type setting with two location types
        SubjectTypeSetting setting = new SubjectTypeSetting();
        setting.setSubjectTypeUUID("subject-type-1");
        setting.setLocationTypeUUIDs(Arrays.asList("location-type-1", "location-type-2", "location-type-to-void"));

        // Create mock OrganisationConfig with the setting
        OrganisationConfig orgConfig = new OrganisationConfig();
        JsonObject settings = new JsonObject();
        List<SubjectTypeSetting> customRegistrationLocations = new ArrayList<>();
        customRegistrationLocations.add(setting);
        settings.put(KeyType.customRegistrationLocations.toString(), customRegistrationLocations);
        orgConfig.setSettings(settings);

        when(organisationConfigRepository.findByOrganisationId(1L)).thenReturn(orgConfig);

        // Execute method - remove the voided address level type
        organisationConfigService.removeVoidedAddressLevelTypeFromCustomRegistrationLocations(
                organisation, "location-type-to-void");

        // Verify that the repository was called to save the updated config
        ArgumentCaptor<OrganisationConfig> configCaptor = ArgumentCaptor.forClass(OrganisationConfig.class);
        verify(organisationConfigRepository).save(configCaptor.capture());

        // Verify that the voided address level type was removed
        OrganisationConfig savedConfig = configCaptor.getValue();
        List<SubjectTypeSetting> updatedSettings = savedConfig.getCustomRegistrationLocations();
        assertEquals(1, updatedSettings.size());

        SubjectTypeSetting updatedSetting = updatedSettings.get(0);
        assertEquals("subject-type-1", updatedSetting.getSubjectTypeUUID());
        assertEquals(2, updatedSetting.getLocationTypeUUIDs().size());
        assertTrue(updatedSetting.getLocationTypeUUIDs().contains("location-type-1"));
        assertTrue(updatedSetting.getLocationTypeUUIDs().contains("location-type-2"));
    }

    @Test
    public void shouldRemoveEntireSettingWhenAllLocationTypesVoided() {
        // Setup
        OrganisationConfigRepository organisationConfigRepository = mock(OrganisationConfigRepository.class);
        OrganisationConfigService organisationConfigService = new OrganisationConfigService(organisationConfigRepository, null, null, null);

        // Create test data
        Organisation organisation = new Organisation();
        organisation.setId(1L);

        // Create subject type setting with only the location type that will be voided
        SubjectTypeSetting setting = new SubjectTypeSetting();
        setting.setSubjectTypeUUID("subject-type-1");
        setting.setLocationTypeUUIDs(Collections.singletonList("location-type-to-void"));

        // Create a second setting that should be unaffected
        SubjectTypeSetting otherSetting = new SubjectTypeSetting();
        otherSetting.setSubjectTypeUUID("subject-type-2");
        otherSetting.setLocationTypeUUIDs(Collections.singletonList("location-type-other"));

        // Create mock OrganisationConfig with the settings
        OrganisationConfig orgConfig = new OrganisationConfig();
        JsonObject settings = new JsonObject();
        List<SubjectTypeSetting> customRegistrationLocations = new ArrayList<>();
        customRegistrationLocations.add(setting);
        customRegistrationLocations.add(otherSetting);
        settings.put(KeyType.customRegistrationLocations.toString(), customRegistrationLocations);
        orgConfig.setSettings(settings);

        when(organisationConfigRepository.findByOrganisationId(1L)).thenReturn(orgConfig);

        // Execute method - remove the only location type from the first setting
        organisationConfigService.removeVoidedAddressLevelTypeFromCustomRegistrationLocations(
                organisation, "location-type-to-void");

        // Verify that the repository was called to save the updated config
        ArgumentCaptor<OrganisationConfig> configCaptor = ArgumentCaptor.forClass(OrganisationConfig.class);
        verify(organisationConfigRepository).save(configCaptor.capture());

        // Verify that the entire setting was removed
        OrganisationConfig savedConfig = configCaptor.getValue();
        List<SubjectTypeSetting> updatedSettings = savedConfig.getCustomRegistrationLocations();
        assertEquals(1, updatedSettings.size());

        // Only the second setting should remain
        SubjectTypeSetting remainingSetting = updatedSettings.get(0);
        assertEquals("subject-type-2", remainingSetting.getSubjectTypeUUID());
        assertEquals(1, remainingSetting.getLocationTypeUUIDs().size());
        assertTrue(remainingSetting.getLocationTypeUUIDs().contains("location-type-other"));
    }
}
