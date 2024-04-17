package org.avni.server.dao;

import org.avni.server.framework.security.UserContextHolder;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

public class RoleSwitchableRepository {
    @PersistenceContext
    protected EntityManager entityManager;

    public RoleSwitchableRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void setRoleBackToUser() {
        Query setRoleBackToWhatever = entityManager.createNativeQuery("set role \"" + UserContextHolder.getOrganisation().getDbUser() + "\"");
        setRoleBackToWhatever.executeUpdate();
    }

    protected void setRoleToNone() {
        Query resetQuery = entityManager.createNativeQuery("reset role;");
        resetQuery.executeUpdate();
    }
}
