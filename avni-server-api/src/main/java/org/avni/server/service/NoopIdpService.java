package org.avni.server.service;

import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;

public class NoopIdpService implements IdpService {
    @Override
    public UserCreateStatus createUser(User user, OrganisationConfig organisationConfig) {
        UserCreateStatus userCreateStatus = new UserCreateStatus();
        userCreateStatus.setIdpUserCreated(true);
        return userCreateStatus;
    }

    @Override
    public void updateUser(User user) {
    }

    @Override
    public void disableUser(User user) {
    }

    @Override
    public void deleteUser(User user) {
    }

    @Override
    public void enableUser(User user) {
    }

    @Override
    public boolean resetPassword(User user, String password) {
        return true;
    }

    @Override
    public UserCreateStatus createUserWithPassword(User user, String password, OrganisationConfig organisationConfig) {
        return this.createUser(user, organisationConfig);
    }

    @Override
    public UserCreateStatus createSuperAdminWithPassword(User user, String password) throws IDPException {
        return createUser(user, null);
    }

    @Override
    public boolean exists(User user) {
        return true;
    }

    @Override
    public UserCreateStatus createUserIfNotExists(User user, OrganisationConfig organisationConfig) {
        return this.createUser(user, organisationConfig);
    }
}
