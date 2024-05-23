package org.avni.server.domain.factory;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;

public class UserContextBuilder {
    private final UserContext userContext;

    public UserContextBuilder() {
        userContext = new UserContext();
    }

    public UserContextBuilder withOrganisation(Organisation organisation) {
        userContext.setOrganisation(organisation);
        return this;
    }

    public UserContext build() {
        return userContext;
    }
}
