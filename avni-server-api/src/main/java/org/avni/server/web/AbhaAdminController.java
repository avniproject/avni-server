package org.avni.server.web;

import org.avni.server.service.abha.AbhaAdminService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.EkaConfigRequest;
import org.avni.server.web.response.EkaConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

// Super administrator only. The admin user holds no roles, so there is no @PreAuthorize; assertIsSuperAdmin decides.
@RestController
public class AbhaAdminController {
    private final AccessControlService accessControlService;
    private final AbhaAdminService abhaAdminService;

    @Autowired
    public AbhaAdminController(AccessControlService accessControlService, AbhaAdminService abhaAdminService) {
        this.accessControlService = accessControlService;
        this.abhaAdminService = abhaAdminService;
    }

    @PutMapping("/abha/admin/ekaConfig")
    @Transactional
    public EkaConfigResponse saveEkaConfig(@RequestParam("organisationUUID") String organisationUUID, @RequestBody EkaConfigRequest request) {
        accessControlService.assertIsSuperAdmin();
        return abhaAdminService.saveEkaConfig(organisationUUID, request);
    }

    @GetMapping("/abha/admin/ekaConfig")
    @Transactional(readOnly = true)
    public EkaConfigResponse getEkaConfig(@RequestParam("organisationUUID") String organisationUUID) {
        accessControlService.assertIsSuperAdmin();
        return abhaAdminService.getEkaConfig(organisationUUID);
    }
}
