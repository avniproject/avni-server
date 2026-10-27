package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The webapp's HIP screens: patients with an ABHA, their visits as care contexts, and linking them through Eka.
// Not @Transactional: linking calls Eka, and its webhook must see what was written before the call.
@RestController
public class AbdmHipController {
    private final AbdmHipLinkingService linkingService;
    private final EkaHipService ekaHipService;
    private final AccessControlService accessControlService;

    public AbdmHipController(AbdmHipLinkingService linkingService, EkaHipService ekaHipService,
                             AccessControlService accessControlService) {
        this.linkingService = linkingService;
        this.ekaHipService = ekaHipService;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/web/abdm/hip/patients")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> patients() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return linkingService.patientsWithAbha();
    }

    @GetMapping("/web/abdm/hip/subjects/{uuid}/care-contexts")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> careContexts(@PathVariable("uuid") String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return linkingService.careContexts(uuid);
    }

    @PostMapping("/web/abdm/hip/subjects/{uuid}/link")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> link(@PathVariable("uuid") String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return ekaHipService.linkCareContexts(uuid);
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
