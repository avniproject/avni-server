package org.avni.server.domain.factory;

import org.avni.server.domain.User;

public class UserBuilder {
    private final User user = new User();

    public UserBuilder id(long id) {
        user.setId(id);
        return this;
    }

    public UserBuilder isAdmin(boolean isAdmin) {
        user.setAdmin(isAdmin);
    	return this;
    }

    public UserBuilder userName(String name) {
        user.setUsername(name);
        return this;
    }

    public UserBuilder phoneNumber(String phoneNumber) {
        user.setPhoneNumber(phoneNumber);
        return this;
    }

    public UserBuilder organisationId(long orgId) {
        user.setOrganisationId(orgId);
        return this;
    }

    public User build() {
        return user;
    }
}
