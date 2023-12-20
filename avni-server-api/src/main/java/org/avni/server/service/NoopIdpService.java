package org.avni.server.service;

import org.avni.messaging.domain.EntityType;
import org.avni.server.common.Messageable;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;

public class NoopIdpService implements IdpService {

    @Messageable(EntityType.User)
    @Override
    public UserCreateStatus createUser(User user, OrganisationConfig organisationConfig) {
        UserCreateStatus userCreateStatus = new UserCreateStatus(user, UserContextHolder.getUser());
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

    @Override
    public long getLastLoginTime(User user) {
        return -1L;
    }
}
