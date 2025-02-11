package org.avni.server.web;

import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.SyncStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.GroupsService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.DatabaseService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.web.request.GroupContract;
import org.avni.server.web.response.metabase.CreateQuestionsResponse;
import org.avni.server.web.response.metabase.SetupStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {
    private final DatabaseService databaseService;
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;
    private final GroupsService groupsService;
    private final OrganisationConfigRepository organisationConfigRepository;

    public MetabaseController(DatabaseService databaseService, MetabaseService metabaseService, AccessControlService accessControlService, OrganisationConfigService organisationConfigService, GroupsService groupsService, OrganisationConfigRepository organisationConfigRepository) {
        this.databaseService = databaseService;
        this.metabaseService = metabaseService;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.groupsService = groupsService;
        this.organisationConfigRepository = organisationConfigRepository;
    }

    @GetMapping("/sync-status")
    public SyncStatus getSyncStatus() {
        organisationConfigService.checkIfReportingMetabaseSelfServiceIsEnabled(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            return databaseService.getInitialSyncStatus();
        } else {
            return SyncStatus.NOT_STARTED;
        }
    }

    @PostMapping("/setup")
    public void setupMetabase() {
        organisationConfigService.checkIfReportingMetabaseSelfServiceIsEnabled(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, true);
        }
        createMetabaseUsersGroupInAvni(organisation);
        metabaseService.setupMetabase();
        databaseService.addCollectionItems();
    }

    private void createMetabaseUsersGroupInAvni(Organisation organisation) {
        GroupContract groupContract = new GroupContract();
        groupContract.setName(Group.METABASE_USERS);
        groupsService.saveGroup(groupContract, organisation);
    }

    @GetMapping("/setup-status")
    public SetupStatusResponse getSetupStatus() {
        organisationConfigService.checkIfReportingMetabaseSelfServiceIsEnabled(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        return new SetupStatusResponse(organisationConfigService.isMetabaseSetupEnabled(organisation));
    }

    @PostMapping("/create-questions")
    public CreateQuestionsResponse createQuestions() {
        organisationConfigService.checkIfReportingMetabaseSelfServiceIsEnabled(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            return new CreateQuestionsResponse(false, "Metabase Setup is not enabled. Cannot create questions.");
        }
        try {
            databaseService.addCollectionItems();
            databaseService.updateGlobalDashboardWithCustomQuestions();
            return new CreateQuestionsResponse(true, "Questions created successfully.");
        } catch (RuntimeException e) {
            return new CreateQuestionsResponse(false, "Database sync is not complete. Cannot create questions.");
        }
    }
}