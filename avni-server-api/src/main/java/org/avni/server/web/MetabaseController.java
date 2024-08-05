package org.avni.server.web;
import org.avni.server.service.metabase.DatabaseService;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.dao.metabase.MetabaseConnector;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {
    private final DatabaseService databaseService;
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;

    public MetabaseController(DatabaseService databaseService,MetabaseService metabaseService, MetabaseConnector metabaseConnector,DatabaseRepository databaseRepository, UserService userService,AccessControlService accessControlService) {
        this.databaseService = databaseService;
        this.metabaseService = metabaseService;
        this.accessControlService= accessControlService;
    }

    @PostMapping("/setup")
    public void setupMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        metabaseService.setupMetabase();
    }

    @PostMapping("/create-questions")
    public void createQuestions() {
        databaseService.createQuestionsForSubjectTypes();
        databaseService.createQuestionsForProgramsAndEncounters();
    }

    @GetMapping("/sync-status")
    public String getSyncStatus() {
        return databaseService.getInitialSyncStatus(metabaseService.getGlobalDatabaseId());
    }

}
