package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public abstract class RoleSwitchableRepository {
    @PersistenceContext
    protected EntityManager entityManager;

    public RoleSwitchableRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void setRoleBackToUser() {
        DbRoleRepository.setDbRoleFromContext(entityManager);
    }

    protected void setRoleToNone() {
        DbRoleRepository.setDbRoleNone(entityManager);
    }
}
