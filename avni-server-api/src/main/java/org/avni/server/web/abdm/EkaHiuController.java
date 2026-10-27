package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// HIU side, sandbox only: the stand-in for the patient approving in a PHR app. The consent requests themselves go through
// AbdmHiuController. Not @Transactional: it calls Eka.
@RestController
public class EkaHiuController {
    private final EkaHiuService ekaHiuService;
    private final AccessControlService accessControlService;

    public EkaHiuController(EkaHiuService ekaHiuService, AccessControlService accessControlService) {
        this.ekaHiuService = ekaHiuService;
        this.accessControlService = accessControlService;
    }

    // Path: the consent request id shown on the HIU screen (or the request's uuid). Body, optional:
    // {hipId, careContexts:[{id, display}]}. Eka's answer as it came: {ok, httpStatus, body} or {ok:false, httpStatus,
    // abdmCode, error, hint}; 502 when Eka refused.
    @PostMapping("/web/abdm/eka/consent-requests/{consentRequestId}/approve")
    @PreAuthorize("hasAnyAuthority('user')")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable("consentRequestId") String consentRequestId,
                                                       @RequestBody(required = false) JsonNode body) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Map<String, Object> answer = ekaHiuService.approveInSandbox(consentRequestId, body);
        return Boolean.TRUE.equals(answer.get("ok")) ? ResponseEntity.ok(answer) : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(answer);
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
