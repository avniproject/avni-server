package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.hip.AbdmHipConsentService;
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
// The webapp's HIP screens: patients with an ABHA, their visits as care contexts, linking, and the consents received.
// Not @Transactional: linking calls the gateway, and the callback must see what was written before the call.
@RestController
public class AbdmHipController {
    private final AbdmHipLinkingService linkingService;
    private final AbdmHipConsentService consentService;
    private final AccessControlService accessControlService;

    public AbdmHipController(AbdmHipLinkingService linkingService, AbdmHipConsentService consentService,
                             AccessControlService accessControlService) {
        this.linkingService = linkingService;
        this.consentService = consentService;
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
        return linkingService.linkCareContexts(uuid);
    }

    @GetMapping("/web/abdm/hip/consents")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> consents() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return consentService.list();
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
