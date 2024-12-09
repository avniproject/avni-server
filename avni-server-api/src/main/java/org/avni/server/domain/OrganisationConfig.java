package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.application.KeyType;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.framework.BaseJsonObject;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.webapp.SubjectTypeSetting;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import jakarta.persistence.Column;
import jakarta.persistence.*;
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

    @Deprecated
    public JsonObject getSettings() {
        return settings;
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

}
