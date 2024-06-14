package org.avni.server.web;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.MetabaseService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;

    public MetabaseController(MetabaseService metabaseService, UserService userService,AccessControlService accessControlService) {
        this.metabaseService = metabaseService;
        this.accessControlService= accessControlService;
    }

    @PostMapping("/setup")
    public void setupMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        metabaseService.setupMetabase();
}
}
