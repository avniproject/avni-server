package org.avni.server.web;

import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.accessControl.PrivilegeType;
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

    @PostMapping("/setup")
    public void setupMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        metabaseService.setupMetabase();
    }

    @GetMapping("/sync-status")
    public SyncStatus getSyncStatus() {
        return databaseService.getInitialSyncStatus();
    }

    @PostMapping("/setup-toggle")
    public void toggleSetupMetabase() {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
        organisationConfig.setMetabaseSetupEnabled(true);
        organisationConfigRepository.save(organisationConfig);

        GroupContract groupContract = new GroupContract();
        groupContract.setName(Group.METABASE_USERS);
        groupsService.saveGroup(groupContract, organisation);

        metabaseService.setupMetabase();
        databaseService.addCollectionItems();

    }

    @GetMapping("/setup-status")
    public SetupStatusResponse getSetupStatus() {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        boolean isEnabled = organisationConfigService.isMetabaseSetupEnabled(organisation);
        return new SetupStatusResponse(isEnabled);
    }

    @PostMapping("/create-questions")
    public CreateQuestionsResponse createQuestions() {
        try {
            databaseService.addCollectionItems();
            databaseService.updateGlobalDashboardWithCustomQuestions();
            return new CreateQuestionsResponse(true, "Questions created successfully.");
        } catch (RuntimeException e) {
            return new CreateQuestionsResponse(false, "Database sync is not complete. Cannot create questions.");
        }
    }

}
