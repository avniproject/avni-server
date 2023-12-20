package org.avni.server.service;

import org.avni.server.domain.Individual;
import org.avni.server.domain.MessageableEntity;
import org.avni.server.domain.User;

public class UserCreateStatus implements MessageableEntity {
    private User newUser;
    private User createdBy;
    private boolean idpUserCreated;
    private boolean defaultPasswordPermanent;
    private boolean nonDefaultPasswordSet;

    public UserCreateStatus(User newUser, User createdBy) {
        this.newUser = newUser;
        this.createdBy = createdBy;
    }

    public void setIdpUserCreated(boolean idpUserCreated) {
        this.idpUserCreated = idpUserCreated;
    }

    public boolean getIdpUserCreated() {
        return idpUserCreated;
    }

    public void setDefaultPasswordPermanent(boolean defaultPasswordPermanent) {
        this.defaultPasswordPermanent = defaultPasswordPermanent;
    }

    public boolean getDefaultPasswordPermanent() {
        return defaultPasswordPermanent;
    }

    public boolean isNonDefaultPasswordSet() {
        return nonDefaultPasswordSet;
    }

    public void setNonDefaultPasswordSet(boolean nonDefaultPasswordSet) {
        this.nonDefaultPasswordSet = nonDefaultPasswordSet;
    }

    @Override
    public Long getEntityTypeId() {
        return null;
    }

    @Override
    public Long getEntityId() {
        return newUser.getId();
    }

    @Override
    public Individual getIndividual() {
        return null;
    }

    @Override
    public User getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean isVoided() {
        return false;
    }
}
