package org.avni.server.importer.batch.metabase;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.avni.server.dao.DbRoleRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.metabase.MetabaseDatabaseRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.Database;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.metabase.DatabaseService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

@Component
@JobScope
public class CannedAnalyticsCreateQuestionsOnlyTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsCreateQuestionsOnlyTasklet.class);

    private final AuthService authService;
    private final OrganisationRepository organisationRepository;
    private final DatabaseService databaseService;
    private final EntityManager entityManager;
    private final MetabaseService metabaseService;
    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public CannedAnalyticsCreateQuestionsOnlyTasklet(AuthService authService, OrganisationRepository organisationRepository, DatabaseService databaseService, EntityManager entityManager, MetabaseService metabaseService) {
        this.authService = authService;
        this.organisationRepository = organisationRepository;
        this.databaseService = databaseService;
        this.entityManager = entityManager;
        this.metabaseService = metabaseService;
    }

    @PostConstruct
    public void authenticateUser() {
        authService.authenticateByUserId(userId, organisationUUID);
        DbRoleRepository.setDbRoleFromContext(entityManager);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        logger.info("Creating questions for canned analytics for organisation {}", organisation.getName());
        try {
            CannedAnalyticsLockProvider.acquireLock(organisation);
            logger.info("Create questions job acquired Lock for organisation {}", organisation.getName());
            Database database = metabaseService.syncDatabase();
            logger.info("Synced database for organisation {}", organisation.getName());
            metabaseService.waitForDatabaseSyncToComplete(organisation, database);
            databaseService.addCollectionItems();
            logger.info("Created questions for canned analytics for organisation {}", organisation.getName());
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_GATEWAY || e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                logger.error("502 Bad Gateway Error: ", e);
                throw new RuntimeException("Metabase is too busy. Please try later.", e);
            }
            throw e;
        } catch (Exception e) {
            logger.error("Error creating questions for canned analytics for organisation {}", organisation.getName(), e);
            throw e;
        } finally {
            MetabaseDatabaseRepository.clearThreadLocalContext();
            CannedAnalyticsLockProvider.releaseLock(organisation);
        }
        return RepeatStatus.FINISHED;
    }
}
