package org.avni.server.importer.batch.metabase;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.avni.server.dao.DbRoleRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.metabase.MetabaseDatabaseRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.metabase.MetabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class CannedAnalyticsTearDownTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsTearDownTasklet.class);

    private final AuthService authService;
    private final OrganisationRepository organisationRepository;
    private final MetabaseService metabaseService;
    private final EntityManager entityManager;
    private final OrganisationConfigService organisationConfigService;
    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public CannedAnalyticsTearDownTasklet(AuthService authService, OrganisationConfigService organisationConfigService, OrganisationRepository organisationRepository, MetabaseService metabaseService, EntityManager entityManager) {
        this.authService = authService;
        this.organisationConfigService = organisationConfigService;
        this.organisationRepository = organisationRepository;
        this.metabaseService = metabaseService;
        this.entityManager = entityManager;
    }

    @PostConstruct
    public void authenticateUser() {
        authService.authenticateByUserId(userId, organisationUUID);
        DbRoleRepository.setDbRoleFromContext(entityManager);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        logger.info("Tearing down canned analytics for organisation {}", organisation.getName());
        try {
            CannedAnalyticsLockProvider.acquireLock(organisation);
            logger.info("Teardown job acquired Lock for organisation {}", organisation.getName());
            metabaseService.tearDownMetabase();
            if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
                organisationConfigService.setMetabaseSetupEnabled(organisation, false);
            }
            logger.info("Tear down completed for canned analytics for organisation {}", organisation.getName());
        } finally {
            MetabaseDatabaseRepository.clearThreadLocalContext();
            CannedAnalyticsLockProvider.releaseLock(organisation);
        }
        return RepeatStatus.FINISHED;
    }
}
