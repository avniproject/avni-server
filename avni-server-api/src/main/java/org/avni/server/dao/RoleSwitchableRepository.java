package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.avni.server.framework.security.UserContextHolder;

public abstract class RoleSwitchableRepository {
    @PersistenceContext
    protected EntityManager entityManager;

    public RoleSwitchableRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void setRoleBackToUserSafe() {
        try {
            setRoleBackToUser();
        } catch (Exception e) {
            // ignore
        }
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
