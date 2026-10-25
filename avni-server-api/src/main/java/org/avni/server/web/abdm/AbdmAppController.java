package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.AbhaRefusal;
import org.avni.server.service.abha.AbhaRefusedException;
import org.avni.server.service.abha.AbhaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The Android app's "share visits to ABHA": the same HIP-initiated linking as the webapp, behind the /abha checks (ABHA
// on, Eka settings, the user's health centre, the patient within the user's reach) instead of an admin privilege.
@RestController
public class AbdmAppController {
    private final AbhaService abhaService;
    private final AbdmHipLinkingService linkingService;
    private final IndividualRepository individualRepository;
    private final TransactionTemplate readOnly;

    public AbdmAppController(AbhaService abhaService, AbdmHipLinkingService linkingService, IndividualRepository individualRepository,
                             PlatformTransactionManager transactionManager) {
        this.abhaService = abhaService;
        this.linkingService = linkingService;
        this.individualRepository = individualRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    // {subjectUUID} -> {careContexts:[{reference, display, hiType, status, error, updatedAt}]}
    @PostMapping("/abha/careContexts/link")
    @PreAuthorize("hasAnyAuthority('user')")
    public Map<String, Object> link(@RequestBody JsonNode body) {
        String subjectUUID = AbdmJson.text(body, "subjectUUID");
        // The checks in a read-only transaction of their own; the linking after it calls the gateway.
        readOnly.executeWithoutResult(status -> {
            abhaService.checkOrganisationAccess();
            Individual individual = subjectUUID == null ? null : individualRepository.findByUuid(subjectUUID);
            if (individual == null || individual.isVoided()) throw AbdmRequestException.notFound("Subject not found");
            abhaService.checkSubjectWithinReach(individual);
        });
        return Map.of("careContexts", linkingService.linkCareContexts(subjectUUID));
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code", "ABDM_" + e.getStatus().name(), "message", e.getMessage()));
    }

    @ExceptionHandler(AbhaRefusedException.class)
    public ResponseEntity<Map<String, String>> refused(AbhaRefusedException e) {
        return ResponseEntity.status(statusOf(e.getRefusal())).body(Map.of("code", e.getRefusal().name()));
    }

    // As AbhaController's.
    private static HttpStatus statusOf(AbhaRefusal refusal) {
        return switch (refusal) {
            case ABHA_UNKNOWN_SUBJECT_TYPE, ABHA_NUMBER_INVALID -> HttpStatus.BAD_REQUEST;
            case ABHA_DISABLED, ABHA_NO_PRIVILEGE, ABHA_NO_HEALTH_CENTRE, ABHA_NOT_YOURS -> HttpStatus.FORBIDDEN;
            case ABHA_NO_CARD -> HttpStatus.NOT_FOUND;
            case ABHA_NOT_CONFIGURED, ABHA_SESSION_EXPIRED -> HttpStatus.CONFLICT;
            case ABHA_EKA_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
    }
}
