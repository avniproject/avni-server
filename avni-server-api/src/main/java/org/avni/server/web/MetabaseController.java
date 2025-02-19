package org.avni.server.web;

import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
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
@RequestMapping("/web/metabase")
public class MetabaseController {
    private final DatabaseService databaseService;
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;
    private final GroupsService groupsService;

    public MetabaseController(DatabaseService databaseService, MetabaseService metabaseService, AccessControlService accessControlService, OrganisationConfigService organisationConfigService, GroupsService groupsService) {
        this.databaseService = databaseService;
        this.metabaseService = metabaseService;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.groupsService = groupsService;
    }

    @GetMapping("/sync-status")
    public SyncStatus getSyncStatus() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            return databaseService.getInitialSyncStatus();
        } else {
            return SyncStatus.NOT_STARTED;
        }
    }

    @PostMapping("/setup")
    public void setupMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        createMetabaseUsersGroupInAvni(organisation);
        metabaseService.setupMetabase();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, true);
        }
    }

    @PostMapping("/teardown")
    public void tearDownMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        metabaseService.tearDownMetabase();
        if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            organisationConfigService.setMetabaseSetupEnabled(organisation, false);
        }
    }

    private void createMetabaseUsersGroupInAvni(Organisation organisation) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        GroupContract groupContract = new GroupContract();
        groupContract.setName(Group.METABASE_USERS);
        groupsService.saveGroup(groupContract, organisation);
    }

    @GetMapping("/setup-status")
    public SetupStatusResponse getSetupStatus() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        return new SetupStatusResponse(organisationConfigService.isMetabaseSetupEnabled(organisation));
    }

    @PostMapping("/create-questions")
    public CreateQuestionsResponse createQuestions() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (!organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            return new CreateQuestionsResponse(false, "Metabase Setup is not enabled. Cannot create questions.");
        }
        try {
            databaseService.addCollectionItems();
            return new CreateQuestionsResponse(true, "Questions created successfully.");
        } catch (RuntimeException e) {
            return new CreateQuestionsResponse(false, "Database sync is not complete. Cannot create questions.");
        }
    }
}
