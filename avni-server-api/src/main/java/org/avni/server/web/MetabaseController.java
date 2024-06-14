package org.avni.server.web;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.MetabaseService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;

    public MetabaseController(MetabaseService metabaseService, AccessControlService accessControlService) {
        this.metabaseService = metabaseService;
        this.accessControlService= accessControlService;
    }

    @PostMapping("/setup")
    public void setupMetabase() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        metabaseService.setupMetabase();
}
}
