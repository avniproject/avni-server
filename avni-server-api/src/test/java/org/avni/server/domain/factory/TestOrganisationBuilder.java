package org.avni.server.domain.factory;

import org.avni.server.domain.Account;
import org.avni.server.domain.Organisation;

import java.util.UUID;

public class TestOrganisationBuilder {
    private final Organisation organisation = new Organisation();

    public TestOrganisationBuilder withMandatoryFields() {
        String placeholder = UUID.randomUUID().toString();
        return withUuid(placeholder).withDbUser(placeholder).withName(placeholder).withSchemaName(placeholder);
    }

    public TestOrganisationBuilder withSchemaName(String schemaName) {
        organisation.setSchemaName(schemaName);
    	return this;
    }

    public TestOrganisationBuilder withUuid(String uuid) {
        organisation.setUuid(uuid);
    	return this;
    }

    public TestOrganisationBuilder withName(String name) {
        organisation.setName(name);
    	return this;
    }

    public TestOrganisationBuilder withDbUser(String dbUser) {
        organisation.setDbUser(dbUser);
    	return this;
    }

    public TestOrganisationBuilder withAccount(Account account) {
        organisation.setAccount(account);
    	return this;
    }

    public Organisation build() {
        return organisation;
    }
}
