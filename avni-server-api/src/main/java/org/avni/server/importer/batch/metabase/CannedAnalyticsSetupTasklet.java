package org.avni.server.importer.batch.metabase;

import jakarta.annotation.PostConstruct;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.GroupsService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.metabase.DatabaseService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.web.request.GroupContract;
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
public class CannedAnalyticsSetupTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsSetupTasklet.class);

    private final AuthService authService;
    private final OrganisationRepository organisationRepository;
    private final GroupsService groupsService;
    private final MetabaseService metabaseService;
    private final DatabaseService databaseService;
    private final OrganisationConfigService organisationConfigService;
    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public CannedAnalyticsSetupTasklet(AuthService authService, OrganisationConfigService organisationConfigService, OrganisationRepository organisationRepository, GroupsService groupsService, MetabaseService metabaseService, DatabaseService databaseService) {
        this.authService = authService;
        this.organisationConfigService = organisationConfigService;
        this.organisationRepository = organisationRepository;
        this.groupsService = groupsService;
        this.metabaseService = metabaseService;
        this.databaseService = databaseService;
    }

    @PostConstruct
    public void authenticateUser() {
        authService.authenticateByUserId(userId, organisationUUID);
    }

    private void setup(Organisation organisation) throws InterruptedException {
        GroupContract groupContract = new GroupContract();
        groupContract.setName(Group.METABASE_USERS);
        groupsService.saveGroup(groupContract, organisation);
        metabaseService.setupMetabase();
        databaseService.addCollectionItems();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, true);
        }
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        logger.info("Setting up canned analytics for organisation {}", organisation.getName());
        try {
            CannedAnalyticsLockProvider.acquireLock(organisation);
            logger.info("Setup job acquired Lock for organisation {}", organisation.getName());
            setup(organisation);
            logger.info("Setup job completed for organisation {}", organisation.getName());
        } catch (Exception e) {
            logger.error("Error setting up canned analytics for organisation {}", organisation.getName(), e);
            throw e;
        } finally {
            CannedAnalyticsLockProvider.releaseLock(organisation);
        }
        return RepeatStatus.FINISHED;
    }
}
