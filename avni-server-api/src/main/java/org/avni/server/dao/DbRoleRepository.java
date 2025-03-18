package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbRoleRepository {
    private static final Logger logger = LoggerFactory.getLogger(RoleSwitchableRepository.class);

    public static void setDbRoleFromContext(EntityManager entityManager) {
        String dbUser = UserContextHolder.getOrganisation().getDbUser();
        logger.info("Setting role back to user: " + dbUser);
        Query setRoleBackToOrgDbUser = entityManager.createNativeQuery("set role \"" + dbUser + "\"");
        setRoleBackToOrgDbUser.executeUpdate();
    }

    public static void setDbRoleNone(EntityManager entityManager) {
        logger.info("Setting role to none");
        Query resetRoleQuery = entityManager.createNativeQuery("reset role;");
        resetRoleQuery.executeUpdate();
    }
}
