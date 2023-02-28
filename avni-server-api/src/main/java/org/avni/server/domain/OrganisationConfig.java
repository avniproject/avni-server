package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.framework.BaseJsonObject;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Entity
@Table(name = "organisation_config")
@BatchSize(size = 100)
public class OrganisationConfig extends OrganisationAwareEntity {

    //  Check keys in OrganisationConfigSettingKeys
    @Column
    @Type(type = "jsonObject")
    private JsonObject settings;

    @Column(name = "worklist_updation_rule")
    private String worklistUpdationRule;

    @Column(name = "export_settings")
    @Type(type = "jsonObject")
    private JsonObject exportSettings;

    @Deprecated
    public JsonObject getSettings() {
        return settings;
    }

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

    public class Settings {
        private final JsonObject settings;

        public Settings(JsonObject settings) {
            this.settings = settings;
        }

        public List<Extension> getExtensions() {
            List<Object> extensions = (List<Object>) settings.get(Extension.EXTENSION_DIR);
            return extensions.stream().map(map -> new Extension((Map<String, Object>) map)).collect(Collectors.toList());
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
}
