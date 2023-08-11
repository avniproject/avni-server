package org.avni.server.domain.factory;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;

import java.util.UUID;

public class TestOrganisationConfigBuilder {
    private final OrganisationConfig organisationConfig = new OrganisationConfig();

    public OrganisationConfig build() {
        return organisationConfig;
    }

    public TestOrganisationConfigBuilder withUuid(String uuid) {
        organisationConfig.setUuid(uuid);
        return this;
    }

    public TestOrganisationConfigBuilder withMandatoryFields() {
        return withUuid(UUID.randomUUID().toString()).withSettings(new JsonObject());
    }

    public TestOrganisationConfigBuilder withOrganisationId(long organisationId) {
        organisationConfig.setOrganisationId(organisationId);
    	return this;
    }

    public TestOrganisationConfigBuilder withSettings(JsonObject settings) {
        organisationConfig.setSettings(settings);
    	return this;
    }
}
