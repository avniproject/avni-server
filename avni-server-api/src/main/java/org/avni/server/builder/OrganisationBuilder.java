package org.avni.server.builder;

import org.avni.server.domain.Organisation;

public class OrganisationBuilder {
    private final Organisation organisation = new Organisation();

    public OrganisationBuilder withMediaPath(String mediaPath) {
        organisation.setMediaDirectory(mediaPath);
        return this;
    }

    public Organisation build() {
        return organisation;
    }
}
