package org.avni.server.service;

import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;

public interface IdpService {
    void createUser(User user, OrganisationConfig organisationConfig) throws IDPException;

    void createInActiveUser(User user, OrganisationConfig organisationConfig) throws IDPException;

    void updateUser(User user);

    void disableUser(User user);

    void deleteUser(User user);

    boolean resetPassword(User user, String password) throws IDPException;

    void createUserWithPassword(User user, String password, OrganisationConfig organisationConfig) throws IDPException;

    void createSuperAdmin(User user, String password) throws IDPException;

    boolean exists(User user);

    void createUserIfNotExists(User user, OrganisationConfig organisationConfig) throws IDPException;

    long getLastLoginTime(User user);

    void enableUser(User user);

    void resendPassword(User user);
}
