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
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class CannedAnalyticsRunner implements ItemWriter<Void> {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsRunner.class);

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
    @Value("#{jobParameters['actionType']}")
    private String actionType;

    @Autowired
    public CannedAnalyticsRunner(AuthService authService, OrganisationConfigService organisationConfigService, OrganisationRepository organisationRepository, GroupsService groupsService, MetabaseService metabaseService, DatabaseService databaseService) {
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

    @Override
    public void write(Chunk<? extends Void> chunk) throws Exception {
        synchronized (String.format("%s-CANNED-ANALYTICS-JOB", organisationUUID).intern()) {
            Organisation organisation = organisationRepository.findByUuid(organisationUUID);
            if (CannedAnalyticsBatchActionType.Setup.name().equals(actionType)) {
                setup(organisation);
            } else if (CannedAnalyticsBatchActionType.Teardown.name().equals(actionType)) {
                tearDown(organisation);
            } else if (CannedAnalyticsBatchActionType.CreateQuestionOnly.name().equals(actionType)) {
                createQuestionsOnly();
            } else {
                logger.error("Invalid action type: {}", actionType);
            }
        }
    }

    private void createQuestionsOnly() {
        databaseService.addCollectionItems();
    }

    private void tearDown(Organisation organisation) {
        metabaseService.tearDownMetabase();
        if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, false);
        }
    }

    private void setup(Organisation organisation) {
        GroupContract groupContract = new GroupContract();
        groupContract.setName(Group.METABASE_USERS);
        groupsService.saveGroup(groupContract, organisation);
        metabaseService.setupMetabase();
        createQuestionsOnly();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, true);
        }
    }
}
