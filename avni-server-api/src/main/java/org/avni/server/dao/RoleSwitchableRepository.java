package org.avni.server.dao;

import org.avni.server.framework.security.UserContextHolder;

import jakarta.persistence.*;

public abstract class RoleSwitchableRepository {
    @PersistenceContext
    protected EntityManager entityManager;

    public RoleSwitchableRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void setRoleBackToUser() {
        Query setRoleBackToOrgDbUser = entityManager.createNativeQuery("set role \"" + UserContextHolder.getOrganisation().getDbUser() + "\"");
        setRoleBackToOrgDbUser.executeUpdate();
    }

    protected void setRoleToNone() {
        Query resetRoleQuery = entityManager.createNativeQuery("reset role;");
        resetRoleQuery.executeUpdate();
    }
}
