package org.avni.server.service;

import org.avni.messaging.domain.EntityType;
import org.avni.server.common.Messageable;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.stereotype.Service;

@Service
public class NoopIdpService implements IdpService {

    @Messageable(EntityType.User)
    @Override
    public void createUser(User user, OrganisationConfig organisationConfig) {
        UserCreateStatus userCreateStatus = new UserCreateStatus(user, UserContextHolder.getUser());
        userCreateStatus.setIdpUserCreated(true);
    }

    @Override
    public void createInActiveUser(User user, OrganisationConfig organisationConfig) throws IDPException {
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
    public boolean resetPassword(User user, String password) {
        return true;
    }

    @Messageable(EntityType.User)
    @Override
    public void createUserWithPassword(User user, String password, OrganisationConfig organisationConfig) {
        this.createUser(user, organisationConfig);
    }

    @Messageable(EntityType.User)
    @Override
    public void createSuperAdmin(User user, String password) throws IDPException {
        this.createUser(user, null);
    }

    @Override
    public boolean exists(User user) {
        return true;
    }

    @Messageable(EntityType.User)
    @Override
    public void createUserIfNotExists(User user, OrganisationConfig organisationConfig) {
        this.createUser(user, organisationConfig);
    }

    @Override
    public long getLastLoginTime(User user) {
        return -1L;
    }

    @Override
    public void enableUser(User user) {
    }

    @Override
    public void resendPassword(User user) {
    }
}
