package org.avni.server.web.request;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;

public class OrganisationConfigRequest {

    private String uuid;
    private JsonObject settings;
    private String worklistUpdationRule;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public JsonObject getSettings() {
        return settings;
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
    public static OrganisationConfigRequest fromOrganisationConfig(OrganisationConfig organisationConfig) {
        OrganisationConfigRequest configRequest = new OrganisationConfigRequest();
        configRequest.setUuid(organisationConfig.getUuid());
        // strip server-only keys so the metadata export never leaks storage routing/target config
        configRequest.setSettings(OrganisationConfig.withoutServerOnlyKeys(organisationConfig.getSettings()));
        configRequest.setWorklistUpdationRule(organisationConfig.getWorklistUpdationRule());
        return configRequest;
    }
}
