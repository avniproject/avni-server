package org.avni.server.web.response;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.joda.time.DateTime;
import org.springframework.hateoas.server.core.Relation;

/**
 * The organisation config as the phone syncs it. Served instead of the entity so that the settings can be
 * trimmed for the client: a hidden concept (avniproject/avni-product#1905) configured as a search result
 * column or a filter is left out, and the stored row is untouched. Carries the fields the phone reads: settings,
 * worklistUpdationRule, and lastModifiedDateTime for the next sync.
 */
@Relation(collectionRelation = "organisationConfig")
public class OrganisationConfigResponse {
    private String uuid;
    private Long organisationId;
    private boolean voided;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;
    private JsonObject settings;
    private String worklistUpdationRule;

    public static OrganisationConfigResponse from(OrganisationConfig organisationConfig, JsonObject settings) {
        OrganisationConfigResponse response = new OrganisationConfigResponse();
        response.uuid = organisationConfig.getUuid();
        response.organisationId = organisationConfig.getOrganisationId();
        response.voided = organisationConfig.isVoided();
        response.createdDateTime = organisationConfig.getCreatedDateTime();
        response.lastModifiedDateTime = organisationConfig.getLastModifiedDateTime();
        response.settings = settings;
        response.worklistUpdationRule = organisationConfig.getWorklistUpdationRule();
        return response;
    }

    public String getUuid() {
        return uuid;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public boolean isVoided() {
        return voided;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public String getWorklistUpdationRule() {
        return worklistUpdationRule;
    }
}
