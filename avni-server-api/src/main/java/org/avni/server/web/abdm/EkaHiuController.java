package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmConfig;
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
// Eka mode, HIU side: the sandbox stand-in for the patient approving in a PHR app, and the HIU keyset call on its own.
// The consent requests themselves go through AbdmHiuController, which hands them to Eka in Eka mode.
// Not @Transactional: both call Eka.
@RestController
public class EkaHiuController {
    private final EkaHiuService ekaHiuService;
    private final AbdmConfig cfg;
    private final AccessControlService accessControlService;

    public EkaHiuController(EkaHiuService ekaHiuService, AbdmConfig cfg, AccessControlService accessControlService) {
        this.ekaHiuService = ekaHiuService;
        this.cfg = cfg;
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
        requireEkaMode();
        Map<String, Object> answer = ekaHiuService.approveInSandbox(consentRequestId, body);
        return Boolean.TRUE.equals(answer.get("ok")) ? ResponseEntity.ok(answer) : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(answer);
    }

    // Sends our HIU public key and nonce to Eka again (PATCH /abdm/v1/hiu/keyset). Refusals reach AbdmControllerAdvice.
    @PostMapping("/web/abdm/eka/hiu/keyset")
    @PreAuthorize("hasAnyAuthority('user')")
    public Map<String, Object> registerKeyset() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        requireEkaMode();
        return ekaHiuService.registerKeyset();
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private void requireEkaMode() {
        if (!cfg.isEka()) throw AbdmRequestException.conflict("Not in Eka mode (abdm.gateway.mode=eka)");
    }
}
