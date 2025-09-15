package org.avni.server.importer.batch;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.DbRoleRepository;
import org.avni.server.framework.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AvniSpringBatchJobHelper {
    private final AuthService authService;
    private final EntityManager entityManager;
    private static final Logger logger = LoggerFactory.getLogger(AvniSpringBatchJobHelper.class);

    @Autowired
    public AvniSpringBatchJobHelper(AuthService authService, EntityManager entityManager) {
        this.authService = authService;
        this.entityManager = entityManager;
    }

    public void authenticate(Long userId, String organisationUUID) {
        try {
            DbRoleRepository.setDbRoleNone(entityManager);
            authService.authenticateByUserId(userId, organisationUUID);
            DbRoleRepository.setDbRoleFromContext(entityManager);
        } catch (Exception e) {
            logger.error("Error authenticating user {} for organisation {}", userId, organisationUUID, e);
            throw e;
        }
    }
}
