package org.avni.server.web;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.MetabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web/metabase/admin")
public class MetabaseInternalController {
    private final AccessControlService accessControlService;
    private final MetabaseService metabaseService;

    @Autowired
    public MetabaseInternalController(AccessControlService accessControlService, MetabaseService metabaseService) {
        this.accessControlService = accessControlService;
        this.metabaseService = metabaseService;
    }

    @PostMapping("/changeSchedule")
    public void changeSchedule() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        this.metabaseService.fixDatabaseSyncSchedule();
    }
}
