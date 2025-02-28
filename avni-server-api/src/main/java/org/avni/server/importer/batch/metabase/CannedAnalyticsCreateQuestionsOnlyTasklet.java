package org.avni.server.importer.batch.metabase;

import jakarta.annotation.PostConstruct;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.metabase.DatabaseService;
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
public class CannedAnalyticsCreateQuestionsOnlyTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsCreateQuestionsOnlyTasklet.class);

    private final AuthService authService;
    private final OrganisationRepository organisationRepository;
    private final DatabaseService databaseService;
    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public CannedAnalyticsCreateQuestionsOnlyTasklet(AuthService authService, OrganisationRepository organisationRepository, DatabaseService databaseService) {
        this.authService = authService;
        this.organisationRepository = organisationRepository;
        this.databaseService = databaseService;
    }

    @PostConstruct
    public void authenticateUser() {
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        logger.info("Creating questions for canned analytics for organisation {}", organisation.getName());
        try {
            CannedAnalyticsLockProvider.acquireLock(organisation);
            logger.info("Create questions job acquired Lock for organisation {}", organisation.getName());
            databaseService.addCollectionItems();
            logger.info("Created questions for canned analytics for organisation {}", organisation.getName());
        } finally {
            CannedAnalyticsLockProvider.releaseLock(organisation);
        }
        return RepeatStatus.FINISHED;
    }
}
