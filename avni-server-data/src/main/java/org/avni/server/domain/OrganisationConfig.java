package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.avni.server.application.KeyType;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.framework.BaseJsonObject;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.avni.server.util.ObjectMapperSingleton;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "organisation_config")
@BatchSize(size = 100)
public class OrganisationConfig extends OrganisationAwareEntity {

    //  Check keys in OrganisationConfigSettingKeys
    @Column
    @Type(value = JSONObjectUserType.class)
    private JsonObject settings;

    @Column(name = "worklist_updation_rule")
    private String worklistUpdationRule;

    @Column(name = "export_settings")
    @Type(value = JSONObjectUserType.class)
    private JsonObject exportSettings;

    /**
     * Org-config setting keys that are SERVER-ONLY and must NEVER reach a client - neither the
     * webapp ({@code /web/organisationConfig}) nor a device ({@code SyncEntityName.OrganisationConfig}).
     * Currently the per-org/per-data-class storage routing + target metadata
     * (avniproject/avni-server#1012, D17): the device must stay backend-agnostic and never see a
     * bucket/endpoint/credentialRef. Filtered out by {@link #getSettingsForSerialization()} (device
     * sync) and by {@code OrganisationConfigService.getOrganisationSettings} (web).
     */
    public static final Set<String> SERVER_ONLY_SETTING_KEYS = Set.of(
            OrganisationConfigSettingKey.storageBackends.name(),
            OrganisationConfigSettingKey.storageTargets.name()
    );

    /**
     * Returns a copy of {@code settings} with every {@link #SERVER_ONLY_SETTING_KEYS} key removed.
     * Safe to serialise to any client surface.
     */
    public static JsonObject withoutServerOnlyKeys(JsonObject settings) {
        if (settings == null) {
            return null;
        }
        JsonObject filtered = new JsonObject(settings);
        SERVER_ONLY_SETTING_KEYS.forEach(filtered::remove);
        return filtered;
    }

    // Internal use only - the raw, unfiltered settings (may contain server-only keys). NOT serialised
    // directly to clients: device sync uses getSettingsForSerialization() below.
    @JsonIgnore
    @Deprecated
    public JsonObject getSettings() {
        return settings;
    }

    /**
     * The {@code settings} as serialised to the DEVICE (Spring Data REST entity sync). Server-only
     * keys are stripped (D17) so the device never receives storage backend/bucket/endpoint/creds.
     */
    @JsonProperty("settings")
    public JsonObject getSettingsForSerialization() {
        return withoutServerOnlyKeys(settings);
    }

    @JsonIgnore
    public Settings getSettingsObject() {
        return new Settings(settings);
    }

    public void setSettings(JsonObject settings) {
        this.settings = settings;
    }
    public String getWorklistUpdationRule(){
        return worklistUpdationRule;
    }

    public void setWorklistUpdationRule(String worklistUpdationRule){
        this.worklistUpdationRule = worklistUpdationRule;
    }

    public Object getConfigValue(OrganisationConfigSettingKey organisationConfigSettingKey) {
        return settings.get(String.valueOf(organisationConfigSettingKey));
    }

    public Optional<Object> getConfigValueOptional(OrganisationConfigSettingKey organisationConfigSettingKey) {
        return Optional.ofNullable(this.getConfigValue(organisationConfigSettingKey));
    }

    public boolean getBooleanConfigValue(OrganisationConfigSettingKey organisationConfigSettingKey) {
        Object configValue = this.getConfigValue(organisationConfigSettingKey);
        if (configValue == null) {
            return false;
        }
        return configValue.equals(true);
    }

    @JsonIgnore
    public JsonObject getExportSettings() {
        return exportSettings;
    }

    public void setExportSettings(JsonObject exportSettings) {
        this.exportSettings = exportSettings;
    }
    public Boolean isFeatureEnabled(String feature) {
        return (Boolean) getSettings().getOrDefault(feature, false);
    }

    @JsonIgnore
    public List<SubjectTypeSetting> getCustomRegistrationLocations() {
        return ObjectMapperSingleton.getObjectMapper().convertValue(this.getSettings().getOrDefault(KeyType.customRegistrationLocations.toString(), Collections.EMPTY_LIST), new TypeReference<List<SubjectTypeSetting>>() {});
    }

    public SubjectTypeSetting getRegistrationSetting(SubjectType subjectType) {
        return this.getCustomRegistrationLocations().stream().filter(subjectTypeSetting -> subjectTypeSetting.getSubjectTypeUUID().equals(subjectType.getUuid())).findFirst().orElse(null);
    }

    public class Settings {
        private final JsonObject settings;

        public Settings(JsonObject settings) {
            this.settings = settings;
        }

        public List<Extension> getExtensions() {
            List<Object> extensions = (List<Object>) settings.get(Extension.EXTENSION_DIR);
            return extensions.stream().map(map -> new Extension((Map<String, Object>) map)).collect(Collectors.toList());
        }

        public boolean useKeycloakAsIdp() {
            Object value = settings.get(OrganisationConfigSettingKey.useKeycloakAsIDP.toString());
            if (value == null)
                return false;
            if (value instanceof Boolean)
                return Boolean.parseBoolean(value.toString());
            return false;
        }

        public Set<String> getSupportedLanguages() {
            if(settings.get("languages") == null
                    || !(settings.get("languages") instanceof Collection)
                    || ((Collection) settings.get("languages")).size() == 0) {
                return Collections.emptySet();
            }
            List<String> allSupportedLanguages = (List<String>) settings.get("languages");
            return new HashSet<>(allSupportedLanguages);
        }
    }

    public static class Extension extends BaseJsonObject {
        public static final String EXTENSION_DIR = "extensions";

        public Extension(Map<String, Object> map) {
            super(map);
        }

        public String getLabel() {
            return getStringValue("label");
        }

        public String getFileName() {
            return getStringValue("fileName");
        }

        public String getFilePath() {
            return getExtensionFilePath(this.getFileName());
        }

        public static String getExtensionFilePath(String fileName) {
            return String.format("%s/%s", EXTENSION_DIR, fileName);
        }
    }

    public boolean isMetabaseSetupEnabled() {
        Boolean setupEnabled = (Boolean) settings.get("metabaseSetupEnabled");
        return setupEnabled != null && setupEnabled;
    }

    public void setMetabaseSetupEnabled(boolean setupEnabled) {
        settings.put("metabaseSetupEnabled", setupEnabled);
    }

    public String getMetabaseSyncStatus() {
        return (String) settings.get("metabaseSyncStatus");
    }

    public void setMetabaseSyncStatus(String status) {
        settings.put("metabaseSyncStatus", status);
    }

}
