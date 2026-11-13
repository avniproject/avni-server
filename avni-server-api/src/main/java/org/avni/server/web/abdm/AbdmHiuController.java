package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hiu.AbdmHiuConsentService;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

// The webapp's HIU screens: ask a patient for consent, follow the requests, read the records received.
// Each endpoint checks ViewAbdmRecords, then answers 404 unless the organisation can receive records (AbdmAccess.hiuOn).
// Every read of records is logged (AbdmRecordReads).
// Not @Transactional: requesting consent calls Eka.
@RestController
public class AbdmHiuController {
    private final EkaHiuService ekaHiuService;
    private final AbdmHiuConsentService consentService;
    private final AbdmHiuDataService dataService;
    private final AccessControlService accessControlService;
    private final AbdmAccess access;

    public AbdmHiuController(EkaHiuService ekaHiuService, AbdmHiuConsentService consentService, AbdmHiuDataService dataService,
                             AccessControlService accessControlService, AbdmAccess access) {
        this.ekaHiuService = ekaHiuService;
        this.consentService = consentService;
        this.dataService = dataService;
        this.accessControlService = accessControlService;
        this.access = access;
    }

    // {abhaAddress, from:"YYYY-MM-DD", to:"YYYY-MM-DD", expiry:"YYYY-MM-DD", hiTypes:[...], purposeCode, requesterName,
    // requesterRegistrationNumber} -> {uuid, status}. The requester, a doctor, is named on every request; 400 without.
    @PostMapping("/web/abdm/hiu/consent-requests")
    @PreAuthorize("hasAnyAuthority('user')")
    public Map<String, Object> requestConsent(@RequestBody JsonNode body) {
        accessControlService.checkPrivilege(PrivilegeType.ViewAbdmRecords);
        access.requireHiuOn();
        return ekaHiuService.requestConsent(AbdmJson.text(body, "abhaAddress"), date(body, "from"), date(body, "to"),
                AbdmFlows.texts(AbdmJson.at(body, "hiTypes"), null), AbdmJson.text(body, "purposeCode"),
                AbdmJson.text(body, "requesterName"), AbdmJson.text(body, "requesterRegistrationNumber"), date(body, "expiry"));
    }

    @GetMapping("/web/abdm/hiu/consent-requests")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> consentRequests() {
        accessControlService.checkPrivilege(PrivilegeType.ViewAbdmRecords);
        access.requireHiuOn();
        return consentService.list();
    }

    @GetMapping("/web/abdm/hiu/consent-requests/{uuid}/records")
    @PreAuthorize("hasAnyAuthority('user')")
    public List<Map<String, Object>> records(@PathVariable("uuid") String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.ViewAbdmRecords);
        access.requireHiuOn();
        return dataService.records(uuid);
    }

    @ExceptionHandler(AbdmRequestException.class)
    public ResponseEntity<Map<String, String>> refused(AbdmRequestException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private static LocalDate date(JsonNode body, String field) {
        String value = AbdmJson.text(body, field);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            throw AbdmRequestException.badRequest("'" + field + "' must be YYYY-MM-DD");
        }
    }
}
