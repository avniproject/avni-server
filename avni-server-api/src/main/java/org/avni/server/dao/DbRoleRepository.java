package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbRoleRepository {
    private static final Logger logger = LoggerFactory.getLogger(RoleSwitchableRepository.class);

    public static void setDbRoleFromContext(EntityManager entityManager) {
        setDbRole(entityManager, UserContextHolder.getOrganisation());
    }

    public static void setDbRole(EntityManager entityManager, Organisation organisation) {
        logger.info("Setting role back to user: " + organisation.getDbUser());
        String setRoleQuery = "set role \"" + organisation.getDbUser() + "\"";
        Query setRoleBackToOrgDbUser = entityManager.createNativeQuery(setRoleQuery);
        setRoleBackToOrgDbUser.executeUpdate();
    }

    public static void setDbRoleNone(EntityManager entityManager) {
        logger.info("Setting role to none");
        Query resetRoleQuery = entityManager.createNativeQuery("reset role;");
        resetRoleQuery.executeUpdate();
    }

    protected static void setDbRole(EntityManager entityManager, String dbRole) {
        logger.info("Setting role to other user: " + dbRole);
        String setRoleQuery = "set role \"" + dbRole + "\"";
        Query setRoleBackToOtherUser = entityManager.createNativeQuery(setRoleQuery);
        setRoleBackToOtherUser.executeUpdate();
    }
}
